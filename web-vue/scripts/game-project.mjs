import fs from "node:fs";
import path from "node:path";
import childProcess from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  GAME_ID_PATTERN,
  JAVA_CLASS_PATTERN,
  normalizeManifest,
  relativePath,
} from "../games/runtime/web/game-manifest.mjs";

// 游戏工程、编译缓存和发布目录分别维护，调用方可以指定临时根目录进行隔离验证
export const FRONTEND_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
export const JAVA_RELEASE = "17";
const BUILD_DIRECTORY = "build";
const IMPORT_FILE_LIMIT = 10000;
const IMPORT_BYTE_LIMIT = 512 * 1024 * 1024;

// 将相对路径限定在指定目录内，并核对符号链接指向的真实位置
export function inside(
  root,
  relative,
  label,
) {
  relativePath(relative, label);
  const absolute = path.resolve(root, relative);
  const prefix = path.resolve(root) + path.sep;
  if (!absolute.startsWith(prefix)) {
    throw new Error(label + "应位于游戏目录内");
  }
  if (fs.existsSync(absolute)) {
    const realRoot = fs.realpathSync(root);
    const realFile = fs.realpathSync(absolute);
    if (realFile !== realRoot && !realFile.startsWith(realRoot + path.sep)) {
      throw new Error(label + "的链接指向目录外部");
    }
  }
  return absolute;
}

// 读取游戏清单并检查重复编号，空清单可用于尚未接入游戏的平台
export function readRegistry(frontendRoot) {
  const registry = JSON.parse(
    fs.readFileSync(path.join(frontendRoot, "games/registry.json"), "utf8"),
  );
  if (!Array.isArray(registry)) {
    throw new Error("游戏清单应为编号数组");
  }
  const ids = [];
  for (const id of registry) {
    if (
      typeof id !== "string" ||
      !GAME_ID_PATTERN.test(id) ||
      id === "runtime" ||
      id === BUILD_DIRECTORY ||
      ids.includes(id)
    ) {
      throw new Error("游戏编号无效、重复或使用了保留目录名");
    }
    ids.push(id);
  }
  return ids;
}

// 解析构建参数和运行配置，Java 源码、现有 JAR 各使用明确的输入字段
export function readProject(root, descriptor) {
  let source = descriptor;
  if (!source) {
    source = JSON.parse(fs.readFileSync(path.join(root, "game.json"), "utf8"));
  }
  if (!source || typeof source !== "object" || typeof source.id !== "string") {
    throw new Error("游戏配置应包含字符串编号");
  }
  const manifest = normalizeManifest(
    source,
    "/games/" + source.id + "/game.json",
  );
  const build = source.build;
  if (!build || typeof build !== "object") {
    throw new Error("游戏配置需要 build 构建信息");
  }
  const project = {
    root: root,
    source: source,
    manifest: manifest,
    build: build,
    inputs: [],
  };
  if (manifest.runtime === "html5") {
    if (manifest.entry !== "runtime/index.html") {
      throw new Error("网页构建入口应为 runtime/index.html");
    }
    for (const name of ["web", "cover"]) {
      const filename = inside(root, build[name], "网页构建字段 " + name);
      if (!fs.existsSync(filename)) {
        throw new Error("网页游戏缺少文件：" + build[name]);
      }
      project.inputs.push(build[name]);
    }
    if (!fs.statSync(inside(root, build.web, "网页源码")).isDirectory()
        || !fs.statSync(inside(root, build.cover, "网页封面")).isFile()) {
      throw new Error("网页源码应为目录，封面应为普通图片");
    }
    for (const file of build.licenses || []) {
      const filename = inside(root, file, "游戏来源说明");
      if (!fs.existsSync(filename) || !fs.statSync(filename).isFile()) {
        throw new Error("游戏来源说明应为已有文件");
      }
      project.inputs.push(file);
    }
    return project;
  }
  if (build.tests !== undefined && !Array.isArray(build.tests)) {
    throw new Error("build.tests 应为测试类名数组");
  }
  if (build.prepareClass !== undefined &&
      (typeof build.prepareClass !== "string" || !JAVA_CLASS_PATTERN.test(build.prepareClass))) {
    throw new Error("prepareClass 应为完整 Java 类名");
  }
  if (
    build.assets &&
    !fs.statSync(inside(root, build.assets, "素材目录")).isDirectory()
  ) {
    throw new Error("build.assets 应指向素材目录");
  }
  for (const name of ["source", "jar", "assets", "cover", "testSource", "resources"]) {
    if (build[name]) {
      const filename = inside(root, build[name], "构建字段 " + name);
      if (!fs.existsSync(filename)) {
        throw new Error("游戏缺少构建文件：" + build[name]);
      }
      project.inputs.push(build[name]);
    }
  }
  if (Boolean(build.source) === Boolean(build.jar)) {
    throw new Error("请在源码目录 source 与预编译 jar 中选择一种构建输入");
  }
  if (
    !build.cover ||
    !fs.statSync(inside(root, build.cover, "封面")).isFile()
  ) {
    throw new Error("游戏配置需要有效的 build.cover 文件");
  }
  if (Boolean(build.assets) !== Boolean(manifest.assets)) {
    throw new Error("素材目录与运行素材包应同时配置");
  }
  const outputs = ["game.jar"];
  if (build.jar && !fs.statSync(inside(root, build.jar, "游戏 JAR")).isFile()) {
    throw new Error("预编译游戏应为 JAR 文件");
  }
  if (
    build.source &&
    !fs.statSync(inside(root, build.source, "源码目录")).isDirectory()
  ) {
    throw new Error("source 应指向 Java 源码目录");
  }
  if (
    build.resources &&
    !fs.statSync(inside(root, build.resources, "Java 资源目录")).isDirectory()
  ) {
    throw new Error("resources 应指向 Java 类路径资源目录");
  }
  for (const key of ["libraries", "licenses", "include"]) {
    const entries = build[key] || [];
    if (!Array.isArray(entries)) {
      throw new Error("build." + key + " 应为路径数组");
    }
    for (const entry of entries) {
      const filename = inside(root, entry, "构建字段 " + key);
      if (!fs.existsSync(filename)) {
        throw new Error("导入文件不存在：" + entry);
      }
      project.inputs.push(entry);
      if (key === "libraries" || key === "licenses") {
        const name = path.basename(entry);
        if (!fs.statSync(filename).isFile() || outputs.includes(name)) {
          throw new Error("发布文件名称重复或不是普通文件：" + entry);
        }
        if (key === "libraries" && !name.endsWith(".jar")) {
          throw new Error("Java 依赖应为 JAR 文件");
        }
        outputs.push(name);
      }
    }
  }
  const expectedClasspath = ["game.jar"];
  for (const library of build.libraries || []) {
    expectedClasspath.push(path.basename(library));
  }
  if (
    JSON.stringify(manifest.classpath) !== JSON.stringify(expectedClasspath)
  ) {
    throw new Error("classpath 应依次对应 game.jar 与 build.libraries");
  }
  for (const test of build.tests || []) {
    if (typeof test !== "string" || !JAVA_CLASS_PATTERN.test(test)) {
      throw new Error("测试入口应为完整 Java 类名");
    }
  }
  if (build.tests && build.tests.length > 0 && !build.testSource) {
    throw new Error("配置测试入口时需要提供 testSource");
  }
  return project;
}

// 枚举工程输入中的普通文件，拒绝符号链接与过大的包，返回待复制相对文件名
export function collectImports(project) {
  const files = [];
  let totalBytes = 0;
  // 同一目录只遍历实际文件，重叠输入通过相对地址去重
  function visit(relative) {
    const absolute = inside(project.root, relative, "导入文件");
    const entry = fs.lstatSync(absolute);
    if (entry.isSymbolicLink()) {
      throw new Error("导入包请使用实际文件：" + relative);
    }
    if (entry.isDirectory()) {
      for (const name of fs.readdirSync(absolute)) {
        visit(relative + "/" + name);
      }
    } else if (entry.isFile() && !files.includes(relative)) {
      totalBytes = totalBytes + entry.size;
      if (files.length >= IMPORT_FILE_LIMIT || totalBytes > IMPORT_BYTE_LIMIT) {
        throw new Error("导入包超过文件数量或体积限制");
      }
      files.push(relative);
    }
  }
  for (const input of project.inputs) {
    visit(input);
  }
  return files;
}

// 取得可用 JDK，优先使用环境设置，再从本机已有安装目录寻找编译器
export function findJdk() {
  const candidates = [];
  if (process.env.JAVA_HOME) {
    candidates.push(process.env.JAVA_HOME);
  }
  if (process.platform === "win32" && fs.existsSync("C:/Program Files/Java")) {
    for (const entry of fs.readdirSync("C:/Program Files/Java", {
      withFileTypes: true,
    })) {
      if (entry.isDirectory()) {
        candidates.push(path.join("C:/Program Files/Java", entry.name));
      }
    }
  }
  const suffix = process.platform === "win32";
  let compiler = "javac";
  if (suffix) {
    compiler = "javac.exe";
  }
  for (const candidate of candidates) {
    const bin = path.join(candidate, "bin");
    if (fs.existsSync(path.join(bin, compiler))) {
      return bin;
    }
  }
  const found = childProcess.spawnSync("javac", ["-version"], {
    encoding: "utf8",
    windowsHide: true,
  });
  if (!found.error && found.status === 0) {
    return "";
  }
  throw new Error("构建 Java 游戏需要已有的 JDK 十七或更高版本");
}

// 使用具名工具执行编译或打包，参数以数组传入，错误时保留完整退出状态
export function runJava(
  jdk,
  tool,
  argumentsList,
  workingDirectory,
) {
  let executable = tool;
  if (process.platform === "win32") {
    executable = tool + ".exe";
  }
  if (jdk) {
    executable = path.join(jdk, executable);
  }
  const result = childProcess.spawnSync(executable, argumentsList, {
    stdio: "inherit",
    windowsHide: true,
    cwd: workingDirectory,
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(tool + " 执行失败，退出码：" + result.status);
  }
}

// 收集手写 Java 源码，输出目录由构建调用方独立提供
export function collectJava(directory, files) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const filename = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      collectJava(filename, files);
    } else if (entry.name.endsWith(".java")) {
      files.push(filename);
    }
  }
}

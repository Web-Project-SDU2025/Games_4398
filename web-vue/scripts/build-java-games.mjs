import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { pathToFileURL } from "node:url";
import { buildNativeGame } from "./build-native-game.mjs";
import {
  FRONTEND_ROOT,
  JAVA_RELEASE,
  inside,
  readRegistry,
  readProject,
  collectJava,
  findJdk,
  runJava,
} from "./game-project.mjs";

// 共用运行器与发布位置均隶属于前端，临时类文件每次使用全新目录
const RUNTIME_SOURCE = "games/runtime/src/main/java";
const RUNTIME_TEST_SOURCE = "games/runtime/src/test/java";
const RUNTIME_WEB_SOURCE = "games/runtime/web";
// 平台与游戏共用颜色、指针和滚动条样式，运行页通过构建副本加载
const SHARED_STYLE_SOURCE = "src/styles";
const SHARED_STYLE_FILES = ["tokens.css", "cursor.css"];
const RUNTIME_WEB_FILES = [
  "game-manifest.mjs",
  "game-resources.mjs",
  "game-registry.mjs",
  "game-session.mjs",
  "game-economy.mjs",
  "game-play.mjs",
  "game-display.mjs",
  "java-player.html",
  "java-player.css",
  "java-player.js",
  "pvz-menu.mjs",
  "native-player.html",
  "native-player.js",
  "native-player.css",
];
const GENERATED_RESOURCE_PATTERN = /^\d+-[a-f0-9]{20}\.bin$/;
const RUNTIME_TEST_CLASSES = [
  "platform.runtime.GameFilesTest",
  "platform.runtime.MainGameTest",
];
const RUN_TESTS = process.argv.includes("--test");
// 固定归档时间使相同源码生成相同资源指纹，页面缓存按真实内容更新
const ARCHIVE_TIME = "2020-01-01T00:00:00Z";

// 资源按原目录进入类路径，拒绝链接和覆盖 class，避免素材替换已编译的实现
function copyResources(source, destination) {
  fs.mkdirSync(destination, { recursive: true });
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const input = inside(source, entry.name, "Java 资源");
    const output = path.join(destination, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error("Java 资源应使用实际文件");
    }
    if (entry.isDirectory()) {
      copyResources(input, output);
    } else if (entry.isFile()) {
      if (entry.name.endsWith(".class") || fs.existsSync(output)) {
        throw new Error("Java 资源不能覆盖类文件或已有资源：" + entry.name);
      }
      fs.copyFileSync(input, output);
    }
  }
}

// 将运行包发布为带内容指纹的数据资源，网页后台加载，配置始终指向同一构建版本
function publishResources(manifest, output) {
  const resources = {};
  const names = [];
  for (const name of manifest.classpath) {
    names.push(name);
  }
  if (manifest.assets) {
    names.push(manifest.assets);
  }
  const directory = path.join(output, "resources");
  fs.mkdirSync(directory, { recursive: true });
  for (let index = 0; index < names.length; index = index + 1) {
    const name = names[index];
    const content = fs.readFileSync(path.join(output, name));
    const hash = crypto.createHash("sha256");
    hash.update(content);
    const digest = hash.digest("hex");
    const fingerprint = digest.substring(0, 20);
    const relative = "resources/" + index + "-" + fingerprint + ".bin";
    fs.writeFileSync(path.join(output, relative), content);
    resources[name] = { path: relative, bytes: content.length };
  }
  return resources;
}

// 配置发布成功后清理旧指纹文件，保留当前清单与用户自行放入的其他资料
function cleanPublishedResources(output, resources) {
  const directory = inside(output, "resources", "发布资源目录");
  const current = new Set();
  for (const resource of Object.values(resources)) {
    current.add(path.basename(resource.path));
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (
      entry.isFile() &&
      GENERATED_RESOURCE_PATTERN.test(entry.name) &&
      !current.has(entry.name)
    ) {
      const filename = inside(directory, entry.name, "旧发布资源");
      fs.unlinkSync(filename);
    }
  }
}

// 编译源码与适配器，预编译 JAR 的原始字节码合并到共用运行器后发布
function compile(
  project,
  jdk,
  classes,
  libraries,
  frontendRoot,
) {
  const sources = [];
  collectJava(path.join(frontendRoot, RUNTIME_SOURCE), sources);
  if (project.build.source) {
    collectJava(
      inside(project.root, project.build.source, "游戏源码"),
      sources,
    );
  } else {
    runJava(
      jdk,
      "jar",
      [
        "--extract",
        "--file",
        inside(project.root, project.build.jar, "游戏 JAR"),
      ],
      classes,
    );
  }
  const argumentsList = [
    "--release",
    JAVA_RELEASE,
    "-encoding",
    "UTF-8",
    "-cp",
    classes + path.delimiter + libraries.join(path.delimiter),
    "-d",
    classes,
  ];
  for (const source of sources) {
    argumentsList.push(source);
  }
  runJava(jdk, "javac", argumentsList);
}

// 以独立目录编译测试，再执行每个游戏声明的检查入口
function testProject(
  project,
  jdk,
  classes,
  libraries,
  frontendRoot,
) {
  if (!project.build.testSource) {
    return;
  }
  const files = [];
  collectJava(
    inside(project.root, project.build.testSource, "测试源码"),
    files,
  );
  collectJava(path.join(frontendRoot, RUNTIME_TEST_SOURCE), files);
  const output = path.join(classes, "../tests");
  fs.mkdirSync(output);
  const classpath = [classes, output, libraries.join(path.delimiter)].join(
    path.delimiter,
  );
  const argumentsList = [
    "--release",
    JAVA_RELEASE,
    "-encoding",
    "UTF-8",
    "-cp",
    classpath,
    "-d",
    output,
  ];
  for (const file of files) {
    argumentsList.push(file);
  }
  runJava(jdk, "javac", argumentsList);
  let assets = project.root;
  if (project.build.assets) {
    assets = inside(project.root, project.build.assets, "游戏素材");
  }
  fs.mkdirSync(path.join(project.root, "build"), { recursive: true });
  for (const name of project.build.tests || []) {
    runJava(jdk, "java", [
      "-Djava.awt.headless=true",
      "-cp",
      classpath,
      name,
      assets,
    ]);
  }
  for (const name of RUNTIME_TEST_CLASSES) {
    runJava(jdk, "java", ["-cp", classpath, name]);
  }
}

// 发布一个游戏，源码构建与 JAR 接入共用素材、封面、许可和运行配置
function buildProject(
  project,
  jdk,
  frontendRoot,
  tests,
) {
  const output = path.join(frontendRoot, "public/games", project.manifest.id);
  const cache = path.join(frontendRoot, "games/build");
  fs.mkdirSync(cache, { recursive: true });
  const temporary = fs.mkdtempSync(path.join(cache, project.manifest.id + "-"));
  const classes = path.join(temporary, "classes");
  fs.mkdirSync(classes);
  const libraries = [];
  fs.mkdirSync(output, { recursive: true });
  try {
    for (const relative of project.build.libraries || []) {
      const file = inside(project.root, relative, "游戏依赖");
      libraries.push(file);
      fs.copyFileSync(file, path.join(output, path.basename(file)));
    }
    compile(project, jdk, classes, libraries, frontendRoot);
    if (project.build.resources) {
      copyResources(inside(project.root, project.build.resources, "Java 资源目录"), classes);
    }
    if (project.build.prepareClass) {
      runJava(jdk, "java", [
        "-Djava.awt.headless=true", "-cp", classes + path.delimiter + libraries.join(path.delimiter),
        project.build.prepareClass, classes,
      ]);
    }
    runJava(jdk, "jar", [
      "--create",
      "--date=" + ARCHIVE_TIME,
      "--file",
      path.join(output, "game.jar"),
      "-C",
      classes,
      ".",
    ]);
    if (project.build.assets) {
      runJava(jdk, "jar", [
        "--create",
        "--date=" + ARCHIVE_TIME,
        "--file",
        path.join(output, project.manifest.assets),
        "-C",
        inside(project.root, project.build.assets, "游戏素材"),
        ".",
      ]);
    }
    const cover = inside(output, project.manifest.cover, "发布封面");
    fs.mkdirSync(path.dirname(cover), { recursive: true });
    fs.copyFileSync(
      inside(project.root, project.build.cover, "游戏封面"),
      cover,
    );
    for (const relative of project.build.licenses || []) {
      fs.copyFileSync(
        inside(project.root, relative, "游戏许可"),
        path.join(output, path.basename(relative)),
      );
    }
    project.manifest.resources = publishResources(project.manifest, output);
    fs.writeFileSync(
      path.join(output, "game.json"),
      JSON.stringify(project.manifest, null, 2) + "\n",
    );
    if (tests) {
      testProject(project, jdk, classes, libraries, frontendRoot);
    }
    cleanPublishedResources(output, project.manifest.resources);
  } finally {
    // temporary 由当前构建在已核对的缓存目录内创建，退出时清理本次生成的全部类文件
    const target = fs.realpathSync(temporary);
    const root = fs.realpathSync(cache) + path.sep;
    if (!target.startsWith(root)) {
      throw new Error("构建临时目录超出游戏缓存范围");
    }
    fs.rmSync(target, { recursive: true, force: true });
  }
  console.log("Java 游戏已构建：" + project.manifest.id);
}

// 从唯一源码清单生成站内注册表和校验模块，新增游戏只维护自己的配置
export async function buildGames(frontendRoot, tests) {
  const ids = readRegistry(frontendRoot);
  const projects = [];
  for (const id of ids) {
    projects.push(readProject(path.join(frontendRoot, "games", id)));
  }
  const jdk = findJdk();
  for (const project of projects) {
    if (project.manifest.runtime === "html5") {
      await buildNativeGame(project, frontendRoot);
    } else {
      buildProject(project, jdk, frontendRoot, tests);
    }
  }
  const addresses = [];
  for (const id of ids) {
    addresses.push("/games/" + id + "/game.json");
  }
  const publicDirectory = path.join(frontendRoot, "public/games");
  fs.mkdirSync(publicDirectory, { recursive: true });
  fs.writeFileSync(
    path.join(publicDirectory, "registry.json"),
    JSON.stringify(addresses, null, 2) + "\n",
  );
  for (const filename of RUNTIME_WEB_FILES) {
    fs.copyFileSync(
      path.join(frontendRoot, RUNTIME_WEB_SOURCE, filename),
      path.join(publicDirectory, filename),
    );
  }
  for (const filename of SHARED_STYLE_FILES) {
    fs.copyFileSync(
      path.join(frontendRoot, SHARED_STYLE_SOURCE, filename),
      path.join(publicDirectory, "platform-" + filename),
    );
  }
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  await buildGames(FRONTEND_ROOT, RUN_TESTS);
}

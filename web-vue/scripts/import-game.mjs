import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import {
  FRONTEND_ROOT,
  readProject,
  readRegistry,
  collectImports,
} from "./game-project.mjs";

// 导入包使用一份 game.json，命令行可显式指定包外的配置文件
const PACKAGE_DIRECTORY = process.argv[2];
const DESCRIPTOR_FILE = process.argv[3];

// 校验所有文件后才复制到新目录，已有游戏由维护者在原目录更新，导入不覆盖现有工程
export function importGame(
  sourceDirectory,
  descriptorFile,
  frontendRoot,
) {
  const source = path.resolve(sourceDirectory);
  const descriptor = JSON.parse(fs.readFileSync(descriptorFile, "utf8"));
  const project = readProject(source, descriptor);
  const registry = readRegistry(frontendRoot);
  if (project.manifest.id === "runtime" || project.manifest.id === "build") {
    throw new Error("游戏编号应与共用运行器和构建缓存的目录名区分");
  }
  const target = path.join(frontendRoot, "games", project.manifest.id);
  if (registry.includes(project.manifest.id) || fs.existsSync(target)) {
    throw new Error("此游戏已存在，请在原游戏目录维护更新");
  }
  const files = collectImports(project);
  const gameRoot = path.join(frontendRoot, "games");
  const staging = fs.mkdtempSync(path.join(gameRoot, ".import-"));
  const registryFile = path.join(gameRoot, "registry.json");
  const registryDraft = path.join(staging, "registry.next");
  let installed = false;
  try {
    for (const relative of files) {
      if (relative === "game.json") {
        continue;
      }
      const destination = path.join(staging, "package", relative);
      fs.mkdirSync(path.dirname(destination), { recursive: true });
      fs.copyFileSync(
        path.join(source, relative),
        destination,
        fs.constants.COPYFILE_EXCL,
      );
    }
    const prepared = path.join(staging, "package");
    fs.mkdirSync(prepared, { recursive: true });
    fs.writeFileSync(
      path.join(prepared, "game.json"),
      JSON.stringify(descriptor, null, 2) + "\n",
      { flag: "wx" },
    );
    registry.push(project.manifest.id);
    fs.writeFileSync(registryDraft, JSON.stringify(registry, null, 2) + "\n");
    fs.renameSync(prepared, target);
    installed = true;
    fs.renameSync(registryDraft, registryFile);
  } catch (error) {
    // 只回收本次新建且尚未完成登记的目标，已有游戏在导入前已经排除
    if (installed && path.dirname(target) === gameRoot) {
      fs.rmSync(target, { recursive: true, force: true });
    }
    throw error;
  } finally {
    const resolved = fs.realpathSync(staging);
    if (path.dirname(resolved) !== fs.realpathSync(gameRoot)) {
      throw new Error("导入临时目录超出游戏工程目录");
    }
    fs.rmSync(resolved, { recursive: true, force: true });
  }
  console.log(
    "已导入 " +
      project.manifest.name +
      "，运行 npm run games:build 生成发布文件",
  );
  return target;
}

// 普通导入只接受明确来源，测试导入模块时跳过命令行入口
function main() {
  if (!PACKAGE_DIRECTORY) {
    throw new Error(
      "用法：npm run games:import -- <游戏包目录> [game.json 配置文件]",
    );
  }
  let descriptor = DESCRIPTOR_FILE;
  if (!descriptor) {
    descriptor = path.join(PACKAGE_DIRECTORY, "game.json");
  }
  importGame(PACKAGE_DIRECTORY, descriptor, FRONTEND_ROOT);
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  main();
}

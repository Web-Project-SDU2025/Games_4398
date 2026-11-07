import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import childProcess from "node:child_process";
import { fileURLToPath } from "node:url";
import { importGame } from "../scripts/import-game.mjs";
import {
  FRONTEND_ROOT,
  readProject,
  readRegistry,
  findJdk,
} from "../scripts/game-project.mjs";
import { buildGames } from "../scripts/build-java-games.mjs";
import { normalizeManifest } from "../games/runtime/web/game-manifest.mjs";

// 使用独立目录准备最小游戏，源码与共用运行器均取自当前项目
function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "4398-game-import-"));
  const frontend = path.join(root, "frontend");
  const source = path.join(root, "package");
  fs.mkdirSync(path.join(frontend, "games"), { recursive: true });
  fs.mkdirSync(path.join(frontend, "src/styles"), { recursive: true });
  for (const filename of ["tokens.css", "cursor.css"]) {
    fs.copyFileSync(
      path.join(FRONTEND_ROOT, "src/styles", filename),
      path.join(frontend, "src/styles", filename),
    );
  }
  fs.writeFileSync(path.join(frontend, "games/registry.json"), "[]");
  fs.cpSync(
    path.join(FRONTEND_ROOT, "games/runtime"),
    path.join(frontend, "games/runtime"),
    { recursive: true },
  );
  const fixturePath = fileURLToPath(
    new URL("./fixtures/java-game/src", import.meta.url),
  );
  fs.cpSync(fixturePath, path.join(source, "src"), { recursive: true });
  fs.writeFileSync(
    path.join(source, "cover.png"),
    Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aJ1kAAAAASUVORK5CYII=",
      "base64",
    ),
  );
  const descriptor = {
    id: "sample",
    name: "示例游戏",
    runtime: "java17",
    mainClass: "sample.Main",
    classpath: ["game.jar"],
    cover: "assets/cover.png",
    arguments: ["{assets}", "start"],
    build: { source: "src", cover: "cover.png", libraries: [] },
  };
  const manifestFile = path.join(source, "game.json");
  fs.writeFileSync(manifestFile, JSON.stringify(descriptor));
  return {
    root: root,
    frontend: frontend,
    source: source,
    descriptor: descriptor,
    manifestFile: manifestFile,
  };
}

// 测试只清理由 fixture 创建的临时目录，项目与原游戏目录保持原值
function cleanup(loaded) {
  const root = fs.realpathSync(loaded.root);
  const temporary = fs.realpathSync(os.tmpdir()) + path.sep;
  assert.ok(root.startsWith(temporary));
  fs.rmSync(root, { recursive: true, force: true });
}

// 新游戏只维护一份配置，导入后同时支持源码构建和已有 JAR 再接入
test("通用游戏包导入、构建与预编译 JAR 接入", function testGenericImport() {
  const loaded = fixture();
  try {
    importGame(loaded.source, loaded.manifestFile, loaded.frontend);
    assert.deepEqual(readRegistry(loaded.frontend), ["sample"]);
    buildGames(loaded.frontend, false);
    const generated = path.join(loaded.frontend, "public/games/sample");
    assert.ok(fs.existsSync(path.join(generated, "game.jar")));
    const manifest = JSON.parse(
      fs.readFileSync(path.join(generated, "game.json"), "utf8"),
    );
    assert.equal(manifest.mainClass, "sample.Main");
    assert.equal(manifest.capabilities.editor, false);
    assert.equal(manifest.assets, "");
    const oldResource = path.join(
      generated,
      "resources/9-00000000000000000000.bin",
    );
    const keptResource = path.join(generated, "resources/notes.txt");
    fs.writeFileSync(oldResource, "old generated resource");
    fs.writeFileSync(keptResource, "user notes");
    const second = path.join(loaded.root, "binary-package");
    fs.mkdirSync(path.join(second, "binary"), { recursive: true });
    fs.copyFileSync(
      path.join(generated, "game.jar"),
      path.join(second, "binary/original.jar"),
    );
    fs.copyFileSync(
      path.join(loaded.source, "cover.png"),
      path.join(second, "cover.png"),
    );
    const descriptor = {
      id: "binary-game",
      name: "预编译示例",
      runtime: "java17",
      mainClass: "sample.Main",
      classpath: ["game.jar"],
      cover: "assets/cover.png",
      build: { jar: "binary/original.jar", cover: "cover.png" },
    };
    const filename = path.join(second, "game.json");
    fs.writeFileSync(filename, JSON.stringify(descriptor));
    importGame(second, filename, loaded.frontend);
    buildGames(loaded.frontend, false);
    assert.equal(fs.existsSync(oldResource), false);
    assert.equal(fs.readFileSync(keptResource, "utf8"), "user notes");
    const jar = path.join(loaded.frontend, "public/games/binary-game/game.jar");
    const jdk = findJdk();
    let java = "java";
    if (process.platform === "win32") {
      java = "java.exe";
    }
    const result = childProcess.spawnSync(
      path.join(jdk, java),
      ["-cp", jar, "sample.Main", "passed"],
      { encoding: "utf8", windowsHide: true },
    );
    assert.equal(result.status, 0);
    assert.equal(result.stdout.trim(), "passed");
    assert.equal(
      fs.readdirSync(path.join(loaded.frontend, "games/build")).length,
      0,
    );
  } finally {
    cleanup(loaded);
  }
});

// 错误配置和重复包在写入前拒绝，导入来源目录与已有游戏内容保持原值
test("通用导入检查重复编号、越界路径和缺失依赖", function testImportValidation() {
  const loaded = fixture();
  try {
    const descriptor = loaded.descriptor;
    descriptor.build.source = "../private";
    // 相对路径的父目录跳转应在读取阶段结束
    assert.throws(function parentPath() {
      readProject(loaded.source, descriptor);
    });
    descriptor.build.source = "src";
    descriptor.build.libraries = ["lib/missing.jar"];
    // 依赖缺失时不向游戏目录复制任何内容
    assert.throws(function missingDependency() {
      readProject(loaded.source, descriptor);
    });
    descriptor.build.libraries = [];
    descriptor.capabilities = { importData: true };
    // 数据导入须由专用适配器验证，普通 main 入口只负责运行
    assert.throws(function missingAdapter() {
      normalizeManifest(descriptor, "/games/sample/game.json");
    });
    delete descriptor.capabilities;
    fs.writeFileSync(loaded.manifestFile, JSON.stringify(descriptor));
    const target = importGame(
      loaded.source,
      loaded.manifestFile,
      loaded.frontend,
    );
    const before = fs.readFileSync(path.join(target, "game.json"), "utf8");
    // 重复导入保留已有游戏及其本地修改
    assert.throws(function duplicate() {
      importGame(loaded.source, loaded.manifestFile, loaded.frontend);
    });
    assert.equal(
      fs.readFileSync(path.join(target, "game.json"), "utf8"),
      before,
    );
  } finally {
    cleanup(loaded);
  }
});

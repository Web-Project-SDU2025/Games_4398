import test from "node:test";
import assert from "node:assert/strict";
import {
  installedGameInfo,
  findInstalledGame,
  gameAddress,
} from "../src/logic/gameRuntime.js";
import { normalizeManifest } from "../games/runtime/web/game-manifest.mjs";

// 配置地址和运行类型需要匹配，绝对外部路径与越界封面应在读取时被拦截
test("Java 游戏配置使用站内登记路径", function testManifest() {
  const configuration = {
    id: "pvz",
    name: "植物大战僵尸",
    runtime: "java17",
    cover: "assets/Screen/MainMenu.png",
    classpath: ["game.jar"],
    adapter: "pvz.browser.BrowserGame",
    capabilities: { editor: true, importData: true, exportData: true },
  };
  const manifest = normalizeManifest(configuration, "/games/pvz/game.json");
  const game = installedGameInfo(manifest);
  assert.equal(game.gamePath, "/games/pvz/game.json");
  assert.equal(findInstalledGame([game], game.gamePath), game);
  assert.equal(findInstalledGame([game], "https://example.com"), null);
  // 地址与游戏编号不匹配时拒绝启动
  assert.throws(function invalidManifest() {
    normalizeManifest(configuration, "/games/other/game.json");
  });
  configuration.cover = "assets/../../private.png";
  // 封面跳出游戏目录时拒绝读取
  assert.throws(function invalidCover() {
    normalizeManifest(configuration, "/games/pvz/game.json");
  });
});

// 开始游戏与编辑器使用同一 Java 页面，账号值按 URL 参数编码
test("Java 游戏和编辑器共享启动约定", function testLaunchAddress() {
  const game = { id: "pvz", capabilities: { editor: true } };
  assert.equal(
    gameAddress(game, 12, false),
    "/games/java-player.html?game=pvz&owner=12",
  );
  assert.equal(
    gameAddress(game, 12, true),
    "/games/java-player.html?game=pvz&owner=12&mode=editor",
  );
  // 游戏编号包含路径分隔符时拒绝构造入口
  assert.throws(function invalidGame() {
    gameAddress({ id: "../private" }, 12, false);
  });
});

// 普通 Java main 游戏省略编辑器和素材包，公共校验给出一致的能力默认值
test("普通 Java 入口按配置启用能力", function testMainGame() {
  const configuration = {
    id: "puzzle",
    name: "拼图",
    runtime: "java17",
    classpath: ["game.jar"],
    mainClass: "puzzle.Main",
    cover: "cover.png",
  };
  const manifest = normalizeManifest(configuration, "/games/puzzle/game.json");
  const game = installedGameInfo(manifest);
  assert.equal(game.capabilities.editor, false);
  assert.equal(game.capabilities.importData, false);
  // 未声明编辑器的游戏只提供普通开始入口
  assert.throws(function unavailableEditor() {
    gameAddress(game, 1, true);
  });
});

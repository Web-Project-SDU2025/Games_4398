import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { gameAddress, launcherGames } from "../src/logic/gameRuntime.js";

// 在内存中创建真实运行仓库，避免测试依赖实际页面和 Java 环境
function createPlayer() {
  let definition;
  // 保存真实仓库定义，测试使用其中的状态与动作
  function defineStore(name, value) {
    definition = value;
    return value;
  }
  let source = fs.readFileSync(
    new URL("../src/stores/gamePlayer.js", import.meta.url),
    "utf8",
  );
  source = source.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  source = source.replace(
    "export const useGamePlayerStore",
    "const useGamePlayerStore",
  );
  const context = { defineStore: defineStore, gameAddress: gameAddress };
  vm.createContext(context);
  vm.runInContext(source, context);
  const player = definition.state();
  for (const name of Object.keys(definition.actions)) {
    player[name] = definition.actions[name].bind(player);
  }
  return player;
}

// 构造已经安装、支持编辑器的游戏条目
function game() {
  return {
    id: "pvz",
    name: "植物大战僵尸",
    gamePath: "/games/pvz/game.json",
    capabilities: { editor: true },
  };
}

// 每个用例使用独立账户对象，切号时可以直接替换令牌
function user() {
  return { id: 12, token: "player-token", loggedIn: true, role: "ROLE_ADMIN" };
}

// 同一游戏在商城和游戏库重复打开时保留原地址和实例状态
test("运行仓库切换页面后复用原游戏", function testRuntimeReuse() {
  const player = createPlayer();
  const account = user();
  assert.equal(player.open(game(), account, "/GameLibrary", false), "");
  const address = player.address;
  const originalGame = player.game;
  player.minimise();
  assert.equal(player.expanded, false);
  assert.equal(player.resume(), "/GameLibrary");
  assert.equal(player.expanded, true);
  assert.equal(player.open(game(), account, "/GameCatalog", false), "");
  assert.equal(player.address, address);
  assert.equal(player.game, originalGame);
  assert.equal(player.routePath, "/GameCatalog");
  assert.equal(player.open(game(), account, "/GameCatalog", true), "");
  assert.equal(player.editorRequest, 1);
  assert.equal(player.address, address);
});

// 单个实例关闭前保留原游戏，账户变化后创建新入口且清空旧编辑器状态
test("关闭状态和账户切换分别处理", function testRuntimeOwnership() {
  const player = createPlayer();
  const account = user();
  player.open(game(), account, "/GameLibrary", false);
  const other = game();
  other.id = "puzzle";
  other.gamePath = "/games/puzzle/game.json";
  assert.notEqual(player.open(other, account, "/GameLibrary", false), "");
  assert.equal(player.game.id, "pvz");
  player.closing = true;
  assert.notEqual(player.open(game(), account, "/GameLibrary", false), "");
  const anotherAccount = user();
  anotherAccount.id = 13;
  anotherAccount.token = "another-token";
  assert.equal(player.open(other, anotherAccount, "/GameLibrary", false), "");
  assert.equal(player.closing, false);
  assert.equal(player.ownerToken, "another-token");
  assert.match(player.address, /owner=13/);
  assert.equal(player.game.id, "puzzle");
  player.clear();
  assert.equal(player.game, null);
  assert.equal(player.address, "");
  assert.equal(player.expanded, false);
});

// 编辑器入口保持管理员限制，直接打开的编辑器不会误标记为正式游戏
test("编辑器和登录权限保持原边界", function testEditorRules() {
  const player = createPlayer();
  const account = user();
  account.role = "ROLE_GAME_USER";
  assert.notEqual(player.open(game(), account, "/GameLibrary", true), "");
  assert.equal(player.address, "");
  account.loggedIn = false;
  assert.notEqual(player.open(game(), account, "/GameLibrary", false), "");
  account.loggedIn = true;
  account.role = "ROLE_ADMIN";
  assert.equal(player.open(game(), account, "/GameLibrary", true), "");
  const address = player.address;
  assert.equal(player.editorLaunch, true);
  assert.notEqual(player.open(game(), account, "/GameLibrary", false), "");
  assert.equal(player.address, address);
});

// 游戏条目统一包含收藏与持有状态，缺少安装包时仍保留游戏资料和不可启动状态
test("游戏库合并显示资料且保留未安装游戏", function testLibraryEntries() {
  const catalog = [
    {
      gameId: 1,
      name: "植物大战僵尸",
      category: "塔防",
      gamePath: "/games/pvz/game.json",
      description: "数据库介绍",
      owned: true,
      favorite: true,
    },
    {
      gameId: 2,
      name: "其他游戏",
      category: "益智",
      gamePath: "/games/other/game.json",
      description: "其他介绍",
      owned: false,
      favorite: false,
    },
  ];
  const installed = [
    {
      id: "pvz",
      gamePath: "/games/pvz/game.json",
      cover: "/games/pvz/media/menu.png",
      description: "游戏玩法",
      capabilities: { editor: true },
    },
  ];
  const entries = launcherGames(catalog, installed);
  assert.equal(entries.length, 2);
  assert.equal(entries[0].favorite, true);
  assert.equal(entries[0].owned, true);
  assert.equal(entries[0].cover, installed[0].cover);
  assert.equal(entries[1].installed, false);
  assert.equal(entries[1].description, "其他介绍");
  assert.equal(catalog[0].description, "数据库介绍");
});

// 消息源与账户必须同时匹配，隐藏在其他页面时仍可刷新记录和奖励
test("公共运行窗口只接收当前实例的同源消息", function testPlayerMessage() {
  let source = fs.readFileSync(
    new URL("../src/components/GamePlayer.vue", import.meta.url),
    "utf8",
  );
  source = source.substring(
    source.indexOf("<script>") + 8,
    source.indexOf("</script>"),
  );
  source = source.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  source = source.replace("export default", "const component =");
  source = source + "\nthis.component = component;";
  let changes = 0;
  let closed = 0;
  let walletReads = 0;
  const account = user();
  // 记录缓存更新次数，后端数据请求由其他测试覆盖
  function dataChanged(kind) {
    assert.ok(["records", "games"].includes(kind));
    changes = changes + 1;
  }
  // 每次读取都返回测试控制的账户令牌
  function useAppStore() {
    return { userInfo: account, dataChanged: dataChanged };
  }
  // 撤回时刷新商城归属，账户余额仍以服务端读取结果为准
  function refreshWallet() {
    walletReads = walletReads + 1;
  }
  // 返回可统计调用次数的钱包替身
  function useWalletStore() {
    return { refresh: refreshWallet };
  }
  // 标记当前实例收到关闭指令
  function closeGame() {
    closed = closed + 1;
  }
  const context = {
    useAppStore: useAppStore,
    useWalletStore: useWalletStore,
    location: { origin: "https://platform.test" },
    PLAYER_MESSAGES: { gameStopped: "stopped" },
    GAMEPLAY_MESSAGES: { recordsChanged: "records", accessRevoked: "revoked" },
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  const frameWindow = {};
  const state = {
    ownerToken: account.token,
    $refs: { frame: { contentWindow: frameWindow } },
    closeGame: closeGame,
  };
  const event = {
    origin: "https://other.test",
    source: frameWindow,
    data: { type: "records" },
  };
  context.component.methods.receiveMessage.call(state, event);
  assert.equal(changes, 0);
  event.origin = "https://platform.test";
  event.source = {};
  context.component.methods.receiveMessage.call(state, event);
  assert.equal(changes, 0);
  event.source = frameWindow;
  context.component.methods.receiveMessage.call(state, event);
  assert.equal(changes, 1);
  event.data.type = "revoked";
  context.component.methods.receiveMessage.call(state, event);
  assert.equal(changes, 3);
  assert.equal(closed, 1);
  assert.equal(walletReads, 1);
  account.token = "another-token";
  context.component.methods.receiveMessage.call(state, event);
  assert.equal(changes, 3);
  assert.equal(closed, 1);
});

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

// 管理员维护的资料用于商城与游戏库，安装清单不能覆盖介绍，未安装游戏仍保留
test("商城和游戏库使用管理员保存的资料", function testLibraryEntries() {
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
      name: "安装包旧名称",
      category: "安装包旧分类",
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
  assert.equal(entries[0].name, catalog[0].name);
  assert.equal(entries[0].category, catalog[0].category);
  assert.equal(entries[0].description, "数据库介绍");
  assert.equal(entries[1].installed, false);
  assert.equal(entries[1].description, "其他介绍");
  assert.equal(catalog[0].description, "数据库介绍");
  // 再次保存或清空介绍都以数据库为准，避免管理员删除后又显示安装包旧文案
  catalog[0].name = "管理员修改后的名称";
  catalog[0].category = "管理员修改后的分类";
  catalog[0].description = "管理员修改后的商店介绍";
  const updated = launcherGames(catalog, installed);
  assert.equal(updated[0].name, catalog[0].name);
  assert.equal(updated[0].category, catalog[0].category);
  assert.equal(updated[0].description, catalog[0].description);
  assert.equal(updated[0].gamePath, catalog[0].gamePath);
  assert.equal(updated[0].capabilities.editor, true);
  catalog[0].description = "";
  const cleared = launcherGames(catalog, installed);
  assert.equal(cleared[0].description, "");
});

// 台球按服务端固定规则结算，常规游戏的通关奖励由游戏资料决定
test("游戏卡片按玩法显示奖励条件", function testGameRewardLabel() {
  const games = launcherGames(
    [
      { gamePath: "/games/billiards/game.json", reward: 85 },
      { gamePath: "/games/pvz/game.json", reward: 15 },
      { gamePath: "/games/other/game.json", reward: 0 },
    ],
    [],
  );
  assert.equal(
    games[0].rewardLabel,
    "单人击败电脑 +20 字块币，成就奖励在分出胜负后结算",
  );
  assert.equal(games[1].rewardLabel, "通关 +15 字块币");
  assert.equal(games[2].rewardLabel, "");
});

// 载入真实运行组件的脚本，浏览器和平台接口由各用例单独提供
function loadPlayerComponent(context) {
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
  vm.createContext(context);
  vm.runInContext(source, context);
  return context.component;
}

// 运行窗口隐藏时只退出平台自己的全屏，其他页面元素不受影响
test("离开专注播放器时退出平台全屏", async function testExitPlayerFullscreen() {
  const shell = {};
  const player = { closest: closest };
  const document = { fullscreenElement: shell, exitFullscreen: exitFullscreen };
  let exitCount = 0;
  // 玩家区域只归属于当前平台容器
  function closest(selector) {
    assert.equal(selector, ".platform-shell");
    return shell;
  }
  // 浏览器可能拒绝退出，全屏状态由它的变更事件同步
  async function exitFullscreen() {
    exitCount = exitCount + 1;
    throw new Error("Fullscreen exit failed");
  }
  const component = loadPlayerComponent({ document: document });
  const state = {
    $refs: { player: player },
    exitPlayerFullscreen: component.methods.exitPlayerFullscreen,
  };
  await component.watch.visible.call(state, false);
  assert.equal(exitCount, 1);
  document.fullscreenElement = {};
  await component.watch.visible.call(state, false);
  assert.equal(exitCount, 1);
});

// 消息源与账户必须同时匹配，隐藏在其他页面时仍可刷新记录和奖励
test("公共运行窗口只接收当前实例的同源消息", function testPlayerMessage() {
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
  loadPlayerComponent(context);
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

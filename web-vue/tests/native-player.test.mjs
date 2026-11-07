import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// 加载原生运行页逻辑，授权和计时使用替身，不访问真实账号
async function fixture(allowed) {
  const parent = {};
  const child = {};
  const sent = [];
  let tracking = 0;
  let stopped = 0;
  // 保存运行器发给子游戏或父平台的消息
  function postMessage(message) {
    sent.push(message.type);
  }
  parent.postMessage = postMessage;
  child.postMessage = postMessage;
  const frame = { contentWindow: child, hidden: true, src: "" };
  const status = { hidden: false, textContent: "正在加载" };
  const body = { dataset: {} };
  // 仅返回运行器声明的两个节点
  function getElementById(id) {
    if (id === "native-game") {
      return frame;
    }
    return status;
  }
  // 直接读取固定清单，测试无需请求或解析其他游戏
  async function readRegisteredGame() {
    return { id: "billiards", name: "美式八球", runtime: "html5", entry: "runtime/index.html" };
  }
  // 账号凭证在初始化时读取一次
  function gameIdentity() {
    return { token: "fixture", userId: 12 };
  }
  // 返回持有或拒绝结果，验证未授权时不加载游戏网页
  async function economyRequest() {
    if (!allowed) {
      return { code: 1, msg: "请先购买此游戏" };
    }
    return { code: 0, data: { userId: 12 } };
  }
  // 保存就绪后的计时创建次数，重复就绪不能开启两个计时器
  function createGameplayTracker() {
    tracking = tracking + 1;
    // 模拟正常保存最终时长并释放定时器
    async function stop() {
      stopped = stopped + 1;
    }
    return { stop: stop };
  }
  // 此测试只接受父平台的关闭消息
  function playerMessage(event, source, origin) {
    if (event.source === source && event.origin === origin) {
      return event.data;
    }
    return null;
  }
  // 监听注册只需保留流程，测试直接调用真实处理函数
  function addEventListener() {
    return;
  }
  // 真实网络不会使用，授权由替身返回
  function fetch() {
    return;
  }
  const location = { origin: "https://platform.test", search: "?game=billiards" };
  const context = {
    document: { getElementById: getElementById, body: body },
    window: { parent: parent, fetch: fetch, addEventListener: addEventListener },
    location: location, localStorage: {}, URLSearchParams: URLSearchParams,
    readRegisteredGame: readRegisteredGame, gameIdentity: gameIdentity, economyRequest: economyRequest,
    createGameplayTracker: createGameplayTracker, playerMessage: playerMessage,
    PLAYER_MESSAGES: { stopGame: "stop", gameStopped: "stopped" },
  };
  let source = fs.readFileSync(new URL("../games/runtime/web/native-player.js", import.meta.url), "utf8");
  source = source.replace(/import[^;]+;/g, "");
  source = source.replace(/initialize\(\);\s*$/, "");
  vm.createContext(context);
  vm.runInContext(source, context);
  await context.initialize();
  // 按指定来源发送消息，正文是测试关注的消息类型
  async function send(sourceWindow, type, origin) {
    await context.receiveMessage({ source: sourceWindow, origin: origin, data: { type: type } });
  }
  // 返回计时器状态供测试核对
  function counts() {
    return { tracking: tracking, stopped: stopped };
  }
  return { frame: frame, status: status, child: child, parent: parent, body: body,
    send: send, sent: sent, counts: counts };
}

// 拒绝授权时不提供子网页地址，也不创建任何游玩记录
test("原生运行页在授权成功前不加载游戏", async function testAuthorization() {
  const loaded = await fixture(false);
  assert.equal(loaded.frame.src, "");
  assert.equal(loaded.frame.hidden, true);
  assert.match(loaded.status.textContent, /请先购买/);
  assert.equal(loaded.counts().tracking, 0);
});

// 只在本实例就绪后计时，外部窗口消息不改变状态，关闭只保存一次
test("原生运行页隔离就绪消息并完成关闭保存", async function testNativeLifecycle() {
  const loaded = await fixture(true);
  assert.equal(loaded.frame.src, "/games/billiards/runtime/index.html");
  assert.equal(loaded.counts().tracking, 0);
  await loaded.send({}, "4398-native-ready", "https://platform.test");
  await loaded.send(loaded.child, "4398-native-ready", "https://outside.test");
  assert.equal(loaded.counts().tracking, 0);
  await loaded.send(loaded.child, "4398-native-ready", "https://platform.test");
  await loaded.send(loaded.child, "4398-native-ready", "https://platform.test");
  assert.equal(loaded.counts().tracking, 1);
  assert.equal(loaded.body.dataset.ready, "true");
  await loaded.send(loaded.parent, "stop", "https://platform.test");
  await loaded.send(loaded.parent, "stop", "https://platform.test");
  assert.equal(loaded.counts().stopped, 1);
  assert.deepEqual(loaded.sent, ["stop", "stopped"]);
});

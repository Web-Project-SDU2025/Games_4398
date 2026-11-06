import test from "node:test";
import assert from "node:assert/strict";
import {
  createGameplayTracker,
  gameplayRequest,
} from "../games/runtime/web/game-play.mjs";
import { recordDescription } from "../src/logic/records.js";

// 构造可手动推进的浏览器环境，定时器只登记回调并由测试驱动
function testHost() {
  const callbacks = new Map();
  const messages = [];
  let time = 0;
  return {
    messages: messages,
    document: { visibilityState: "visible" },
    performance: {
      // 返回手动控制的单调时钟，测试不阻塞等待真实时间
      now: function now() {
        return time;
      },
    },
    crypto: {
      // 预览使用固定合法编号，正式代码继续由浏览器创建随机编号
      randomUUID: function randomUUID() {
        return "a88f3c18-67c7-46fa-bd50-66e8566b29a4";
      },
    },
    location: { origin: "https://platform.test" },
    parent: {
      // 保存同源平台收到的刷新消息
      postMessage: function postMessage(message, origin) {
        assert.equal(origin, "https://platform.test");
        messages.push(message);
      },
    },
    // 时间推进只供测试使用，控制普通游戏在可见页的有效秒数
    advance: function advance(milliseconds) {
      time = time + milliseconds;
    },
    // 记录轮询回调，测试通过 flush 执行相同流程
    setInterval: function setInterval(callback) {
      callbacks.set("timer", callback);
      return "timer";
    },
    // 关闭后删除定时回调
    clearInterval: function clearInterval() {
      callbacks.delete("timer");
    },
    // 保存关闭事件监听，便于验证退出时的 keepalive 请求
    addEventListener: function addEventListener(name, callback) {
      callbacks.set(name, callback);
    },
    // 正常关闭后清理退出监听
    removeEventListener: function removeEventListener(name) {
      callbacks.delete(name);
    },
    // 测试直接触发指定浏览器事件
    dispatch: function dispatch(name) {
      const callback = callbacks.get(name);
      if (callback) {
        callback();
      }
    },
  };
}

// 适配器批量上报开局、失败和胜利，开局响应丢失时只重试同一编号
test("游戏事件按顺序重试并通知记录与奖励更新", async function testAdapterEvents() {
  const host = testHost();
  const events = [
    {
      type: "begin",
      runId: "run",
      level: 0,
      hash: "official",
      activeMilliseconds: 0,
    },
    { type: "heartbeat", runId: "run", activeMilliseconds: 5000 },
    {
      type: "finish",
      runId: "run",
      outcome: "passed",
      activeMilliseconds: 10000,
    },
  ];
  const adapter = {
    // 读取后清空事件，与真实 Java 适配器的 drain 行为一致
    gameEvents: async function gameEvents() {
      return JSON.stringify(events.splice(0));
    },
  };
  const requests = [];
  let firstBegin = true;
  // 首次模拟响应丢失，重试仍返回同一挑战码
  async function fetchResource(address, options) {
    const values = JSON.parse(options.body).data;
    requests.push({ address: address, values: values });
    if (address.endsWith("/begin") && firstBegin) {
      firstBegin = false;
      throw new Error("网络暂时中断");
    }
    if (address.endsWith("/begin")) {
      return Response.json({
        code: 0,
        data: { challenge: "ticket", reward: 0 },
      });
    }
    assert.equal(values.challenge, "ticket");
    let reward = 0;
    if (address.endsWith("/finish")) {
      reward = 15;
    }
    return Response.json({ code: 0, data: { reward: reward } });
  }
  const tracker = createGameplayTracker(
    adapter,
    { id: "example" },
    { token: "token" },
    fetchResource,
    host,
  );
  await tracker.flush();
  assert.equal(requests[0].values.runId, requests[1].values.runId);
  assert.ok(requests[requests.length - 1].address.endsWith("/finish"));
  assert.equal(requests[requests.length - 1].values.activeMilliseconds, 10000);
  assert.ok(host.messages.some(hasRewardMessage));
  await tracker.stop();
  assert.equal(requests.length, 4);
});

// 查找资金刷新消息，断言实际奖励与记录刷新分别发送
function hasRewardMessage(message) {
  return message.type === "4398-wallet-changed";
}

// 普通入口无需关卡适配器也能计时，隐藏页面和编辑器期间保留原累计值
test("普通游戏自动计时且关闭时保存最终结果", async function testGenericGame() {
  const host = testHost();
  const requests = [];
  // 普通游戏使用与关卡游戏相同的服务接口
  async function fetchResource(address, options) {
    const values = JSON.parse(options.body).data;
    requests.push({ address: address, values: values });
    return Response.json({ code: 0, data: { challenge: "ticket", reward: 0 } });
  }
  const tracker = createGameplayTracker(
    null,
    { id: "puzzle" },
    { token: "token" },
    fetchResource,
    host,
  );
  await tracker.flush();
  host.advance(5000);
  await tracker.flush();
  host.document.visibilityState = "hidden";
  host.advance(5000);
  await tracker.flush();
  host.document.visibilityState = "visible";
  tracker.pause(true);
  host.advance(5000);
  await tracker.flush();
  tracker.pause(false);
  host.advance(5000);
  await tracker.stop();
  const finish = requests[requests.length - 1];
  assert.ok(finish.address.endsWith("/finish"));
  assert.equal(finish.values.activeMilliseconds, 10000);
  assert.equal(finish.values.outcome, "closed");
});

// HTTP 请求仅发送身份和事件数据，页面关闭使用 keepalive 保留最后一次结算
test("通用请求携带认证并支持退出保存", async function testGameplayRequest() {
  // 使用接口替身核对真实选项，金额字段由服务器决定
  async function fetchResource(address, options) {
    assert.equal(address, "/api/gameplay/finish");
    assert.equal(options.keepalive, true);
    assert.equal(options.headers.Authorization, "Bearer token");
    assert.equal(JSON.parse(options.body).data.amount, undefined);
    return Response.json({ code: 0, data: {} });
  }
  await gameplayRequest(
    "finish",
    { runId: "run", outcome: "failed" },
    { token: "token" },
    fetchResource,
    true,
  );
});

// 服务器撤回当前会话后只通知平台关闭，后续结束事件使用原结束状态
test("游戏撤回通知关闭且保持原奖励", async function testRevokedGame() {
  const host = testHost();
  let requests = 0;
  // 开局重试也可能读取到已撤回会话，直接返回其最终状态
  async function fetchResource() {
    requests = requests + 1;
    return Response.json({
      code: 0,
      data: { challenge: "ticket", outcome: "revoked", reward: 0 },
    });
  }
  const tracker = createGameplayTracker(
    null,
    { id: "puzzle" },
    { token: "token" },
    fetchResource,
    host,
  );
  await tracker.flush();
  assert.equal(host.messages.length, 1);
  assert.equal(host.messages[0].type, "4398-game-access-revoked");
  await tracker.stop();
  assert.equal(requests, 1);
});

// 记录描述包含账户、北京时间和结果，试玩使用独立标记
test("关卡结果统一显示为账户与时间描述", function testDescription() {
  const record = {
    playerName: "玩家",
    username: "player",
    automatic: true,
    outcome: "passed",
    levelName: "第 1 关",
    playedAt: "2026-11-06T04:05:00Z",
    mode: "official",
  };
  assert.match(recordDescription(record), /玩家于.*12:05通过第 1 关/);
  record.outcome = "failed";
  record.mode = "practice";
  assert.match(recordDescription(record), /未通过第 1 关（试玩）/);
});

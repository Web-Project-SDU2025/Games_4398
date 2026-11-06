import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { setImmediate as nextTask } from "node:timers/promises";
import {
  ADMIN_COIN_LIMIT,
  ADMIN_REASON_LIMIT,
  assetOperation,
} from "../src/logic/admin.js";

// 载入真实弹窗方法，接口替身保留请求内容和响应时机
function componentState(filename, services) {
  services.ADMIN_COIN_LIMIT = ADMIN_COIN_LIMIT;
  services.ADMIN_REASON_LIMIT = ADMIN_REASON_LIMIT;
  services.assetOperation = assetOperation;
  const source = fs.readFileSync(new URL(filename, import.meta.url), "utf8");
  const start = source.indexOf("<script>") + 8;
  const end = source.indexOf("</script>", start);
  let script = source.substring(start, end);
  script = script.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  script = script.replace("export default", "const component =");
  script = script + "\nthis.component = component;";
  vm.createContext(services);
  vm.runInContext(script, services);
  const component = services.component;
  const state = component.data();
  for (const name of Object.keys(component.methods)) {
    state[name] = component.methods[name].bind(state);
  }
  state.events = [];
  state.closed = 0;
  state.$refs = {
    dialog: {
      // 打开状态交由界面测试检查，逻辑用例只记录关闭次数
      showModal: function showModal() {
        return;
      },
      // 取消和成功关闭均记录，失败请求继续保留弹窗
      close: function close() {
        state.closed = state.closed + 1;
      },
    },
  };
  // 记录成功事件，用于验证列表刷新目标与目标账户相同
  function emit(name, userId) {
    state.events.push({ name: name, userId: userId });
  }
  state.$emit = emit;
  return state;
}

// 相同业务参数复用编号，改数量后创建新操作
test("资产操作按内容复用请求编号", function testOperationIdentity() {
  const values = {
    userId: 12,
    quantity: 10,
    direction: "grant",
    reason: "活动",
  };
  const original = assetOperation(values, null);
  assert.equal(assetOperation(values, original), original);
  values.quantity = 11;
  const changed = assetOperation(values, original);
  assert.notEqual(changed.requestId, original.requestId);
  assert.match(changed.requestId, /^[0-9a-f-]{36}$/);
});

// 调整失败时保留表单与编号，取消和非法数量均在调用接口前处理
test("字块币调整校验、取消与重试", async function testAdjustment() {
  const store = { userInfo: { token: "admin" } };
  const requests = [];
  let succeeds = false;
  // 提供测试控制的当前账户令牌
  function useAppStore() {
    return store;
  }
  // 首次模拟网络错误，后续返回成功并记录完整参数
  async function adjustBlockcoin(values) {
    requests.push(values);
    if (!succeeds) {
      throw new Error("网络中断");
    }
    return { code: 0 };
  }
  const state = componentState("../src/components/BlockcoinAdjustment.vue", {
    useAppStore: useAppStore,
    adjustBlockcoin: adjustBlockcoin,
  });
  const account = { userId: 12, username: "player", balance: 100 };
  state.open(account);
  state.close();
  assert.equal(requests.length, 0);
  state.open(account);
  state.form.reason = "活动";
  state.form.quantity = 1.5;
  await state.submit();
  assert.equal(requests.length, 0);
  state.form.quantity = 10;
  await state.submit();
  assert.equal(state.notice, "网络中断");
  assert.equal(account.balance, 100);
  succeeds = true;
  await state.submit();
  assert.equal(requests.length, 2);
  assert.equal(requests[0].requestId, requests[1].requestId);
  assert.equal(requests[1].direction, "grant");
  assert.equal(requests[1].amount, undefined);
  assert.equal(state.events[0].userId, 12);
  state.open(account);
  store.userInfo.token = "other";
  await state.submit();
  assert.equal(requests.length, 2);
});

// 列表迟到响应按序号隔离，撤回前需要原因与明确确认
test("账户游戏库查询与撤回确认", async function testLibraryRevocation() {
  const store = { userInfo: { token: "admin" } };
  const pending = [];
  const requests = [];
  // 读取当前测试令牌，模拟打开弹窗期间切换账户
  function useAppStore() {
    return store;
  }
  // 保存每次查询的完成入口，允许后一个查询先结束
  function getAccountLibrary(userId) {
    // 每个请求保存独立账户编号和完成函数
    function remember(resolve) {
      pending.push({ userId: userId, resolve: resolve });
    }
    return new Promise(remember);
  }
  // 撤回只记录参数，真实资金与归属由后端测试检查
  async function revokeAccountGame(values) {
    requests.push(values);
    return { code: 0 };
  }
  const state = componentState("../src/components/AccountGameLibrary.vue", {
    useAppStore: useAppStore,
    getAccountLibrary: getAccountLibrary,
    revokeAccountGame: revokeAccountGame,
  });
  const first = state.open({ userId: 12, username: "first" });
  const second = state.open({ userId: 13, username: "second" });
  pending[1].resolve({ code: 0, data: [{ gameId: 8, name: "植物大战僵尸" }] });
  await second;
  pending[0].resolve({ code: 0, data: [{ gameId: 9, name: "旧响应" }] });
  await first;
  assert.equal(state.games[0].gameId, 8);
  state.selectGame(state.games[0]);
  state.reason = "管理调整";
  await state.revoke();
  assert.equal(requests.length, 0);
  state.clearSelection();
  assert.equal(requests.length, 0);
  state.selectGame(state.games[0]);
  state.reason = "管理调整";
  state.confirmed = true;
  const revoked = state.revoke();
  await nextTask();
  pending[2].resolve({ code: 0, data: [] });
  await revoked;
  assert.equal(requests.length, 1);
  assert.equal(requests[0].userId, 13);
  assert.equal(requests[0].gameId, 8);
  assert.equal(requests[0].refund, undefined);
  assert.equal(state.games.length, 0);
  assert.equal(state.events[0].userId, 13);
  const switched = state.query();
  store.userInfo.token = "player";
  pending[3].resolve({ code: 0, data: [{ gameId: 10 }] });
  await switched;
  assert.equal(state.games.length, 0);
});

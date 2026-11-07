import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// 读取共用请求模块，替身只记录参数，测试数据保存在内存中
function loadRequests() {
  const changed = [];
  const store = { userInfo: { token: "original-token" } };
  let result = { code: 0 };
  let failure = null;
  let request = null;
  let afterPost = null;
  // 返回当前身份，模拟切号时同一仓库内的令牌变化
  function useAppStore() {
    return store;
  }
  // 收集缓存版本变化，失败请求保持空列表
  function dataChanged(kind) {
    changed.push(kind);
  }
  // 收集请求参数，并按测试指定的结果完成请求
  async function post(
    url,
    body,
    options,
  ) {
    request = { url: url, body: body, options: options };
    if (failure) {
      throw failure;
    }
    if (afterPost) {
      afterPost();
    }
    return { data: result };
  }
  // 修改后端替身结果，供成功和业务失败共用
  function setResult(value) {
    result = value;
  }
  // 设置网络失败，异常继续交给调用方
  function setFailure(value) {
    failure = value;
  }
  // 在请求完成前切换身份，模拟迟到的写入响应
  function changeAccountDuringRequest() {
    store.userInfo.token = "next-token";
  }
  // 启用请求期间切号场景
  function switchDuringRequest() {
    afterPost = changeAccountDuringRequest;
  }
  // 返回已捕获的请求参数
  function readRequest() {
    return request;
  }
  store.dataChanged = dataChanged;
  const filename = new URL("../src/services/genServ.js", import.meta.url);
  let source = fs.readFileSync(filename, "utf8");
  source = source.replace(/^import .*;\r?\n/gm, "");
  source = source.replace(/export /g, "");
  const context = { axios: { post: post }, useAppStore: useAppStore };
  vm.createContext(context);
  vm.runInContext(source, context);
  let gameSource = fs.readFileSync(
    new URL("../src/services/gameServ.js", import.meta.url),
    "utf8",
  );
  gameSource = gameSource.replace(/^import .*;\r?\n/gm, "");
  gameSource = gameSource.replace(/export /g, "");
  vm.runInContext(gameSource, context);
  return {
    context: context,
    changed: changed,
    setResult: setResult,
    setFailure: setFailure,
    switchDuringRequest: switchDuringRequest,
    readRequest: readRequest,
  };
}

// 共用写入按接口成功结果更新缓存，业务失败和网络失败各自保留原缓存
test("写入成功更新指定缓存，失败保留原版本", async function testWriteResults() {
  const loaded = loadRequests();
  await loaded.context.writeRequest("/save", { gameId: 1 }, "games");
  assert.deepEqual(loaded.changed, ["games"]);
  const request = loaded.readRequest();
  assert.equal(request.options.headers.Authorization, "Bearer original-token");
  assert.equal(request.body.data.gameId, 1);
  loaded.setResult({ code: 1 });
  await loaded.context.writeRequest("/save", {}, "records");
  assert.deepEqual(loaded.changed, ["games"]);
  loaded.setFailure(new Error("network failure"));
  try {
    await loaded.context.writeRequest("/save", {}, "records");
    assert.fail("此请求应返回网络失败");
  } catch (error) {
    assert.equal(error.message, "network failure");
  }
  assert.deepEqual(loaded.changed, ["games"]);
});

// 请求完成前切号时，原账号的响应只返回调用方，新账号缓存保持原状态
test("切号后迟到的写入响应保留新账户缓存版本", async function testWriteAccountRace() {
  const loaded = loadRequests();
  loaded.switchDuringRequest();
  await loaded.context.writeRequest("/save", {}, "games");
  assert.deepEqual(loaded.changed, []);
});

// 删除游戏与记录共用成功结果，业务失败和切号场景分别保留当前缓存版本
test("删除游戏携带关联清理确认并同步记录缓存", async function testGameDeletion() {
  const loaded = loadRequests();
  await loaded.context.deleteGame(12);
  assert.equal(loaded.readRequest().body.data.gameId, 12);
  assert.equal(loaded.readRequest().body.data.removeRelated, true);
  assert.deepEqual(loaded.changed, ["games", "records"]);
  loaded.setResult({ code: 1 });
  await loaded.context.deleteGame(12);
  assert.deepEqual(loaded.changed, ["games", "records"]);
  const racing = loadRequests();
  racing.switchDuringRequest();
  await racing.context.deleteGame(12);
  assert.deepEqual(racing.changed, []);
});

// 记录操作只发送隐藏地址与编号，资金和游戏归属缓存由原业务维护
test("记录操作提交隐藏请求", async function testRecordHideRequest() {
  const loaded = loadRequests();
  await loaded.context.hideRecord(4);
  const request = loaded.readRequest();
  assert.equal(request.url, "/api/game/recordHide");
  assert.equal(request.body.data.recordId, 4);
  assert.deepEqual(loaded.changed, ["records"]);
  await loaded.context.restoreRecord(4);
  assert.equal(loaded.readRequest().url, "/api/game/recordRestore");
  assert.equal(loaded.readRequest().body.data.recordId, 4);
  assert.deepEqual(loaded.changed, ["records", "records"]);
});

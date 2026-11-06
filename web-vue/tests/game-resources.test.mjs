import test from "node:test";
import assert from "node:assert/strict";
import {
  readResource,
  loadGameResources,
} from "../games/runtime/web/game-resources.mjs";

// 正常资源通过后台请求进入 Java 内存，路径使用发布指纹，原 JAR 名仅用于运行配置
test("游戏资源由网页完整预载到 Java 内存文件", async function testResourcePreload() {
  const requests = [];
  const added = [];
  const removed = [];
  const content = new Uint8Array([80, 75, 3, 4]);
  // 返回一份小型二进制响应并记录后台请求
  async function fetchResource(url) {
    requests.push(url);
    return new Response(content, {
      headers: { "content-type": "application/octet-stream" },
    });
  }
  // 模拟运行环境的内存文件写入
  function addFile(name, bytes) {
    added.push({ name: name, bytes: bytes.length });
  }
  // 记录失败时应释放的内存文件
  function removeFile(name) {
    removed.push(name);
  }
  const configuration = {
    id: "sample",
    classpath: ["game.jar"],
    assets: "assets.zip",
    resources: {
      "game.jar": { path: "resources/code.bin", bytes: 4 },
      "assets.zip": { path: "resources/assets.bin", bytes: 4 },
    },
  };
  const result = await loadGameResources(
    configuration,
    fetchResource,
    addFile,
    removeFile,
  );
  assert.deepEqual(requests, [
    "/games/sample/resources/code.bin",
    "/games/sample/resources/assets.bin",
  ]);
  assert.deepEqual(result.classpath, ["/str/4398-sample-0.jar"]);
  assert.equal(result.assets, "/str/4398-sample-assets.zip");
  assert.equal(added.length, 2);
  assert.deepEqual(removed, []);
});

// 页面错误和未完整返回均结束于可读提示，失败安装的内存文件按本次列表回收
test("资源加载失败可读且回收已预载内容", async function testResourceFailure() {
  let attempts = 0;
  // 模拟服务返回网页而不是游戏包
  async function htmlResponse() {
    attempts = attempts + 1;
    return new Response("<html>missing</html>", {
      headers: { "content-type": "text/html" },
    });
  }
  // 错误正文应由资源检查拒绝
  async function rejectHtml() {
    await readResource("/games/sample/resources/game.bin", htmlResponse, 4);
  }
  await assert.rejects(rejectHtml, /返回了网页/);
  assert.equal(attempts, 2);
  const removed = [];
  // 第一项成功，素材项模拟返回缺失状态
  async function partlyAvailable(url) {
    if (url.endsWith("assets.zip")) {
      return new Response("", { status: 404 });
    }
    return new Response(new Uint8Array([80, 75, 3, 4]));
  }
  // 内存替身接受第一项资源
  function addFile() {
    return;
  }
  // 回收列表只包含本次已经写入的资源
  function removeFile(name) {
    removed.push(name);
  }
  const configuration = {
    id: "sample",
    classpath: ["game.jar"],
    assets: "assets.zip",
    resources: {},
  };
  // 第二项资源缺失时，整个预载流程返回失败
  async function rejectPartial() {
    await loadGameResources(
      configuration,
      partlyAvailable,
      addFile,
      removeFile,
    );
  }
  await assert.rejects(rejectPartial, /资源请求失败/);
  assert.deepEqual(removed, ["/str/4398-sample-0.jar"]);
});

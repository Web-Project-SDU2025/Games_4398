import test from "node:test";
import assert from "node:assert/strict";
import {
  filterProducts,
  productCategories,
  coinAmount,
} from "../src/logic/store.js";
import {
  economyRequest,
  gameIdentity,
} from "../games/runtime/web/game-economy.mjs";

// 商品筛选同时保持分类与持有状态，筛选不改写原资料
test("商城筛选与字块币显示", function testStoreFilters() {
  const games = [
    {
      name: "植物大战僵尸",
      category: "塔防",
      description: "草坪",
      owned: false,
    },
    { name: "拼图", category: "益智", description: "方块", owned: true },
  ];
  assert.equal(filterProducts(games, "草坪", "", "all").length, 1);
  assert.equal(filterProducts(games, "", "", "owned")[0].name, "拼图");
  assert.equal(filterProducts(games, "", "塔防", "available").length, 1);
  assert.deepEqual(productCategories(games), ["塔防", "益智"]);
  assert.equal(coinAmount(100), "100");
  assert.equal(coinAmount(null), "—");
});

// 运行授权复用登录身份与现有 POST 协议，余额与奖励数额不由调用方提交
test("游戏运行授权使用当前凭证", async function testEconomyRequest() {
  const storage = {
    // 返回测试账户资料，后续请求使用读取时的身份
    getItem: function getSession() {
      return JSON.stringify({
        userInfo: { loggedIn: true, token: "token", id: 12 },
      });
    },
  };
  const identity = gameIdentity(storage);
  // 核对调用路径、认证与参数结构
  async function fetchResource(address, options) {
    assert.equal(address, "/api/store/authorize");
    assert.equal(options.headers.Authorization, "Bearer token");
    const body = JSON.parse(options.body);
    assert.equal(body.data.gamePath, "/games/example/game.json");
    assert.equal(body.data.amount, undefined);
    return Response.json({ code: 0, data: { userId: 12, editorAllowed: false } });
  }
  const result = await economyRequest(
    "authorize",
    { gamePath: "/games/example/game.json", editor: false },
    identity,
    fetchResource,
  );
  assert.equal(result.data.userId, 12);
});

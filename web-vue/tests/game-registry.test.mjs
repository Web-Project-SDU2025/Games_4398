import test from "node:test";
import assert from "node:assert/strict";
import {
  readGameRegistry,
  readRegisteredGames,
  readRegisteredGame,
} from "../games/runtime/web/game-registry.mjs";

// 提供普通 Java 游戏的最少配置，验证共享读取流程而非具体游戏内容
function configuration(id) {
  return {
    id: id,
    name: id,
    runtime: "java17",
    classpath: ["game.jar"],
    mainClass: "sample.Main",
    cover: "cover.png",
  };
}

// 列表读取全部配置，启动器只读取选中的一份，两者使用同一个配置校验
test("平台与启动器共享注册表读取", async function testRegisteredGames() {
  const requests = [];
  const addresses = ["/games/first/game.json", "/games/second/game.json"];
  // 返回独立配置并记录请求，确认启动时没有预先加载其他游戏
  async function fetchResource(address) {
    requests.push(address);
    if (address === "/games/registry.json") {
      return Response.json(addresses);
    }
    if (address === addresses[0]) {
      return Response.json(configuration("first"));
    }
    return Response.json(configuration("second"));
  }
  const selected = await readRegisteredGame("first", fetchResource);
  assert.equal(selected.id, "first");
  assert.deepEqual(requests, ["/games/registry.json", addresses[0]]);
  const games = await readRegisteredGames(fetchResource);
  assert.equal(games.length, 2);
  assert.equal(games[1].id, "second");
});

// 重复条目和越界地址在请求配置前被拒绝，未知编号也不会直接拼接访问地址
test("游戏注册表拒绝重复地址和未登记编号", async function testInvalidRegistry() {
  let registry = ["/games/first/game.json", "/games/first/game.json"];
  // 每次返回当前用例的注册表
  async function fetchRegistry() {
    return Response.json(registry);
  }
  // 重复配置应给出明确结果
  async function duplicate() {
    await readGameRegistry(fetchRegistry);
  }
  await assert.rejects(duplicate, /重复/);
  registry = ["/games/../game.json"];
  // 越界路径应在读取时结束
  async function outsideDirectory() {
    await readGameRegistry(fetchRegistry);
  }
  await assert.rejects(outsideDirectory, /无效/);
  registry = ["/games/first/game.json"];
  // 未登记编号只读取注册表
  async function unregistered() {
    await readRegisteredGame("second", fetchRegistry);
  }
  await assert.rejects(unregistered, /尚未登记/);
});

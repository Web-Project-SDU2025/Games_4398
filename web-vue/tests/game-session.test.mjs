import test from "node:test";
import assert from "node:assert/strict";
import {
  PLAYER_MESSAGES,
  gameOwner,
  playerMessage,
  editorLevel,
} from "../games/runtime/web/game-session.mjs";

// 账户目录与编号在平台和游戏页采用相同边界，异常参数按配置默认值处理
test("游戏账户目录和编辑器编号使用共享规则", function testSessionParameters() {
  assert.equal(gameOwner(12), "12");
  assert.equal(gameOwner("player-name"), "player-name");
  assert.equal(gameOwner("../other"), "standalone");
  assert.equal(gameOwner(null), "standalone");
  const limits = { minimum: 3, maximum: 99 };
  assert.equal(editorLevel("8", limits), 8);
  assert.equal(editorLevel(null, limits), 3);
  assert.equal(editorLevel("1.5", limits), 3);
  assert.equal(editorLevel("100", limits), 3);
});

// 同源页面中的其他窗口也可能发送消息，只处理当前运行实例与完整窗口状态
test("游戏窗口指令核对来源与状态字段", function testPlayerMessages() {
  const source = {};
  const origin = "http://localhost";
  const message = { type: PLAYER_MESSAGES.editorState, open: true };
  const event = { source: source, origin: origin, data: message };
  assert.equal(playerMessage(event, source, origin), message);
  assert.equal(playerMessage(event, {}, origin), null);
  assert.equal(playerMessage(event, source, "https://example.com"), null);
  event.data = { type: PLAYER_MESSAGES.editorState, open: "true" };
  assert.equal(playerMessage(event, source, origin), null);
  event.data = { type: "unknown" };
  assert.equal(playerMessage(event, source, origin), null);
  event.data = { type: PLAYER_MESSAGES.openEditor };
  assert.equal(playerMessage(event, source, origin), event.data);
});

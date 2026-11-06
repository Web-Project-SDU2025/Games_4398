import test from "node:test";
import assert from "node:assert/strict";
import { windowBounds } from "../games/runtime/web/game-display.mjs";

// 编辑器与确认弹窗可能位于不同位置，合并外框后所有窗口都应进入可见区域
test("游戏和编辑器共用完整窗口外框", function testWindowBounds() {
  const bounds = windowBounds(
    [
      { left: 320, top: 170, width: 800, height: 620 },
      { left: 50, top: 100, width: 1200, height: 800 },
      { left: -20, top: 500, width: 400, height: 500 },
    ],
    1440,
    960,
  );
  assert.deepEqual(bounds, { left: -20, top: 100, width: 1270, height: 900 });
  assert.deepEqual(windowBounds([], 1440, 960), {
    left: 0,
    top: 0,
    width: 1440,
    height: 960,
  });
});

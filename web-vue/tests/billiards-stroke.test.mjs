import test from "node:test";
import assert from "node:assert/strict";
import { STROKE, createStroke, beginStroke, advanceStroke, cancelStroke } from "../games/billiards/web/stroke.mjs";
import { createMatch, takeShot } from "../games/billiards/web/rules.mjs";
import { velocity } from "../games/billiards/web/physics.mjs";

// 模拟每次动作状态已画到屏幕，再推进下一显示帧
function displayAndAdvance(stroke, seconds) {
  stroke.presented = true;
  return advanceStroke(stroke, seconds);
}

// 即使传入很大的时间也不能跳过尚未显示的动作，触球只能报告一次
test("电脑完整展示瞄准蓄力出杆后才触球", function testAutomaticStroke() {
  const stroke = createStroke();
  beginStroke(stroke, { x: 1, y: 0 }, 0.7, { x: 200, y: 280 }, true, false);
  assert.equal(stroke.stage, "aim");
  assert.equal(advanceStroke(stroke, 10), false);
  assert.equal(stroke.stage, "aim");
  for (const stage of ["pull", "hold", "strike", "contact"]) {
    assert.equal(displayAndAdvance(stroke, 10), false);
    assert.equal(stroke.stage, stage);
    assert.equal(advanceStroke(stroke, 10), false);
    assert.equal(stroke.stage, stage);
  }
  assert.ok(Math.abs(stroke.pull + STROKE.tipGap / STROKE.pullDistance) < 1e-12);
  assert.equal(displayAndAdvance(stroke, 0), true);
  assert.equal(stroke.stage, "finish");
  assert.equal(displayAndAdvance(stroke, STROKE.finishSeconds), false);
  assert.equal(stroke.active, false);
});

// 玩家已手动拉杆，只补前送动作；取消后不得留下延迟击球
test("玩家松手冻结方向并支持取消未出杆动作", function testPlayerStroke() {
  const stroke = createStroke();
  const direction = { x: 0, y: -1 };
  beginStroke(stroke, direction, 0.6, { x: 300, y: 280 }, false, false);
  direction.y = 1;
  assert.equal(stroke.direction.y, -1);
  assert.equal(stroke.stage, "strike");
  displayAndAdvance(stroke, STROKE.strikeSeconds / 2);
  assert.ok(stroke.pull < 0.6 && stroke.pull > 0);
  cancelStroke(stroke);
  assert.equal(displayAndAdvance(stroke, 10), false);
  assert.equal(stroke.active, false);
});

// 误差已包含在最终方向中，画杆使用的球心和方向必须与真实出球一致
test("电脑球杆与真实初速使用同一球心和最终方向", function testStrokeMatchesShot() {
  const match = createMatch();
  match.phase = "aiming";
  const stroke = createStroke();
  const origin = match.world.balls[0].body.position;
  beginStroke(stroke, { x: 3, y: -4 }, 0.6, origin, true, false);
  assert.equal(stroke.origin.x, origin.x);
  assert.equal(stroke.origin.y, origin.y);
  assert.equal(takeShot(match, stroke.direction, stroke.power), true);
  const speed = velocity(match.world.balls[0]);
  const length = Math.hypot(speed.x, speed.y);
  assert.ok(Math.abs(speed.x / length - stroke.direction.x) < 1e-12);
  assert.ok(Math.abs(speed.y / length - stroke.direction.y) < 1e-12);
});

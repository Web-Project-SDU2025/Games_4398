import test from "node:test";
import assert from "node:assert/strict";
import { createMatch, resetMatch, openRound, takeShot, stepMatch, aimingMessage } from "../web/rules.mjs";
import { placeBall, removeBall, canPlace } from "../web/physics.mjs";
import { AI, legalTargets, planAiShot, planAiPlacement } from "../web/ai.mjs";
import { PHYSICS } from "../web/config.mjs";

// 使用固定中间值消除随机误差，验证 AI 实际计算的路线和力度
function centeredRandom() {
  return 0.5;
}

// 只保留主球与一颗目标球，用生产物理验证 AI 选择的进袋路线
function singleTarget(cueX, cueY, targetX, targetY) {
  const match = createMatch();
  resetMatch(match, "single");
  openRound(match, 1);
  match.breakShot = false;
  match.groups = ["stripe", "solid"];
  match.phase = "aiming";
  for (const ball of match.world.balls) {
    if (ball.id !== 0 && ball.id !== 1) {
      removeBall(match.world, ball);
    }
  }
  placeBall(match.world, match.world.balls[0], cueX, cueY);
  placeBall(match.world, match.world.balls[1], targetX, targetY);
  return match;
}

// 常见直球与斜球应具备真实进球能力，不能只保证生成一个方向
test("AI 常见直球和斜球使用真实物理能够进球", function testAiPots() {
  const layouts = [[500, 280, 500, 150], [300, 280, 500, 150], [400, 400, 650, 250]];
  let potted = 0;
  for (const layout of layouts) {
    const match = singleTarget(layout[0], layout[1], layout[2], layout[3]);
    const plan = planAiShot(match, centeredRandom);
    assert.ok(Number.isFinite(plan.direction.x));
    assert.ok(plan.power > 0 && plan.power <= 1);
    takeShot(match, plan.direction, plan.power);
    let steps = 0;
    while (match.phase === "moving" && steps < 12000) {
      stepMatch(match, PHYSICS.step);
      steps = steps + 1;
    }
    assert.ok(!match.world.record.pots.includes(0), JSON.stringify({ layout: layout, plan: plan, pots: match.world.record.pots }));
    if (!match.world.balls[1].active) {
      potted = potted + 1;
    }
  }
  assert.ok(potted >= 2, "三个常见局面至少应能打进两球，实际 " + potted);
});

// 固定多组普通误差与偶发失误，验证电脑有稳定进球能力但不会消除所有偏差
test("AI 有误差时仍能处理简单球且长台不会只用最小力度", function testAiAccuracyAndPower() {
  const randomValues = [0.05, 0.2, 0.5, 0.8, 0.95];
  let potted = 0;
  const directions = new Set();
  for (const value of randomValues) {
    // 每组固定随机值覆盖不同方向的误差，真实游戏仍使用 Math.random
    function fixedRandom() {
      return value;
    }
    const match = singleTarget(500, 280, 500, 150);
    const plan = planAiShot(match, fixedRandom);
    directions.add(plan.direction.x);
    takeShot(match, plan.direction, plan.power);
    let steps = 0;
    while (match.phase === "moving" && steps < 12000) {
      stepMatch(match, PHYSICS.step);
      steps = steps + 1;
    }
    if (!match.world.balls[1].active) {
      potted = potted + 1;
    }
    assert.ok(!match.world.record.pots.includes(0));
  }
  assert.ok(potted >= 4, "简单球在五组误差下至少应打进四球，实际 " + potted);
  assert.ok(directions.size > 1, "不同误差仍应产生不同瞄准方向");
  const longTable = singleTarget(180, 400, 740, 180);
  const longPlan = planAiShot(longTable, centeredRandom);
  assert.ok(longPlan.power > AI.minimumPower * 1.5, "长台应使用高于短球的力度");
});

// 电脑可正确摆放自由球，并在组清完前避开八号
test("AI 目标球组、自由球摆放与随机先手", function testAiTurn() {
  const match = singleTarget(500, 280, 500, 150);
  const position = planAiPlacement(match);
  assert.ok(position);
  assert.equal(canPlace(match.world, position.x, position.y, match.restricted, 0), true);
  assert.deepEqual(legalTargets(match).map(readId), [1]);
  resetMatch(match, "single");
  openRound(match, 0);
  assert.equal(match.current, 0);
  resetMatch(match, "single");
  openRound(match, 1);
  assert.equal(match.current, 1);
  assert.match(aimingMessage(match), /电脑.*开球不定组/);
  match.breakShot = false;
  assert.match(aimingMessage(match), /开放球台/);
});

// 读取目标球号，测试不修改生产球对象
function readId(ball) {
  return ball.id;
}

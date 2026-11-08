import test from "node:test";
import assert from "node:assert/strict";
import { createWorld, placeBall, removeBall, setVelocity, velocity, stepWorld,
  atRest, createRecord, canPlace } from "../web/physics.mjs";
import { PHYSICS } from "../web/config.mjs";
import { createMatch, takeShot, stepMatch, judgeShot, awardAchievements } from "../web/rules.mjs";
import { installedGameInfo, gameAddress } from "../../../src/logic/gameRuntime.js";
import { normalizeManifest } from "../../runtime/web/game-manifest.mjs";
import { aimDirection, aimPreview, limitPullAnchor, shotPower } from "../web/aim.mjs";

// 满力后的多余拖动不再形成回拉空行程，横向与斜向拖动使用同一套距离限制
test("满力后回拉立即减小力度并能退回取消区", function testPullOverflow() {
  for (const direction of [{ x: -1, y: 0 }, { x: 0.6, y: 0.8 }]) {
    let anchor = { x: 500, y: 280 };
    const far = { x: anchor.x + direction.x * 400, y: anchor.y + direction.y * 400 };
    anchor = limitPullAnchor(anchor, far);
    assert.ok(Math.abs(shotPower(anchor, far) - 1) < 1e-12);
    const back = { x: far.x - direction.x * 12, y: far.y - direction.y * 12 };
    anchor = limitPullAnchor(anchor, back);
    assert.ok(Math.abs(shotPower(anchor, back) - 0.9) < 1e-12);
    const cancel = { x: far.x - direction.x * 125, y: far.y - direction.y * 125 };
    anchor = limitPullAnchor(anchor, cancel);
    assert.equal(shotPower(anchor, cancel), 0);
    const full = { x: far.x + direction.x * 100, y: far.y + direction.y * 100 };
    anchor = limitPullAnchor(anchor, full);
    const reverse = { x: full.x - direction.x * 60, y: full.y - direction.y * 60 };
    anchor = limitPullAnchor(anchor, reverse);
    assert.ok(Math.abs(shotPower(anchor, reverse) - 0.5) < 1e-12);
  }
  const start = { x: 200, y: 280 };
  assert.equal(limitPullAnchor(start, { x: 120, y: 280 }), start);
  assert.equal(shotPower(start, { x: 120, y: 280 }), 0.5);
  assert.equal(limitPullAnchor(null, start), null);
});

// 直接构造只读球位，验证预览几何，不额外运行完整物理比赛
function closeAim(targetX, targetY, direction) {
  const match = { world: { balls: [
    { id: 0, active: true, body: { position: { x: 300, y: 280 } } },
    { id: 1, active: true, body: { position: { x: targetX, y: targetY } } },
  ] } };
  return aimPreview(match, direction);
}

// 贴球方向决定是否碰撞，远离与切向不应被微小重叠截断
test("近距离瞄准区分接近、远离、擦边和极小间隙", function testCloseAim() {
  for (const offset of [22, 21.99]) {
    const forward = closeAim(300 + offset, 280, { x: 1, y: 0 });
    assert.equal(forward.target.id, 1);
    assert.equal(forward.distance, 0);
    assert.equal(forward.targetDirection.x, 1);
    const away = closeAim(300 + offset, 280, { x: -0.1, y: Math.sqrt(0.99) });
    assert.equal(away.target, null);
    assert.ok(away.distance > 100);
    const tangent = closeAim(300 + offset, 280, { x: 0, y: -1 });
    assert.equal(tangent.target, null);
    assert.ok(tangent.distance > 100);
  }
  const near = closeAim(322.000001, 280, { x: 1, y: 0 });
  assert.ok(Math.abs(near.distance - 0.000001) < 1e-10);
  const cut = closeAim(321.99, 280, { x: 0.5, y: -Math.sqrt(0.75) });
  assert.equal(cut.target.id, 1);
  assert.ok(cut.cueDirection.y < -0.9);
  assert.ok(cut.cueDirection.x >= 0 && cut.cueDirection.x < 0.1);
});

// 检查四边相切和轻微重叠的瞄准，确保即时反弹可见且袋口保持开放
test("贴库预览区分朝库、离库、沿库与袋口缺口", function testRailAim() {
  const edges = [
    { origin: { x: 300, y: 71 }, normal: { x: 0, y: 1 } },
    { origin: { x: 300, y: 489 }, normal: { x: 0, y: -1 } },
    { origin: { x: 71, y: 280 }, normal: { x: 1, y: 0 } },
    { origin: { x: 929, y: 280 }, normal: { x: -1, y: 0 } },
  ];
  for (const edge of edges) {
    for (const overlap of [0, 0.01]) {
      const origin = { x: edge.origin.x - edge.normal.x * overlap,
        y: edge.origin.y - edge.normal.y * overlap };
      const match = { world: { balls: [{ id: 0, active: true, body: { position: origin } }] } };
      const outward = { x: -edge.normal.x, y: -edge.normal.y };
      const immediate = aimPreview(match, outward);
      assert.equal(immediate.distance, 0);
      assert.ok(immediate.railDirection.x * edge.normal.x + immediate.railDirection.y * edge.normal.y > 0.99);
      const inward = aimPreview(match, edge.normal);
      assert.ok(inward.distance > 100);
      const tangent = aimPreview(match, { x: -edge.normal.y, y: edge.normal.x });
      assert.ok(tangent.distance > 100);
    }
  }
  const gapMatch = { world: { balls: [{ id: 0, active: true,
    body: { position: { x: 500, y: 71 } } }] } };
  const gap = aimPreview(gapMatch, { x: 0, y: -1 });
  assert.equal(gap.railDirection, null);
  assert.equal(gap.distance, 1100);
});

// 球心靠库时鼠标离球十像素也要更新方向，近切线的极小残差不能让预览跳远
test("贴库小范围瞄准及时更新且近切线预览稳定", function testRailFineAim() {
  const origin = { x: 300, y: 71.000001 };
  const match = { world: { balls: [{ id: 0, active: true, body: { position: origin } }] } };
  const direction = aimDirection(match, { x: 290, y: origin.y }, { x: 0, y: 1 });
  assert.equal(direction.x, 1);
  assert.equal(direction.y, 0);
  const grazing = aimPreview(match, { x: Math.sqrt(1 - 1e-16), y: -1e-8 });
  assert.ok(grazing.distance > 100);
  assert.ok(Number.isFinite(grazing.ghost.x));
  const approaching = aimPreview(match, { x: Math.sqrt(1 - 1e-8), y: -0.0001 });
  assert.equal(approaching.distance, 0);
  assert.ok(approaching.railDirection.y > 0);
});

// 从真实 Matter 世界移除多余球，只保留指定球号便于构造可复现击球
function isolate(ids) {
  const world = createWorld();
  for (const ball of world.balls) {
    if (!ids.includes(ball.id)) {
      removeBall(world, ball);
    }
  }
  world.record = createRecord();
  return world;
}

// 连续推进真实固定步，球停止后返回步数，避免测试依赖机器墙钟
function finish(world) {
  let steps = 0;
  while (!atRest(world) && steps < 12000) {
    stepWorld(world, PHYSICS.step);
    steps = steps + 1;
  }
  return steps;
}

// 单球滑动后逐渐进入滚动，速度减至零而非永远微动
test("原生台球摩擦停止与摆球边界", function testCloth() {
  const world = isolate([0]);
  placeBall(world, world.balls[0], 300, 280);
  setVelocity(world.balls[0], 200, 0);
  const steps = finish(world);
  assert.ok(steps > 100 && steps < 12000);
  assert.ok(world.balls[0].body.position.x > 320);
  assert.equal(velocity(world.balls[0]).x, 0);
  assert.equal(canPlace(world, 400, 280, true, 0), false);
  assert.equal(canPlace(world, 190, 280, true, 0), true);
});

// 正撞应先报正确目标、再传递动量，记录由生产碰撞监听器提供
test("原生碰撞事件和目标球动量传递", function testContact() {
  const world = isolate([0, 1]);
  placeBall(world, world.balls[0], 300, 280);
  placeBall(world, world.balls[1], 345, 280);
  setVelocity(world.balls[0], 500, 0);
  for (let step = 0; step < 30; step = step + 1) {
    stepWorld(world, PHYSICS.step);
  }
  assert.equal(world.record.first, 1);
  assert.ok(velocity(world.balls[1]).x > 200);
  assert.ok(world.record.events.length > 0);
});

// 真实碰库后速度应反向，材质能量损失与袋口捕获分别验证
test("原生库边反弹、袋口与满力开球能够停稳", function testRailsAndBreak() {
  const world = isolate([0]);
  placeBall(world, world.balls[0], 300, 90);
  setVelocity(world.balls[0], 0, -500);
  for (let step = 0; step < 30; step = step + 1) {
    stepWorld(world, PHYSICS.step);
  }
  assert.ok(velocity(world.balls[0]).y > 0);
  assert.ok(velocity(world.balls[0]).y < 450);
  assert.ok(world.record.rails.has(0));
  const rack = createWorld();
  setVelocity(rack.balls[0], PHYSICS.maxSpeed, 0);
  assert.ok(finish(rack) < 12000);
  assert.equal(rack.record.first, 1);
  for (const ball of rack.balls) {
    if (ball.active) {
      assert.ok(Number.isFinite(ball.body.position.x));
      assert.ok(ball.body.position.x >= 40 && ball.body.position.x <= 960);
    }
  }
});

// 白球先碰库再落袋时只保留碰库和落袋，不生成后端禁止的白球翻袋记录
test("白球碰库后落袋不会提交翻袋证据", function testCueBankScratch() {
  const world = isolate([0]);
  placeBall(world, world.balls[0], 500, 130);
  world.record.rails.add(0);
  setVelocity(world.balls[0], 0, -450);
  finish(world);
  assert.ok(world.record.pots.includes(0));
  assert.ok(world.record.rails.has(0));
  assert.equal(world.record.bankPots.has(0), false);
});

// 沿中袋走廊运行真实物理，验证落袋、定组与轮次接线
test("原生比赛完成一次真实进球并定组", function testPhysicalPot() {
  const match = createMatch();
  match.world = isolate([0, 9]);
  placeBall(match.world, match.world.balls[0], 500, 190);
  placeBall(match.world, match.world.balls[9], 500, 150);
  match.phase = "aiming";
  match.breakShot = false;
  assert.equal(takeShot(match, { x: 0, y: -1 }, 0.3), true);
  let steps = 0;
  while (match.phase === "moving" && steps < 12000) {
    stepMatch(match, PHYSICS.step);
    steps = steps + 1;
  }
  assert.equal(match.world.balls[9].active, false);
  assert.equal(match.phase, "resolving");
  assert.deepEqual(match.groups, ["stripe", "solid"]);
  assert.equal(match.current, 0);
  assert.equal(match.tone, "legal");
});

// 构造正式判罚需要的出杆前局面，后续记录仍交给生产判罚函数
function shot(group, breakShot) {
  return { player: 0, group: group, breakShot: breakShot, before: [1, 2, 8, 9, 10] };
}

// 检查自由球、提前八号和同杆清组胜利，避免原生迁移改变规则边界
test("原生判罚保留开球、自由球与八号胜负", function testRules() {
  const record = createRecord();
  let verdict = judgeShot(shot("", true), record);
  assert.ok(verdict.fouls.includes("开球未达标"));
  record.first = 9;
  record.railAfter = true;
  verdict = judgeShot(shot("solid", false), record);
  assert.ok(verdict.fouls.includes("先碰到的不是本方目标球"));
  record.first = 1;
  record.pots = [8];
  verdict = judgeShot(shot("solid", false), record);
  assert.equal(verdict.winner, 1);
  assert.ok(verdict.fouls.includes("8 号球提前落袋"));
  record.pots = [1, 2, 8];
  verdict = judgeShot(shot("solid", false), record);
  assert.equal(verdict.winner, 0);
  record.pots = [1, 2, 8, 0];
  verdict = judgeShot(shot("solid", false), record);
  assert.equal(verdict.winner, 1);
});

// 双收只计本方球，犯规不奖励，碰库证据用于解锁翻袋与勾球
test("原生成就区分本方球、犯规与技巧球", function testAchievements() {
  const match = createMatch();
  const record = createRecord();
  record.first = 1;
  record.pots = [1, 2, 9];
  record.cueRailBefore = true;
  record.bankPots.add(1);
  const context = shot("solid", false);
  let verdict = judgeShot(context, record);
  awardAchievements(match, context, record, verdict);
  assert.ok(match.achievements[0].has("一杆双收"));
  assert.ok(!match.achievements[0].has("一杆三收"));
  assert.ok(match.achievements[0].has("翻袋好球"));
  assert.ok(match.achievements[0].has("勾球进袋"));
  record.pots.push(0);
  verdict = judgeShot(context, record);
  const fresh = createMatch();
  awardAchievements(fresh, context, record, verdict);
  assert.equal(fresh.achievements[0].size, 0);
});

// 原生配置保持原商品入口，但平台地址必须走无需 Java 的运行页
test("原生台球使用独立运行入口且拒绝越界路径", function testNativeManifest() {
  const configuration = { id: "billiards", name: "美式八球", runtime: "html5",
    entry: "runtime/index.html", cover: "media/table.png" };
  const manifest = normalizeManifest(configuration, "/games/billiards/game.json");
  assert.equal(gameAddress(installedGameInfo(manifest), 12, false),
    "/games/native-player.html?game=billiards&owner=12");
  configuration.entry = "../outside.html";
  // 站内网页入口同样需要限制目录范围
  function outsideEntry() {
    normalizeManifest(configuration, "/games/billiards/game.json");
  }
  assert.throws(outsideEntry);
});

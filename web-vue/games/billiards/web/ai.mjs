import { TABLE, PHYSICS, groupOf } from "./config.mjs";
import Matter from "matter-js";
import { canPlace, createWorld, createRecord, placeBall, removeBall, setVelocity, stepWorld, atRest } from "./physics.mjs";

// 思考时间使用秒，误差使用弧度和力度比例，所有难度参数集中维护
export const AI = {
  thinkSeconds: 0.8,
  angleError: 0.0018,
  powerError: 0.022,
  // 少数击球加入较大但有界的失误，普通球保持稳定，避免每杆都偏得明显
  mistakeChance: 0.12,
  mistakeAngle: 0.006,
  maximumDifficulty: 1.8,
  minimumCut: 0.32,
  pathMargin: 0.8,
  placementStep: 65,
  placementDistance: 100,
  minimumPower: 0.16,
  maximumPower: 0.95,
  breakPower: 0.92,
  rollReserve: 80,
  // 路线越长，候选力度下限越高，防止所有成功路线都退回轻推
  powerDistance: 2200,
  previewSeconds: 10,
  powerScales: [0.6, 0.85, 1, 1.3, 1.7],
  previewAngles: [0, -0.01, 0.01],
  // 候选先比较犯规和进球，只有结果接近时才优先选择接近估算值的力度
  foulPenalty: 100,
  potScore: 10,
  powerDeviationWeight: 0.5,
  anglePenaltyWeight: 1,
  // 路线评分兼顾行程和切角，距离单位为逻辑像素
  potDistanceWeight: 0.7,
  cutPenalty: 1000,
  difficultyDistance: 1200,
  // 无直进路线时保持足够力度完成碰球和碰库
  contactPower: 0.42,
  kickPower: 0.65,
  fallbackPower: 0.4,
  contactDifficulty: 0.5,
  breakDifficulty: 0.3,
  fallbackScore: 1000,
  // 摆球搜索避开边缘，开球位置按台面宽度比例生成
  placementInset: 30,
  placementEndInset: 20,
  breakPositionRatio: 0.15,
};
// 几何运算的浮点容差，防止极短线段发生除零
const GEOMETRY_EPSILON = 0.000001;

// 复用一个独立预演世界，电脑思考只改这份副本，不能提前改变正式对局
let previewWorld = null;

// 对候选力度运行同一套物理，优先保留合法进球并避开白球落袋
function chooseSafePower(match, plan) {
  if (!previewWorld) {
    previewWorld = createWorld();
  }
  let selected = { power: plan.power, direction: plan.direction };
  let bestScore = -Infinity;
  let bestOutcome = -Infinity;
  const targets = legalTargets(match);
  let minimumPower = AI.minimumPower;
  if (plan.minimumPower) {
    minimumPower = plan.minimumPower;
  }
  // 优先尝试距离对应的力度；全部方案都有犯规风险时，才允许降低到短球力度
  for (const powerFloor of [minimumPower, AI.minimumPower]) {
    if (bestOutcome >= 0) {
      break;
    }
    for (const angle of AI.previewAngles) {
      const direction = { x: plan.direction.x * Math.cos(angle) - plan.direction.y * Math.sin(angle),
        y: plan.direction.x * Math.sin(angle) + plan.direction.y * Math.cos(angle) };
      for (const scale of AI.powerScales) {
        const power = Math.max(powerFloor, Math.min(AI.maximumPower, plan.power * scale));
        runPreview(match, direction, power);
        const powerPenalty = Math.abs(power - plan.power) * AI.powerDeviationWeight;
        const anglePenalty = Math.abs(angle) * AI.anglePenaltyWeight;
        const outcome = previewScore(match, targets);
        const score = outcome - powerPenalty - anglePenalty;
        if (score > bestScore) {
          bestScore = score;
          bestOutcome = outcome;
          selected = { power: power, direction: direction };
        }
      }
    }
  }
  return selected;
}

// 每个候选都从正式球位复制，清空旧碰撞缓存后运行有限时长的物理副本
function runPreview(match, direction, power) {
  for (const ball of match.world.balls) {
    const copy = previewWorld.balls[ball.id];
    if (ball.active) {
      placeBall(previewWorld, copy, ball.body.position.x, ball.body.position.y);
    } else {
      removeBall(previewWorld, copy);
    }
  }
  Matter.Engine.clear(previewWorld.engine);
  previewWorld.record = createRecord();
  const speed = PHYSICS.minSpeed + power * (PHYSICS.maxSpeed - PHYSICS.minSpeed);
  setVelocity(previewWorld.balls[0], direction.x * speed, direction.y * speed);
  let seconds = 0;
  while (!atRest(previewWorld) && seconds < AI.previewSeconds) {
    stepWorld(previewWorld, PHYSICS.step);
    seconds = seconds + PHYSICS.step;
  }
}

// 合法进球加分，落白球、提前进八号和先碰错球扣分，分数只用于电脑选杆
function previewScore(match, targets) {
  let score = 0;
  let legalFirst = false;
  for (const target of targets) {
    if (target.id === previewWorld.record.first) {
      legalFirst = true;
    }
  }
  if (!legalFirst || previewWorld.record.off.length > 0) {
    score = score - AI.foulPenalty;
  }
  // 打到目标后也要有进球或碰库，不能把刚好碰到球便停住的轻杆当成安全选择
  if (legalFirst && !previewWorld.record.railAfter && previewWorld.record.pots.length === 0) {
    score = score - AI.foulPenalty;
  }
  for (const id of previewWorld.record.pots) {
    if (id === 0) {
      score = score - AI.foulPenalty;
    } else if (id === 8 && !(targets.length === 1 && targets[0].id === 8)) {
      score = score - AI.foulPenalty;
    } else if (id === 8 || groupOf(id) === match.groups[match.current] || !match.groups[match.current]) {
      score = score + AI.potScore;
    }
  }
  return score;
}

// 返回当前方可以先碰的目标，未定组时避开八号，清组后只打八号
export function legalTargets(match) {
  const result = [];
  const group = match.groups[match.current];
  for (const ball of match.world.balls) {
    if (ball.active && ball.id !== 0 && ball.id !== 8
        && (!group || groupOf(ball.id) === group)) {
      result.push(ball);
    }
  }
  if (result.length === 0 && group && match.world.balls[8].active) {
    result.push(match.world.balls[8]);
  }
  return result;
}

// 检查线段走廊是否被其他球阻挡，忽略列表包含运动球及接触目标
function clearPath(match, start, end, ignored) {
  const horizontal = end.x - start.x;
  const vertical = end.y - start.y;
  const lengthSquared = horizontal * horizontal + vertical * vertical;
  if (lengthSquared < GEOMETRY_EPSILON) {
    return true;
  }
  for (const ball of match.world.balls) {
    if (!ball.active || ignored.includes(ball.id)) {
      continue;
    }
    const position = ball.body.position;
    let fraction = ((position.x - start.x) * horizontal + (position.y - start.y) * vertical) / lengthSquared;
    fraction = Math.max(0, Math.min(1, fraction));
    const distance = Math.hypot(position.x - start.x - horizontal * fraction,
      position.y - start.y - vertical * fraction);
    if (distance < TABLE.radius * 2 + AI.pathMargin) {
      return false;
    }
  }
  return true;
}

// 从主球位置评估每个目标到袋口的虚影球瞄点，距离和切角共同决定优先级
function bestPot(match, origin) {
  let best = null;
  for (const target of legalTargets(match)) {
    const position = target.body.position;
    for (const pocket of match.world.pockets) {
      const potDistance = Math.hypot(pocket.x - position.x, pocket.y - position.y);
      if (potDistance <= TABLE.radius) {
        continue;
      }
      const targetX = (pocket.x - position.x) / potDistance;
      const targetY = (pocket.y - position.y) / potDistance;
      const ghost = { x: position.x - targetX * TABLE.radius * 2,
        y: position.y - targetY * TABLE.radius * 2 };
      if (ghost.x < TABLE.left + TABLE.radius || ghost.x > TABLE.right - TABLE.radius
          || ghost.y < TABLE.top + TABLE.radius || ghost.y > TABLE.bottom - TABLE.radius) {
        continue;
      }
      const cueDistance = Math.hypot(ghost.x - origin.x, ghost.y - origin.y);
      if (cueDistance < GEOMETRY_EPSILON) {
        continue;
      }
      const direction = { x: (ghost.x - origin.x) / cueDistance, y: (ghost.y - origin.y) / cueDistance };
      const cut = direction.x * targetX + direction.y * targetY;
      if (cut < AI.minimumCut || !clearPath(match, origin, ghost, [0, target.id])
          || !clearPath(match, position, pocket, [0, target.id])) {
        continue;
      }
      // 目标球需要滚过袋口距离，白球还需克服碰撞前行程和切球损失
      const rolling = PHYSICS.rolling * PHYSICS.gravity;
      const targetSpeed = Math.sqrt(2 * rolling * (potDistance + AI.rollReserve));
      const required = Math.sqrt(targetSpeed * targetSpeed / (cut * cut) + 2 * rolling * cueDistance);
      // 无初始旋转的球会在滑动阶段损失速度，保留比例由惯性参数推导
      const rollingRatio = 1 / (1 + PHYSICS.inertia);
      const initialSpeed = required / rollingRatio;
      const minimumPower = Math.min(AI.maximumPower, AI.minimumPower + cueDistance / AI.powerDistance);
      const power = Math.max(minimumPower, Math.min(AI.maximumPower,
        (initialSpeed - PHYSICS.minSpeed) / (PHYSICS.maxSpeed - PHYSICS.minSpeed)));
      const score = cueDistance + potDistance * AI.potDistanceWeight + (1 - cut) * AI.cutPenalty;
      if (!best || score < best.score) {
        best = { direction: direction, power: power, score: score,
          minimumPower: minimumPower, difficulty: 1 - cut + cueDistance / AI.difficultyDistance };
      }
    }
  }
  return best;
}

// 无直接进袋路线时先找无遮挡的合法目标，避免为了进球故意先碰对方球
function safeContact(match, origin) {
  let best = null;
  for (const target of legalTargets(match)) {
    const position = target.body.position;
    const distance = Math.hypot(position.x - origin.x, position.y - origin.y);
    if (distance > 0 && clearPath(match, origin, position, [0, target.id])
        && (!best || distance < best.score)) {
      best = { direction: { x: (position.x - origin.x) / distance, y: (position.y - origin.y) / distance },
        power: AI.contactPower, score: distance, difficulty: AI.contactDifficulty };
    }
  }
  if (best) {
    return best;
  }
  // 完全球被遮挡时尝试一库解球，用反射法求主球碰库点
  const walls = [
    { axis: "x", value: TABLE.left + TABLE.radius },
    { axis: "x", value: TABLE.right - TABLE.radius },
    { axis: "y", value: TABLE.top + TABLE.radius },
    { axis: "y", value: TABLE.bottom - TABLE.radius },
  ];
  for (const target of legalTargets(match)) {
    const position = target.body.position;
    for (const wall of walls) {
      const reflected = { x: position.x, y: position.y };
      reflected[wall.axis] = 2 * wall.value - reflected[wall.axis];
      const delta = reflected[wall.axis] - origin[wall.axis];
      if (Math.abs(delta) < GEOMETRY_EPSILON) {
        continue;
      }
      const fraction = (wall.value - origin[wall.axis]) / delta;
      if (fraction <= 0 || fraction >= 1) {
        continue;
      }
      const contact = { x: origin.x + (reflected.x - origin.x) * fraction,
        y: origin.y + (reflected.y - origin.y) * fraction };
      let onRail = contact.y >= TABLE.top + TABLE.cornerGap
        && contact.y <= TABLE.bottom - TABLE.cornerGap;
      if (wall.axis === "y") {
        const middle = (TABLE.left + TABLE.right) / 2;
        onRail = (contact.x >= TABLE.left + TABLE.cornerGap && contact.x <= middle - TABLE.sideGap)
          || (contact.x >= middle + TABLE.sideGap && contact.x <= TABLE.right - TABLE.cornerGap);
      }
      if (onRail && clearPath(match, origin, contact, [0])
          && clearPath(match, contact, position, [0, target.id])) {
        const length = Math.hypot(contact.x - origin.x, contact.y - origin.y);
        return { direction: { x: (contact.x - origin.x) / length, y: (contact.y - origin.y) / length },
          power: AI.kickPower, score: length, difficulty: 1 };
      }
    }
  }
  const targets = legalTargets(match);
  if (targets.length > 0) {
    const position = targets[0].body.position;
    const length = Math.hypot(position.x - origin.x, position.y - origin.y);
    return { direction: { x: (position.x - origin.x) / length, y: (position.y - origin.y) / length },
      power: AI.fallbackPower, score: length, difficulty: 1 };
  }
  return { direction: { x: 1, y: 0 }, power: AI.fallbackPower, score: AI.fallbackScore, difficulty: 1 };
}

// 为可行方案加入有界误差，简单球较准，长台或大切角的失误可能性更高
export function planAiShot(match, random) {
  const origin = match.world.balls[0].body.position;
  let plan;
  if (match.breakShot) {
    const target = match.world.balls[1].body.position;
    const distance = Math.hypot(target.x - origin.x, target.y - origin.y);
    plan = { direction: { x: (target.x - origin.x) / distance, y: (target.y - origin.y) / distance },
      power: AI.breakPower, score: 0, difficulty: AI.breakDifficulty };
  } else {
    plan = bestPot(match, origin);
    if (!plan) {
      plan = safeContact(match, origin);
    }
    const tested = chooseSafePower(match, plan);
    plan.power = tested.power;
    plan.direction = tested.direction;
  }
  const difficulty = Math.min(AI.maximumDifficulty, 1 + plan.difficulty);
  let angle = (random() + random() - 1) * AI.angleError * difficulty;
  if (random() < AI.mistakeChance) {
    angle = angle + (random() * 2 - 1) * AI.mistakeAngle * difficulty;
  }
  const cosine = Math.cos(angle);
  const sine = Math.sin(angle);
  const direction = { x: plan.direction.x * cosine - plan.direction.y * sine,
    y: plan.direction.x * sine + plan.direction.y * cosine };
  const power = Math.max(AI.minimumPower, Math.min(1, plan.power * (1 + (random() * 2 - 1) * AI.powerError)));
  return { direction: direction, power: power };
}

// 自由球优先选择直线进袋的白球位置，再按网格找合法落点，开球保持在开球线后
export function planAiPlacement(match) {
  if (match.breakShot) {
    return { x: TABLE.left + (TABLE.right - TABLE.left) * AI.breakPositionRatio, y: TABLE.center };
  }
  let best = null;
  for (const target of legalTargets(match)) {
    for (const pocket of match.world.pockets) {
      const position = target.body.position;
      const distance = Math.hypot(position.x - pocket.x, position.y - pocket.y);
      const candidate = { x: position.x + (position.x - pocket.x) / distance * AI.placementDistance,
        y: position.y + (position.y - pocket.y) / distance * AI.placementDistance };
      if (canPlace(match.world, candidate.x, candidate.y, match.restricted, 0)) {
        const plan = bestPot(match, candidate);
        if (plan && (!best || plan.score < best.score)) {
          best = { position: candidate, score: plan.score };
        }
      }
    }
  }
  if (best) {
    return best.position;
  }
  for (let horizontal = TABLE.left + AI.placementInset;
    horizontal < TABLE.right - AI.placementEndInset; horizontal = horizontal + AI.placementStep) {
    for (let vertical = TABLE.top + AI.placementInset;
      vertical < TABLE.bottom - AI.placementEndInset; vertical = vertical + AI.placementStep) {
      if (canPlace(match.world, horizontal, vertical, match.restricted, 0)) {
        return { x: horizontal, y: vertical };
      }
    }
  }
  return null;
}

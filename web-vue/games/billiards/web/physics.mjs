import Matter from "matter-js";
import { TABLE, PHYSICS, RACK, createPockets } from "./config.mjs";

// Matter 的速度以六十分之一秒为单位，接口统一转换为原游戏的像素每秒
const MATTER_HZ = 60;

// 创建单杆证据，碰库和落袋分别记录先后顺序以供判罚与成就判断
export function createRecord() {
  return { first: -1, pots: [], rails: new Set(), railAfter: false,
    cueRailBefore: false, bankPots: new Set(), off: [], events: [] };
}

// 创建零重力的台球世界，碰撞检测与冲量解算交给 Matter.js
export function createWorld() {
  const engine = Matter.Engine.create({
    gravity: { x: 0, y: 0 }, positionIterations: PHYSICS.positionIterations,
    velocityIterations: PHYSICS.velocityIterations, enableSleeping: false,
  });
  const world = { engine: engine, balls: [], pockets: createPockets(), record: createRecord() };
  createRails(world);
  for (let id = 0; id < 16; id = id + 1) {
    const body = Matter.Bodies.circle(0, 0, TABLE.radius, {
      restitution: PHYSICS.ballRestitution, friction: PHYSICS.contactFriction,
      frictionStatic: 0, frictionAir: 0, slop: 0, label: "ball",
    }, PHYSICS.circleSides);
    Matter.Body.setMass(body, 1);
    world.balls.push({ id: id, body: body, active: false, spinX: 0, spinY: 0 });
    body.plugin.ballId = id;
  }
  // 同时监听新接触和持续接触，真正接近时才记录，防止相切球架被当成已击中
  function contactStarted(event) {
    configureContacts(event.pairs);
    recordContacts(world, event.pairs);
  }
  // Matter 默认取两物体恢复系数的较大者，台球碰库需要明确使用库边系数
  function configureActiveContacts() {
    configureContacts(engine.pairs.list);
  }
  Matter.Events.on(engine, "collisionStart collisionActive", contactStarted);
  Matter.Events.on(engine, "beforeSolve", configureActiveContacts);
  resetWorld(world);
  return world;
}

// 只调整接触对材质，碰撞法线、穿透修正和冲量仍由 Matter 求解
function configureContacts(pairs) {
  for (const pair of pairs) {
    if (pair.bodyA.label === "rail" || pair.bodyB.label === "rail") {
      pair.restitution = PHYSICS.railRestitution;
    } else {
      pair.restitution = PHYSICS.ballRestitution;
    }
  }
}

// 按袋口缺口创建六段静态库边，薄长矩形的内沿与原游戏边线一致
function createRails(world) {
  const thickness = TABLE.railThickness;
  const left = TABLE.left + TABLE.cornerGap;
  const right = TABLE.right - TABLE.cornerGap;
  const middleLeft = 500 - TABLE.sideGap;
  const middleRight = 500 + TABLE.sideGap;
  const ranges = [[left, middleLeft], [middleRight, right]];
  const rails = [];
  for (const range of ranges) {
    const center = (range[0] + range[1]) / 2;
    const width = range[1] - range[0];
    rails.push(Matter.Bodies.rectangle(center, TABLE.top - thickness / 2, width, thickness));
    rails.push(Matter.Bodies.rectangle(center, TABLE.bottom + thickness / 2, width, thickness));
  }
  const height = TABLE.bottom - TABLE.top - TABLE.cornerGap * 2;
  rails.push(Matter.Bodies.rectangle(TABLE.left - thickness / 2, TABLE.center, thickness, height));
  rails.push(Matter.Bodies.rectangle(TABLE.right + thickness / 2, TABLE.center, thickness, height));
  for (const rail of rails) {
    Matter.Body.setStatic(rail, true);
    rail.label = "rail";
    rail.restitution = PHYSICS.railRestitution;
    rail.friction = PHYSICS.contactFriction;
    rail.frictionStatic = 0;
    rail.slop = 0;
    Matter.Composite.add(world.engine.world, rail);
  }
}

// 返回一颗球的速度，单位统一为逻辑像素每秒
export function velocity(ball) {
  const current = Matter.Body.getVelocity(ball.body);
  return { x: current.x * MATTER_HZ, y: current.y * MATTER_HZ };
}

// 保存指定线速度，避免调用方直接修改 Matter 的内部积分状态
export function setVelocity(ball, horizontal, vertical) {
  Matter.Body.setVelocity(ball.body, { x: horizontal / MATTER_HZ, y: vertical / MATTER_HZ });
}

// 把球放回指定位置并停止，未入世界的球同时重新加入
export function placeBall(world, ball, horizontal, vertical) {
  Matter.Body.setPosition(ball.body, { x: horizontal, y: vertical });
  setVelocity(ball, 0, 0);
  Matter.Body.setAngularVelocity(ball.body, 0);
  ball.spinX = 0;
  ball.spinY = 0;
  if (!ball.active) {
    Matter.Composite.add(world.engine.world, ball.body);
    ball.active = true;
  }
}

// 从物理世界移除落袋球，保留球号与最后位置供托盘和记录读取
export function removeBall(world, ball) {
  if (ball.active) {
    Matter.Composite.remove(world.engine.world, ball.body);
    ball.active = false;
    setVelocity(ball, 0, 0);
    ball.spinX = 0;
    ball.spinY = 0;
  }
}

// 重置完整球架，位置留出极小间距以避免静止球架产生初始穿透
export function resetWorld(world) {
  Matter.Engine.clear(world.engine);
  placeBall(world, world.balls[0], 192, TABLE.center);
  let slot = 0;
  const diameter = TABLE.radius * 2 + 0.05;
  for (let row = 0; row < 5; row = row + 1) {
    for (let column = 0; column <= row; column = column + 1) {
      const horizontal = TABLE.foot + row * diameter * Math.sqrt(3) / 2;
      const vertical = TABLE.center + (column - row / 2) * diameter;
      placeBall(world, world.balls[RACK[slot]], horizontal, vertical);
      slot = slot + 1;
    }
  }
  world.record = createRecord();
}

// 记录实际接近的球球或球库接触，成就与声音只读取这一份事件
function recordContacts(world, pairs) {
  const record = world.record;
  for (const pair of pairs) {
    let first = pair.bodyA;
    let second = pair.bodyB;
    if (first.label !== "ball") {
      first = pair.bodyB;
      second = pair.bodyA;
    }
    if (first.label !== "ball") {
      continue;
    }
    const ball = world.balls[first.plugin.ballId];
    const moving = velocity(ball);
    if (second.label === "ball") {
      const other = world.balls[second.plugin.ballId];
      const otherVelocity = velocity(other);
      const horizontal = second.position.x - first.position.x;
      const vertical = second.position.y - first.position.y;
      const approaching = (moving.x - otherVelocity.x) * horizontal
        + (moving.y - otherVelocity.y) * vertical;
      if (approaching <= 0.01) {
        continue;
      }
      if (record.first < 0) {
        if (ball.id === 0) {
          record.first = other.id;
        } else if (other.id === 0) {
          record.first = ball.id;
        }
      }
      const impact = Math.hypot(moving.x - otherVelocity.x, moving.y - otherVelocity.y);
      record.events.push({ kind: "ball", speed: impact });
    } else if (second.label === "rail" && Math.hypot(moving.x, moving.y) > PHYSICS.stopSpeed) {
      record.rails.add(ball.id);
      if (record.first >= 0) {
        record.railAfter = true;
      } else if (ball.id === 0) {
        record.cueRailBefore = true;
      }
    }
  }
}

// 按接触点滑动速度施加台呢摩擦，平移与水平旋转一起变化，滚动后使用较小阻力
function clothFriction(ball, seconds) {
  const current = velocity(ball);
  const slipX = current.x - TABLE.radius * ball.spinY;
  const slipY = current.y + TABLE.radius * ball.spinX;
  const slip = Math.hypot(slipX, slipY);
  let horizontal = current.x;
  let vertical = current.y;
  let remaining = seconds;
  if (slip > PHYSICS.stopSpeed) {
    const deceleration = PHYSICS.sliding * PHYSICS.gravity;
    const duration = Math.min(seconds, slip / (deceleration * (1 + 1 / PHYSICS.inertia)));
    const change = deceleration * duration;
    horizontal = horizontal - slipX / slip * change;
    vertical = vertical - slipY / slip * change;
    const spinChange = change / (PHYSICS.inertia * TABLE.radius);
    ball.spinX = ball.spinX - slipY / slip * spinChange;
    ball.spinY = ball.spinY + slipX / slip * spinChange;
    remaining = seconds - duration;
  }
  if (remaining > 0) {
    const speed = Math.hypot(horizontal, vertical);
    const next = Math.max(0, speed - PHYSICS.rolling * PHYSICS.gravity * remaining);
    if (speed > 0) {
      horizontal = horizontal * next / speed;
      vertical = vertical * next / speed;
    }
    ball.spinX = -vertical / TABLE.radius;
    ball.spinY = horizontal / TABLE.radius;
  }
  if (Math.hypot(horizontal, vertical) < PHYSICS.stopSpeed) {
    horizontal = 0;
    vertical = 0;
    ball.spinX = 0;
    ball.spinY = 0;
    Matter.Body.setAngularVelocity(ball.body, 0);
  }
  setVelocity(ball, horizontal, vertical);
}

// 推进固定步长，按最快球细分子步，防止满力击球越过球体或袋口
export function stepWorld(world, seconds) {
  let maximum = 0;
  for (const ball of world.balls) {
    if (ball.active) {
      const speed = velocity(ball);
      maximum = Math.max(maximum, Math.hypot(speed.x, speed.y));
    }
  }
  const substeps = Math.max(1, Math.ceil(maximum * seconds / PHYSICS.maxTravel));
  const duration = seconds / substeps;
  for (let index = 0; index < substeps; index = index + 1) {
    for (const ball of world.balls) {
      if (ball.active) {
        clothFriction(ball, duration);
      }
    }
    Matter.Engine.update(world.engine, duration * 1000);
    capturePockets(world);
  }
}

// 落袋只按真实捕获圆判断，异常越界另记犯规，不把未进袋球算作得分
function capturePockets(world) {
  for (const ball of world.balls) {
    if (!ball.active) {
      continue;
    }
    const position = ball.body.position;
    for (const pocket of world.pockets) {
      if (Math.hypot(position.x - pocket.x, position.y - pocket.y) <= pocket.radius) {
        const speed = velocity(ball);
        world.record.pots.push(ball.id);
        // 翻袋只记录目标球，白球碰库后落袋仍是犯规，不能作为翻袋证据提交
        if (ball.id !== 0 && world.record.rails.has(ball.id)) {
          world.record.bankPots.add(ball.id);
        }
        world.record.events.push({ kind: "pocket", speed: Math.hypot(speed.x, speed.y) });
        removeBall(world, ball);
        break;
      }
    }
    if (ball.active && (position.x < TABLE.left - 45 || position.x > TABLE.right + 45
        || position.y < TABLE.top - 45 || position.y > TABLE.bottom + 45)) {
      world.record.off.push(ball.id);
      removeBall(world, ball);
    }
  }
}

// 返回所有在台球是否已停止，隐藏球不参与等待
export function atRest(world) {
  for (const ball of world.balls) {
    if (ball.active) {
      const speed = velocity(ball);
      if (Math.hypot(speed.x, speed.y) >= PHYSICS.stopSpeed) {
        return false;
      }
    }
  }
  return true;
}

// 校验摆球区域、袋口和与其他球的间距，自由球与开球共用同一几何规则
export function canPlace(world, horizontal, vertical, restricted, ignoreId) {
  const radius = TABLE.radius;
  if (horizontal < TABLE.left + radius || horizontal > TABLE.right - radius
      || vertical < TABLE.top + radius || vertical > TABLE.bottom - radius) {
    return false;
  }
  if (restricted && horizontal > TABLE.head - radius) {
    return false;
  }
  for (const ball of world.balls) {
    if (ball.active && ball.id !== ignoreId) {
      if (Math.hypot(horizontal - ball.body.position.x, vertical - ball.body.position.y)
          < radius * 2 + TABLE.placementGap) {
        return false;
      }
    }
  }
  for (const pocket of world.pockets) {
    if (Math.hypot(horizontal - pocket.x, vertical - pocket.y) < pocket.radius + radius) {
      return false;
    }
  }
  return true;
}

// 在置球点附近查找空位，开球进八号或飞球后按固定顺序恢复
export function respot(world, id) {
  for (let distance = 0; distance < 800; distance = distance + TABLE.radius) {
    const horizontal = TABLE.foot - distance;
    if (canPlace(world, horizontal, TABLE.center, false, id)) {
      placeBall(world, world.balls[id], horizontal, TABLE.center);
      return;
    }
  }
  for (let vertical = TABLE.top + 30; vertical < TABLE.bottom; vertical = vertical + 24) {
    for (let horizontal = TABLE.left + 30; horizontal < TABLE.right; horizontal = horizontal + 24) {
      if (canPlace(world, horizontal, vertical, false, id)) {
        placeBall(world, world.balls[id], horizontal, vertical);
        return;
      }
    }
  }
  throw new Error("球台没有可用的置球位置");
}

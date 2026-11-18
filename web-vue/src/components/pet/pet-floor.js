import Matter from "matter-js";

export const TOY_RADIUS = 0.2;
export const TOY_START = { x: 1.33, z: 0.98 };
export const BOWL_START = { x: -1.55, z: 0.85 };
export const BOWL_RADIUS = 0.36;
const SCALE = 120;
const STEP_MS = 1000 / 120;
const KICK_SPEED = 4.8;
const STOP_SPEED = 0.08;
const HALF_WIDTH = 3.1;
const MIN_DEPTH = -2.1;
const MAX_DEPTH = 2.17;

// 用 Matter.js 在地板平面模拟球、猫和食碗，画面仍由 Three.js 绘制
export function createPetFloor() {
  const engine = Matter.Engine.create({ gravity: { x: 0, y: 0 } });
  const toy = Matter.Bodies.circle(TOY_START.x * SCALE, TOY_START.z * SCALE, TOY_RADIUS * SCALE, {
    restitution: 0.64,
    frictionAir: 0.048,
    friction: 0,
  });
  const cat = Matter.Bodies.circle(0, 0, 0.42 * SCALE, { isStatic: true });
  const bowl = Matter.Bodies.circle(BOWL_START.x * SCALE, BOWL_START.z * SCALE, BOWL_RADIUS * SCALE, { isStatic: true });
  const stand = Matter.Bodies.circle(2.02 * SCALE, -1.18 * SCALE, 0.32 * SCALE, { isStatic: true });
  const middle = (MIN_DEPTH + MAX_DEPTH) / 2;
  const depth = MAX_DEPTH - MIN_DEPTH + 0.3;
  const width = HALF_WIDTH * 2 + 0.3;
  Matter.Composite.add(engine.world, [
    toy, cat, bowl, stand,
    makeWall(-HALF_WIDTH - 0.15, middle, 0.3, depth),
    makeWall(HALF_WIDTH + 0.15, middle, 0.3, depth),
    makeWall(0, MIN_DEPTH - 0.15, width, 0.3),
    makeWall(0, MAX_DEPTH + 0.15, width, 0.3),
  ]);
  let remaining = 0;

  // 模型改变位置后，同步地面占用范围，球就不会从猫脚底穿过去
  function placeCat(horizontal, depth) {
    Matter.Body.setPosition(cat, { x: horizontal * SCALE, y: depth * SCALE });
  }

  // 拖动或归位食碗时，把碰撞体同步到网格的新位置
  function placeBowl(position) {
    Matter.Body.setPosition(bowl, { x: position.x * SCALE, y: position.z * SCALE });
  }

  // 手和小猫控制玩具时暂停惯性，让球停在指定的位置
  function placeToy(position) {
    stop();
    Matter.Body.setPosition(toy, { x: position.x * SCALE, y: position.z * SCALE });
  }

  // 球停稳后才能安排归位，避免小猫追到球之前的位置
  function toyIsRolling() {
    return toy.speed >= STOP_SPEED;
  }

  // 根据猫和球的相对方向拨球，力度越大滚得越远
  function push(horizontal, depth) {
    Matter.Body.setVelocity(toy, { x: horizontal * KICK_SPEED, y: depth * KICK_SPEED });
  }

  // 玩耍起步前收住正在滚动的球，也用于减弱动态效果
  function stop() {
    Matter.Body.setVelocity(toy, { x: 0, y: 0 });
    Matter.Body.setAngularVelocity(toy, 0);
    remaining = 0;
  }

  // 用固定小步推进，显示器帧率不改变碰撞时的速度和滚动距离
  function advance(milliseconds) {
    remaining = remaining + milliseconds;
    while (remaining >= STEP_MS) {
      Matter.Engine.update(engine, STEP_MS);
      remaining = remaining - STEP_MS;
    }
    if (toy.speed > 0 && toy.speed < STOP_SPEED) {
      stop();
    }
    return { x: toy.position.x / SCALE, z: toy.position.y / SCALE };
  }

  return { placeCat: placeCat, placeBowl: placeBowl, placeToy: placeToy, toyIsRolling: toyIsRolling, push: push, stop: stop, advance: advance };
}

// 把屋内可滚动的范围换算成物理引擎使用的长方形边界
function makeWall(horizontal, depth, width, length) {
  return Matter.Bodies.rectangle(horizontal * SCALE, depth * SCALE, width * SCALE, length * SCALE, {
    isStatic: true,
  });
}

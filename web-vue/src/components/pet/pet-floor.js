import Matter from "matter-js";

// 玩具球的半径，模型、碰撞和拖动使用同一个值
export const TOY_RADIUS = 0.2;
// 球在垫子上的初始位置和归位目标
export const TOY_START = { x: 1.33, z: 0.98 };
// 食碗的初始位置和归位目标
export const BOWL_START = { x: -1.55, z: 0.85 };
// 食碗的碰撞半径，同时用于限制拖动间距
export const BOWL_RADIUS = 0.36;
// 碗沿的高度和管身半径，用于保持进食动作和碗沿之间的距离
export const BOWL_RIM = { height: 0.18, thickness: 0.044 };
// 一个场景单位对应的 Matter.js 长度
const SCALE = 120;
// 物理每秒计算 120 次，不受显示帧率影响
const STEP_MS = 1000 / 120;
// 猫拨球时给球的初始速度，具体方向由调用方传入
const KICK_SPEED = 4.8;
// 球低于这个速度时按停稳处理，可以开始归位
const STOP_SPEED = 0.08;
// 球可滚动区域的左右半宽
const HALF_WIDTH = 3.1;
// 滚动区域靠墙一侧的边界
const MIN_DEPTH = -2.1;
// 滚动区域靠镜头一侧的边界
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

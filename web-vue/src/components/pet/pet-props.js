import { MathUtils, Vector3 } from "three";
import { BOWL_RADIUS, BOWL_START, TOY_RADIUS, TOY_START } from "./pet-floor.js";

// 归位时每秒走过的场景距离
const WALK_SPEED = 1.85;
// 即使物件挪动很少，也给每段移动留出这个最短秒数
const MIN_MOVE_DURATION = 0.32;
// 从坐姿站到四条腿上的秒数
const STAND_DURATION = 0.28;
// 回到垫子中央后坐下所用的秒数
const SIT_DURATION = 0.27;
// 拾取或放下物件所用的秒数
const PICKUP_DURATION = 0.27;
// 松手后等待归位的秒数，留时间看清物件的新位置
const RETURN_DELAY = 0.48;
// 拨球时猫与球中心的距离
const TOY_REACH = 0.76;
// 叼碗时猫与食碗中心的距离，预留头部和碗的空间
const BOWL_REACH = 1.05;
// 小于这个位移时视为仍在原位
const MIN_DISPLACEMENT = 0.045;
// 物件拖动范围的左右半宽
const EDGE_WIDTH = 2.52;
// 物件拖动范围的前侧边界
const EDGE_DEPTH = 1.93;
// 物件拖动范围的后侧边界，避免推到墙边摆件里
const BACK_EDGE_DEPTH = -0.62;
// 两件物品的碰撞半径之外保留的间距
const PROP_GAP = 0.04;
// 拖动物件时给宠物身体留出的空间
const CAT_CLEARANCE = 0.63;
// 叼碗时将碗抬离垫子的高度
const BOWL_LIFT = 0.23;
// 停步或起步时调整步幅的秒数
const STRIDE_BLEND_SECONDS = 0.14;

// 计算物件的新位置，不能推到猫身上、另一件物品里或画面边缘外
export function dragPropPosition(name, position, other, cat) {
  let radius = TOY_RADIUS;
  let otherRadius = BOWL_RADIUS;
  let reserved = BOWL_START;
  if (name === "bowl") {
    radius = BOWL_RADIUS;
    otherRadius = TOY_RADIUS;
    reserved = TOY_START;
  }
  const horizontal = MathUtils.clamp(position.x, -EDGE_WIDTH + radius, EDGE_WIDTH - radius);
  const depth = MathUtils.clamp(position.z, BACK_EDGE_DEPTH, EDGE_DEPTH - radius);
  const clear = radius + otherRadius + PROP_GAP;
  if (Math.hypot(horizontal - cat.x, depth - cat.z) < radius + CAT_CLEARANCE) {
    return null;
  }
  if (Math.hypot(horizontal - other.x, depth - other.z) < clear) {
    return null;
  }
  if (Math.hypot(horizontal - reserved.x, depth - reserved.z) < clear) {
    return null;
  }
  return { x: horizontal, z: depth };
}

// 返回靠近物件、放回原位和回到垫子的路线，所有动作共享这条时间线
export function makePropReturn(name, position, now) {
  let home = TOY_START;
  let reach = TOY_REACH;
  if (name === "bowl") {
    home = BOWL_START;
    reach = BOWL_REACH;
  }
  const start = new Vector3(position.x, 0, position.z);
  const end = new Vector3(home.x, 0, home.z);
  const difference = end.clone();
  difference.sub(start);
  const distance = difference.length();
  if (distance < MIN_DISPLACEMENT) {
    return null;
  }
  const direction = difference.normalize();
  const behind = start.clone();
  behind.addScaledVector(direction, -reach);
  const drop = end.clone();
  drop.addScaledVector(direction, -reach);
  const outward = behind.length();
  const inward = drop.length();
  const arrival = STAND_DURATION + Math.max(MIN_MOVE_DURATION, outward / WALK_SPEED);
  const pickup = arrival + PICKUP_DURATION;
  const delivery = pickup + Math.max(MIN_MOVE_DURATION, distance / WALK_SPEED);
  const homecoming = delivery + PICKUP_DURATION + Math.max(MIN_MOVE_DURATION, inward / WALK_SPEED);
  const finished = homecoming + SIT_DURATION;
  return {
    name: name, startedAt: now + RETURN_DELAY * 1000, start: start, end: end,
    behind: behind, drop: drop, arrival: arrival, pickup: pickup, delivery: delivery,
    outward: outward, inward: inward, distance: distance, homecoming: homecoming, finished: finished,
  };
}

// 在给定时间计算猫和物件的位置，归位时球贴地滚，食碗被叼起一点
export function readPropReturn(route, now) {
  const elapsed = (now - route.startedAt) / 1000;
  if (elapsed < 0) {
    return null;
  }
  const loweringEnd = route.delivery + PICKUP_DURATION;
  const cat = new Vector3();
  const item = route.start.clone();
  let face = route.start;
  let stand = 1;
  let distance = 0;
  let stride = 0;
  let reach = 0;
  let lift = 0;
  if (elapsed < STAND_DURATION) {
    stand = smoothProgress(elapsed, 0, STAND_DURATION);
  } else if (elapsed < route.arrival) {
    const progress = smoothProgress(elapsed, STAND_DURATION, route.arrival);
    cat.copy(route.behind);
    cat.multiplyScalar(progress);
    distance = route.outward * progress;
    stride = strideWeight(elapsed, STAND_DURATION, route.arrival);
  } else if (elapsed < route.pickup) {
    distance = route.outward;
    cat.copy(route.behind);
    reach = smoothProgress(elapsed, route.arrival, route.pickup);
    lift = reach;
  } else if (elapsed < route.delivery) {
    const progress = smoothProgress(elapsed, route.pickup, route.delivery);
    cat.lerpVectors(route.behind, route.drop, progress);
    item.lerp(route.end, progress);
    face = route.end;
    distance = route.outward + route.distance * progress;
    stride = strideWeight(elapsed, route.pickup, route.delivery);
    reach = 1;
    lift = 1;
  } else {
    item.copy(route.end);
    const progress = smoothProgress(elapsed, loweringEnd, route.homecoming);
    cat.copy(route.drop);
    cat.multiplyScalar(1 - progress);
    distance = route.outward + route.distance + route.inward * progress;
    if (elapsed < loweringEnd) {
      face = route.end;
      reach = 1 - smoothProgress(elapsed, route.delivery, loweringEnd);
      lift = reach;
    } else if (elapsed < route.homecoming) {
      face = new Vector3();
      stride = strideWeight(elapsed, loweringEnd, route.homecoming);
    } else {
      stand = 1 - smoothProgress(elapsed, route.homecoming, route.finished);
    }
  }
  if (route.name === "bowl") {
    item.y = lift * BOWL_LIFT;
  }
  return { cat: cat, item: item, face: face, stand: stand, distance: distance, stride: stride, reach: reach, done: elapsed >= route.finished };
}

// 在每段移动的两端减速，换阶段时不会突然改变位置
function smoothProgress(elapsed, start, end) {
  return MathUtils.smoothstep(elapsed, start, end);
}

// 起步和停步时渐变步幅，行进距离决定四条腿的落脚顺序
function strideWeight(elapsed, start, end) {
  return Math.min(1, (elapsed - start) / STRIDE_BLEND_SECONDS, (end - elapsed) / STRIDE_BLEND_SECONDS);
}

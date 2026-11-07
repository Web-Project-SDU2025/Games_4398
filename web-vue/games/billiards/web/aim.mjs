import { TABLE, PLAY, PHYSICS } from "./config.mjs";

// 点积和距离计算的浮点容差，用于排除贴球或贴库时没有接近速度的切向接触
const CONTACT_EPSILON = 1e-9;
// 贴库方向的浮点容差，近乎沿库时不计算遥远且不稳定的交点
const RAIL_DIRECTION_EPSILON = 1e-6;
// 球心与库边接触位置的距离容差，单位为逻辑像素，用于吸收碰撞求解残差
const RAIL_GAP_EPSILON = 0.02;
// 无障碍瞄准线的最大长度，单位为逻辑像素
const PREVIEW_DISTANCE = 1100;
// 球台尺寸固定，预览复用库边描述，不在每次鼠标移动时重建对象
const BOUNDARIES = createBoundaries();

// 从球台配置生成球心接触库边的范围，袋口位置保留真实缺口
function createBoundaries() {
  const middle = (TABLE.left + TABLE.right) / 2;
  return [
    { axis: "y", value: TABLE.top + TABLE.radius, normal: 1,
      minimum: TABLE.left + TABLE.cornerGap, maximum: middle - TABLE.sideGap },
    { axis: "y", value: TABLE.top + TABLE.radius, normal: 1,
      minimum: middle + TABLE.sideGap, maximum: TABLE.right - TABLE.cornerGap },
    { axis: "y", value: TABLE.bottom - TABLE.radius, normal: -1,
      minimum: TABLE.left + TABLE.cornerGap, maximum: middle - TABLE.sideGap },
    { axis: "y", value: TABLE.bottom - TABLE.radius, normal: -1,
      minimum: middle + TABLE.sideGap, maximum: TABLE.right - TABLE.cornerGap },
    { axis: "x", value: TABLE.left + TABLE.radius, normal: 1,
      minimum: TABLE.top + TABLE.cornerGap, maximum: TABLE.bottom - TABLE.cornerGap },
    { axis: "x", value: TABLE.right - TABLE.radius, normal: -1,
      minimum: TABLE.top + TABLE.cornerGap, maximum: TABLE.bottom - TABLE.cornerGap },
  ];
}

// 从光标指向主球更新单位瞄准方向，靠近球心时沿用上一方向以免抖动
export function aimDirection(match, cursor, previous) {
  const position = match.world.balls[0].body.position;
  const horizontal = position.x - cursor.x;
  const vertical = position.y - cursor.y;
  const distance = Math.hypot(horizontal, vertical);
  const nearestRail = Math.min(position.x - TABLE.left, TABLE.right - position.x,
    position.y - TABLE.top, TABLE.bottom - position.y);
  let deadZone = PLAY.aimDeadZone;
  if (nearestRail <= TABLE.radius + RAIL_GAP_EPSILON) {
    deadZone = PLAY.railAimDeadZone;
  }
  if (distance < deadZone) {
    return previous;
  }
  return { x: horizontal / distance, y: vertical / distance };
}

// 拖过满力时移动计距起点，丢弃多余行程，让反向回拉立即降低力度
export function limitPullAnchor(anchor, cursor) {
  if (!anchor) {
    return null;
  }
  const horizontal = cursor.x - anchor.x;
  const vertical = cursor.y - anchor.y;
  const distance = Math.hypot(horizontal, vertical);
  if (distance <= PLAY.pullMaximum) {
    return anchor;
  }
  const scale = PLAY.pullMaximum / distance;
  return { x: cursor.x - horizontal * scale, y: cursor.y - vertical * scale };
}

// 有效拉杆距离转换为零到一力度，死区内松手表示取消
export function shotPower(anchor, cursor) {
  if (!anchor) {
    return 0;
  }
  const distance = Math.hypot(cursor.x - anchor.x, cursor.y - anchor.y);
  if (distance <= PLAY.pullDeadZone) {
    return 0;
  }
  return Math.min(1, (distance - PLAY.pullDeadZone) / (PLAY.pullMaximum - PLAY.pullDeadZone));
}

// 射线与圆求首次交点，碰球预览把目标半径扩为两球半径之和
function circleDistance(origin, direction, center, radius) {
  const horizontal = center.x - origin.x;
  const vertical = center.y - origin.y;
  const projection = horizontal * direction.x + vertical * direction.y;
  const distanceSquared = horizontal * horizontal + vertical * vertical;
  const gapSquared = distanceSquared - radius * radius;
  // 贴住或轻微重叠时，只有朝目标球靠近才算即刻碰撞，远离和擦边不能截断整条预览
  if (gapSquared <= 0) {
    if (projection > CONTACT_EPSILON) {
      return 0;
    }
    return Infinity;
  }
  const perpendicular = distanceSquared - projection * projection;
  if (projection <= 0 || perpendicular > radius * radius) {
    return Infinity;
  }
  const root = Math.sqrt(Math.max(0, radius * radius - perpendicular));
  // 用等价的除法形式避免两个接近数相减，近距离碰撞的落点因此更稳定
  return gapSquared / (projection + root);
}

// 预览读取物理世界的库边和球位，选择最近碰撞，给出虚影球与分离方向
export function aimPreview(match, direction) {
  const origin = match.world.balls[0].body.position;
  let distance = PREVIEW_DISTANCE;
  let target = null;
  let railDirection = null;
  for (const ball of match.world.balls) {
    if (ball.active && ball.id !== 0) {
      const hit = circleDistance(origin, direction, ball.body.position, TABLE.radius * 2);
      if (hit < distance) {
        distance = hit;
        target = ball;
      }
    }
  }
  for (const boundary of BOUNDARIES) {
    let delta = direction.y;
    let start = origin.y;
    if (boundary.axis === "x") {
      delta = direction.x;
      start = origin.x;
    }
    // 库边只拦截朝台外的方向，贴库向内或沿库瞄准时不能被身后的库边截断
    const normalSpeed = delta * boundary.normal;
    if (normalSpeed >= -RAIL_DIRECTION_EPSILON) {
      continue;
    }
    const gap = (start - boundary.value) * boundary.normal;
    let hit = 0;
    if (gap > RAIL_GAP_EPSILON) {
      hit = gap / -normalSpeed;
    }
    let along = origin.x + direction.x * hit;
    if (boundary.axis === "x") {
      along = origin.y + direction.y * hit;
    }
    if (hit < distance && along >= boundary.minimum && along <= boundary.maximum) {
      distance = hit;
      target = null;
      railDirection = { x: direction.x, y: direction.y };
      if (boundary.axis === "x") {
        railDirection.x = -railDirection.x;
      } else {
        railDirection.y = -railDirection.y;
      }
    }
  }
  const ghost = { x: origin.x + direction.x * distance, y: origin.y + direction.y * distance };
  let targetDirection = null;
  let cueDirection = null;
  if (target) {
    const horizontal = target.body.position.x - ghost.x;
    const vertical = target.body.position.y - ghost.y;
    const length = Math.hypot(horizontal, vertical);
    if (length > CONTACT_EPSILON) {
      targetDirection = { x: horizontal / length, y: vertical / length };
      const approaching = direction.x * targetDirection.x + direction.y * targetDirection.y;
      const impulse = (1 + PHYSICS.ballRestitution) * approaching / 2;
      const cueX = direction.x - targetDirection.x * impulse;
      const cueY = direction.y - targetDirection.y * impulse;
      const cueLength = Math.hypot(cueX, cueY);
      if (cueLength > CONTACT_EPSILON) {
        cueDirection = { x: cueX / cueLength, y: cueY / cueLength };
      }
    }
  }
  return { origin: origin, ghost: ghost, target: target, distance: distance,
    targetDirection: targetDirection, cueDirection: cueDirection, railDirection: railDirection };
}

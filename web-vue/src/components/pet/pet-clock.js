import { MathUtils } from "three";

// 表盘的整圈弧度，十二小时制的指针都用它换算
export const CLOCK_TURN = 2 * Math.PI;
// 表盘与边框间有一小圈空隙，点中可见表面才调整指针
export const CLOCK_FACE_RADIUS = 0.27;
// 松开后保留临时读数的毫秒数
const PREVIEW_MS = 1200;
// 从临时时间平滑回到实时时间的毫秒数
const RESUME_MS = 260;
// 表盘中心附近没有清晰方向，忽略这个范围内的拨针
const CENTER_RADIUS = 0.035;
// 分针转一圈对应 60 分钟，累计 12 圈时针才转一圈
const MINUTES_PER_TURN = 60;
// 时针转一圈对应的分钟数，校回实时时按这一圈取最短路径
const MINUTES_PER_DIAL = 12 * MINUTES_PER_TURN;
// 一整天有两圈时针，窗景需要区分表盘上同一个位置的上午和下午
const MINUTES_PER_DAY = 2 * MINUTES_PER_DIAL;
// 用时间戳记录拖动读数，松手后再恢复走时
const MILLISECONDS_PER_MINUTE = 60 * 1000;

// 将设备本地时间换成指针角度，小时针随分针移动
export function readClockAngles(date, offsetMinutes) {
  const seconds = date.getSeconds() + date.getMilliseconds() / 1000;
  const dialMinutes = readClockMinutes(date, offsetMinutes);
  const hourMinutes = MathUtils.euclideanModulo(dialMinutes, MINUTES_PER_DIAL);
  const minutes = MathUtils.euclideanModulo(dialMinutes, MINUTES_PER_TURN);
  return {
    hour: -hourMinutes / MINUTES_PER_DIAL * CLOCK_TURN,
    minute: -minutes / MINUTES_PER_TURN * CLOCK_TURN,
    second: -seconds / 60 * CLOCK_TURN,
  };
}

// 读出拨针指向的本地小时，跨过午夜时回到第二天的零点
export function readClockHour(date, offsetMinutes) {
  const minutes = readClockMinutes(date, offsetMinutes);
  const dayMinutes = MathUtils.euclideanModulo(minutes, MINUTES_PER_DAY);
  return Math.floor(dayMinutes / MINUTES_PER_TURN);
}

// 为表盘和窗景使用同一份读数，保留拖动累计的整圈和小数分钟
function readClockMinutes(date, offsetMinutes) {
  const seconds = date.getSeconds() + date.getMilliseconds() / 1000;
  const localMinutes = date.getHours() * MINUTES_PER_TURN + date.getMinutes();
  return localMinutes + seconds / 60 + offsetMinutes;
}

// 创建只影响表盘的临时拨针状态，用单调时间计算松手后的恢复
export function createClockPreview() {
  let offset = 0;
  let previousMinute = null;
  let heldTime = 0;
  let releaseAt = -Infinity;

  // 第一下对准表盘，继续拖动时累计每一小段位移而不是丢弃整圈
  function point(horizontal, vertical, date, now) {
    if (Math.hypot(horizontal, vertical) < CENTER_RADIUS) {
      return false;
    }
    const minute = Math.atan2(horizontal, vertical) / CLOCK_TURN * MINUTES_PER_TURN;
    if (previousMinute === null) {
      const currentOffset = readOffset(now, false, date);
      const seconds = date.getSeconds() + date.getMilliseconds() / 1000;
      const shownMinute = date.getMinutes() + seconds / 60 + currentOffset;
      const change = shortestChange(minute - shownMinute, MINUTES_PER_TURN);
      heldTime = date.getTime() + (currentOffset + change) * MILLISECONDS_PER_MINUTE;
    } else {
      // 跨过十二点或反向时只取相邻点的短弧，时针仍会记住之前的整圈
      const change = shortestChange(minute - previousMinute, MINUTES_PER_TURN);
      heldTime = heldTime + change * MILLISECONDS_PER_MINUTE;
    }
    previousMinute = minute;
    return true;
  }

  // 记录完成交互的时刻，开始等待自动校时
  function release(now, date) {
    if (previousMinute !== null) {
      const minutes = readOffset(now, false, date);
      offset = shortestChange(minutes, MINUTES_PER_DIAL);
      previousMinute = null;
      releaseAt = now;
    }
  }

  // 拖动时保留偏移，松手后逐渐回到实时读数
  function readOffset(now, reducedMotion, date) {
    if (previousMinute !== null) {
      return (heldTime - date.getTime()) / MILLISECONDS_PER_MINUTE;
    }
    const elapsed = now - releaseAt;
    if (elapsed < PREVIEW_MS) {
      return offset;
    }
    if (reducedMotion || elapsed >= PREVIEW_MS + RESUME_MS) {
      return 0;
    }
    return offset * (1 - MathUtils.smoothstep(elapsed, PREVIEW_MS, PREVIEW_MS + RESUME_MS));
  }

  return { point: point, release: release, readOffset: readOffset };
}

// 把绕表盘的差值换到最近的方向，正值顺拨，负值逆拨
function shortestChange(minutes, turnMinutes) {
  const halfTurn = turnMinutes / 2;
  return MathUtils.euclideanModulo(minutes + halfTurn, turnMinutes) - halfTurn;
}

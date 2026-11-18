import { MathUtils } from "three";
import { TOY_RADIUS } from "./pet-floor.js";

const STAND_END = 0.28;
const ARRIVAL = 0.93;
const REACH_START = 1.01;
export const PLAY_CONTACT = 1.24;
const RELEASE = 1.46;
const WALK_HOME = 2.17;
export const PLAY_DURATION = 2.45;

// 先站起来并朝向球，再走到伸爪能碰到的位置，拨完后退回去坐下
export function readPlayPose(elapsed, target, size) {
  const distance = Math.hypot(target.x, target.z);
  const reach = 0.43 * size + TOY_RADIUS;
  const path = Math.max(0, distance - reach);
  const forward = MathUtils.smoothstep(elapsed, STAND_END, ARRIVAL);
  const backward = MathUtils.smoothstep(elapsed, RELEASE, WALK_HOME);
  const travel = path * (forward - backward);
  let horizontal = 0;
  let depth = 0;
  if (distance > 0) {
    horizontal = target.x / distance * travel;
    depth = target.z / distance * travel;
  }
  const rise = MathUtils.smoothstep(elapsed, 0, STAND_END);
  const sit = MathUtils.smoothstep(elapsed, WALK_HOME, PLAY_DURATION);
  const extend = MathUtils.smoothstep(elapsed, REACH_START, PLAY_CONTACT);
  const retract = MathUtils.smoothstep(elapsed, PLAY_CONTACT, RELEASE);
  const stride = Math.max(stepWeight(elapsed, STAND_END, ARRIVAL), stepWeight(elapsed, RELEASE, WALK_HOME));
  return { x: horizontal, z: depth, stand: rise - sit, distance: travel / size, stride: stride, reach: extend - retract };
}

// 起步和停步收小步幅，停下来拨球时三条腿稳稳撑在地上
function stepWeight(elapsed, start, end) {
  if (elapsed <= start || elapsed >= end) {
    return 0;
  }
  return Math.min(1, (elapsed - start) / 0.16, (end - elapsed) / 0.16);
}

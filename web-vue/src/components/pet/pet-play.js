import { MathUtils } from "three";
import { TOY_RADIUS } from "./pet-floor.js";

// 从逗玩开始计时，小猫四足站稳的时刻，单位为秒
const STAND_END = 0.28;
// 走到球前并停下的时刻，单位为秒
const ARRIVAL = 0.93;
// 停稳后开始伸出前爪的时刻，单位为秒
const REACH_START = 1.01;
// 前爪碰球的时刻，场景据此启动球的物理运动
export const PLAY_CONTACT = 1.24;
// 收回前爪并开始后退的时刻，单位为秒
const RELEASE = 1.46;
// 回到垫子中央并开始坐下的时刻，单位为秒
const WALK_HOME = 2.17;
// 从起身到坐回原位的总秒数
export const PLAY_DURATION = 2.45;
// 本体模型的伸爪距离，场景会按当前等级体型缩放
const PAW_REACH = 0.43;
// 步幅在起步和停步时从零过渡所用的秒数
const STRIDE_BLEND_SECONDS = 0.16;

// 先站起来并朝向球，再走到伸爪能碰到的位置，拨完后退回去坐下
export function readPlayPose(elapsed, target, size) {
  const distance = Math.hypot(target.x, target.z);
  const reach = PAW_REACH * size + TOY_RADIUS;
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
  return Math.min(1, (elapsed - start) / STRIDE_BLEND_SECONDS, (end - elapsed) / STRIDE_BLEND_SECONDS);
}

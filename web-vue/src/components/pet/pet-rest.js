import { AnimationClip, MathUtils, NumberKeyframeTrack } from "three";

export const REST_DURATION = 8.25;
const REST_TIMES = [0, 0.28, 0.62, 1.05, 1.26, 7.08, 7.28, 7.65, REST_DURATION];
const CLOSED_FROM = 0.82;
const CLOSED_UNTIL = 7.5;
const STEPS_PER_POSE = 8;

// 依次记录坐姿、探爪、伏低、侧卧、抬头和撑起时各关节的位置
export function createRestClip() {
  const tracks = [];

  // 胸和胯向垫子落下，头先跟着向前探，再独立靠到前爪旁
  addTrack(tracks, "chest.position[y]", [0.56, 0.54, 0.49, 0.454, 0.454, 0.454, 0.48, 0.52, 0.56]);
  addTrack(tracks, "chest.position[z]", [-0.15, -0.17, -0.21, -0.24, -0.24, -0.24, -0.22, -0.18, -0.15]);
  addTrack(tracks, "chest.scale[y]", [1, 0.97, 0.8, 0.7, 0.7, 0.7, 0.75, 0.93, 1]);
  addTrack(tracks, "chest.scale[z]", [1, 1.03, 1.22, 1.33, 1.33, 1.33, 1.27, 1.07, 1]);
  addTrack(tracks, "chest.rotation[z]", [0, 0.03, 0.19, 0.48, 0.48, 0.48, 0.43, 0.15, 0]);
  addTrack(tracks, "head.position[y]", [1.65, 1.48, 0.95, 0.67, 0.67, 0.67, 0.78, 1.22, 1.65]);
  addTrack(tracks, "head.position[z]", [0.15, 0.43, 0.73, 0.77, 0.77, 0.77, 0.75, 0.49, 0.15]);
  addTrack(tracks, "head.position[x]", [0, -0.04, -0.31, -0.49, -0.49, -0.49, -0.4, -0.12, 0]);
  // 脸颊比头壳更靠前，减少俯角和侧倾才能让它停在垫子上方
  addTrack(tracks, "head.rotation[x]", [0, 0.15, 0.24, 0.025, 0.025, 0.025, -0.1, -0.16, 0]);
  addTrack(tracks, "head.rotation[z]", [0, 0.04, 0.16, 0.23, 0.23, 0.23, 0.17, 0.08, 0]);
  addTrack(tracks, "collar.position[y]", [1.091, 0.98, 0.67, 0.53, 0.53, 0.53, 0.6, 0.86, 1.091]);
  addTrack(tracks, "collar.position[x]", [0, -0.04, -0.19, -0.23, -0.23, -0.23, -0.19, -0.08, 0]);
  addTrack(tracks, "collar.position[z]", [0.075, 0.12, 0.27, 0.31, 0.31, 0.31, 0.27, 0.17, 0.075]);
  addTrack(tracks, "collar.rotation[z]", [0, 0.04, 0.18, 0.52, 0.52, 0.52, 0.4, 0.14, 0]);

  // 先由左前爪探地承重，右爪随后收进胸前，起身时两爪重新向前撑
  addTrack(tracks, "leftPaw.position[y]", [0.45, 0.4, 0.35, 0.37, 0.37, 0.37, 0.34, 0.4, 0.45]);
  addTrack(tracks, "leftPaw.position[x]", [-0.238, -0.26, -0.4, -0.48, -0.48, -0.48, -0.4, -0.27, -0.238]);
  addTrack(tracks, "leftPaw.position[z]", [0.25, 0.31, 0.45, 0.48, 0.48, 0.48, 0.46, 0.34, 0.25]);
  addTrack(tracks, "leftPaw.rotation[x]", [0, -0.44, -0.89, -1.02, -1.02, -1.02, -1.03, -0.47, 0]);
  addTrack(tracks, "leftElbow.rotation[x]", [0, 0.3, 0.43, 1.1, 1.1, 1.1, 0.63, 0.28, 0]);
  addTrack(tracks, "leftWrist.rotation[x]", [0, 0.07, 0.26, -0.12, -0.12, -0.12, 0.24, 0.14, 0]);
  addTrack(tracks, "leftWrist.position[z]", [0.082, 0.082, 0.15, 0.17, 0.17, 0.17, 0.18, 0.1, 0.082]);
  addTrack(tracks, "rightPaw.position[y]", [0.45, 0.43, 0.35, 0.39, 0.39, 0.39, 0.33, 0.4, 0.45]);
  addTrack(tracks, "rightPaw.position[x]", [0.238, 0.22, 0.24, 0.26, 0.26, 0.26, 0.26, 0.24, 0.238]);
  addTrack(tracks, "rightPaw.position[z]", [0.25, 0.28, 0.39, 0.45, 0.45, 0.45, 0.42, 0.35, 0.25]);
  addTrack(tracks, "rightPaw.rotation[x]", [0, -0.14, -0.7, -1.37, -1.37, -1.37, -0.95, -0.42, 0]);
  addTrack(tracks, "rightElbow.rotation[x]", [0, 0.08, 0.33, 1.28, 1.28, 1.28, 0.73, 0.32, 0]);
  addTrack(tracks, "rightWrist.rotation[x]", [0, 0, 0.18, -0.63, -0.63, -0.63, 0.08, 0.1, 0]);

  // 后腿错开收进肚子旁，尾巴的网格则沿垫子弯过来
  addTrack(tracks, "leftHip.position[y]", [0.3, 0.29, 0.29, 0.29, 0.29, 0.29, 0.29, 0.3, 0.3]);
  addTrack(tracks, "leftHip.rotation[y]", [0, -0.1, -0.24, -0.42, -0.42, -0.42, -0.32, -0.14, 0]);
  addTrack(tracks, "leftKnee.rotation[x]", [0, 0.13, 0.42, 0.64, 0.64, 0.64, 0.5, 0.16, 0]);
  addTrack(tracks, "rightHip.position[y]", [0.3, 0.28, 0.27, 0.37, 0.37, 0.37, 0.31, 0.32, 0.3]);
  addTrack(tracks, "rightHip.rotation[x]", [0, 0.08, 0.28, 0.63, 0.63, 0.63, 0.37, 0.16, 0]);
  addTrack(tracks, "rightKnee.rotation[x]", [0, 0.12, 0.44, 0.92, 0.92, 0.92, 0.53, 0.18, 0]);
  addTrack(tracks, "tailSkin.morphTargetInfluences[0]", [0, 0.1, 0.69, 1, 1, 1, 0.94, 0.43, 0]);
  return new AnimationClip("rest", REST_DURATION, tracks);
}

// 每段单独缓入缓出，避免短动作和几秒钟的停留相邻时插值越过地面
function addTrack(tracks, property, values) {
  const times = [REST_TIMES[0]];
  const samples = [values[0]];
  for (let pose = 1; pose < REST_TIMES.length; pose = pose + 1) {
    const start = values[pose - 1];
    const end = values[pose];
    if (start !== end) {
      for (let step = 1; step < STEPS_PER_POSE; step = step + 1) {
        const fraction = step / STEPS_PER_POSE;
        const blend = MathUtils.smoothstep(fraction, 0, 1);
        times.push(MathUtils.lerp(REST_TIMES[pose - 1], REST_TIMES[pose], fraction));
        samples.push(MathUtils.lerp(start, end, blend));
      }
    }
    times.push(REST_TIMES[pose]);
    samples.push(end);
  }
  tracks.push(new NumberKeyframeTrack(property, times, samples));
}

// 头靠到前爪后闭眼，起身过程中再睁眼
export function restEyesClosed(elapsed) {
  return elapsed >= CLOSED_FROM && elapsed < CLOSED_UNTIL;
}

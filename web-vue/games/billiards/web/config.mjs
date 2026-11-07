// 画布、球台和交互距离使用原游戏的逻辑像素，缩放只在界面层处理
export const TABLE = {
  width: 1000, height: 700, left: 60, right: 940, top: 60, bottom: 500,
  radius: 11, head: 280, foot: 720, center: 280,
  cornerGap: 37, sideGap: 25, cornerCatch: 36, sideCatch: 19,
  pocketOffset: 10, railThickness: 36, placementGap: 0.5,
};
// 速度单位为像素每秒，时间单位为秒，恢复系数和摩擦系数没有单位
export const PHYSICS = {
  step: 0.005, maxFrame: 0.1, maximumSteps: 20, maxTravel: 5,
  gravity: 3392, sliding: 0.18, rolling: 0.03, inertia: 0.4,
  ballRestitution: 0.94, railRestitution: 0.8, contactFriction: 0.08,
  stopSpeed: 0.5, maxSpeed: 3000, minSpeed: 200, timeout: 60,
  positionIterations: 8, velocityIterations: 8, circleSides: 48,
};
// 瞄准死区与拉杆距离使用逻辑像素，判定和成就提示使用秒
export const PLAY = {
  aimDeadZone: 27.5, pullDeadZone: 20, pullMaximum: 140,
  // 贴库时可供鼠标移动的空间较小，缩小球心死区以便细调方向
  railAimDeadZone: 4,
  verdictSeconds: 1.4, achievementSeconds: 4, breakRails: 4,
  doublePots: 2, triplePots: 3, runShots: 3,
};
// 渲染帧率上限独立于物理步长，高刷新率屏幕也不需要每秒重复合成数百张球台
export const DISPLAY = { maximumFps: 60, canvasHeight: 550 };
// 标准球架按从顶点到后排的顺序排列，两个后角分别属于不同球组
export const RACK = [1, 9, 2, 3, 8, 10, 11, 4, 12, 5, 6, 13, 7, 14, 15];

// 返回目标球球组，主球和八号不属于任何玩家球组
export function groupOf(id) {
  if (id >= 1 && id <= 7) {
    return "solid";
  }
  if (id >= 9 && id <= 15) {
    return "stripe";
  }
  return "";
}

// 返回中文玩家名称，所有消息通过同一入口保留编号空格
export function playerName(player) {
  return "玩家 " + (player + 1);
}

// 返回玩家球组说明，尚未定组时显示中性状态
export function groupName(group) {
  if (group === "solid") {
    return "全色 1–7";
  }
  if (group === "stripe") {
    return "花色 9–15";
  }
  return "未定组";
}

// 从原球台尺寸生成袋心与捕获圆，物理与预览读取同一份几何
export function createPockets() {
  const offset = TABLE.pocketOffset / Math.sqrt(2);
  return [
    { x: TABLE.left - offset, y: TABLE.top - offset, radius: TABLE.cornerCatch },
    { x: 500, y: TABLE.top - TABLE.pocketOffset, radius: TABLE.sideCatch },
    { x: TABLE.right + offset, y: TABLE.top - offset, radius: TABLE.cornerCatch },
    { x: TABLE.right + offset, y: TABLE.bottom + offset, radius: TABLE.cornerCatch },
    { x: 500, y: TABLE.bottom + TABLE.pocketOffset, radius: TABLE.sideCatch },
    { x: TABLE.left - offset, y: TABLE.bottom + offset, radius: TABLE.cornerCatch },
  ];
}

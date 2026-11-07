// 击球动作使用秒，阶段分别保留瞄准、后拉、停杆、前送和收杆的可见时间
export const STROKE = {
  aimSeconds: 0.2, pullSeconds: 0.25, holdSeconds: 0.125,
  strikeSeconds: 0.15, finishSeconds: 0.15,
  // 减少动态效果时缩短后拉幅度，仍保留击球时机，不能让球突然启动
  reducedPullScale: 0.35,
  tipGap: 6, pullDistance: 90,
};

// 创建单次动作状态，界面持有它，规则和物理不依赖动画实现
export function createStroke() {
  return { active: false, stage: "", elapsed: 0, presented: false,
    direction: null, origin: null, power: 0, pull: 0, opacity: 1, pullScale: 1 };
}

// 冻结方向、球位和力度，电脑播放完整准备动作，玩家从已完成的拉杆开始前送
export function beginStroke(stroke, direction, power, origin, automatic, reducedMotion) {
  const length = Math.hypot(direction.x, direction.y);
  if (!Number.isFinite(length) || length === 0) {
    cancelStroke(stroke);
    return;
  }
  stroke.active = true;
  // 画杆、画预览与实际出球共用单位方向，不能让显示与物理各自缩放
  stroke.direction = { x: direction.x / length, y: direction.y / length };
  stroke.origin = { x: origin.x, y: origin.y };
  stroke.power = power;
  stroke.pullScale = 1;
  if (reducedMotion && automatic) {
    stroke.pullScale = STROKE.reducedPullScale;
  }
  stroke.pull = power * stroke.pullScale;
  stroke.opacity = 1;
  let stage = "strike";
  if (automatic) {
    stage = "aim";
    stroke.pull = 0;
  }
  changeStage(stroke, stage);
}

// 新阶段先等待一帧实际绘制，再推进计时，避免补步或慢帧跳过关键姿态
function changeStage(stroke, stage) {
  stroke.stage = stage;
  stroke.elapsed = 0;
  stroke.presented = false;
}

// 每个显示帧只推进一个阶段，返回 true 表示已展示触球姿态且本帧可以真正击球
export function advanceStroke(stroke, seconds) {
  if (!stroke.active || !stroke.presented) {
    return false;
  }
  stroke.elapsed = stroke.elapsed + seconds;
  if (stroke.stage === "aim") {
    if (stroke.elapsed >= STROKE.aimSeconds) {
      changeStage(stroke, "pull");
    }
  } else if (stroke.stage === "pull") {
    const progress = Math.min(1, stroke.elapsed / STROKE.pullSeconds);
    stroke.pull = stroke.power * stroke.pullScale * progress;
    if (progress === 1) {
      changeStage(stroke, "hold");
    }
  } else if (stroke.stage === "hold") {
    if (stroke.elapsed >= STROKE.holdSeconds) {
      changeStage(stroke, "strike");
    }
  } else if (stroke.stage === "strike") {
    const progress = Math.min(1, stroke.elapsed / STROKE.strikeSeconds);
    const contactPull = -STROKE.tipGap / STROKE.pullDistance;
    const startPull = stroke.power * stroke.pullScale;
    stroke.pull = startPull + (contactPull - startPull) * progress;
    if (progress === 1) {
      changeStage(stroke, "contact");
    }
  } else if (stroke.stage === "contact") {
    changeStage(stroke, "finish");
    return true;
  } else if (stroke.stage === "finish") {
    stroke.opacity = Math.max(0, 1 - stroke.elapsed / STROKE.finishSeconds);
    if (stroke.opacity === 0) {
      cancelStroke(stroke);
    }
  }
  return false;
}

// 重开、取消和失焦清除尚未击出的动作，已经发生的球运动由物理继续处理
export function cancelStroke(stroke) {
  stroke.active = false;
  stroke.stage = "";
  stroke.pull = 0;
  stroke.opacity = 1;
  stroke.presented = false;
}

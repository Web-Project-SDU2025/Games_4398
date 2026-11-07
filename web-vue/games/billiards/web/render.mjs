import { TABLE, DISPLAY } from "./config.mjs";
import { aimPreview } from "./aim.mjs";
import { STROKE } from "./stroke.mjs";

// 画面颜色与原球台保持一致，线宽和球杆长度使用逻辑像素
const COLORS = { aim: "#ffffff99", target: "#4fc3f7", cue: "#e8c98a",
  legal: "#66bb6a", foul: "#e53935" };
const CUE_LENGTH = 300;
const TARGET_GUIDE = 90;
// 白球碰撞后方向线的长度，贴球时仍可从白球轮廓外读出方向，单位为逻辑像素
const CUE_GUIDE = 60;

// 绘制一颗实际球体，球影与球身分两轮绘制以避免阴影覆盖相邻球
function drawBall(context, ball, images) {
  const image = images["ball-" + ball.id];
  context.drawImage(image, Math.round(ball.body.position.x) - image.width / 2,
    Math.round(ball.body.position.y) - image.height / 2);
}

// 绘制瞄准线和虚影球，原生 Canvas 绘制无需跨 Java 图形运行环境
function drawAim(context, match, ui, images) {
  let direction = ui.direction;
  if (ui.stroke && ui.stroke.active) {
    direction = ui.stroke.direction;
  }
  const preview = aimPreview(match, direction);
  const origin = preview.origin;
  const ghost = preview.ghost;
  context.save();
  context.strokeStyle = COLORS.aim;
  context.lineWidth = 1.3;
  context.setLineDash([7, 6]);
  context.beginPath();
  context.moveTo(origin.x, origin.y);
  context.lineTo(ghost.x, ghost.y);
  context.stroke();
  context.setLineDash([]);
  context.globalAlpha = 0.35;
  const cue = images["ball-0"];
  context.drawImage(cue, ghost.x - cue.width / 2, ghost.y - cue.height / 2);
  context.globalAlpha = 1;
  // 贴库时碰撞前行程短到被白球遮住，另画反弹方向，不能把零距离接触画成穿库
  if (preview.railDirection) {
    context.strokeStyle = COLORS.aim;
    context.beginPath();
    context.moveTo(ghost.x, ghost.y);
    context.lineTo(ghost.x + preview.railDirection.x * CUE_GUIDE,
      ghost.y + preview.railDirection.y * CUE_GUIDE);
    context.stroke();
  }
  if (preview.target && preview.targetDirection) {
    const position = preview.target.body.position;
    context.strokeStyle = COLORS.target;
    context.beginPath();
    context.moveTo(position.x, position.y);
    context.lineTo(position.x + preview.targetDirection.x * TARGET_GUIDE,
      position.y + preview.targetDirection.y * TARGET_GUIDE);
    context.stroke();
    if (preview.cueDirection) {
      context.strokeStyle = COLORS.aim;
      context.beginPath();
      context.moveTo(ghost.x, ghost.y);
      context.lineTo(ghost.x + preview.cueDirection.x * CUE_GUIDE,
        ghost.y + preview.cueDirection.y * CUE_GUIDE);
      context.stroke();
    }
  }
  context.restore();
}

// 动作保留出杆时的球位和方向，击球后白球先移动，球杆在原位置收起
function drawCue(context, match, ui) {
  let origin = match.world.balls[0].body.position;
  let direction = ui.direction;
  let power = ui.power;
  context.save();
  if (ui.stroke && ui.stroke.active) {
    origin = ui.stroke.origin;
    direction = ui.stroke.direction;
    power = ui.stroke.pull;
    context.globalAlpha = ui.stroke.opacity;
  }
  const pull = STROKE.tipGap + TABLE.radius + power * STROKE.pullDistance;
  context.lineCap = "round";
  context.lineWidth = 5;
  context.strokeStyle = COLORS.cue;
  context.beginPath();
  context.moveTo(origin.x - direction.x * pull, origin.y - direction.y * pull);
  context.lineTo(origin.x - direction.x * (pull + CUE_LENGTH),
    origin.y - direction.y * (pull + CUE_LENGTH));
  context.stroke();
  context.restore();
}

// 拖动主球时绘制合法或非法落点，用对勾和叉号同时提供形状反馈
function drawPlacement(context, match, ui) {
  if (!ui.cursor) {
    return;
  }
  const horizontal = Math.max(TABLE.left + TABLE.radius, Math.min(TABLE.right - TABLE.radius, ui.cursor.x));
  const vertical = Math.max(TABLE.top + TABLE.radius, Math.min(TABLE.bottom - TABLE.radius, ui.cursor.y));
  context.save();
  context.strokeStyle = COLORS.foul;
  if (ui.placeValid) {
    context.strokeStyle = COLORS.legal;
  }
  context.fillStyle = context.strokeStyle + "44";
  context.lineWidth = 2;
  context.beginPath();
  context.arc(horizontal, vertical, TABLE.radius + 3, 0, Math.PI * 2);
  context.fill();
  context.stroke();
  context.beginPath();
  if (ui.placeValid) {
    context.moveTo(horizontal - 5, vertical);
    context.lineTo(horizontal - 1, vertical + 5);
    context.lineTo(horizontal + 7, vertical - 5);
  } else {
    context.moveTo(horizontal - 5, vertical - 5);
    context.lineTo(horizontal + 5, vertical + 5);
    context.moveTo(horizontal - 5, vertical + 5);
    context.lineTo(horizontal + 5, vertical - 5);
  }
  context.stroke();
  context.restore();
}

// 每次画面变化时合成一帧，静态底图只绘制一次，循环不生成新位图
export function drawFrame(context, match, ui, images) {
  context.clearRect(0, 0, TABLE.width, DISPLAY.canvasHeight);
  context.drawImage(images.table, 12, 12);
  // 电脑未完成选杆时隐藏上一杆姿态，方向确认后再同时显示球杆与瞄准线
  const thinking = match.mode === "single" && match.current === 1
    && !(ui.stroke && ui.stroke.active);
  // 落点提示只属于正在摆球的玩家，电脑回合不能读取玩家遗留的光标位置
  const humanPlacement = match.phase === "placement"
    && !(match.mode === "single" && match.current === 1);
  if (match.phase === "aiming" && !thinking) {
    drawAim(context, match, ui, images);
  }
  if ((match.phase === "aiming" && !thinking) || (ui.stroke && ui.stroke.active)) {
    drawCue(context, match, ui);
  }
  for (const ball of match.world.balls) {
    if (ball.active && !(ball.id === 0 && humanPlacement && ui.cursor)) {
      context.drawImage(images.shadow, Math.round(ball.body.position.x) - images.shadow.width / 2,
        Math.round(ball.body.position.y) - images.shadow.height / 2);
    }
  }
  for (const ball of match.world.balls) {
    if (ball.active && !(ball.id === 0 && humanPlacement && ui.cursor)) {
      drawBall(context, ball, images);
    }
  }
  if (match.phase === "placement") {
    if (match.restricted) {
      context.save();
      context.strokeStyle = "#ffb300";
      context.setLineDash([8, 6]);
      context.beginPath();
      context.moveTo(TABLE.head, TABLE.top);
      context.lineTo(TABLE.head, TABLE.bottom);
      context.stroke();
      context.restore();
    }
    if (humanPlacement) {
      drawPlacement(context, match, ui);
    }
  }
}

import { TABLE, PHYSICS, PLAY, DISPLAY } from "./config.mjs";
import { createMatch, resetMatch, openRound, placeCue, takeShot, stepMatch } from "./rules.mjs";
import { canPlace } from "./physics.mjs";
import { aimDirection, shotPower, limitPullAnchor } from "./aim.mjs";
import { loadImages, createAudio } from "./assets.mjs";
import { drawFrame } from "./render.mjs";
import { createComputer } from "./computer.mjs";
import { createHud } from "./hud.mjs";
import { createStroke, beginStroke, advanceStroke, cancelStroke } from "./stroke.mjs";

const canvas = document.getElementById("table");
const context = canvas.getContext("2d");
const match = createMatch();
const audio = createAudio();
const ui = { cursor: null, direction: { x: 1, y: 0 }, anchor: null, power: 0,
  lastPower: 0, placeValid: false, dirty: true, stopped: false, stroke: createStroke(),
  settlement: "" };
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const computer = createComputer(match, ui, reducedMotion, cancelPull);
let images = null;
let hud = null;
let lastTime = 0;
let pending = 0;
let frameId = 0;
let lastDrawTime = -Infinity;
let lastRevision = -1;
const start = document.getElementById("start");
const chooseModeButton = document.getElementById("choose-mode");
const reportedRounds = new Map();
let awaitingRound = "";
let rewardHideAt = 0;
const modeControls = document.getElementById("mode-controls");
const rewardStatus = document.getElementById("reward-status");

// 单人电脑回合由自动控制器处理，双人模式始终接受本地输入
function humanTurn() {
  return !ui.stroke.active && (match.mode !== "single" || match.current === 0);
}

// 按游戏实际判决顺序报告开盘与单杆证据，金额由登录账户所在的平台后端核定
function reportShots() {
  for (const report of match.reports) {
    if (!reportedRounds.has(report.round)) {
      reportedRounds.set(report.round, crypto.randomUUID());
    }
    window.parent.postMessage({ type: "4398-billiards-shot", roundId: reportedRounds.get(report.round),
      sequence: report.sequence, shot: report.shot, matchMode: report.matchMode }, location.origin);
    if (report.sequence === 0) {
      awaitingRound = reportedRounds.get(report.round);
    }
  }
  match.reports.length = 0;
}

// 把屏幕输入换算为逻辑像素，CSS 缩放和高分屏不会改变实际击球位置
function pointerPosition(event) {
  const bounds = canvas.getBoundingClientRect();
  return { x: (event.clientX - bounds.left) * TABLE.width / bounds.width,
    y: (event.clientY - bounds.top) * DISPLAY.canvasHeight / bounds.height };
}

// 更新瞄准或摆球虚影，输入只设置脏标记，重绘由统一帧循环安排
function movePointer(event) {
  if (!humanTurn() || match.phase === "opening") {
    return;
  }
  ui.cursor = pointerPosition(event);
  if (match.phase === "aiming") {
    ui.direction = aimDirection(match, ui.cursor, ui.direction);
    ui.anchor = limitPullAnchor(ui.anchor, ui.cursor);
    ui.power = shotPower(ui.anchor, ui.cursor);
  }
  ui.placeValid = canPlace(match.world, ui.cursor.x, ui.cursor.y, match.restricted, 0);
  ui.dirty = true;
}

// 开始拖动时捕获指针，鼠标离开台面后松开也能正确完成或取消击球
function pressPointer(event) {
  if (event.button !== 0 || ui.stopped || !humanTurn()) {
    return;
  }
  audio.unlock();
  canvas.focus();
  movePointer(event);
  if (match.phase === "aiming" || match.phase === "placement") {
    canvas.setPointerCapture(event.pointerId);
    ui.anchor = ui.cursor;
  }
}

// 松手时使用最终方向和拉距，摆球失败保留摆球阶段，死区内松手不出杆
function releasePointer(event) {
  if (!ui.anchor) {
    return;
  }
  movePointer(event);
  if (match.phase === "placement") {
    placeCue(match, ui.cursor.x, ui.cursor.y);
  } else if (match.phase === "aiming") {
    if (ui.power > 0) {
      beginStroke(ui.stroke, ui.direction, ui.power, match.world.balls[0].body.position,
        false, reducedMotion.matches);
    }
  }
  cancelPull();
  if (canvas.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId);
  }
}

// 清除临时拉杆状态，取消、失焦和右键共用此函数
function cancelPull() {
  ui.anchor = null;
  ui.power = 0;
  ui.dirty = true;
}

// 屏蔽台面右键菜单并取消当前拉杆，不影响平台其他区域的菜单
function cancelContext(event) {
  event.preventDefault();
  cancelStroke(ui.stroke);
  cancelPull();
}

// 重新开始只重置当前游戏，保留已经解锁的音频环境与平台会话
function restart() {
  if (match.phase === "opening") {
    return;
  }
  audio.unlock();
  const mode = document.querySelector('input[name="mode"]:checked').value;
  resetMatch(match, mode);
  match.phase = "opening";
  match.message = "正在确定开球方";
  modeControls.hidden = true;
  start.disabled = true;
  computer.reset();
  cancelStroke(ui.stroke);
  ui.settlement = "";
  rewardStatus.hidden = true;
  cancelPull();
  ui.direction = { x: 1, y: 0 };
  ui.lastPower = 0;
  canvas.focus();
}

// 返回模式选择只在重新开盘时生效，不能修改服务器已确认的本盘模式
function chooseMode() {
  if (match.phase === "opening") {
    return;
  }
  cancelPull();
  cancelStroke(ui.stroke);
  match.mode = "local";
  computer.reset();
  ui.settlement = "";
  rewardStatus.hidden = true;
  match.phase = "start";
  match.message = "选择模式后开始对局";
  modeControls.hidden = false;
  match.revision = match.revision + 1;
}

// 动作按显示帧推进，先展示杆头触球，再调用现有规则出杆，避免在物理补步里跳过动作
function updateStroke(seconds) {
  if (!ui.stroke.active) {
    return;
  }
  const hit = advanceStroke(ui.stroke, seconds);
  ui.power = ui.stroke.power;
  if (ui.stroke.stage === "aim" || ui.stroke.stage === "pull") {
    ui.power = ui.stroke.pull;
  }
  ui.dirty = true;
  if (hit) {
    if (takeShot(match, ui.stroke.direction, ui.stroke.power)) {
      ui.lastPower = ui.stroke.power;
    } else {
      cancelStroke(ui.stroke);
    }
  }
  if (!ui.stroke.active) {
    ui.power = 0;
  }
}

// 音效按钮与键盘使用同一开关，并将状态提供给辅助阅读工具
function toggleSound() {
  audio.unlock();
  const muted = audio.toggle();
  document.getElementById("sound").setAttribute("aria-pressed", String(!muted));
}

// 输入框不属于游戏，快捷键只在当前游戏页生效，不拦截平台键盘输入
function keyPressed(event) {
  if (event.key === "r" || event.key === "R") {
    restart();
  } else if (event.key === "Escape") {
    cancelStroke(ui.stroke);
    cancelPull();
  } else if (event.key === "m" || event.key === "M") {
    toggleSound();
  }
}

// 固定物理步与显示帧解耦，页面暂时卡顿时限制追赶数量，避免长任务阻塞输入
function animate(now) {
  if (ui.stopped) {
    return;
  }
  let elapsed = 0;
  if (lastTime > 0) {
    elapsed = Math.min(PHYSICS.maxFrame, (now - lastTime) / 1000);
  }
  lastTime = now;
  pending = pending + elapsed;
  let steps = 0;
  while (pending >= PHYSICS.step && steps < PHYSICS.maximumSteps) {
    stepMatch(match, PHYSICS.step);
    pending = pending - PHYSICS.step;
    steps = steps + 1;
  }
  if (steps === PHYSICS.maximumSteps) {
    pending = 0;
  }
  computer.update(elapsed);
  updateStroke(elapsed);
  audio.play(match.world.record.events);
  reportShots();
  if (!rewardStatus.hidden && now >= rewardHideAt) {
    rewardStatus.hidden = true;
  }
  if ((ui.dirty || lastRevision !== match.revision)
      && now - lastDrawTime >= 1000 / DISPLAY.maximumFps) {
    drawFrame(context, match, ui, images);
    ui.stroke.presented = true;
    hud.update();
    lastRevision = match.revision;
    ui.dirty = false;
    lastDrawTime = now;
  }
  frameId = requestAnimationFrame(animate);
}

// 网页失焦时取消未完成拉杆，返回时不追赶后台积压的模拟时间
function visibilityChanged() {
  cancelStroke(ui.stroke);
  cancelPull();
  lastTime = 0;
  pending = 0;
}

// 停止帧循环并移除事件监听，平台移除 iframe 前即释放音频和输入状态
function stop() {
  if (ui.stopped) {
    return;
  }
  ui.stopped = true;
  computer.reset();
  cancelStroke(ui.stroke);
  document.body.dataset.stopped = "true";
  cancelAnimationFrame(frameId);
  audio.close();
  canvas.removeEventListener("pointermove", movePointer);
  canvas.removeEventListener("pointerdown", pressPointer);
  canvas.removeEventListener("pointerup", releasePointer);
  canvas.removeEventListener("pointercancel", cancelPull);
  canvas.removeEventListener("contextmenu", cancelContext);
  window.removeEventListener("keydown", keyPressed);
  window.removeEventListener("blur", visibilityChanged);
  window.removeEventListener("message", receiveCommand);
  document.getElementById("restart").removeEventListener("click", restart);
  document.getElementById("sound").removeEventListener("click", toggleSound);
  document.getElementById("change-mode").removeEventListener("click", chooseMode);
  chooseModeButton.removeEventListener("click", chooseMode);
  start.removeEventListener("click", restart);
  document.removeEventListener("visibilitychange", visibilityChanged);
}

// 仅接受同源父运行器的停止消息，不提供外部脚本修改球局的入口
function receiveCommand(event) {
  if (event.source !== window.parent || event.origin !== location.origin || !event.data || ui.stopped) {
    return;
  }
  if (event.data.type === "4398-stop-game") {
    stop();
  } else if (event.data.type === "4398-billiards-result") {
    const message = event.data;
    const result = message.result;
    if (!result || match.phase === "start" ||
        message.roundId !== reportedRounds.get(match.roundNumber)) {
      return;
    }
    if (message.sequence === 0 && message.roundId === awaitingRound) {
      awaitingRound = "";
      start.disabled = false;
      if (result.code === 0 && [0, 1].includes(result.data.starter)) {
        openRound(match, result.data.starter);
        ui.dirty = true;
      } else {
        match.phase = "start";
        match.message = result.msg || "开局失败，请重试";
        modeControls.hidden = false;
        match.revision = match.revision + 1;
      }
    }
    // 结果卡片只显示服务器确认的金额，非终局同步结果不写入结算区
    if (result.code === 0 && result.data.ended === true) {
      ui.settlement = "本局没有字块币奖励";
      if (result.data.reward > 0) {
        ui.settlement = "到账 +" + result.data.reward + " 字块币";
      }
      ui.dirty = true;
    }
    if (result.code !== 0) {
      if (message.sequence > 0) {
        ui.settlement = "字块币结算未确认，可在字块币流水中查看";
        ui.dirty = true;
      }
      rewardStatus.textContent = result.msg;
      rewardStatus.hidden = false;
      rewardHideAt = performance.now() + PLAY.achievementSeconds * 1000;
    }
  }
}

// 全部图像加载后再展示首帧，游戏启动没有 Java 或外部运行环境下载
async function initialize() {
  try {
    images = await loadImages();
    if (ui.stopped) {
      return;
    }
    canvas.addEventListener("pointermove", movePointer);
    canvas.addEventListener("pointerdown", pressPointer);
    canvas.addEventListener("pointerup", releasePointer);
    canvas.addEventListener("pointercancel", cancelPull);
    canvas.addEventListener("contextmenu", cancelContext);
    document.getElementById("restart").addEventListener("click", restart);
    document.getElementById("sound").addEventListener("click", toggleSound);
    document.getElementById("change-mode").addEventListener("click", chooseMode);
    chooseModeButton.addEventListener("click", chooseMode);
    start.addEventListener("click", restart);
    window.addEventListener("keydown", keyPressed);
    window.addEventListener("blur", visibilityChanged);
    document.addEventListener("visibilitychange", visibilityChanged);
    drawFrame(context, match, ui, images);
    hud = createHud(match, ui, images);
    hud.update();
    document.body.dataset.ready = "true";
    window.parent.postMessage({ type: "4398-native-ready" }, location.origin);
    frameId = requestAnimationFrame(animate);
  } catch (error) {
    const message = document.getElementById("load-error");
    message.hidden = false;
    message.textContent = "加载失败：" + error.message;
    window.parent.postMessage({ type: "4398-native-error", message: message.textContent }, location.origin);
  }
}

window.addEventListener("message", receiveCommand);
window.addEventListener("pagehide", stop, { once: true });
initialize();

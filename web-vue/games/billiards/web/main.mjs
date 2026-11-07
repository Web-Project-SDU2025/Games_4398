import { TABLE, PHYSICS, PLAY, DISPLAY, groupOf, groupName } from "./config.mjs";
import { createMatch, resetMatch, openRound, participantName, placeCue, takeShot, stepMatch } from "./rules.mjs";
import { canPlace } from "./physics.mjs";
import { aimDirection, shotPower, limitPullAnchor } from "./aim.mjs";
import { loadImages, createAudio } from "./assets.mjs";
import { drawFrame } from "./render.mjs";
import { AI, planAiPlacement } from "./ai.mjs";
import { createStroke, beginStroke, advanceStroke, cancelStroke } from "./stroke.mjs";

const canvas = document.getElementById("table");
const context = canvas.getContext("2d");
const match = createMatch();
const audio = createAudio();
const ui = { cursor: null, direction: { x: 1, y: 0 }, anchor: null, power: 0,
  lastPower: 0, placeValid: false, dirty: true, stopped: false, stroke: createStroke() };
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
let images = null;
let lastTime = 0;
let pending = 0;
let frameId = 0;
let lastDrawTime = -Infinity;
let lastRevision = -1;
let lastHud = "";
let traySignature = "";
const playerPanels = [document.getElementById("player-one"), document.getElementById("player-two")];
const groupLabels = [document.getElementById("group-one"), document.getElementById("group-two")];
const trays = [document.getElementById("tray-one"), document.getElementById("tray-two")];
const modal = document.getElementById("modal");
const start = document.getElementById("start");
const status = document.getElementById("status");
const verdict = document.getElementById("verdict");
const achievement = document.getElementById("achievement");
const reportedRounds = new Map();
let awaitingRound = "";
let aiWait = 0;
let aiPhase = "";
let aiWorker = null;
let aiRequest = 0;
let aiPlanning = false;
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
  aiWait = 0;
  aiPhase = "";
  stopAiPlanning();
  cancelStroke(ui.stroke);
  rewardStatus.hidden = true;
  cancelPull();
  ui.direction = { x: 1, y: 0 };
  ui.lastPower = 0;
  canvas.focus();
}

// 开场和终局共用主操作，点击后直接进入新局摆球阶段
function startMatch() {
  restart();
}

// 返回模式选择只在重新开盘时生效，不能修改服务器已确认的本盘模式
function chooseMode() {
  if (match.phase === "opening") {
    return;
  }
  cancelPull();
  cancelStroke(ui.stroke);
  match.mode = "local";
  stopAiPlanning();
  match.phase = "start";
  match.message = "选择模式后开始对局";
  modeControls.hidden = false;
  match.revision = match.revision + 1;
}

// 电脑每次摆球与出杆分开等待，连续进球后重新判断局面，不重复使用旧目标
function updateAi(seconds) {
  if (ui.stroke.active || aiPlanning) {
    return;
  }
  if (match.mode !== "single" || match.current !== 1
      || !["placement", "aiming"].includes(match.phase)) {
    aiWait = 0;
    aiPhase = "";
    return;
  }
  if (aiPhase !== match.phase) {
    aiPhase = match.phase;
    aiWait = 0;
    ui.cursor = null;
    ui.placeValid = false;
    cancelPull();
    match.message = "电脑思考中…";
    match.revision = match.revision + 1;
  }
  aiWait = aiWait + seconds;
  if (aiWait < AI.thinkSeconds) {
    return;
  }
  aiWait = 0;
  aiPhase = "";
  if (match.phase === "placement") {
    const position = planAiPlacement(match);
    if (position) {
      placeCue(match, position.x, position.y);
    }
    ui.cursor = null;
  } else {
    requestAiShot();
  }
  ui.dirty = true;
}

// 只复制球号、在台状态和位置，避免把 Matter 内部循环引用传给后台线程
function requestAiShot() {
  aiPlanning = true;
  aiRequest = aiRequest + 1;
  const balls = [];
  for (const ball of match.world.balls) {
    const position = ball.body.position;
    balls.push({ id: ball.id, active: ball.active,
      body: { position: { x: position.x, y: position.y } } });
  }
  try {
    if (!aiWorker) {
      aiWorker = new Worker(new URL("./ai-worker.mjs", import.meta.url), { type: "module" });
      aiWorker.addEventListener("message", receiveAiPlan);
      aiWorker.addEventListener("error", aiPlanningFailed);
    }
    aiWorker.postMessage({ request: aiRequest, balls: balls, groups: match.groups.slice(),
      current: match.current, breakShot: match.breakShot });
  } catch (error) {
    aiPlanningFailed();
  }
}

// 后台完成后才显示最终方向，旧盘或已取消请求不能触发出杆
function receiveAiPlan(event) {
  if (event.data.request !== aiRequest || !aiPlanning || ui.stopped
      || match.mode !== "single" || match.current !== 1 || match.phase !== "aiming") {
    return;
  }
  if (event.data.error) {
    aiPlanningFailed();
    return;
  }
  const plan = event.data.plan;
  aiPlanning = false;
  beginStroke(ui.stroke, plan.direction, plan.power, match.world.balls[0].body.position,
    true, reducedMotion.matches);
  ui.direction = ui.stroke.direction;
  ui.dirty = true;
}

// 后台失败时保留当前球局并显示原因，不能反复在画面线程执行重计算
function aiPlanningFailed() {
  match.message = "电脑选杆失败，请重新开始本盘";
  match.revision = match.revision + 1;
  ui.dirty = true;
}

// 重开、换模式和关闭页面终止旧计算，让返回结果不会串到下一盘
function stopAiPlanning() {
  aiRequest = aiRequest + 1;
  aiPlanning = false;
  if (aiWorker) {
    aiWorker.terminate();
    aiWorker = null;
  }
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

// 球组或落袋球变化时才重建托盘，运动帧不反复修改 DOM
function updateTrays() {
  let signature = match.groups.join(",");
  for (const ball of match.world.balls) {
    signature = signature + ":" + String(ball.active);
  }
  if (signature === traySignature) {
    return;
  }
  traySignature = signature;
  for (let player = 0; player < 2; player = player + 1) {
    trays[player].replaceChildren();
    let count = 0;
    for (const ball of match.world.balls) {
      if (!ball.active && match.groups[player] && groupOf(ball.id) === match.groups[player]) {
        const image = document.createElement("img");
        image.src = images["ball-" + ball.id].src;
        image.alt = ball.id + " 号球已落袋";
        trays[player].appendChild(image);
        count = count + 1;
      }
    }
    for (let slot = count; slot < 7; slot = slot + 1) {
      const empty = document.createElement("i");
      empty.setAttribute("aria-hidden", "true");
      trays[player].appendChild(empty);
    }
  }
}

// 只在阶段和文字变化时更新状态区，Canvas 负责球位，DOM 负责可读文字与按钮
function updateHud() {
  let power = ui.power;
  if (match.phase === "moving") {
    power = ui.lastPower;
  }
  const percent = Math.round(power * 100);
  const signature = match.mode + match.phase + match.current + match.message + match.notice + percent + match.winner;
  if (signature === lastHud) {
    return;
  }
  lastHud = signature;
  status.textContent = match.message;
  status.dataset.tone = match.tone;
  document.getElementById("power").value = power;
  document.getElementById("power-label").textContent = "力度 " + percent + "%";
  for (let player = 0; player < 2; player = player + 1) {
    playerPanels[player].classList.toggle("active", match.current === player
      && match.phase !== "start" && match.phase !== "over");
    groupLabels[player].textContent = groupName(match.groups[player]);
    let label = "玩家 1 · 我的账户";
    if (player === 1) {
      label = "玩家 2 · 本地对手";
      if (match.mode === "single") {
        label = "电脑";
      }
    } else if (match.mode === "single") {
      label = "你 · 我的账户";
    }
    playerPanels[player].querySelector("strong").textContent = label;
  }
  verdict.hidden = match.phase !== "resolving";
  verdict.textContent = match.message;
  verdict.dataset.tone = match.tone;
  achievement.hidden = !match.notice || match.phase === "over";
  achievement.textContent = match.notice;
  modal.hidden = !["start", "over", "opening"].includes(match.phase);
  document.getElementById("instructions").hidden = match.phase !== "start";
  document.getElementById("reason").hidden = match.phase !== "over";
  document.getElementById("reason").textContent = match.reason;
  document.querySelector(".prompt").dataset.outcome = String(match.phase === "over");
  let title = "美式八球";
  start.textContent = "点击开始";
  if (match.phase === "over") {
    title = participantName(match, match.winner) + " 获胜";
    start.textContent = "再来一局";
    const earned = [];
    for (const item of match.achievements[match.winner]) {
      earned.push(item);
    }
    document.getElementById("summary").textContent = earned.join(" / ");
    document.getElementById("summary").hidden = earned.length === 0;
  } else {
    document.getElementById("summary").hidden = true;
  }
  document.getElementById("prompt-title").textContent = title;
  if (match.phase === "opening") {
    document.getElementById("prompt-title").textContent = "正在确定开球方";
    start.textContent = "正在开局";
  }
  updateTrays();
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
  updateAi(elapsed);
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
    updateHud();
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
  stopAiPlanning();
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
  start.removeEventListener("click", startMatch);
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
    if (!result || message.roundId !== reportedRounds.get(match.roundNumber)) {
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
    // 到账由平台统一提示，子游戏只显示同步错误，避免同一结算弹出两条消息
    if (result.code !== 0) {
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
    start.addEventListener("click", startMatch);
    window.addEventListener("keydown", keyPressed);
    window.addEventListener("blur", visibilityChanged);
    document.addEventListener("visibilitychange", visibilityChanged);
    drawFrame(context, match, ui, images);
    updateHud();
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

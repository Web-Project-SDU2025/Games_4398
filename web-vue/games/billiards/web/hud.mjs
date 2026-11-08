import { groupOf, groupName } from "./config.mjs";
import { participantName } from "./rules.mjs";

// 托盘保留每组七个球位，未落袋位置显示空槽
const GROUP_BALL_COUNT = 7;

// 管理比赛文字、球组托盘和开场浮层，图像只在球组变化时用于生成落袋球图标
export function createHud(match, ui, images) {
  const panels = [document.getElementById("player-one"), document.getElementById("player-two")];
  const groups = [document.getElementById("group-one"), document.getElementById("group-two")];
  const trays = [document.getElementById("tray-one"), document.getElementById("tray-two")];
  const status = document.getElementById("status");
  const verdict = document.getElementById("verdict");
  const achievement = document.getElementById("achievement");
  const modal = document.getElementById("modal");
  const start = document.getElementById("start");
  const instructions = document.getElementById("instructions");
  const reason = document.getElementById("reason");
  const prompt = document.querySelector(".prompt");
  const title = document.getElementById("prompt-title");
  const summary = document.getElementById("summary");
  const power = document.getElementById("power");
  const powerLabel = document.getElementById("power-label");
  let previousHud = "";
  let previousTray = "";

  // 仅在球组或在台状态改变时重建托盘，运动帧不重复修改 DOM
  function updateTrays() {
    let signature = match.groups.join(",");
    for (const ball of match.world.balls) {
      signature = signature + ":" + String(ball.active);
    }
    if (signature === previousTray) {
      return;
    }
    previousTray = signature;
    for (let player = 0; player < panels.length; player = player + 1) {
      const tray = trays[player];
      tray.replaceChildren();
      let count = 0;
      for (const ball of match.world.balls) {
        if (!ball.active && match.groups[player] && groupOf(ball.id) === match.groups[player]) {
          const image = document.createElement("img");
          image.src = images["ball-" + ball.id].src;
          image.alt = ball.id + " 号球已落袋";
          tray.appendChild(image);
          count = count + 1;
        }
      }
      for (let slot = count; slot < GROUP_BALL_COUNT; slot = slot + 1) {
        const empty = document.createElement("i");
        empty.setAttribute("aria-hidden", "true");
        tray.appendChild(empty);
      }
    }
  }

  // 模式和当前方共同决定玩家名称，编号空格在此统一维护
  function updatePlayers() {
    for (let player = 0; player < panels.length; player = player + 1) {
      const panel = panels[player];
      const active = match.current === player && match.phase !== "start" && match.phase !== "over";
      panel.classList.toggle("active", active);
      groups[player].textContent = groupName(match.groups[player]);
      let label = "玩家 1 · 我的账户";
      if (player === 1) {
        label = "玩家 2 · 本地对手";
        if (match.mode === "single") {
          label = "电脑";
        }
      } else if (match.mode === "single") {
        label = "你 · 我的账户";
      }
      panel.querySelector("strong").textContent = label;
    }
  }

  // 开场、开局等待和终局共用浮层，终局成就从实际获胜方读取
  function updatePrompt() {
    modal.hidden = !["start", "over", "opening"].includes(match.phase);
    instructions.hidden = match.phase !== "start";
    reason.hidden = match.phase !== "over";
    reason.textContent = match.reason;
    prompt.dataset.outcome = String(match.phase === "over");
    title.textContent = "美式八球";
    start.textContent = "点击开始";
    summary.hidden = true;
    if (match.phase === "over") {
      title.textContent = participantName(match, match.winner) + " 获胜";
      start.textContent = "再来一局";
      const earned = match.achievements[match.winner];
      summary.textContent = earned.join(" / ");
      summary.hidden = earned.length === 0;
    } else if (match.phase === "opening") {
      title.textContent = "正在确定开球方";
      start.textContent = "正在开局";
    }
  }

  // 状态或力度变化时同步文字，Canvas 继续独立处理球台和球杆
  function update() {
    let displayedPower = ui.power;
    if (match.phase === "moving") {
      displayedPower = ui.lastPower;
    }
    const percent = Math.round(displayedPower * 100);
    const signature = JSON.stringify([
      match.mode, match.phase, match.current, match.message, match.notice,
      match.tone, percent, match.winner, match.reason, match.groups,
    ]);
    if (signature === previousHud) {
      return;
    }
    previousHud = signature;
    status.textContent = match.message;
    status.dataset.tone = match.tone;
    power.value = displayedPower;
    powerLabel.textContent = "力度 " + percent + "%";
    verdict.hidden = match.phase !== "resolving";
    verdict.textContent = match.message;
    verdict.dataset.tone = match.tone;
    achievement.hidden = !match.notice || match.phase === "over";
    achievement.textContent = match.notice;
    updatePlayers();
    updatePrompt();
    updateTrays();
  }

  return { update: update };
}

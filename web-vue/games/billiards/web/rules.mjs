import { PHYSICS, PLAY, groupOf, groupName, playerName } from "./config.mjs";
import { createWorld, resetWorld, createRecord, placeBall, removeBall,
  setVelocity, stepWorld, atRest, canPlace, respot } from "./physics.mjs";

// 建立一局双人比赛，界面只通过这些状态读取轮次与判罚
export function createMatch() {
  const match = { world: createWorld(), phase: "start", current: 0, groups: ["", ""],
    breakShot: true, restricted: true, message: "点击开始对局", tone: "neutral",
    winner: -1, reason: "", shot: null, shotSeconds: 0, verdictSeconds: 0,
    pending: "aiming", achievements: [new Set(), new Set()], notice: "", noticeSeconds: 0,
    runPlayer: -1, runShots: 0, revision: 0, roundNumber: 0, shotNumber: 0, reports: [], mode: "local" };
  return match;
}

// 新局清空成就与球组，进入开球摆放，商城持有关系不受影响
export function resetMatch(match, mode) {
  resetWorld(match.world);
  match.current = 0;
  match.groups = ["", ""];
  match.breakShot = true;
  match.restricted = true;
  match.phase = "placement";
  match.message = "拖动主球到开球线后，松开落位";
  match.tone = "neutral";
  match.winner = -1;
  match.reason = "";
  match.shot = null;
  match.achievements = [new Set(), new Set()];
  match.notice = "";
  match.noticeSeconds = 0;
  match.runPlayer = -1;
  match.runShots = 0;
  match.roundNumber = match.roundNumber + 1;
  match.shotNumber = 0;
  if (mode) {
    match.mode = mode;
  }
  match.reports.push({ round: match.roundNumber, sequence: 0, matchMode: match.mode });
  match.revision = match.revision + 1;
}

// 用服务器给出的先手完成开局，两种模式共用同一规则流程
export function openRound(match, starter) {
  if (starter !== 0 && starter !== 1) {
    throw new Error("开球方无效");
  }
  match.current = starter;
  match.phase = "placement";
  match.message = participantName(match, starter) + " 先手开球 · 主球须置于开球线后";
  match.revision = match.revision + 1;
}

// 单人模式使用电脑对手名称，玩家编号仍供双人模式和服务端判定使用
export function participantName(match, player) {
  if (match.mode === "single") {
    if (player === 1) {
      return "电脑";
    }
    return "你";
  }
  return playerName(player);
}

// 在合法落点摆放主球，失败时保留摆球阶段，允许玩家继续拖动
export function placeCue(match, horizontal, vertical) {
  if (match.phase !== "placement"
      || !canPlace(match.world, horizontal, vertical, match.restricted, 0)) {
    return false;
  }
  placeBall(match.world, match.world.balls[0], horizontal, vertical);
  match.phase = "aiming";
  match.message = aimingMessage(match);
  match.revision = match.revision + 1;
  return true;
}

// 冻结出杆前球组和在台球号，判罚不能读取已经被本杆改变的初始条件
export function takeShot(match, direction, power) {
  if (match.phase !== "aiming" || power <= 0 || !match.world.balls[0].active) {
    return false;
  }
  const length = Math.hypot(direction.x, direction.y);
  if (length === 0) {
    return false;
  }
  const before = [];
  for (const ball of match.world.balls) {
    if (ball.active && ball.id !== 0) {
      before.push(ball.id);
    }
  }
  match.shot = { player: match.current, group: match.groups[match.current],
    breakShot: match.breakShot, before: before };
  match.world.record = createRecord();
  const speed = PHYSICS.minSpeed + Math.min(1, power) * (PHYSICS.maxSpeed - PHYSICS.minSpeed);
  setVelocity(match.world.balls[0], direction.x / length * speed, direction.y / length * speed);
  match.phase = "moving";
  match.shotSeconds = 0;
  match.notice = "";
  match.noticeSeconds = 0;
  match.message = "球在滚动…";
  match.revision = match.revision + 1;
  return true;
}

// 只在球运动时运行物理，其余阶段仅推进必要的提示计时
export function stepMatch(match, seconds) {
  if (match.noticeSeconds > 0) {
    match.noticeSeconds = Math.max(0, match.noticeSeconds - seconds);
    if (match.noticeSeconds === 0) {
      match.notice = "";
      match.revision = match.revision + 1;
    }
  }
  if (match.phase === "moving") {
    stepWorld(match.world, seconds);
    match.shotSeconds = match.shotSeconds + seconds;
    match.revision = match.revision + 1;
    if (atRest(match.world) || match.shotSeconds >= PHYSICS.timeout) {
      for (const ball of match.world.balls) {
        setVelocity(ball, 0, 0);
      }
      resolveShot(match);
    }
  } else if (match.phase === "resolving") {
    match.verdictSeconds = match.verdictSeconds - seconds;
    if (match.verdictSeconds <= 0) {
      match.phase = match.pending;
      if (match.phase === "aiming") {
        match.message = aimingMessage(match);
      } else if (match.phase === "placement") {
        match.message = "自由球：拖动主球到合法位置，松开落位";
        if (match.restricted) {
          match.message = "自由球：主球须置于开球线后";
        }
      }
      match.revision = match.revision + 1;
    }
  }
}

// 未定组是开放球台，开球进球只保留击球权，后续普通合法进球才确定球组
export function aimingMessage(match) {
  const player = participantName(match, match.current);
  if (!match.groups[match.current]) {
    if (match.breakShot) {
      return player + " 开球 · 开球不定组";
    }
    return player + " 击球 · 开放球台，下一次合法进球定组";
  }
  return player + " 击球 · 目标 " + groupName(match.groups[match.current]);
}

// 根据已冻结局面和实际事件判罚，先判断犯规，再确定球组、续杆和胜负
export function judgeShot(shot, record) {
  const fouls = [];
  const pots = [];
  for (const id of record.pots) {
    if (id !== 0 && !pots.includes(id)) {
      pots.push(id);
    }
  }
  if (record.first < 0) {
    fouls.push("未击中任何球");
  }
  let remainingBefore = 0;
  let remainingAfter = 0;
  for (const id of shot.before) {
    if (shot.group && groupOf(id) === shot.group) {
      remainingBefore = remainingBefore + 1;
      if (!pots.includes(id)) {
        remainingAfter = remainingAfter + 1;
      }
    }
  }
  if (!shot.breakShot && record.first >= 0) {
    let legalFirst = record.first !== 8;
    if (shot.group) {
      legalFirst = groupOf(record.first) === shot.group;
      if (remainingBefore === 0) {
        legalFirst = record.first === 8;
      }
    }
    if (!legalFirst) {
      fouls.push("先碰到的不是本方目标球");
    }
  }
  if (record.pots.includes(0)) {
    fouls.push("主球落袋");
  }
  if (!shot.breakShot && record.first >= 0 && !record.railAfter && record.pots.length === 0) {
    fouls.push("碰球后无球碰库");
  }
  if (record.off.length > 0) {
    fouls.push("球飞出台面");
  }
  let objectRails = 0;
  for (const id of record.rails) {
    if (id !== 0) {
      objectRails = objectRails + 1;
    }
  }
  let illegalBreak = false;
  if (shot.breakShot && pots.length === 0 && objectRails < PLAY.breakRails) {
    illegalBreak = true;
    fouls.push("开球未达标");
  }
  let assigned = "";
  if (!shot.group && !shot.breakShot && fouls.length === 0) {
    for (const id of pots) {
      if (groupOf(id)) {
        assigned = groupOf(id);
        break;
      }
    }
  }
  let ownGroup = shot.group;
  if (!ownGroup) {
    ownGroup = assigned;
  }
  let ownPots = 0;
  for (const id of pots) {
    if (id !== 8 && (shot.breakShot || groupOf(id) === ownGroup)) {
      ownPots = ownPots + 1;
    }
  }
  let winner = -1;
  let reason = "";
  if (pots.includes(8) && !shot.breakShot) {
    if (!shot.group || remainingAfter > 0) {
      winner = 1 - shot.player;
      reason = "8 号球提前落袋（本方还有球在台上）";
      fouls.push("8 号球提前落袋");
    } else if (fouls.length > 0) {
      winner = 1 - shot.player;
      reason = "8 号球与犯规同杆落袋：" + fouls[0];
    } else {
      winner = shot.player;
      reason = "本方球已清，8 号球合法落袋";
    }
  }
  const continues = fouls.length === 0 && (ownPots > 0 || (shot.breakShot && pots.includes(8)));
  let next = 1 - shot.player;
  if (continues) {
    next = shot.player;
  }
  return { fouls: fouls, assigned: assigned, ownGroup: ownGroup, ownPots: ownPots,
    continues: continues, next: next, winner: winner, reason: reason,
    restricted: illegalBreak || shot.breakShot };
}

// 将判决应用到对局，八号开球落袋和异常飞球按原规则重新置位
function resolveShot(match) {
  const shot = match.shot;
  const record = match.world.record;
  const verdict = judgeShot(shot, record);
  if (shot.breakShot && record.pots.includes(8)) {
    respot(match.world, 8);
  }
  for (const id of record.off) {
    if (id !== 0) {
      respot(match.world, id);
    }
  }
  if (verdict.assigned) {
    match.groups[shot.player] = verdict.assigned;
    match.groups[1 - shot.player] = "solid";
    if (verdict.assigned === "solid") {
      match.groups[1 - shot.player] = "stripe";
    }
  }
  awardAchievements(match, shot, record, verdict);
  match.breakShot = false;
  match.current = verdict.next;
  match.tone = "legal";
  match.pending = "aiming";
  if (verdict.winner >= 0) {
    match.winner = verdict.winner;
    match.reason = verdict.reason;
    match.message = participantName(match, verdict.winner) + " 获胜 · " + verdict.reason;
    match.tone = "win";
    match.pending = "over";
  } else if (verdict.fouls.length > 0) {
    match.restricted = verdict.restricted;
    removeBall(match.world, match.world.balls[0]);
    match.pending = "placement";
    match.tone = "foul";
    match.message = "犯规：" + verdict.fouls.join("、") + " · " + participantName(match, match.current) + " 获自由球";
    if (match.restricted) {
      match.message = match.message + "，须置于开球线后";
    }
  } else if (verdict.assigned) {
    match.message = participantName(match, shot.player) + " 打进 " + verdict.ownPots + " 球，本组为 " + groupName(verdict.assigned);
  } else if (verdict.continues) {
    match.message = participantName(match, shot.player) + " 继续击球";
    if (shot.breakShot) {
      match.message = participantName(match, shot.player) + " 开球进球，继续击球 · 暂不定组";
    }
  } else {
    match.message = "换手： " + participantName(match, match.current) + " 击球";
  }
  match.phase = "resolving";
  match.verdictSeconds = PLAY.verdictSeconds;
  match.revision = match.revision + 1;
  match.shotNumber = match.shotNumber + 1;
  const rails = [];
  const banks = [];
  for (const id of record.rails) {
    rails.push(id);
  }
  for (const id of record.bankPots) {
    banks.push(id);
  }
  match.reports.push({ round: match.roundNumber, sequence: match.shotNumber,
    shot: { player: shot.player, first: record.first, pots: record.pots.slice(), rails: rails,
      bankPots: banks, off: record.off.slice(), railAfter: record.railAfter,
      cueRailBefore: record.cueRailBefore } });
  return verdict;
}

// 每人每项每局解锁一次，犯规和换手结束连击，技巧球依实际碰库先后判断
export function awardAchievements(match, shot, record, verdict) {
  if (verdict.fouls.length > 0 || verdict.ownPots === 0) {
    match.runPlayer = -1;
    match.runShots = 0;
    return;
  }
  if (match.runPlayer !== shot.player) {
    match.runShots = 0;
  }
  match.runPlayer = shot.player;
  match.runShots = match.runShots + 1;
  const earned = match.achievements[shot.player];
  const candidates = [];
  if (shot.breakShot) {
    candidates.push("开门红");
  } else {
    if (verdict.ownPots >= PLAY.triplePots) {
      candidates.push("一杆三收");
    } else if (verdict.ownPots === PLAY.doublePots) {
      candidates.push("一杆双收");
    }
    for (const id of record.bankPots) {
      if (groupOf(id) === verdict.ownGroup) {
        candidates.push("翻袋好球");
        break;
      }
    }
    if (record.cueRailBefore) {
      candidates.push("勾球进袋");
    }
  }
  if (match.runShots >= PLAY.runShots) {
    candidates.push("连下三城");
  }
  const unlocked = [];
  for (const title of candidates) {
    if (!earned.has(title)) {
      earned.add(title);
      unlocked.push(title);
    }
  }
  if (unlocked.length > 0) {
    match.notice = participantName(match, shot.player) + " · " + unlocked.join(" · ");
    match.noticeSeconds = PLAY.achievementSeconds;
  }
  if (!verdict.continues) {
    match.runPlayer = -1;
    match.runShots = 0;
  }
}

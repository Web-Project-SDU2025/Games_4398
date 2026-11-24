// 每个账号的牌局战报使用独立存储前缀
const REPORT_KEY = "4398.joker-table.report.v1:";
// 牌局编号只接受存档生成的短文本
const RUN_ID = /^[a-z0-9-]{1,80}$/;

// 战报和牌局一样按平台账号保存
export function jokerBattleReportPrefix(owner) {
  if (typeof owner !== "number" && typeof owner !== "string") {
    return "";
  }
  const id = String(owner);
  if (!/^[1-9][0-9]*$/.test(id) || !Number.isSafeInteger(Number(id))) {
    return "";
  }
  return REPORT_KEY + id + ":";
}

// 只有打完的牌局才有战报，八注全破才算通关
function isBattleResult(result) {
  return Boolean(
    result &&
    typeof result.runId === "string" && RUN_ID.test(result.runId) &&
    Number.isInteger(result.ante) && result.ante >= 1 && result.ante <= 8 &&
    Number.isSafeInteger(result.score) && result.score >= 0 &&
    (result.outcome === "lost" || (result.outcome === "won" && result.ante === 8)),
  );
}

// 读取单局战报，跳过损坏或与存储键不符的记录
function readStoredReport(storage, key, prefix) {
  try {
    const result = JSON.parse(storage.getItem(key));
    if (
      isBattleResult(result) && key === prefix + result.runId &&
      Number.isSafeInteger(result.playedAt) && result.playedAt > 0 &&
      typeof result.newPersonalBest === "boolean"
    ) {
      return result;
    }
  } catch {
    return null;
  }
  return null;
}

// 逐局汇总最近战报和最高分，分别结束的牌局使用独立存储键
function summarizeReports(storage, prefix) {
  let latest = null;
  let bestScore = 0;
  for (let index = 0; index < storage.length; index = index + 1) {
    const key = storage.key(index);
    if (!key || !key.startsWith(prefix)) {
      continue;
    }
    const result = readStoredReport(storage, key, prefix);
    if (!result) {
      continue;
    }
    bestScore = Math.max(bestScore, result.score);
    if (
      !latest || result.playedAt > latest.playedAt ||
      (result.playedAt === latest.playedAt && result.runId > latest.runId)
    ) {
      latest = result;
    }
  }
  if (!latest) {
    return null;
  }
  return { latest: latest, bestScore: bestScore };
}

// 从本机存储读取当前账号的战报汇总
export function readJokerBattleReport(owner, storage) {
  const prefix = jokerBattleReportPrefix(owner);
  if (!prefix) {
    return null;
  }
  try {
    return summarizeReports(storage || window.localStorage, prefix);
  } catch {
    return null;
  }
}

// 每局保存一次并保留结算时的新纪录标记
export function saveJokerBattleReport(owner, result, storage) {
  const prefix = jokerBattleReportPrefix(owner);
  if (!prefix || !isBattleResult(result)) {
    return null;
  }
  try {
    const browserStorage = storage || window.localStorage;
    const key = prefix + result.runId;
    const existing = readStoredReport(browserStorage, key, prefix);
    if (existing) {
      return existing;
    }
    const previous = summarizeReports(browserStorage, prefix);
    let playedAt = Date.now();
    if (previous) {
      playedAt = Math.max(playedAt, previous.latest.playedAt + 1);
    }
    const report = {
      runId: result.runId,
      ante: result.ante,
      score: result.score,
      outcome: result.outcome,
      playedAt: playedAt,
      newPersonalBest: !previous || result.score > previous.bestScore,
    };
    browserStorage.setItem(key, JSON.stringify(report));
    return report;
  } catch {
    return null;
  }
}

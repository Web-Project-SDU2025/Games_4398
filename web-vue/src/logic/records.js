import { formatRecordTime } from "./dashboard.js";
import { getErrorMessage } from "./account.js";

// 游戏结果在个人记录、管理员记录和首页中使用相同文字
const OUTCOME_NAMES = {
  passed: "通过",
  failed: "未通过",
  abandoned: "未通过",
  closed: "未通过",
  playing: "正在游玩",
  manual: "手动记录",
  revoked: "已撤回",
};

// 记录列表只显示当前账户选择保留的条目，首页统计继续使用原始记录
export function visibleRecords(records, includeHidden) {
  const result = [];
  for (const record of records) {
    if (includeHidden === true || record.hidden !== true) {
      result.push(record);
    }
  }
  return result;
}

// 旧版本后端返回地址错误时提供重启提示，其余错误保留服务器的实际原因
export function recordActionError(error) {
  if (error.response) {
    const status = error.response.status;
    if (status === 404 || status === 405) {
      return "请在 IDEA 中重启后端，启用记录隐藏和恢复功能";
    }
    if (status === 401 || status === 403) {
      return "当前登录已失效，请重新登录";
    }
  }
  return getErrorMessage(error, "记录显示设置保存失败，请稍后重试");
}

// 根据服务器结果返回简短状态，旧记录沿用手动记录标记
export function outcomeName(record) {
  return OUTCOME_NAMES[record.outcome] || OUTCOME_NAMES.manual;
}

// 拼接账户、时间和关卡结果，时间统一使用北京时间，手动记录保留原展示语义
export function recordDescription(record) {
  const player = record.playerName || record.username;
  const time = formatRecordTime(record.playedAt);
  if (!record.automatic) {
    return player + "于" + time + "游玩" + record.name;
  }
  let level = record.levelName || record.name;
  if (record.mode === "practice") {
    level = level + "（试玩）";
  } else if (record.mode === "custom") {
    level = level + "（自定义）";
  }
  return player + "于" + time + outcomeName(record) + level;
}

export const ACTIVITY_DAYS = 7;
export const RECENT_RECORD_LIMIT = 5;
export const PREVIEW_GAME_LIMIT = 3;
const DAY_MILLISECONDS = 24 * 60 * 60 * 1000;
const SECONDS_PER_HOUR = 3600;

// 将日期转换为北京时间的年月日，返回适合比较的日期文字
export function dateKey(date) {
  return date.toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" });
}

// 将后端的游玩时间转换为日期，没有时区的时间按北京时间处理
export function recordDate(value) {
  if (!value) {
    return new Date(NaN);
  }
  let text = String(value);
  text = text.replace(" ", "T");
  if (!/Z$|[+-]\d{2}:\d{2}$/.test(text)) {
    text = text + "+08:00";
  }
  return new Date(text);
}

// 创建最近几天的空统计，now 是当前时间，返回按日期排列的数组
export function createActivity(now) {
  const activity = [];
  for (let index = ACTIVITY_DAYS - 1; index >= 0; index = index - 1) {
    const date = new Date(now.getTime() - index * DAY_MILLISECONDS);
    const key = dateKey(date);
    activity.push({ date: key, label: key.substring(5), minutes: 0 });
  }
  return activity;
}

// 比较两条记录的日期，让最新记录排在前面
function compareRecords(first, second) {
  const firstDate = recordDate(first.playedAt);
  const secondDate = recordDate(second.playedAt);
  let firstTime = firstDate.getTime();
  let secondTime = secondDate.getTime();
  if (Number.isNaN(firstTime)) {
    firstTime = 0;
  }
  if (Number.isNaN(secondTime)) {
    secondTime = 0;
  }
  return secondTime - firstTime;
}

// 根据游戏列表、可见记录和当前用户编号整理个人概览，now 用于确定最近七天
export function summarizeDashboard(games, records, userId, now) {
  const library = [];
  let favorites = 0;
  for (const game of games) {
    if (game.owned) {
      library.push(game);
    }
    if (game.favorite) {
      favorites = favorites + 1;
    }
  }
  const ownRecords = [];
  const activity = createActivity(now);
  let totalSeconds = 0;
  for (const record of records) {
    // 管理员的接口会返回所有用户记录，首页只汇总当前账户
    if (Number(record.userId) !== Number(userId)) {
      continue;
    }
    ownRecords.push(record);
    const seconds = Math.max(0, Number(record.durationSeconds) || 0);
    totalSeconds = totalSeconds + seconds;
    const date = recordDate(record.playedAt);
    if (Number.isNaN(date.getTime())) {
      continue;
    }
    const key = dateKey(date);
    for (const day of activity) {
      if (day.date === key) {
        day.minutes = day.minutes + seconds / 60;
      }
    }
  }
  ownRecords.sort(compareRecords);
  const totalHours = totalSeconds / SECONDS_PER_HOUR;
  return {
    catalogCount: games.length,
    libraryCount: library.length,
    favoriteCount: favorites,
    recordCount: ownRecords.length,
    hours: totalHours.toFixed(1),
    recentRecords: ownRecords.slice(0, RECENT_RECORD_LIMIT),
    libraryPreview: library.slice(0, PREVIEW_GAME_LIMIT),
    activity: activity,
  };
}

// 根据已收录数量计算百分比，空目录返回零，避免除以零
export function collectionPercent(count, total) {
  if (total === 0) {
    return 0;
  }
  return Math.round((count / total) * 100);
}

// 将记录时间显示为北京时间，无法识别时显示提示
export function formatRecordTime(value) {
  const date = recordDate(value);
  if (Number.isNaN(date.getTime())) {
    return "时间未知";
  }
  return date.toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

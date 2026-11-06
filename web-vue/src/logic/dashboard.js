// 活动图显示的天数，按北京时间确定日期
export const ACTIVITY_DAYS = 7;
// 活动图允许的统计粒度，单位为小时
export const ACTIVITY_INTERVALS = [
  { hours: 1, label: "每小时" },
  { hours: 4, label: "每 4 小时" },
  { hours: 24, label: "每天" },
];
// 首次打开首页时按天汇总
export const DEFAULT_ACTIVITY_HOURS = 24;
// 首页最近记录最多显示的条数
export const RECENT_RECORD_LIMIT = 5;
// 首页游戏库预览最多显示的游戏数
export const PREVIEW_GAME_LIMIT = 3;
// 时长的小数位数，首页文字和图表使用同一精度
export const DURATION_DECIMALS = 1;
// 记录与统计共用北京时间
const RECORD_TIME_ZONE = "Asia/Shanghai";
const RECORD_TIME_OFFSET = "+08:00";
// 小时转换使用的秒数
const SECONDS_PER_HOUR = 3600;
// 自然日长度与默认统计粒度分别维护
const HOURS_PER_DAY = 24;
// 时间段计算统一使用毫秒
const HOUR_MILLISECONDS = SECONDS_PER_HOUR * 1000;
const DAY_MILLISECONDS = HOURS_PER_DAY * HOUR_MILLISECONDS;

// 将日期转换为北京时间的年月日，返回适合比较的日期文字
export function dateKey(date) {
  return date.toLocaleDateString("sv-SE", { timeZone: RECORD_TIME_ZONE });
}

// 将后端的游玩时间转换为日期，没有时区的时间按北京时间处理
export function recordDate(value) {
  if (!value) {
    return new Date(NaN);
  }
  let text = String(value);
  text = text.replace(" ", "T");
  if (!/Z$|[+-]\d{2}:\d{2}$/.test(text)) {
    text = text + RECORD_TIME_OFFSET;
  }
  return new Date(text);
}

// 返回允许的统计粒度，旧设置或异常值统一使用默认粒度
function activityHours(value) {
  for (const interval of ACTIVITY_INTERVALS) {
    if (interval.hours === value) {
      return value;
    }
  }
  return DEFAULT_ACTIVITY_HOURS;
}

// 创建最近七个自然日的时间段，小时与四小时统计都从北京时间零点对齐
export function createActivity(now, intervalHours) {
  const hours = activityHours(intervalHours);
  const today = dateKey(now);
  const midnight = recordDate(today + "T00:00:00");
  const firstTime = midnight.getTime() - (ACTIVITY_DAYS - 1) * DAY_MILLISECONDS;
  const intervalMilliseconds = hours * HOUR_MILLISECONDS;
  const count =
    Math.floor((now.getTime() - firstTime) / intervalMilliseconds) + 1;
  const activity = [];
  for (let index = 0; index < count; index = index + 1) {
    const startTime = firstTime + index * intervalMilliseconds;
    const date = new Date(startTime);
    const key = dateKey(date);
    let label = key.substring(5);
    if (hours < HOURS_PER_DAY) {
      const time = date.toLocaleTimeString("en-GB", {
        timeZone: RECORD_TIME_ZONE,
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });
      label = label + " " + time;
    }
    activity.push({ startTime: startTime, label: label, minutes: 0 });
  }
  return activity;
}

// 按记录的游玩时间汇总已筛选的个人记录，空时段保留零值，未来和无效时间直接跳过
export function aggregateActivity(records, now, intervalHours) {
  const hours = activityHours(intervalHours);
  const activity = createActivity(now, hours);
  const firstTime = activity[0].startTime;
  for (const record of records) {
    const time = recordDate(record.playedAt).getTime();
    if (!Number.isFinite(time) || time > now.getTime()) {
      continue;
    }
    const index = Math.floor((time - firstTime) / (hours * HOUR_MILLISECONDS));
    if (index < 0 || index >= activity.length) {
      continue;
    }
    const seconds = Number(record.durationSeconds);
    if (Number.isFinite(seconds) && seconds > 0) {
      activity[index].minutes = activity[index].minutes + seconds / 60;
    }
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

// 整理个人概览并保留该账户记录，统计粒度切换共用本次刷新时间
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
  let totalSeconds = 0;
  for (const record of records) {
    // 管理员的接口会返回所有用户记录，首页只汇总当前账户
    if (Number(record.userId) !== Number(userId)) {
      continue;
    }
    ownRecords.push(record);
    const seconds = Number(record.durationSeconds);
    if (Number.isFinite(seconds) && seconds > 0) {
      totalSeconds = totalSeconds + seconds;
    }
  }
  ownRecords.sort(compareRecords);
  const totalHours = totalSeconds / SECONDS_PER_HOUR;
  return {
    catalogCount: games.length,
    libraryCount: library.length,
    favoriteCount: favorites,
    recordCount: ownRecords.length,
    hours: totalHours.toFixed(DURATION_DECIMALS),
    libraryPreview: library.slice(0, PREVIEW_GAME_LIMIT),
    records: ownRecords,
    generatedAt: now.getTime(),
  };
}

// 将记录时间显示为北京时间，无法识别时显示提示
export function formatRecordTime(value) {
  const date = recordDate(value);
  if (Number.isNaN(date.getTime())) {
    return "时间未知";
  }
  return date.toLocaleString("zh-CN", {
    timeZone: RECORD_TIME_ZONE,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

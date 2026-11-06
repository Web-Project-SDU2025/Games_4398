import test from "node:test";
import assert from "node:assert/strict";
import * as dashboard from "../src/logic/dashboard.js";
import { visibleRecords } from "../src/logic/records.js";

// 管理员返回的全量记录也必须按用户筛选，最近七天按北京时间划分
test("概览只统计当前账户并正确处理北京时间跨日", function testPersonalSummary() {
  const games = [
    { gameId: 1, owned: true, favorite: true },
    { gameId: 2, owned: true, favorite: false },
    { gameId: 3, owned: false, favorite: false },
  ];
  const records = [
    {
      recordId: 1,
      userId: 2,
      durationSeconds: 3600,
      playedAt: "2026-11-02T16:30:00Z",
    },
    {
      recordId: 2,
      userId: 2,
      durationSeconds: 600,
      playedAt: "2026-11-02T23:30:00",
    },
    {
      recordId: 3,
      userId: 99,
      durationSeconds: 9000,
      playedAt: "2026-11-03T10:00:00",
    },
    {
      recordId: 4,
      userId: 2,
      durationSeconds: 1200,
      playedAt: "2026-10-01T10:00:00",
    },
  ];
  const now = new Date("2026-11-03T12:00:00+08:00");
  const summary = dashboard.summarizeDashboard(games, records, 2, now);
  assert.equal(summary.catalogCount, 3);
  assert.equal(summary.libraryCount, 2);
  assert.equal(summary.favoriteCount, 1);
  assert.equal(summary.recordCount, 3);
  assert.equal(summary.hours, "1.5");
  assert.equal(summary.records[0].recordId, 1);
  assert.equal(summary.records.length, 3);
  const activity = dashboard.aggregateActivity(summary.records, now, 24);
  assert.equal(activity.length, dashboard.ACTIVITY_DAYS);
  assert.equal(activity[6].label, "11-03");
  assert.equal(activity[6].minutes, 60);
  assert.equal(activity[5].minutes, 10);
});

// 空列表返回零值，无法解析的时间返回明确文字
test("空概览和无效日期有明确结果", function testEmptySummary() {
  const summary = dashboard.summarizeDashboard(
    [],
    [],
    2,
    new Date("2026-11-03T12:00:00+08:00"),
  );
  assert.equal(summary.catalogCount, 0);
  assert.equal(summary.recordCount, 0);
  assert.equal(summary.hours, "0.0");
  assert.equal(dashboard.formatRecordTime("invalid"), "时间未知");
  assert.equal(dashboard.formatRecordTime(null), "时间未知");
});

// 小时、四小时和自然日以同一北京时间边界汇总，切换粒度保持总时长
test("活动统计按小时、四小时与每天对齐", function testActivityIntervals() {
  const records = [
    { durationSeconds: 600, playedAt: "2026-11-05T23:59:59+08:00" },
    { durationSeconds: 1200, playedAt: "2026-11-05T16:00:00Z" },
    { durationSeconds: 1800, playedAt: "2026-11-06T03:59:59" },
    { durationSeconds: 2400, playedAt: "2026-11-06 04:00:00" },
    { durationSeconds: 60, playedAt: "2026-11-06T11:00:00+08:00" },
    { durationSeconds: 9000, playedAt: "2026-10-30T23:59:59+08:00" },
    { durationSeconds: 9000, playedAt: "2026-11-06T12:31:00+08:00" },
    { durationSeconds: 9000, playedAt: "invalid" },
    { durationSeconds: -60, playedAt: "2026-11-06T11:00:00+08:00" },
    { durationSeconds: Infinity, playedAt: "2026-11-06T11:00:00+08:00" },
  ];
  const now = new Date("2026-11-06T12:30:00+08:00");
  const original = JSON.stringify(records);
  for (const interval of dashboard.ACTIVITY_INTERVALS) {
    const activity = dashboard.aggregateActivity(records, now, interval.hours);
    let totalMinutes = 0;
    for (const period of activity) {
      totalMinutes = totalMinutes + period.minutes;
      assert.ok(period.startTime <= now.getTime());
    }
    assert.equal(totalMinutes, 101);
    assert.equal(
      activity[0].startTime,
      new Date("2026-10-31T00:00:00+08:00").getTime(),
    );
  }
  const hourly = dashboard.aggregateActivity(records, now, 1);
  assert.equal(hourly.length, 157);
  assert.equal(hourly[144].label, "11-06 00:00");
  assert.equal(hourly[144].minutes, 20);
  assert.equal(hourly[147].minutes, 30);
  assert.equal(hourly[148].minutes, 40);
  const fourHourly = dashboard.aggregateActivity(records, now, 4);
  assert.equal(fourHourly.length, 40);
  assert.equal(fourHourly[36].minutes, 50);
  assert.equal(fourHourly[37].label, "11-06 04:00");
  assert.equal(fourHourly[37].minutes, 40);
  const daily = dashboard.aggregateActivity(records, now, 24);
  assert.equal(daily[6].minutes, 91);
  assert.equal(daily[5].minutes, 10);
  assert.equal(JSON.stringify(records), original);
});

// 空列表保留连续零值时段，未知粒度使用默认的按天统计
test("活动统计支持空数据和异常粒度", function testEmptyActivity() {
  const now = new Date("2026-11-06T00:00:00+08:00");
  const activity = dashboard.aggregateActivity([], now, 3);
  assert.equal(activity.length, dashboard.ACTIVITY_DAYS);
  for (const period of activity) {
    assert.equal(period.minutes, 0);
  }
  const hourly = dashboard.createActivity(now, 1);
  assert.equal(hourly[hourly.length - 1].label, "11-06 00:00");
});

// 隐藏只筛选记录列表，首页仍按全部原记录计算次数、时长和活动
test("隐藏记录保留概览统计", function testHiddenRecords() {
  const records = [
    {
      recordId: 1,
      userId: 2,
      hidden: true,
      durationSeconds: 3600,
      playedAt: "2026-11-06T10:00:00",
    },
    {
      recordId: 2,
      userId: 2,
      hidden: false,
      durationSeconds: 1800,
      playedAt: "2026-11-06T11:00:00",
    },
  ];
  const visible = visibleRecords(records);
  assert.equal(visible.length, 1);
  assert.equal(visible[0].recordId, 2);
  const now = new Date("2026-11-06T12:00:00+08:00");
  const summary = dashboard.summarizeDashboard([], records, 2, now);
  assert.equal(summary.recordCount, 2);
  assert.equal(summary.hours, "1.5");
  assert.equal(visibleRecords(summary.records).length, 1);
  const activity = dashboard.aggregateActivity(summary.records, now, 24);
  assert.equal(activity[6].minutes, 90);
  assert.equal(records.length, 2);
});

import test from "node:test";
import assert from "node:assert/strict";
import * as dashboard from "../src/logic/dashboard.js";

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
  assert.equal(summary.recentRecords[0].recordId, 1);
  assert.equal(summary.activity.length, dashboard.ACTIVITY_DAYS);
  assert.equal(summary.activity[6].date, "2026-11-03");
  assert.equal(summary.activity[6].minutes, 60);
  assert.equal(summary.activity[5].minutes, 10);
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
  assert.equal(dashboard.collectionPercent(0, 0), 0);
  assert.equal(dashboard.collectionPercent(2, 3), 67);
  assert.equal(dashboard.formatRecordTime("invalid"), "时间未知");
  assert.equal(dashboard.formatRecordTime(null), "时间未知");
});

import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";
import {
  jokerBattleReportPrefix,
  readJokerBattleReport,
  saveJokerBattleReport,
} from "../games/joker-table/web/battle-report.mjs";

// 使用与浏览器 Storage 相同的读取和枚举方式
function createStorage() {
  const entries = new Map();
  return {
    length: 0,
    // 枚举已保存的键
    key: function key(index) {
      return Array.from(entries.keys())[index] || null;
    },
    // 读取已保存的文本
    getItem: function getItem(key) {
      return entries.get(key) || null;
    },
    // 写入后同步更新键数量
    setItem: function setItem(key, value) {
      entries.set(key, value);
      this.length = entries.size;
    },
  };
}

// 个人纪录取所有已结束牌局中的最高单手，同分时保留原纪录
test("小丑牌战报分别保留最近一局和个人最高分", function testLatestAndBest() {
  const storage = createStorage();
  const first = saveJokerBattleReport("42", {
    runId: "run-one", ante: 2, score: 520, outcome: "lost",
  }, storage);
  assert.equal(first.newPersonalBest, true);
  const win = saveJokerBattleReport(42, {
    runId: "run-two", ante: 8, score: 158000, outcome: "won",
  }, storage);
  assert.equal(win.newPersonalBest, true);
  const tied = saveJokerBattleReport(42, {
    runId: "run-three", ante: 8, score: 158000, outcome: "lost",
  }, storage);
  assert.equal(tied.newPersonalBest, false);
  const latest = saveJokerBattleReport(42, {
    runId: "run-four", ante: 1, score: 32, outcome: "lost",
  }, storage);
  assert.equal(latest.newPersonalBest, false);
  assert.deepEqual(readJokerBattleReport(42, storage), {
    latest: latest, bestScore: 158000,
  });
});

// 旧牌局重复结算时沿用原战报和纪录标记
test("恢复同一局不会覆盖更新的战报", function testRepeatedReport() {
  const storage = createStorage();
  const original = { runId: "run-first", ante: 3, score: 1000, outcome: "lost" };
  const first = saveJokerBattleReport(17, original, storage);
  const recent = saveJokerBattleReport(17, {
    runId: "run-second", ante: 1, score: 35, outcome: "lost",
  }, storage);
  assert.deepEqual(saveJokerBattleReport(17, original, storage), first);
  assert.equal(storage.length, 2);
  assert.deepEqual(readJokerBattleReport(17, storage), {
    latest: recent, bestScore: 1000,
  });
});

// 跳过损坏数据与其他账号的数据，拒绝保存未结束的牌局
test("小丑牌战报按账号读取并跳过损坏的存储项", function testOwnerAndValidation() {
  const storage = createStorage();
  const result = { runId: "run-shared", ante: 1, score: 40, outcome: "lost" };
  assert.equal(saveJokerBattleReport(0, result, storage), null);
  assert.equal(saveJokerBattleReport("017", result, storage), null);
  assert.equal(saveJokerBattleReport(17, {
    runId: "run-pending", ante: 1, score: 40, outcome: "closed",
  }, storage), null);
  storage.setItem(jokerBattleReportPrefix(17) + "broken", "{");
  assert.equal(readJokerBattleReport(17, storage), null);
  assert.equal(saveJokerBattleReport(17, result, storage).newPersonalBest, true);
  assert.equal(readJokerBattleReport(18, storage), null);
  assert.equal(saveJokerBattleReport(18, result, storage).newPersonalBest, true);
  assert.equal(readJokerBattleReport(17, storage).bestScore, 40);
});

// 恢复旧存档，实际打完一局后立刻记录，重新打开时沿用战报
test("旧牌局结算一次，恢复结算和新开局不重复写战报", function testRunSettlement() {
  const context = vm.createContext({});
  for (const filename of ["engine.js", "run-store.js"]) {
    const source = fs.readFileSync(
      new URL("../games/joker-table/web/" + filename, import.meta.url),
      "utf8",
    );
    vm.runInContext(source, context);
  }
  const engine = context.JokerEngine;
  const runStore = context.JokerRunStore;
  const storage = createStorage();
  storage.setItem("midnight-draw.run.v1", JSON.stringify({ seed: 9, history: [] }));

  // 模拟原生播放器绑定当前平台账号
  function recordBattle(result) {
    return saveJokerBattleReport(42, result, storage);
  }

  const run = runStore.open(engine, storage, recordBattle);
  assert.equal(readJokerBattleReport(42, storage), null);
  for (let hand = 0; hand < 4; hand = hand + 1) {
    assert.ok(run.perform("play", [run.game.state.hand[0].id]));
  }
  assert.equal(run.game.state.phase, "lost");
  const settled = readJokerBattleReport(42, storage);
  assert.equal(settled.latest.ante, 1);
  assert.equal(settled.latest.outcome, "lost");
  assert.equal(settled.latest.score, run.game.state.bestHand.total);
  assert.equal(run.hasNewPersonalBest(), true);

  const restored = runStore.open(engine, storage, recordBattle);
  assert.equal(restored.game.state.phase, "lost");
  assert.equal(restored.hasNewPersonalBest(), true);
  const fresh = runStore.reset(engine, storage, recordBattle);
  assert.equal(fresh.hasNewPersonalBest(), false);
  assert.deepEqual(readJokerBattleReport(42, storage), settled);
});

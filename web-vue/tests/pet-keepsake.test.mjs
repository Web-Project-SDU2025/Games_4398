import assert from "node:assert/strict";
import test from "node:test";
import { advancePetStars, readPetKeepsake, savePetKeepsake } from "../src/logic/petKeepsake.js";

// 先连山边的星，再从低处连向右上方，误点之后可以从头重来
test("连星按顺序推进，重复点亮不影响进度", function testStarTrail() {
  assert.equal(advancePetStars(0, "star-high"), 0);
  assert.equal(advancePetStars(0, "star-ridge"), 1);
  assert.equal(advancePetStars(1, "star-ridge"), 1);
  assert.equal(advancePetStars(1, "star-high"), 0);
  assert.equal(advancePetStars(1, "star-lake"), 2);
  assert.equal(advancePetStars(2, "star-high"), 3);
  assert.equal(advancePetStars(3, "star-ridge"), 3);
});

// 同一个浏览器的不同账户各有自己的明信片，重新发现不覆盖首次记录
test("明信片保留首次发现的日期和昵称", function testFirstKeepsake() {
  const values = new Map();

  // 用内存模拟浏览器本地存储
  function getItem(key) {
    return values.get(key) || null;
  }

  // 按本地存储的行为写入 JSON 字符串
  function setItem(key, value) {
    values.set(key, value);
  }

  const storage = { getItem: getItem, setItem: setItem };
  const firstDate = new Date("2026-11-20T21:00:00Z");
  const nextDate = new Date("2026-11-21T21:00:00Z");
  const card = savePetKeepsake(storage, 12, "小橙", firstDate);
  assert.deepEqual(readPetKeepsake(storage, 12), card);
  assert.equal(readPetKeepsake(storage, 13), null);
  assert.deepEqual(savePetKeepsake(storage, 12, "橙子", nextDate), card);
  assert.equal(card.nickname, "小橙");
  assert.equal(card.date, firstDate.toISOString());
});

import assert from "node:assert/strict";
import test from "node:test";
import { createPvzMenu } from "../games/runtime/web/pvz-menu.mjs";

test("PVZ 先显示网页加载层，直到 Java 首屏就绪才切换", async function () {
  const frames = [];
  const errors = [];
  const adventure = {
    dataset: { pvzEntry: "adventure" },
    addEventListener(type, listener) {
      this.click = listener;
    },
  };
  const elements = {
    "pvz-menu-entries": { hidden: true, querySelectorAll: () => [adventure] },
    "pvz-menu-loading": { hidden: true },
    "pvz-loading-title": {},
    "pvz-loading-detail": {},
  };
  const menu = createPvzMenu(
    { getElementById: (id) => elements[id] },
    (frame) => frames.push(frame),
    (error) => errors.push(error),
  );
  function paintFrame(timestamp) {
    for (const frame of frames.splice(0)) {
      frame(timestamp);
    }
  }
  menu.setAvailable(true);
  const opening = adventure.click({ currentTarget: adventure });
  assert.equal(elements["pvz-menu-loading"].hidden, false);
  assert.equal(menu.takeRequest(), 0);
  paintFrame(16);
  assert.equal(menu.takeRequest(), 0);
  paintFrame(32);
  await opening;
  assert.equal(menu.takeRequest(), 1);
  assert.equal(menu.takeRequest(), 0);
  menu.setAvailable(false);

  const halfway = menu.report(50);
  paintFrame(48);
  assert.match(elements["pvz-loading-detail"].textContent, /^1%/);
  paintFrame(64);
  await halfway;
  paintFrame(8048);
  assert.match(elements["pvz-loading-detail"].textContent, /^50%/);
  const preparing = menu.report(99);
  paintFrame(9048);
  assert.match(elements["pvz-loading-detail"].textContent, /^59% · 正在准备选关界面/);
  paintFrame(9064);
  await preparing;
  paintFrame(14948);
  assert.match(elements["pvz-loading-detail"].textContent, /^98%/);
  paintFrame(15048);
  assert.match(elements["pvz-loading-detail"].textContent, /^99%/);
  assert.equal(elements["pvz-menu-loading"].hidden, false);

  const ready = menu.report(100);
  assert.equal(elements["pvz-menu-loading"].hidden, false);
  paintFrame(15064);
  paintFrame(15080);
  await ready;
  assert.equal(elements["pvz-menu-loading"].hidden, true);
  assert.equal(elements["pvz-menu-entries"].hidden, true);
  menu.setAvailable(true);
  const reopening = adventure.click({ currentTarget: adventure });
  paintFrame(15100);
  paintFrame(15116);
  await reopening;
  const shortLoad = menu.report(99);
  paintFrame(15132);
  paintFrame(15148);
  await shortLoad;
  assert.match(elements["pvz-loading-detail"].textContent, /^1%/);
  const alreadyReady = menu.report(100);
  paintFrame(15164);
  paintFrame(15180);
  await alreadyReady;
  assert.equal(elements["pvz-menu-loading"].hidden, true);
  assert.deepEqual(errors, []);
});

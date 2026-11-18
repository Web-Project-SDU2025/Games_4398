import assert from "node:assert/strict";
import test from "node:test";
import { createPvzMenu } from "../games/runtime/web/pvz-menu.mjs";

// 使用可控的绘制帧，验证加载层和 Java 菜单请求的先后顺序
test("PVZ 先显示网页加载层，直到 Java 首屏就绪才切换", async function testPvzMenuLoading() {
  const frames = [];
  const errors = [];
  const adventure = {
    dataset: { pvzEntry: "adventure" },
    // 保存菜单注册的点击函数，测试可以直接模拟点击
    addEventListener: function addEventListener(type, listener) {
      this.click = listener;
    },
  };
  const almanac = {
    dataset: { pvzEntry: "almanac" },
    addEventListener: adventure.addEventListener,
  };
  const elements = {
    "pvz-menu-entries": {
      hidden: true,
      // 提供测试用的冒险模式和图鉴按钮
      querySelectorAll: function querySelectorAll() {
        return [adventure, almanac];
      },
    },
    "pvz-menu-loading": { hidden: true },
    "pvz-loading-title": {},
    "pvz-loading-detail": {},
  };
  // 按编号查找测试节点，代替浏览器文档
  function getElementById(id) {
    return elements[id];
  }
  // 将回调留到下一次模拟绘制
  function queueFrame(frame) {
    frames.push(frame);
  }
  // 收集错误，核对正常加载期间不会进入失败分支
  function recordError(error) {
    errors.push(error);
  }
  const document = { getElementById: getElementById };
  const menu = createPvzMenu(document, queueFrame, recordError);

  // 运行当前帧的回调，回调新排的任务留到下一帧
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

  // 图鉴应使用独立的加载文案和请求编号
  const openAlmanac = almanac.click({ currentTarget: almanac });
  assert.equal(elements["pvz-loading-title"].textContent, "正在加载图鉴");
  paintFrame(15200);
  paintFrame(15216);
  await openAlmanac;
  assert.equal(menu.takeRequest(), 2);
  const almanacProgress = menu.report(99);
  paintFrame(15232);
  paintFrame(15248);
  await almanacProgress;
  assert.match(elements["pvz-loading-detail"].textContent, /正在准备图鉴界面$/);
});

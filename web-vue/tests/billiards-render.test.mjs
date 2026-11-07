import test from "node:test";
import assert from "node:assert/strict";
import { drawFrame } from "../games/billiards/web/render.mjs";

// 记录真实绘制入口调用的图像和落点圆，不需要启动浏览器或额外加载素材
class DrawingContext {
  // 保存本帧画出的图像和圆数量，用于区分实体白球与玩家落点提示
  constructor() {
    this.images = [];
    this.circles = 0;
  }
  // 记录实体球或球台绘制，不检查与本次修复无关的像素坐标
  drawImage(image) {
    this.images.push(image);
  }
  // 落点图标使用圆形外框，电脑回合不得调用此方法
  arc() {
    this.circles = this.circles + 1;
  }
  // 清屏不影响本测试记录
  clearRect() {}
  // 保存画笔状态不影响本测试记录
  save() {}
  // 恢复画笔状态不影响本测试记录
  restore() {}
  // 开球线虚线不属于落点圆
  setLineDash() {}
  // 新建绘制路径不改变本测试记录
  beginPath() {}
  // 线段起点不属于落点圆
  moveTo() {}
  // 线段终点不属于落点圆
  lineTo() {}
  // 绘制边线不改变已记录圆数量
  stroke() {}
  // 填充由正式绘制函数安排，本测试只检查落点是否出现
  fill() {}
}

// 保留旧光标分别验证电脑自由球与玩家摆球，防止通过清空光标掩盖绘制判断错误
test("电脑自由球不显示玩家落点图标且不隐藏实体白球", function testComputerPlacement() {
  const images = { table: {}, shadow: { width: 24, height: 24 }, "ball-0": { width: 24, height: 24 } };
  for (const valid of [true, false]) {
    const match = { mode: "single", current: 1, phase: "placement", restricted: false,
      world: { balls: [{ id: 0, active: true, body: { position: { x: 300, y: 280 } } }] } };
    const ui = { cursor: { x: 400, y: 280 }, placeValid: valid, stroke: { active: false } };
    const computer = new DrawingContext();
    drawFrame(computer, match, ui, images);
    assert.equal(computer.circles, 0);
    assert.ok(computer.images.includes(images["ball-0"]));
    for (const mode of ["single", "local"]) {
      match.mode = mode;
      match.current = 0;
      if (mode === "local") {
        match.current = 1;
      }
      const human = new DrawingContext();
      drawFrame(human, match, ui, images);
      assert.equal(human.circles, 1);
      assert.equal(human.images.includes(images["ball-0"]), false);
    }
  }
});

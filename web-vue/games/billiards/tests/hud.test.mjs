import test from "node:test";
import assert from "node:assert/strict";
import { createHud } from "../web/hud.mjs";
import { createMatch } from "../web/rules.mjs";

// 提供状态栏所需的最小节点，验证终局渲染时不依赖真实浏览器
class StatusNode {
  // 保存文字、显示状态与子节点，球员名称沿用同一测试节点
  constructor() {
    this.textContent = "";
    this.hidden = false;
    this.dataset = {};
    this.children = [];
    this.classList = { toggle: toggleClass };
  }

  // 测试只检查比赛文字，不模拟球员名称的实际标签结构
  querySelector() {
    return this;
  }

  // 每次托盘重建时移除上一组落袋球
  replaceChildren() {
    this.children = [];
  }

  // 收集托盘中的图像或空槽
  appendChild(child) {
    this.children.push(child);
  }

  // 记录可访问属性，测试不依赖具体浏览器实现
  setAttribute(name, value) {
    this[name] = value;
  }
}

// 当前用例只验证终局摘要，球员高亮不影响断言
function toggleClass() {
  return;
}

// 成就保存为 Set，摘要必须先转成有序文字列表，不能直接调用数组方法
test("终局状态栏正确显示获胜方和成就集合", function testVictorySummary() {
  const previousDocument = globalThis.document;
  const nodes = new Map();

  // 同一个编号始终返回同一节点，方便核对真实状态栏写入
  function getElementById(id) {
    if (!nodes.has(id)) {
      nodes.set(id, new StatusNode());
    }
    return nodes.get(id);
  }

  // 浮层容器也使用相同的节点记录方式
  function querySelector(selector) {
    return getElementById(selector);
  }

  // 托盘空槽无需加载图片，保留节点属性即可
  function createElement() {
    return new StatusNode();
  }

  globalThis.document = {
    getElementById: getElementById,
    querySelector: querySelector,
    createElement: createElement,
  };
  try {
    const match = createMatch();
    match.mode = "single";
    match.phase = "over";
    match.winner = 0;
    match.reason = "合法打进 8 号球";
    match.achievements[0].add("一杆双收");
    match.achievements[0].add("翻袋好球");
    const hud = createHud(match, { power: 0, lastPower: 0 }, {});
    hud.update();
    assert.equal(nodes.get("prompt-title").textContent, "你 获胜");
    assert.equal(nodes.get("summary").textContent, "一杆双收 / 翻袋好球");
    assert.equal(nodes.get("summary").hidden, false);
    assert.equal(nodes.get("modal").hidden, false);
    match.phase = "start";
    hud.update();
    assert.equal(nodes.get("summary").hidden, true);
  } finally {
    if (previousDocument === undefined) {
      delete globalThis.document;
    } else {
      globalThis.document = previousDocument;
    }
  }
});

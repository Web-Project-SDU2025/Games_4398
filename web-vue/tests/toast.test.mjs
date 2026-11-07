import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// 加载真实提示逻辑，组件和时间使用替身，检查消息来源与清理行为
function fixture() {
  const messages = [];
  let clock = 1000;
  let closed = 0;
  // 保存显示选项并返回可关闭的消息句柄
  function ElMessage(options) {
    messages.push(options);
    // 记录关闭次数，核对来源更新和卸载时都释放旧提示
    function close() {
      closed = closed + 1;
    }
    return { close: close };
  }
  // 提供可手动推进的时间，重复消息测试不需要真实等待
  function now() {
    return clock;
  }
  // 模拟浏览器顶层容器，提示挂载后不受原生对话框尺寸限制
  function createElement() {
    // 测试只关注挂载目标，图形行为交给浏览器检查
    function noop() {
      return;
    }
    return { setAttribute: noop, showPopover: noop, remove: noop };
  }
  // 记录器不需要真实 DOM，只需接受新容器
  function appendChild() {
    return;
  }
  const context = { ElMessage: ElMessage, Date: { now: now },
    document: { body: { appendChild: appendChild }, createElement: createElement } };
  let source = fs.readFileSync(new URL("../src/tools/toast.js", import.meta.url), "utf8");
  source = source.replace(/import[^;]+;/g, "");
  source = source.replaceAll("export ", "");
  source = source + "\nglobalThis.directive = toastDirective;";
  vm.createContext(context);
  vm.runInContext(source, context);
  // 返回下一时刻，允许相同文案作为新操作再次显示
  function advance() {
    clock = clock + 1000;
  }
  // 读取句柄关闭次数
  function closeCount() {
    return closed;
  }
  return { context: context, messages: messages, advance: advance, closeCount: closeCount };
}

// 空消息不弹出，结果类型和自动关闭时间来自统一入口
test("toast 统一结果类型、时长与重复消息", function testToastOptions() {
  const loaded = fixture();
  const show = loaded.context.showToast;
  assert.equal(show(""), null);
  show("游戏已加入游戏库");
  assert.equal(loaded.messages[0].type, "success");
  assert.equal(loaded.messages[0].duration, 4000);
  assert.equal(loaded.messages[0].showClose, true);
  assert.equal(show("游戏已加入游戏库"), null);
  loaded.advance();
  show("游戏已加入游戏库");
  assert.equal(loaded.messages.length, 2);
  show("字块币不足");
  assert.equal(loaded.messages[2].type, "error");
});

// 原生对话框中的消息使用独立顶层，来源改变或移除时清理提示
test("toast 支持原生对话框与来源生命周期", function testToastSource() {
  const loaded = fixture();
  // 模态框接纳顶层提示节点，保证提示不会被浏览器标为不可交互
  function appendChild() {
    return;
  }
  const dialog = { appendChild: appendChild };
  // 模拟来源位于已打开的原生对话框中
  function closest() {
    return dialog;
  }
  // 属性仅记录隐藏标记，来源本身不再展示结果文字
  function setAttribute() {
    return;
  }
  const element = { closest: closest, setAttribute: setAttribute };
  loaded.context.directive.mounted(element, { value: "保存失败" });
  assert.equal(element.hidden, true);
  assert.equal(loaded.messages[0].appendTo.className, "toast-overlay");
  loaded.context.directive.updated(element, { value: "保存失败", oldValue: "保存失败" });
  assert.equal(loaded.messages.length, 1);
  loaded.advance();
  loaded.context.directive.updated(element, { value: "保存成功", oldValue: "保存失败" });
  assert.equal(loaded.closeCount(), 1);
  loaded.context.directive.beforeUnmount(element);
  assert.equal(loaded.closeCount(), 2);
});

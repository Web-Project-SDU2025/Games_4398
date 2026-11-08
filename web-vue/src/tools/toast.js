import { ElMessage } from "element-plus";

// 提示在四秒后自动关闭，用户也可以点击关闭按钮或悬停阅读
const TOAST_DURATION = 4000;
// 提示与窗口顶部留出距离，避免紧贴浏览器边缘
const TOAST_OFFSET = 24;
// 同一轮渲染中的重复消息只创建一次，单位为毫秒
const DUPLICATE_INTERVAL = 250;
let previousMessage = "";
let previousTime = 0;
const activeToasts = new WeakMap();

// 根据结果文案选择图标和颜色，调用方可以明确指定提示类型
function toastType(message) {
  if (/失败|无法|错误|不足|无效|不能为空|不一致|超出|请先|请填写|请检查|尚未|未配置|不正确|不可用|不能|最多|过期|失效/.test(message)) {
    return "error";
  }
  if (/成功|已加入|已保存|已修改|已删除|已复制|已发送|已发布|已更新|已恢复|已撤回/.test(message)) {
    return "success";
  }
  return "info";
}

// 显示纯文本结果提示，对话框中的消息使用独立顶层，避免被表单边界裁剪
export function showToast(message, type, source) {
  if (typeof message !== "string" || message.trim() === "") {
    return null;
  }
  const now = Date.now();
  if (message === previousMessage && now - previousTime < DUPLICATE_INTERVAL) {
    return null;
  }
  previousMessage = message;
  previousTime = now;
  let target = document.body;
  let overlay = null;
  if (source) {
    const dialog = source.closest("dialog[open]");
    if (dialog) {
      overlay = document.createElement("div");
      overlay.className = "toast-overlay";
      overlay.setAttribute("popover", "manual");
      dialog.appendChild(overlay);
      overlay.showPopover();
      target = overlay;
    }
  }
  let tone = type;
  if (!tone) {
    tone = toastType(message);
  }
  // 消息结束后回收顶层容器，关闭表单不会留下不可见遮罩
  function closeOverlay() {
    if (overlay) {
      overlay.remove();
    }
  }
  return ElMessage({
    message: message,
    type: tone,
    duration: TOAST_DURATION,
    offset: TOAST_OFFSET,
    showClose: true,
    grouping: false,
    customClass: "platform-toast",
    appendTo: target,
    onClose: closeOverlay,
  });
}

// 保留消息来源绑定，提示脱离页面布局，避免表格和表单被消息推移
function mountToast(element, binding) {
  element.hidden = true;
  element.setAttribute("data-toast-source", "");
  sendBoundToast(element, binding);
}

// 只在消息内容变化时发送，时钟和输入框刷新不会重复弹出
function updateToast(element, binding) {
  if (binding.value !== binding.oldValue) {
    sendBoundToast(element, binding);
  }
}

// 更新前关闭同一来源的旧提示，避免加载失败消息和成功消息同时堆叠
function sendBoundToast(element, binding) {
  removeToast(element);
  const toast = showToast(binding.value, binding.arg, element);
  if (toast) {
    activeToasts.set(element, toast);
  }
}

// 对话框或页面卸载时关闭其提示，防止提示留在已经隐藏的对话框里
function removeToast(element) {
  const toast = activeToasts.get(element);
  if (toast) {
    toast.close();
    activeToasts.delete(element);
  }
}

export const toastDirective = {
  mounted: mountToast,
  updated: updateToast,
  beforeUnmount: removeToast,
};

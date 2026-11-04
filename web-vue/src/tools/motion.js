const activeReveals = new WeakMap();
// 刷新反馈从此透明度恢复到完全显示
const REVEAL_START_OPACITY = 0.65;

// 数据更新后淡入，重复刷新先取消旧动画，表格保持原位置
export function revealContent(element) {
  const previous = activeReveals.get(element);
  if (previous) {
    previous.cancel();
    activeReveals.delete(element);
  }
  if (!element.animate || !element.isConnected) {
    return;
  }
  const styles = getComputedStyle(document.documentElement);
  let duration = parseFloat(styles.getPropertyValue("--dur-short"));
  const easingValue = styles.getPropertyValue("--ease-out");
  const easing = easingValue.trim();
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    duration = parseFloat(styles.getPropertyValue("--dur-reduced"));
  }
  const frames = [{ opacity: REVEAL_START_OPACITY }, { opacity: 1 }];
  const animation = element.animate(frames, {
    duration: duration,
    easing: easing,
  });
  activeReveals.set(element, animation);
}

// 绑定数据改变时播放反馈，输入和时钟等更新复用当前内容
function updateReveal(element, binding) {
  // 首次数据填充和路由切换期间使用页面自身过渡
  if (
    element.closest(
      ".page-enter-active, .page-leave-active, .panel-enter-active, .panel-leave-active",
    )
  ) {
    return;
  }
  // 请求期间由半透明样式显示等待反馈，完成后再执行淡入
  if (element.getAttribute("aria-busy") === "true") {
    return;
  }
  if (binding.value !== binding.oldValue) {
    revealContent(element);
  }
}

// 元素移除时取消当前动画，缓存切换和快速切页共用此清理流程
function removeReveal(element) {
  const animation = activeReveals.get(element);
  if (animation) {
    animation.cancel();
    activeReveals.delete(element);
  }
}

// 指令只负责视觉反馈，业务代码继续控制数据和等待状态
export const revealDirective = {
  updated: updateReveal,
  beforeUnmount: removeReveal,
};

// 新消息出现后显示底部，减少动态效果时立即滚动，不播放位移动画
export function scrollToBottom(element) {
  if (!element) {
    return;
  }
  let behavior = "smooth";
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    behavior = "auto";
  }
  element.scrollTo({ top: element.scrollHeight, behavior: behavior });
}

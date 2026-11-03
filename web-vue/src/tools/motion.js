const activeReveals = new WeakMap();

// 数据更新后轻微淡入，不移动表格；重复刷新会取消旧动画，避免叠加
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
  const easing = styles.getPropertyValue("--ease-out").trim();
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    duration = parseFloat(styles.getPropertyValue("--dur-reduced"));
  }
  const frames = [{ opacity: 0.65 }, { opacity: 1 }];
  const animation = element.animate(frames, {
    duration: duration,
    easing: easing,
  });
  activeReveals.set(element, animation);
}

// 只在绑定的数据对象发生变化时播放，不对输入、时钟等每次更新播放动画
function updateReveal(element, binding) {
  // 首次数据填充和路由切换共用页面过渡，不再叠加第二次淡入
  if (
    element.closest(
      ".page-enter-active, .page-leave-active, .panel-enter-active, .panel-leave-active"
    )
  ) {
    return;
  }
  // 请求尚未结束时由样式显示等待反馈，避免淡入覆盖正在等待的半透明状态
  if (element.getAttribute("aria-busy") === "true") {
    return;
  }
  if (binding.value !== binding.oldValue) {
    revealContent(element);
  }
}

// 元素移除时取消未完成动画，避免缓存页面或快速切页残留动效
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

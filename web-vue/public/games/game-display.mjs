// 社区署名留白集中维护，署名保持在可见画面下方
export const DISPLAY_OPTIONS = {
  creditHeight: 38,
};

// 返回所有可见窗口的外框，多个编辑器和弹窗一起放入画面，加载时使用完整桌面
export function windowBounds(windows, width, height) {
  if (windows.length === 0) {
    return { left: 0, top: 0, width: width, height: height };
  }
  let left = windows[0].left;
  let top = windows[0].top;
  let right = left + windows[0].width;
  let bottom = top + windows[0].height;
  for (const window of windows) {
    left = Math.min(left, window.left);
    top = Math.min(top, window.top);
    right = Math.max(right, window.left + window.width);
    bottom = Math.max(bottom, window.top + window.height);
  }
  return { left: left, top: top, width: right - left, height: bottom - top };
}

// 观察 Java 窗口并居中可见区域，保持原始尺寸，小屏通过滚动查看完整画面
export function centerGameDisplay(width, height) {
  const host = document.getElementById("java-display");
  const desktop = document.getElementById("cheerpjDisplay");
  let scheduled = 0;

  // 读取窗口原始布局尺寸，避开画布重绘，让游戏更新保持原有频率
  function updateDisplay() {
    scheduled = 0;
    const windows = [];
    for (const window of desktop.querySelectorAll(":scope > .cjWindow")) {
      if (window.style.display !== "none") {
        windows.push({
          left: window.offsetLeft,
          top: window.offsetTop,
          width: window.offsetWidth,
          height: window.offsetHeight,
        });
      }
    }
    const bounds = windowBounds(windows, width, height);
    const credit = DISPLAY_OPTIONS.creditHeight;
    host.style.width = bounds.width + "px";
    host.style.height = bounds.height + credit + "px";
    desktop.style.left = -bounds.left + "px";
    desktop.style.top = -bounds.top + "px";
    desktop.style.setProperty("--credit-left", bounds.left + "px");
    desktop.style.setProperty(
      "--credit-top",
      bounds.top + bounds.height + "px",
    );
    desktop.style.setProperty("--credit-width", bounds.width + "px");
  }

  // 同一帧的窗口移动与尺寸变化合并处理，避免反复计算布局
  function scheduleDisplay() {
    if (scheduled === 0) {
      scheduled = requestAnimationFrame(updateDisplay);
    }
  }

  // 只跟踪窗口布局，素材画布和编辑器文本的频繁变化交给原运行环境
  function observeWindows(mutations) {
    for (const mutation of mutations) {
      if (
        (mutation.type === "childList" && mutation.target === desktop) ||
        mutation.target.parentElement === desktop
      ) {
        scheduleDisplay();
        return;
      }
    }
  }

  const observer = new MutationObserver(observeWindows);
  observer.observe(desktop, {
    childList: true,
    attributes: true,
    subtree: true,
    attributeFilter: ["style"],
  });
  updateDisplay();

  // 页面关闭时结束布局观察和待执行任务，重新进入游戏创建新实例
  function stopDisplay() {
    observer.disconnect();
    cancelAnimationFrame(scheduled);
  }
  window.addEventListener("pagehide", stopDisplay, { once: true });
}

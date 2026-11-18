// 百分比从 1 到 99 的显示节奏，实际进度始终是上限
const PROGRESS_DURATION_MS = 15000;
// 菜单请求编号与 Java 端读取的编号一致
const ADVENTURE_REQUEST = 1;
const ALMANAC_REQUEST = 2;

// 让上一帧有机会显示，之后再进入 Java，或撤下已经完成的加载层
export function afterMenuPaint(nextFrame) {
  // 等待两次刷新回调，让浏览器在中间绘制一次加载层
  function waitForPaint(resolve) {
    // 第一次回调只预约下一帧，避免立即进入 Java 加载
    nextFrame(function firstFrame() {
      nextFrame(resolve);
    });
  }
  return new Promise(waitForPaint);
}

// 由网页接管菜单入口和加载层，Java 只处理资源并发布进度
export function createPvzMenu(document, nextFrame, onError) {
  const entries = document.getElementById("pvz-menu-entries");
  const loading = document.getElementById("pvz-menu-loading");
  const title = document.getElementById("pvz-loading-title");
  const detail = document.getElementById("pvz-loading-detail");
  let available = false;
  let active = false;
  let isAlmanac = false;
  let request = 0;
  let actualProgress = 0;
  let progressStartedAt = null;
  let progressFramePending = false;

  // 仅在 Java 菜单可用且没有正在加载的页面时显示入口
  function updateEntries() {
    entries.hidden = !available || active;
  }

  // 加载结束时清除请求和加载层，按 Java 菜单状态恢复入口
  function finish() {
    active = false;
    request = 0;
    loading.hidden = true;
    updateEntries();
  }

  // 已有等待绘制的帧时，不重复排队
  function scheduleProgressFrame() {
    if (!progressFramePending) {
      progressFramePending = true;
      nextFrame(drawProgress);
    }
  }

  // 根据经过时间增加显示进度，但不超过 Java 已报告的进度
  function drawProgress(timestamp) {
    progressFramePending = false;
    if (!active || actualProgress === 0) {
      return;
    }
    if (progressStartedAt === null) {
      progressStartedAt = timestamp;
    }
    const elapsed = timestamp - progressStartedAt;
    const pacedProgress = 1 + Math.floor(elapsed * 98 / PROGRESS_DURATION_MS);
    const displayed = Math.min(actualProgress, pacedProgress);
    let stage = "正在读取关卡封面";
    if (isAlmanac) {
      stage = "正在准备图鉴条目";
      if (actualProgress === 99) {
        stage = "正在准备图鉴界面";
      }
    } else if (actualProgress === 99) {
      stage = "正在准备选关界面";
    }
    const message = displayed + "% · " + stage;
    if (detail.textContent !== message) {
      detail.textContent = message;
    }
    if (displayed < actualProgress) {
      scheduleProgressFrame();
    }
  }

  // 点击菜单时先显示对应加载层，浏览器画出后再通知 Java
  async function openEntry(event) {
    if (active || !available) {
      return;
    }
    isAlmanac = event.currentTarget.dataset.pvzEntry === "almanac";
    active = true;
    actualProgress = 0;
    progressStartedAt = null;
    updateEntries();
    if (isAlmanac) {
      title.textContent = "正在加载图鉴";
      detail.textContent = "正在准备图鉴条目…";
    } else {
      title.textContent = "正在加载冒险模式";
      detail.textContent = "正在读取关卡列表…";
    }
    loading.hidden = false;
    await afterMenuPaint(nextFrame);
    if (active) {
      if (isAlmanac) {
        request = ALMANAC_REQUEST;
      } else {
        request = ADVENTURE_REQUEST;
      }
    }
  }

  // 接收 Java 的进度、失败或完成状态，完成时等首屏画出再收起加载层
  async function report(progress) {
    if (!active) {
      return;
    }
    if (progress < 0) {
      finish();
      onError(new Error("页面加载失败，请重试"));
      return;
    }
    if (progress === 100) {
      await afterMenuPaint(nextFrame);
      finish();
      return;
    }
    actualProgress = Math.max(actualProgress, progress);
    scheduleProgressFrame();
    await afterMenuPaint(nextFrame);
  }

  for (const button of entries.querySelectorAll("button")) {
    button.addEventListener("click", openEntry);
  }
  return {
    // 返回尚未读取的菜单请求，清空后不会重复处理
    takeRequest: function takeRequest() {
      const pending = request;
      request = 0;
      return pending;
    },
    // 同步 Java 菜单是否可用，并刷新网页入口的显示状态
    setAvailable: function setAvailable(visible) {
      available = visible;
      updateEntries();
    },
    report: report,
    finish: finish,
  };
}

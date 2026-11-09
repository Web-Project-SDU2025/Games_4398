// 百分比从 1 到 99 的显示节奏，实际进度始终是上限
const PROGRESS_DURATION_MS = 15000;

// 让上一帧有机会显示，之后再进入 Java，或撤下已经完成的加载层
export function afterMenuPaint(nextFrame) {
  return new Promise(function waitForPaint(resolve) {
    nextFrame(function firstFrame() {
      nextFrame(resolve);
    });
  });
}

// 由网页接管菜单入口和加载层，Java 只处理资源并发布进度
export function createPvzMenu(document, nextFrame, onError) {
  const entries = document.getElementById("pvz-menu-entries");
  const loading = document.getElementById("pvz-menu-loading");
  const title = document.getElementById("pvz-loading-title");
  const detail = document.getElementById("pvz-loading-detail");
  let available = false;
  let active = false;
  let book = false;
  let request = 0;
  let actualProgress = 0;
  let progressStartedAt = null;
  let progressFramePending = false;

  function updateEntries() {
    entries.hidden = !available || active;
  }

  function finish() {
    active = false;
    request = 0;
    loading.hidden = true;
    updateEntries();
  }

  function scheduleProgressFrame() {
    if (!progressFramePending) {
      progressFramePending = true;
      nextFrame(drawProgress);
    }
  }

  function drawProgress(timestamp) {
    progressFramePending = false;
    if (!active || actualProgress === 0) {
      return;
    }
    if (progressStartedAt === null) {
      progressStartedAt = timestamp;
    }
    const elapsed = timestamp - progressStartedAt;
    const displayed = Math.min(actualProgress, 1 + Math.floor(elapsed * 98 / PROGRESS_DURATION_MS));
    let stage = book ? "正在准备图鉴条目" : "正在读取关卡封面";
    if (actualProgress === 99) {
      stage = book ? "正在准备图鉴界面" : "正在准备选关界面";
    }
    const message = displayed + "% · " + stage;
    if (detail.textContent !== message) {
      detail.textContent = message;
    }
    if (displayed < actualProgress) {
      scheduleProgressFrame();
    }
  }

  async function openEntry(event) {
    if (active || !available) {
      return;
    }
    book = event.currentTarget.dataset.pvzEntry === "almanac";
    active = true;
    actualProgress = 0;
    progressStartedAt = null;
    updateEntries();
    title.textContent = book ? "正在加载图鉴" : "正在加载冒险模式";
    detail.textContent = book ? "正在准备图鉴条目…" : "正在读取关卡列表…";
    loading.hidden = false;
    await afterMenuPaint(nextFrame);
    if (active) {
      request = book ? 2 : 1;
    }
  }

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
    takeRequest: function takeRequest() {
      const pending = request;
      request = 0;
      return pending;
    },
    setAvailable: function setAvailable(visible) {
      available = visible;
      updateEntries();
    },
    report: report,
    finish: finish,
  };
}

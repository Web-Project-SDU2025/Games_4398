const parentFrame = window.parent;
const framed = parentFrame !== window && location.origin !== "null";
const owner = new URLSearchParams(location.search).get("owner");
let stopped = false;

if (framed && owner && /^[1-9][0-9]*$/.test(owner) && Number.isSafeInteger(Number(owner))) {
  const suffix = ":" + owner;
  globalThis.JokerPlatformStorage = {
    // 读取当前账户的牌局存档
    getItem: function readSave(key) {
      return localStorage.getItem(key + suffix);
    },
    // 记录当前账户的牌局操作
    setItem: function writeSave(key, value) {
      localStorage.setItem(key + suffix, value);
    },
  };
}

// 只接受同源播放器结束指令，离开子页以释放游戏循环和音频
function stopGame(event) {
  if (event.origin !== location.origin || event.source !== parentFrame ||
      !event.data || event.data.type !== "4398-stop-game" || stopped) {
    return;
  }
  stopped = true;
  location.replace("about:blank");
}

// 在存档范围确定后加载游戏，把模块和首屏初始化失败交给播放器
async function startGame() {
  try {
    await import("./runtime.js");
    if (framed && !stopped) {
      parentFrame.postMessage({ type: "4398-native-ready" }, location.origin);
    }
  } catch (error) {
    if (framed && !stopped) {
      parentFrame.postMessage({ type: "4398-native-error", message: "牌桌加载失败" }, location.origin);
    }
  }
}

if (framed) {
  window.addEventListener("message", stopGame);
}
startGame();

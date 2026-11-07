// 静态位图由原 Java 绘制器生成，浏览器使用本地构建资源，不再运行 Java
const IMAGE_URLS = import.meta.glob("./assets/*.png", { eager: true, query: "?url", import: "default" });
const BALL_AUDIO = new URL("./assets/ball.wav", import.meta.url).href;
const POCKET_AUDIO = new URL("./assets/pocket.wav", import.meta.url).href;
// 播放音量上限按两种音色分别维护，轻碰仍能保留自然起音
const AUDIO_GAIN = { ball: 0.5, pocket: 0.65 };
// 同种撞击短时间合并，避免开球产生大量重复声音，单位为毫秒
const AUDIO_WINDOW_MS = 40;

// 加载一张图像，成功后返回可直接用于 Canvas 的 Image 对象
function loadImage(address) {
  // 图片事件使用具名回调，失败时向游戏加载提示提供资源地址
  function waitForImage(resolve, reject) {
    const image = new Image();
    // 图片解码完成后再允许首帧绘制
    function loaded() {
      resolve(image);
    }
    // 保留图片地址，便于辨认加载失败的实际资源
    function failed() {
      reject(new Error("图像加载失败：" + address));
    }
    image.onload = loaded;
    image.onerror = failed;
    image.src = address;
  }
  return new Promise(waitForImage);
}

// 并行加载全部小图像，返回按文件名索引的只读素材表
export async function loadImages() {
  const names = [];
  const tasks = [];
  for (const file of Object.keys(IMAGE_URLS)) {
    const filename = file.substring(file.lastIndexOf("/") + 1, file.length - 4);
    names.push(filename);
    tasks.push(loadImage(IMAGE_URLS[file]));
  }
  const images = await Promise.all(tasks);
  const result = {};
  for (let index = 0; index < names.length; index = index + 1) {
    result[names[index]] = images[index];
  }
  return result;
}

// 使用浏览器音频播放课程采样，首次用户输入时解锁，运行时不计算连续静音采样
export function createAudio() {
  let context = null;
  let muted = false;
  const buffers = {};
  const previous = { ball: -Infinity, pocket: -Infinity };
  let loading = null;

  // 读取 WAV 并裁掉原录音头部静音，保持声音与接触画面同步
  async function decode(kind, address, leadSeconds) {
    const response = await fetch(address);
    if (!response.ok) {
      throw new Error("音效加载失败");
    }
    const bytes = await response.arrayBuffer();
    const original = await context.decodeAudioData(bytes);
    const offset = Math.floor(original.sampleRate * leadSeconds);
    const length = original.length - offset;
    const trimmed = context.createBuffer(1, length, original.sampleRate);
    const destination = trimmed.getChannelData(0);
    const source = original.getChannelData(0);
    for (let index = 0; index < length; index = index + 1) {
      destination[index] = source[index + offset];
    }
    buffers[kind] = trimmed;
  }

  // 用户手势建立音频环境，失败时保留游戏操作并允许之后重试
  async function unlock() {
    try {
      if (!context) {
        context = new AudioContext();
      }
      await context.resume();
      if (!loading) {
        loading = Promise.all([decode("ball", BALL_AUDIO, 0.057), decode("pocket", POCKET_AUDIO, 0.1)]);
      }
      await loading;
    } catch (error) {
      loading = null;
    }
  }

  // 消费本帧接触事件，每种声音只播放最响一次并按撞击速度调整音量
  function play(events) {
    const strongest = { ball: 0, pocket: 0 };
    for (const event of events) {
      strongest[event.kind] = Math.max(strongest[event.kind], event.speed);
    }
    events.length = 0;
    if (!context || muted) {
      return;
    }
    const now = performance.now();
    for (const kind of Object.keys(strongest)) {
      const speed = strongest[kind];
      if (speed < 45 || !buffers[kind] || now - previous[kind] < AUDIO_WINDOW_MS) {
        continue;
      }
      previous[kind] = now;
      const source = context.createBufferSource();
      const gain = context.createGain();
      source.buffer = buffers[kind];
      gain.gain.value = AUDIO_GAIN[kind] * Math.min(1, Math.sqrt(speed / 1500));
      source.connect(gain);
      gain.connect(context.destination);
      source.start();
    }
  }

  // 静音只改变声音开关，物理事件和比赛状态照常推进
  function toggle() {
    muted = !muted;
    return muted;
  }

  // 平台关闭游戏时释放音频环境，不在后台保留输出设备
  function close() {
    if (context) {
      context.close();
    }
  }
  return { unlock: unlock, play: play, toggle: toggle, close: close };
}

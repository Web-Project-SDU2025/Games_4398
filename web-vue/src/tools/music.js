// 背景音乐只引用站内音频，原视频中的 AAC 音轨按原音质转存
export const MUSIC_SOURCE = "/audio/mirror.m4a";
// 进度滑块每次调整的秒数
export const MUSIC_SEEK_STEP = 1;
// 全站音频输出倍率，保留音量滑块百分比并将实际输出降为原先的一成
export const AUDIO_OUTPUT_GAIN = 0.1;

// 读取音轨的有效时长和位置，元数据尚未加载时返回零
export function musicProgress(audio) {
  let duration = 0;
  if (Number.isFinite(audio.duration) && audio.duration > 0) {
    duration = audio.duration;
  }
  let position = 0;
  if (Number.isFinite(audio.currentTime)) {
    position = Math.max(0, Math.min(audio.currentTime, duration));
  }
  return { position: position, duration: duration };
}

// 将秒数转换为分秒文字，无效时间显示为零
export function musicTime(seconds) {
  let total = 0;
  if (Number.isFinite(seconds) && seconds > 0) {
    total = Math.floor(seconds);
  }
  const minutes = Math.floor(total / 60);
  const remainder = total % 60;
  let remainderText = String(remainder);
  if (remainder < 10) {
    remainderText = "0" + remainderText;
  }
  return minutes + ":" + remainderText;
}

// 管理单个播放器的启停和重试，关闭或卸载期间完成的旧播放请求按最新开关处理
export function createMusicPlayer(audio, report) {
  let enabled = false;
  let volume = 0;
  let pending = false;
  let disposed = false;

  // 尝试播放，浏览器拦截时保留开启偏好，等待下一次用户操作重试
  async function play() {
    if (disposed || !enabled || volume === 0 || pending) {
      return;
    }
    if (!audio.paused) {
      report("playing");
      return;
    }
    pending = true;
    try {
      await audio.play();
      if (disposed || !enabled || volume === 0) {
        audio.pause();
      } else {
        report("playing");
      }
    } catch (error) {
      if (disposed || !enabled || volume === 0) {
        return;
      }
      if (error.name === "NotAllowedError" || error.name === "AbortError") {
        report("waiting");
      } else {
        report("error");
      }
    } finally {
      pending = false;
    }
  }

  // 应用当前开关和百分比音量，零音量暂停音轨并保留音乐开启状态
  async function configure(settings) {
    if (disposed) {
      return;
    }
    enabled = settings.musicEnabled;
    volume = settings.musicVolume;
    audio.volume = (volume / 100) * AUDIO_OUTPUT_GAIN;
    if (!enabled) {
      audio.pause();
      report("paused");
      return;
    }
    if (volume === 0) {
      audio.pause();
      report("silent");
      return;
    }
    await play();
  }

  // 跳转到指定秒数并返回实际位置，暂停状态保持原样，读取失败时返回空值
  function seek(seconds) {
    if (disposed || !Number.isFinite(seconds)) {
      return null;
    }
    const progress = musicProgress(audio);
    if (progress.duration === 0) {
      return null;
    }
    const position = Math.max(0, Math.min(seconds, progress.duration));
    try {
      audio.currentTime = position;
      return audio.currentTime;
    } catch (error) {
      return null;
    }
  }

  // 页面框架卸载时停止音频，仍在等待的播放请求完成后也保持停止
  function dispose() {
    disposed = true;
    enabled = false;
    audio.pause();
  }

  return {
    configure: configure,
    retry: play,
    seek: seek,
    dispose: dispose,
  };
}

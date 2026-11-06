import test from "node:test";
import assert from "node:assert/strict";
import {
  createMusicPlayer,
  musicProgress,
  musicTime,
  AUDIO_OUTPUT_GAIN,
} from "../src/tools/music.js";

// 使用可控制播放结果的音轨替身，分别记录播放次数和真实暂停状态
function createAudio() {
  return {
    paused: true,
    volume: 1,
    currentTime: 0,
    duration: 180,
    playCalls: 0,
    // 成功播放后更新状态，模拟浏览器返回的播放 Promise
    play: async function play() {
      this.playCalls = this.playCalls + 1;
      this.paused = false;
    },
    // 暂停保持当前播放位置，后续可以继续
    pause: function pause() {
      this.paused = true;
    },
  };
}

// 浏览器首次拦截后通过用户操作重试，成功播放期间的音量调整沿用原音轨
test("音乐支持自动播放拦截后的重试及即时音量", async function testMusicRetry() {
  const audio = createAudio();
  let state = "";
  // 将播放器报告保存为测试状态
  function report(value) {
    state = value;
  }
  let blocked = true;
  // 首次拒绝播放，第二次播放在用户操作后成功
  async function blockedPlay() {
    audio.playCalls = audio.playCalls + 1;
    if (blocked) {
      const error = new Error("需要用户操作");
      error.name = "NotAllowedError";
      throw error;
    }
    audio.paused = false;
  }
  audio.play = blockedPlay;
  const player = createMusicPlayer(audio, report);
  await player.configure({ musicEnabled: true, musicVolume: 30 });
  assert.equal(state, "waiting");
  assert.equal(audio.volume, 0.3 * AUDIO_OUTPUT_GAIN);
  blocked = false;
  await player.retry();
  assert.equal(state, "playing");
  await player.configure({ musicEnabled: true, musicVolume: 70 });
  assert.equal(audio.volume, 0.7 * AUDIO_OUTPUT_GAIN);
  assert.equal(audio.playCalls, 2);
  await player.configure({ musicEnabled: false, musicVolume: 70 });
  await player.retry();
  assert.equal(state, "paused");
  assert.equal(audio.paused, true);
  assert.equal(audio.playCalls, 2);
});

// 关闭或卸载期间迟到的播放结果保持停止，快速重复操作只发出一次播放请求
test("音乐关闭与卸载处理迟到播放结果", async function testMusicRace() {
  const audio = createAudio();
  let complete;
  // 保存完成函数，测试安排关闭与成功响应的顺序
  function waitForPlay(resolve) {
    complete = resolve;
  }
  // 播放请求暂时等待，模拟浏览器正在加载音轨
  function delayedPlay() {
    audio.playCalls = audio.playCalls + 1;
    return new Promise(waitForPlay);
  }
  // 记录状态的流程由其他测试检查，此处只验证声音实际停止
  function report() {
    return;
  }
  audio.play = delayedPlay;
  const player = createMusicPlayer(audio, report);
  const loading = player.configure({ musicEnabled: true, musicVolume: 30 });
  await player.retry();
  assert.equal(audio.playCalls, 1);
  await player.configure({ musicEnabled: false, musicVolume: 30 });
  audio.paused = false;
  complete();
  await loading;
  assert.equal(audio.paused, true);
  const second = player.configure({ musicEnabled: true, musicVolume: 30 });
  player.dispose();
  audio.paused = false;
  complete();
  await second;
  assert.equal(audio.paused, true);
  await player.retry();
  assert.equal(audio.playCalls, 2);
});

// 零音量保持开启偏好而暂停音轨，恢复音量继续播放，读取失败显示可重试状态
test("音乐区分零音量与音轨错误", async function testMusicVolumeAndError() {
  const audio = createAudio();
  let state = "";
  // 记录每一次界面应显示的播放器状态
  function report(value) {
    state = value;
  }
  const player = createMusicPlayer(audio, report);
  await player.configure({ musicEnabled: true, musicVolume: 0 });
  assert.equal(state, "silent");
  assert.equal(audio.playCalls, 0);
  await player.configure({ musicEnabled: true, musicVolume: 45 });
  assert.equal(state, "playing");
  assert.equal(audio.volume, 0.45 * AUDIO_OUTPUT_GAIN);
  await player.configure({ musicEnabled: false, musicVolume: 45 });
  // 模拟音频资源读取失败
  async function failPlay() {
    throw new Error("音轨读取失败");
  }
  audio.play = failPlay;
  await player.configure({ musicEnabled: true, musicVolume: 45 });
  assert.equal(state, "error");
});

// 拖动时限制音轨范围，暂停、零音量和关闭状态都保留用户选择的位置
test("音乐跳转处理范围、未知时长与卸载状态", async function testMusicSeek() {
  const audio = createAudio();
  // 当前测试关注跳转对音轨的影响，播放状态通过音轨替身记录
  function report() {
    return;
  }
  const player = createMusicPlayer(audio, report);
  assert.equal(player.seek(45), 45);
  assert.equal(audio.paused, true);
  assert.equal(player.seek(-10), 0);
  assert.equal(player.seek(200), 180);
  assert.equal(player.seek(NaN), null);
  await player.configure({ musicEnabled: true, musicVolume: 50 });
  assert.equal(player.seek(75), 75);
  assert.equal(audio.paused, false);
  await player.configure({ musicEnabled: false, musicVolume: 50 });
  assert.equal(player.seek(30), 30);
  assert.equal(audio.paused, true);
  audio.duration = Infinity;
  assert.equal(player.seek(10), null);
  assert.deepEqual(musicProgress(audio), { position: 0, duration: 0 });
  audio.duration = 180;
  player.dispose();
  assert.equal(player.seek(60), null);
  assert.equal(audio.currentTime, 30);
  assert.equal(musicTime(75.8), "1:15");
  assert.equal(musicTime(NaN), "0:00");
});

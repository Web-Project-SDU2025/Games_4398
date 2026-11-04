import test from "node:test";
import assert from "node:assert/strict";
import * as preferences from "../src/logic/preferences.js";

// 超范围和损坏字段回到有效范围，每次默认设置保持独立
test("界面偏好校验数值边界并整理旧数据", function testPreferenceBounds() {
  const settings = preferences.normalizePreferences({
    trailEnabled: false,
    trailDensity: 100000,
    trailSize: -20,
    loginMotion: false,
  });
  assert.equal(settings.trailDensity, preferences.TRAIL_DENSITY.maximum);
  assert.equal(settings.trailSize, preferences.TRAIL_SIZE.minimum);
  assert.equal(settings.trailEnabled, false);
  assert.equal(settings.loginMotion, false);
  const invalid = preferences.normalizePreferences({
    trailDensity: null,
    trailSize: Infinity,
  });
  assert.deepEqual(invalid, preferences.defaultPreferences());
  assert.deepEqual(
    preferences.normalizePreferences(null),
    preferences.defaultPreferences(),
  );
  const defaults = preferences.defaultPreferences();
  assert.equal(defaults.trailDensity, 40);
  assert.equal(defaults.trailSize, 10);
  defaults.trailEnabled = false;
  assert.equal(preferences.defaultPreferences().trailEnabled, true);
});

// 保存和读取使用同一键名，模拟刷新后恢复用户选择
test("界面偏好保存后可恢复，损坏和受限存储有明确结果", function testPreferenceStorage() {
  let content = "";
  const storage = {
    // 记录写入内容并核对存储键
    setItem: function setItem(key, value) {
      assert.equal(key, preferences.PREFERENCES_KEY);
      content = value;
    },
    // 将已保存内容返回读取流程
    getItem: function getItem(key) {
      assert.equal(key, preferences.PREFERENCES_KEY);
      return content;
    },
  };
  const settings = preferences.defaultPreferences();
  settings.trailSize = preferences.TRAIL_SIZE.maximum;
  settings.loginMotion = false;
  assert.equal(preferences.savePreferences(storage, settings), true);
  assert.deepEqual(preferences.readPreferences(storage), settings);
  content = "{broken";
  assert.deepEqual(
    preferences.readPreferences(storage),
    preferences.defaultPreferences(),
  );
  assert.equal(preferences.savePreferences(null, settings), false);
  assert.deepEqual(
    preferences.readPreferences(null),
    preferences.defaultPreferences(),
  );
});

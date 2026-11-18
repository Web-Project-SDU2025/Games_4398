<template>
  <main class="auth-page">
    <AuthGridBackground :paused="motionPaused" :speed="motionSpeed" />
    <button
      class="auth-motion-toggle"
      type="button"
      :aria-label="motionButtonTitle"
      :title="motionButtonTitle"
      :aria-pressed="motionPaused"
      @click="toggleBackgroundMotion"
    >
      <span
        v-if="motionPaused"
        class="i-ep-video-play"
        aria-hidden="true"
      ></span>
      <span v-else class="i-ep-video-pause" aria-hidden="true"></span>
    </button>
    <button
      class="auth-settings-toggle"
      type="button"
      aria-label="登录背景设置"
      title="登录背景设置"
      :aria-expanded="settingsOpen"
      aria-controls="login-background-settings"
      @click="toggleSettings"
    >
      <span class="i-ep-setting" aria-hidden="true"></span>
    </button>
    <section
      v-if="settingsOpen"
      id="login-background-settings"
      class="auth-settings-panel"
      aria-label="登录背景设置"
    >
      <label class="settings-row" for="login-background-motion">
        <span>播放背景动画</span>
        <input
          id="login-background-motion"
          type="checkbox"
          :checked="!motionPaused"
          @change="setBackgroundMotion"
        />
      </label>
      <label class="settings-range" for="login-background-speed">
        <span>
          动画速度
          <output for="login-background-speed">{{ motionSpeed }}×</output>
        </span>
        <input
          id="login-background-speed"
          type="range"
          :min="motionSpeedRange.minimum"
          :max="motionSpeedRange.maximum"
          :value="motionSpeed"
          :disabled="motionPaused"
          @input="setBackgroundSpeed"
        />
      </label>
    </section>
    <div class="auth-layout">
      <aside class="auth-gallery" aria-labelledby="login-games-title">
        <h2 id="login-games-title">今天想玩点什么？</h2>
        <div class="auth-poster-art" aria-hidden="true"></div>
        <ul class="auth-poster-games" aria-label="已接入游戏">
          <li>小丑牌</li>
          <li>美式八球</li>
          <li>植物大战僵尸</li>
        </ul>
      </aside>
      <section class="auth-panel" aria-labelledby="site-title">
        <h1 id="site-title">{{ formTitle }}</h1>
        <div class="auth-tabs" role="group" aria-label="账户操作">
          <button
            type="button"
            :aria-pressed="mode === 'login'"
            :disabled="actionBusy"
            @click="changeMode('login')"
          >
            登录
          </button>

          <button
            type="button"
            :aria-pressed="mode === 'register'"
            :disabled="actionBusy"
            @click="changeMode('register')"
          >
            注册
          </button>
        </div>
        <form @submit.prevent="submit" :aria-busy="submitting">
          <fieldset :disabled="actionBusy">
            <div class="auth-field">
              <label for="account-name">账号</label>
              <input
                id="account-name"
                v-model.trim="username"
                name="username"
                autocomplete="username"
                :maxlength="limits.USERNAME_LIMIT"
                required
              />
            </div>
            <div v-if="mode === 'register'" class="auth-field-pair">
              <div class="auth-field">
                <label for="nickname">昵称</label>
                <input
                  id="nickname"
                  v-model.trim="name"
                  name="nickname"
                  autocomplete="nickname"
                  :maxlength="limits.NAME_LIMIT"
                  required
                />
              </div>
              <div class="auth-field">
                <label for="email">邮箱 <small>选填</small></label>
                <input
                  id="email"
                  v-model.trim="email"
                  name="email"
                  type="email"
                  autocomplete="email"
                  :maxlength="limits.EMAIL_LIMIT"
                />
              </div>
            </div>
            <div :class="{ 'auth-field-pair': mode === 'register' }">
              <div class="auth-field">
                <label for="account-password">密码</label>
                <input
                  id="account-password"
                  v-model="password"
                  name="password"
                  type="password"
                  :autocomplete="passwordAutocomplete"
                  :maxlength="passwordLimit"
                  required
                />
              </div>
              <div v-if="mode === 'register'" class="auth-field">
                <label for="confirm-password">确认密码</label>
                <input
                  id="confirm-password"
                  v-model="confirmPassword"
                  name="confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  :maxlength="passwordLimit"
                  required
                />
              </div>
            </div>
            <div class="auth-field">
              <label for="captcha">验证码</label>
              <div class="captcha-row">
                <input
                  id="captcha"
                  v-model.trim="validateCode"
                  inputmode="numeric"
                  autocomplete="off"
                  :maxlength="limits.CAPTCHA_LENGTH"
                  :aria-describedby="captchaDescription"
                  pattern="[0-9]{4}"
                  required
                />
                <button
                  class="captcha-button"
                  type="button"
                  :disabled="captchaLoading"
                  :aria-busy="captchaLoading"
                  aria-label="刷新验证码"
                  title="刷新验证码"
                  @click="refreshCaptcha"
                >
                  <img
                    v-if="captchaImage"
                    :src="captchaImage"
                    alt="图形验证码"
                    width="108"
                    height="40"
                  />
                  <span v-else-if="captchaLoading">加载中…</span>
                  <span v-else>重新加载</span>
                  <span v-if="!captchaLoading" class="i-ep-refresh" aria-hidden="true"></span>
                </button>
              </div>
              <div id="captcha-feedback" class="auth-feedback" role="status">
                <p v-if="captchaError">{{ captchaError }}</p>
              </div>
            </div>
            <div class="auth-feedback auth-form-feedback" role="status">
              <p v-if="notice">{{ notice }}</p>
            </div>
            <button
              class="auth-submit"
              type="submit"
              :disabled="actionBusy || captchaLoading || !captchaImage"
            >
              {{ submitText }}
            </button>
          </fieldset>
        </form>
        <div v-if="demoEnabled" class="auth-demo">
          <div>
            <strong>课程演示</strong>
            <small>一键进入管理员账户</small>
          </div>
          <button
            class="demo-login"
            type="button"
            :disabled="actionBusy"
            @click="demoLogin"
          >
            <template v-if="demoBusy">正在进入…</template>
            <template v-else>演示登录</template>
            <span class="i-ep-arrow-right" aria-hidden="true"></span>
          </button>
        </div>
      </section>
    </div>
  </main>
</template>

<script>
import { useAppStore } from "./stores/app";
import * as account from "./logic/account.js";
import * as auth from "./logic/auth.js";
import * as limits from "./logic/limits.js";
import { usePreferencesStore } from "./stores/preferences.js";
import { getSwitchAccounts } from "./services/userServ.js";
import AuthGridBackground from "./components/AuthGridBackground.vue";
import {
  LOGIN_MOTION_SPEED,
  normalizePreferences,
} from "./logic/preferences.js";

export default {
  components: { AuthGridBackground: AuthGridBackground },
  // 每次打开登录页都创建独立的表单和背景设置状态
  data: function loginState() {
    const form = auth.createLoginForm();
    form.limits = limits;
    form.passwordLimit = account.REGISTER_PASSWORD_LIMIT;
    form.settingsOpen = false;
    form.motionSpeedRange = LOGIN_MOTION_SPEED;
    form.demoEnabled = false;
    form.demoBusy = false;
    return form;
  },
  computed: {
    // 登录填写已有密码，注册填写新密码，供浏览器选择正确的自动填充方式
    passwordAutocomplete: function passwordAutocomplete() {
      if (this.mode === "login") {
        return "current-password";
      }
      return "new-password";
    },
    // 验证码加载失败时，将错误提示与验证码输入框关联
    captchaDescription: function captchaDescription() {
      if (this.captchaError) {
        return "captcha-feedback";
      }
      return undefined;
    },
    // 普通提交与演示登录使用同一会话，等待时禁止另一条路径
    actionBusy: function actionBusy() {
      return this.submitting || this.demoBusy;
    },
    // 登录页与设置窗口共用背景开关，刷新页面后恢复浏览器中的选择
    motionPaused: function motionPaused() {
      return !usePreferencesStore().settings.loginMotion;
    },
    // 登录背景速度从偏好存储读取，登录页设置与通用设置保持一致
    motionSpeed: function motionSpeed() {
      return usePreferencesStore().settings.loginMotionSpeed;
    },
    // 根据暂停状态返回按钮提示，切换登录和注册时沿用当前选择
    motionButtonTitle: function motionButtonTitle() {
      if (this.motionPaused) {
        return "播放背景动画";
      }
      return "暂停背景动画";
    },
    // 在同一面板中用标题区分登录和注册
    formTitle: function formTitle() {
      if (this.mode === "register") {
        return "创建账户";
      }
      return "欢迎回来";
    },
    // 请求尚未结束时，按钮显示提交进度而不是重复提示用户登录或注册
    submitText: function submitText() {
      if (this.submitting) {
        return "正在提交…";
      }
      if (this.mode === "register") {
        return "注册";
      }
      return "登录";
    },
  },
  // 页面加载后获取验证码，并探测演示模式是否开启
  created: function loadCaptcha() {
    this.refreshCaptcha();
    this.loadDemoStatus();
  },
  methods: {
    // 显示或收起登录页的快捷背景设置
    toggleSettings: function toggleSettings() {
      this.settingsOpen = !this.settingsOpen;
    },
    // 根据复选框状态保存背景动画开关
    setBackgroundMotion: function setBackgroundMotion(event) {
      const store = usePreferencesStore();
      const settings = normalizePreferences(store.settings);
      settings.loginMotion = event.target.checked;
      store.update(settings);
    },
    // 保存速度倍数，画布在下一帧使用新设置
    setBackgroundSpeed: function setBackgroundSpeed(event) {
      const store = usePreferencesStore();
      const settings = normalizePreferences(store.settings);
      settings.loginMotionSpeed = Number(event.target.value);
      store.update(settings);
    },
    // 切换背景动画的暂停状态，画布保留暂停时的画面
    toggleBackgroundMotion: function toggleBackgroundMotion() {
      const store = usePreferencesStore();
      const settings = normalizePreferences(store.settings);
      settings.loginMotion = this.motionPaused;
      store.update(settings);
    },
    // 刷新验证码，同时清空上次填写的验证码
    refreshCaptcha: async function refreshCaptcha() {
      await auth.refreshCaptcha(this);
    },
    // 切换表单时保留账号，清空密码并获取新的验证码
    changeMode: async function changeMode(mode) {
      if (this.demoBusy) {
        return;
      }
      await auth.changeLoginMode(this, mode);
    },
    // 注册游戏用户，成功后回到登录表单
    register: async function register() {
      await auth.registerAccount(this);
    },
    // 登录后保存登录信息和菜单，跳转到主界面
    login: async function login() {
      const store = useAppStore();
      await store.login(
        this.username,
        this.password,
        this.validateCodeId,
        this.validateCode,
      );
      try {
        await store.setNavi();
      } catch (error) {
        store.logout();
        throw error;
      }
      this.password = "";
      await this.$router.replace("/MainPage");
    },
    // 读取切号接口的开启状态，演示模式开启时登录页显示一键进入按钮
    loadDemoStatus: async function loadDemoStatus() {
      try {
        const status = await getSwitchAccounts("");
        this.demoEnabled = status.enabled === true;
      } catch (error) {
        this.demoEnabled = false;
      }
    },
    // 演示模式下免密进入管理员账户，凭证由后端按目标账户重新签发
    demoLogin: async function demoLogin() {
      if (this.actionBusy || !this.demoEnabled) {
        return;
      }
      this.notice = "";
      this.demoBusy = true;
      try {
        const store = useAppStore();
        await store.switchAccount("admin");
        await this.$router.replace("/MainPage");
      } catch (error) {
        this.notice = account.getErrorMessage(error, "演示登录失败，请稍后重试");
      } finally {
        this.demoBusy = false;
      }
    },
    // 检查表单后执行登录或注册，并显示请求结果
    submit: async function submit() {
      if (this.actionBusy || this.captchaLoading || !this.captchaImage) {
        return;
      }
      this.notice = "";
      if (this.mode === "register" && this.password !== this.confirmPassword) {
        this.notice = "两次输入的密码不一致";
        return;
      }
      this.submitting = true;
      try {
        if (this.mode === "register") {
          await this.register();
        } else {
          await this.login();
        }
      } catch (error) {
        this.notice = account.getErrorMessage(
          error,
          "请求失败，请检查网络后重试",
        );
        await this.refreshCaptcha();
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

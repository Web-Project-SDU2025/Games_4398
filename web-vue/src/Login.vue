<template>
  <main class="auth-page">
    <div class="auth-layout">
      <section class="auth-panel" aria-labelledby="site-title">
        <!-- 登录页面上部区域 -->
        <h1 id="site-title">{{ formTitle }}</h1>
        <div
          v-if="!adminOnly"
          class="auth-tabs"
          role="tablist"
          aria-label="账户操作"
        >
          <button
            id="login-tab"
            type="button"
            role="tab"
            :aria-selected="mode === 'login'"
            aria-controls="account-form"
            :disabled="submitting"
            @click="changeMode('login')"
          >
            登录
          </button>

          <button
            id="register-tab"
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            aria-controls="account-form"
            :disabled="submitting"
            @click="changeMode('register')"
          >
            注册
          </button>
        </div>
        <!-- 登录与注册共用账号、密码和验证码表单 -->
        <Transition name="page" mode="out-in">
          <form
            :key="mode"
            id="account-form"
            @submit.prevent="submit"
            :aria-busy="submitting"
          >
            <fieldset :disabled="submitting">
              <label for="account-name">账号</label>
              <input
                id="account-name"
                v-model.trim="username"
                autocomplete="username"
                maxlength="20"
                required
              />
              <!-- 用户注册表单内容 -->
              <template v-if="mode === 'register'">
                <label for="nickname">昵称</label>
                <input
                  id="nickname"
                  v-model.trim="name"
                  autocomplete="nickname"
                  maxlength="50"
                  required
                />
                <label for="email">邮箱（可选）</label>
                <input
                  id="email"
                  v-model.trim="email"
                  type="email"
                  autocomplete="email"
                  maxlength="60"
                />
              </template>
              <!-- 用户登录表单内容 -->
              <label for="account-password">密码</label>
              <input
                v-if="mode === 'login'"
                id="account-password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                :maxlength="passwordLimit"
                required
              />
              <input
                v-else
                id="account-password"
                v-model="password"
                type="password"
                autocomplete="new-password"
                :maxlength="passwordLimit"
                required
              />
              <template v-if="mode === 'register'">
                <label for="confirm-password">确认密码</label>
                <input
                  id="confirm-password"
                  v-model="confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  :maxlength="passwordLimit"
                  required
                />
              </template>
              <label for="captcha">验证码</label>
              <div class="captcha-row">
                <input
                  id="captcha"
                  v-model.trim="validateCode"
                  inputmode="numeric"
                  autocomplete="off"
                  maxlength="4"
                  pattern="[0-9]{4}"
                  required
                />
                <button
                  class="captcha-button"
                  v-reveal="captchaImage"
                  type="button"
                  :disabled="captchaLoading"
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
                  <span v-else>重新加载</span>
                </button>
              </div>
              <button
                class="auth-submit"
                type="submit"
                :disabled="submitting || captchaLoading || !captchaImage"
              >
                {{ submitText }}
              </button>
            </fieldset>
            <p
              v-reveal="notice"
              class="auth-notice"
              role="status"
              aria-live="polite"
            >
              {{ notice }}
            </p>
          </form>
        </Transition>
      </section>
      <aside class="auth-introduction">
        <img
          src="/images/game-collection.png"
          alt="电脑上的游戏库"
          width="772"
          height="528"
        />
        <h2>你的游戏，尽在这里</h2>
        <p>回到游戏库，收藏喜欢的游戏，留下每一次游玩的记录。</p>
        <button
          v-if="!adminOnly"
          class="platform-secondary-button"
          type="button"
          :disabled="submitting"
          @click="changeMode('register')"
        >
          创建我的账户
        </button>
        <p class="auth-login-links">
          <RouterLink v-if="!adminOnly" to="/AdminLogin">管理员登录</RouterLink>
          <RouterLink v-else to="/Login">返回用户登录</RouterLink>
        </p>
      </aside>
    </div>
  </main>
</template>

<script>
import { useAppStore } from "./stores/app";
import * as account from "./logic/account.js";
import * as auth from "./logic/auth.js";

export default {
  props: { adminOnly: Boolean },
  // 返回表单中使用的数据
  data: function loginState() {
    const form = auth.createLoginForm();
    form.passwordLimit = account.REGISTER_PASSWORD_LIMIT;
    return form;
  },
  computed: {
    // 根据当前操作显示表单标题
    formTitle: function formTitle() {
      if (this.adminOnly) {
        return "管理员登录";
      }
      if (this.mode === "register") {
        return "创建账户";
      }
      return "欢迎回来";
    },
    // 提交期间显示等待状态
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
  // 页面加载后获取验证码
  created: function loadCaptcha() {
    this.refreshCaptcha();
  },
  methods: {
    // 刷新验证码，同时清空上次填写的验证码
    refreshCaptcha: async function refreshCaptcha() {
      await auth.refreshCaptcha(this);
    },
    // 切换表单时保留账号，清空密码并获取新的验证码
    changeMode: async function changeMode(mode) {
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
        this.adminOnly
      );
      try {
        await store.setNavi();
      } catch (error) {
        store.logout();
        throw error;
      }
      this.password = "";
      let nextPage = "/MainPage";
      if (this.adminOnly) {
        nextPage = "/AccountManage";
      }
      await this.$router.replace(nextPage);
    },
    // 检查表单后执行登录或注册，并显示请求结果
    submit: async function submit() {
      if (this.submitting || this.captchaLoading) {
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
          "请求失败，请检查网络后重试"
        );
        await this.refreshCaptcha();
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

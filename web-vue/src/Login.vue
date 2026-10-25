<template>
  <main class="auth-page">
    <section class="auth-panel" aria-labelledby="site-title">
      <!-- 登录页面上部区域 -->
      <h1 id="site-title">4398游戏管理</h1>
      <div class="auth-tabs" role="tablist" aria-label="账户操作">
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
      <!-- 登录与注册共用账号、密码和验证码表单。 -->
      <form id="account-form" @submit.prevent="submit" :aria-busy="submitting">
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
            maxlength="60"
            required
          />
          <input
            v-else
            id="account-password"
            v-model="password"
            type="password"
            autocomplete="new-password"
            maxlength="60"
            required
          />
          <template v-if="mode === 'register'">
            <label for="confirm-password">确认密码</label>
            <input
              id="confirm-password"
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              maxlength="60"
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
        <p class="auth-notice" role="status" aria-live="polite">{{ notice }}</p>
      </form>
    </section>
  </main>
</template>

<script>
import { getValidateCode, registerUser } from "./services/userServ";
import { useAppStore } from "./stores/app";

export default {
  // 返回表单中使用的数据。
  data: function () {
    return {
      mode: "login",
      username: "",
      name: "",
      email: "",
      password: "",
      confirmPassword: "",
      validateCode: "",
      validateCodeId: null,
      captchaImage: "",
      captchaLoading: false,
      submitting: false,
      notice: "",
    };
  },
  computed: {
    submitText: function () {
      if (this.submitting) {
        return "正在提交…";
      }
      if (this.mode === "register") {
        return "注册";
      }
      return "登录";
    },
  },
  // 页面加载后获取验证码。
  created: function () {
    this.refreshCaptcha();
  },
  methods: {
    // 刷新验证码，同时清空上次填写的验证码。
    refreshCaptcha: async function () {
      this.captchaLoading = true;
      this.captchaImage = "";
      this.validateCode = "";
      this.validateCodeId = null;
      try {
        const result = await getValidateCode();
        this.validateCodeId = result.validateCodeId;
        this.captchaImage = result.img;
        if (this.notice === "验证码加载失败，请点击重新加载") {
          this.notice = "";
        }
      } catch (error) {
        this.notice = "验证码加载失败，请点击重新加载";
      } finally {
        this.captchaLoading = false;
      }
    },
    // 切换表单时保留账号，清空密码并获取新的验证码。
    changeMode: async function (mode) {
      if (this.mode === mode || this.submitting) {
        return;
      }
      this.mode = mode;
      this.password = "";
      this.confirmPassword = "";
      this.notice = "";
      await this.refreshCaptcha();
    },
    // 注册游戏用户，成功后回到登录表单。
    register: async function () {
      const account = {
        username: this.username,
        name: this.name,
        email: this.email,
        password: this.password,
        validateCodeId: this.validateCodeId,
        validateCode: this.validateCode,
      };
      const result = await registerUser(account);
      if (result.code !== 0) {
        throw new Error(result.msg);
      }
      this.mode = "login";
      this.password = "";
      this.confirmPassword = "";
      this.notice = "注册成功，请登录";
      await this.refreshCaptcha();
    },
    // 登录后保存登录信息和菜单，跳转到主界面。
    login: async function () {
      const store = useAppStore();
      await store.login(
        this.username,
        this.password,
        this.validateCodeId,
        this.validateCode
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
    submit: async function () {
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
        this.notice = "请求失败，请检查网络后重试";
        if (error.response && error.response.data && error.response.data.msg) {
          this.notice = error.response.data.msg;
        } else if (!error.response && error.message && !error.isAxiosError) {
          this.notice = error.message;
        }
        await this.refreshCaptcha();
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  padding: 40px 16px;
  box-sizing: border-box;
  background: #f5f6f7;
  color: #222;
}
.auth-panel {
  width: 360px;
  max-width: 100%;
  margin: 0 auto;
}
h1 {
  margin: 0 0 24px;
  font-size: 24px;
}
.auth-tabs {
  display: flex;
  border-bottom: 1px solid #ccc;
  margin-bottom: 24px;
}
.auth-tabs button {
  width: 50%;
  padding: 12px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: #555;
  cursor: pointer;
  font-size: 16px;
}
.auth-tabs button[aria-selected="true"] {
  color: #17614d;
  border-bottom-color: #17614d;
}
fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
  min-width: 0;
}
label {
  margin-top: 8px;
  font-size: 14px;
}
input {
  width: 100%;
  min-width: 0;
  height: 42px;
  padding: 8px 10px;
  box-sizing: border-box;
  border: 1px solid #aaa;
  border-radius: 4px;
  background: white;
  color: #222;
  font: inherit;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid #17614d;
  outline-offset: 2px;
}
.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px;
  gap: 12px;
}
.captcha-button {
  width: 110px;
  height: 42px;
  border: 1px solid #aaa;
  border-radius: 4px;
  padding: 0;
  background: white;
  cursor: pointer;
}
.captcha-button img {
  display: block;
  width: 108px;
  height: 40px;
}
.auth-submit {
  margin-top: 16px;
  height: 42px;
  border: 0;
  border-radius: 4px;
  background: #17614d;
  color: white;
  font-size: 16px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: wait;
}
.auth-notice {
  min-height: 24px;
  margin-top: 16px;
  color: #8a2626;
  overflow-wrap: anywhere;
}
</style>

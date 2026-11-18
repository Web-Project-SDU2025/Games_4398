import { getValidateCode, registerUser } from "../services/userServ.js";
import { createRegistration } from "./account.js";

// 每次打开登录页都创建新表单，不带入上一次的密码和验证码
export function createLoginForm() {
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
    captchaRequest: 0,
    captchaError: "",
    submitting: false,
    notice: "",
  };
}

// 只显示最后一次请求的验证码，避免迟到的图片覆盖刚刷新的验证码
export async function refreshCaptcha(form) {
  const request = form.captchaRequest + 1;
  form.captchaRequest = request;
  form.captchaLoading = true;
  form.captchaError = "";
  form.captchaImage = "";
  form.validateCode = "";
  form.validateCodeId = null;
  try {
    const result = await getValidateCode();
    if (request !== form.captchaRequest) {
      return;
    }
    form.validateCodeId = result.validateCodeId;
    form.captchaImage = result.img;
  } catch (error) {
    if (request === form.captchaRequest) {
      form.captchaError = "验证码加载失败，请点击刷新按钮重试";
    }
  } finally {
    if (request === form.captchaRequest) {
      form.captchaLoading = false;
    }
  }
}

// 用户可以带着当前账号切换表单，但需要重新输入密码和验证码
export async function changeLoginMode(form, mode) {
  if (form.mode === mode || form.submitting) {
    return;
  }
  form.mode = mode;
  form.password = "";
  form.confirmPassword = "";
  form.notice = "";
  await refreshCaptcha(form);
}

// 提交注册资料，成功后清除密码并恢复登录表单，失败时交给调用方显示错误
export async function registerAccount(form) {
  const registration = createRegistration(form);
  const result = await registerUser(registration);
  if (result.code !== 0) {
    throw new Error(result.msg);
  }
  form.mode = "login";
  form.password = "";
  form.confirmPassword = "";
  form.notice = "注册成功，请登录";
  await refreshCaptcha(form);
}

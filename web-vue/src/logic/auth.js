import { getValidateCode, registerUser } from "../services/userServ.js";
import { createRegistration } from "./account.js";

// 创建独立的登录表单数据，避免页面重新打开时保留上次输入的密码
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
    submitting: false,
    notice: "",
  };
}

// 更新表单的验证码图片和编号，同时清除旧输入，避免提交已经失效的验证码
export async function refreshCaptcha(form) {
  form.captchaLoading = true;
  form.captchaImage = "";
  form.validateCode = "";
  form.validateCodeId = null;
  try {
    const result = await getValidateCode();
    form.validateCodeId = result.validateCodeId;
    form.captchaImage = result.img;
    if (form.notice === "验证码加载失败，请点击重新加载") {
      form.notice = "";
    }
  } catch (error) {
    form.notice = "验证码加载失败，请点击重新加载";
  } finally {
    form.captchaLoading = false;
  }
}

// 切换登录或注册表单，保留账号但清空密码，防止两种操作共用密码输入
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

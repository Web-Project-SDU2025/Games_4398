import { getValidateCode, registerUser } from "../services/userServ.js";
import { createRegistration } from "./account.js";

// 创建独立登录表单，每次打开页面时密码和验证码均为空
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

// 更新验证码图片和编号，刷新时清除旧输入，失败时提供重试提示
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

// 切换表单时保留账号、清空密码，提交期间保持当前模式
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

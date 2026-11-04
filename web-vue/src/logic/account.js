import { CHANGE_PASSWORD_LIMIT } from "./limits.js";
export { REGISTER_PASSWORD_LIMIT, CHANGE_PASSWORD_LIMIT } from "./limits.js";

/** 根据账户资料返回昵称；没有昵称时使用账号 */
export function getDisplayName(user) {
  if (user.perName) {
    return user.perName;
  }
  return user.username;
}

/** 将后端角色名称转成界面上的中文名称 */
export function getRoleName(role) {
  if (role === "ROLE_ADMIN") {
    return "管理员";
  }
  return "游戏用户";
}

/** 检查旧密码、新密码和确认密码，返回错误文字；通过时返回空字符串 */
export function checkPasswordChange(oldPassword, newPassword, confirmation) {
  if (!oldPassword || !newPassword) {
    return "旧密码和新密码不能为空";
  }
  if (oldPassword === newPassword) {
    return "新密码和旧密码相同，不能修改";
  }
  if (newPassword !== confirmation) {
    return "两次输入的新密码不一致";
  }
  if (newPassword.length > CHANGE_PASSWORD_LIMIT) {
    return "密码长度最大为" + CHANGE_PASSWORD_LIMIT + "位";
  }
  return "";
}

/** 按注册协议整理字段，界面状态由表单自身管理 */
export function createRegistration(form) {
  return {
    username: form.username,
    name: form.name,
    email: form.email,
    password: form.password,
    validateCodeId: form.validateCodeId,
    validateCode: form.validateCode,
  };
}

/** 优先显示服务器错误；网络异常时使用调用方提供的提示 */
export function getErrorMessage(error, fallback) {
  if (error.response) {
    const response = error.response.data;
    if (response && response.msg) {
      return response.msg;
    }
  } else if (error.message && !error.isAxiosError) {
    return error.message;
  }
  return fallback;
}

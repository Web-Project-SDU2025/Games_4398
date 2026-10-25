import axios from "axios";

export async function userLoginReq(
  username,
  password,
  validateCodeId,
  validateCode
) {
  const result = await axios.post("/auth/login", {
    username: username,
    password: password,
    validateCodeId: validateCodeId,
    validateCode: validateCode,
  });
  // 等待服务器返回有效的登录令牌。
  if (typeof result.data.token !== "string" || result.data.token === "") {
    throw new Error("服务器未返回登录信息");
  }
  return result.data;
}

export async function getValidateCode() {
  const result = await axios.post("/auth/getValidateCode", { data: {} });
  if (result.data.code !== 0) {
    throw new Error(result.data.msg);
  }
  return result.data.data;
}

export async function registerUser(account) {
  const result = await axios.post("/auth/registerUser", { data: account });
  return result.data;
}

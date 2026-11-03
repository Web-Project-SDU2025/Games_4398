import axios from "axios";

// 发送账号、密码和验证码，返回有效的登录信息
export async function userLoginReq(
  username,
  password,
  validateCodeId,
  validateCode,
  adminOnly
) {
  let url = "/auth/login";
  if (adminOnly) {
    url = "/auth/adminLogin";
  }
  const result = await axios.post(url, {
    username: username,
    password: password,
    validateCodeId: validateCodeId,
    validateCode: validateCode,
  });
  // 等待服务器返回有效的登录令牌
  if (typeof result.data.token !== "string" || result.data.token === "") {
    throw new Error("服务器未返回登录信息");
  }
  return result.data;
}

// 本机检查可用时返回账户选项，线上关闭时返回空列表
export async function getSwitchAccounts(token) {
  const result = await axios.post(
    "/api/localAccount/list",
    { data: {} },
    {
      headers: { Authorization: "Bearer " + token },
    }
  );
  if (result.data.code !== 0) {
    throw new Error(result.data.msg);
  }
  return result.data.data;
}

// 让后端签发目标账户的真实凭证，再获取对应权限菜单，全部成功才切换页面身份
export async function switchAccount(username, token) {
  const response = await axios.post(
    "/api/localAccount/switch",
    { data: { username: username } },
    {
      headers: { Authorization: "Bearer " + token },
    }
  );
  if (response.data.code !== 0) {
    throw new Error(response.data.msg);
  }
  const session = response.data.data;
  const menus = await axios.post(
    "/api/base/getMenuList",
    { data: {} },
    {
      headers: { Authorization: "Bearer " + session.token },
    }
  );
  if (menus.data.code !== 0) {
    throw new Error(menus.data.msg);
  }
  return { session: session, menus: menus.data.data };
}

// 获取验证码图片和编号；提交时使用编号查找对应验证码
export async function getValidateCode() {
  const result = await axios.post("/auth/getValidateCode", { data: {} });
  if (result.data.code !== 0) {
    throw new Error(result.data.msg);
  }
  return result.data.data;
}

// 提交注册资料，返回服务器给出的注册结果
export async function registerUser(account) {
  const result = await axios.post("/auth/registerUser", { data: account });
  return result.data;
}

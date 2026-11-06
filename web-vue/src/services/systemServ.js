import { generalRequest } from "~/services/genServ";

// 获取当前账户可见的导航菜单，读取失败时交给调用方处理
export async function getMenuList() {
  const result = await generalRequest("/api/base/getMenuList", {});
  if (result.code !== 0) {
    throw new Error(result.msg);
  }
  return result.data;
}

// 提交当前账户的旧密码和新密码，返回处理结果
export function updatePassword(passwords) {
  return generalRequest("/api/base/updatePassword", passwords);
}

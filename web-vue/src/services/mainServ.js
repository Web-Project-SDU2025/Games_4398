import { generalRequest } from "./genServ";

// 获取菜单列表
export async function getMenuList() {
  const result = await generalRequest("/api/base/getMenuList", {});
  if (result.code !== 0) {
    throw new Error(result.msg);
  }
  return result.data;
}

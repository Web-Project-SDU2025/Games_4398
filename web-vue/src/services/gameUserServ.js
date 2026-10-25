import { generalRequest } from "./genServ";

// 查询账户列表，参数是账号或昵称。
export function getAccounts(search) {
  return generalRequest("/api/gameUser/list", { search: search });
}

// 保存账户资料，返回操作结果。
export function saveProfile(profile) {
  return generalRequest("/api/gameUser/save", profile);
}

// 创建账户，返回操作结果。
export function createAccount(profile) {
  return generalRequest("/api/gameUser/create", profile);
}

// 删除指定游戏用户，返回操作结果。
export function deleteAccount(userId) {
  return generalRequest("/api/gameUser/delete", { userId: userId });
}

// 获取当前账户的个人资料。
export function getProfile() {
  return generalRequest("/api/gameUser/profile", {});
}

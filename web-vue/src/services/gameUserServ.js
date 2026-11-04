import { generalRequest, writeRequest } from "./genServ.js";

// 查询账户列表，参数是账号或昵称
export function getAccounts(search) {
  return generalRequest("/api/gameUser/list", { search: search });
}

// 保存账户资料，返回操作结果
export function saveProfile(profile) {
  return generalRequest("/api/gameUser/save", profile);
}

// 创建账户，返回操作结果
export function createAccount(profile) {
  return generalRequest("/api/gameUser/create", profile);
}

// 删除指定游戏用户，返回操作结果
export function deleteAccount(userId) {
  return writeRequest("/api/gameUser/delete", { userId: userId }, "records");
}

// 获取当前账户的个人资料
export function getProfile() {
  return generalRequest("/api/gameUser/profile", {});
}

// 管理员修改资料与角色使用专用接口，普通资料保存不具备改角色的能力
export function saveAccount(profile) {
  return generalRequest("/api/gameUser/adminSave", profile);
}

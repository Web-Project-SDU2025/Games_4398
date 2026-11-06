import { generalRequest, writeRequest } from "./genServ.js";

// 管理员按账户与日期分页读取流水，查询过程保留原账本
export function getAdminBlockcoin(filters) {
  return generalRequest("/api/admin/blockcoin/list", filters);
}

// 发放或收回字块币，请求编号用于识别网络重试
export function adjustBlockcoin(values) {
  return generalRequest("/api/admin/blockcoin/adjust", values);
}

// 管理员读取指定账户实际持有的游戏
export function getAccountLibrary(userId) {
  return generalRequest("/api/admin/library/list", { userId: userId });
}

// 撤回游戏只改变归属，原购买金额与账本由后端保留
export function revokeAccountGame(values) {
  return generalRequest("/api/admin/library/revoke", values);
}

// 管理员按账号或游戏名查询记录与分数
export function getAdminRecords(search) {
  return generalRequest("/api/admin/records/list", { search: search });
}

// 新建或修改记录，编号存在时修改，不存在时新建
export function saveAdminRecord(record) {
  return writeRequest("/api/admin/records/save", record, "records");
}

// 管理员隐藏自己的列表条目，玩家记录与结算继续保留
export function hideAdminRecord(recordId) {
  return writeRequest(
    "/api/admin/records/hide",
    { recordId: recordId },
    "records",
  );
}

// 恢复当前管理员隐藏的条目，玩家自己的显示状态继续保留
export function restoreAdminRecord(recordId) {
  return writeRequest(
    "/api/admin/records/restore",
    { recordId: recordId },
    "records",
  );
}

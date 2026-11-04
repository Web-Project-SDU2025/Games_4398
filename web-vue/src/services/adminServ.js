import { generalRequest, writeRequest } from "./genServ.js";

// 管理员按账号或游戏名查询记录与分数
export function getAdminRecords(search) {
  return generalRequest("/api/admin/records/list", { search: search });
}

// 新建或修改记录，编号存在时修改，不存在时新建
export function saveAdminRecord(record) {
  return writeRequest("/api/admin/records/save", record, "records");
}

// 删除记录，同时删除这次游玩对应的分数
export function deleteAdminRecord(recordId) {
  return writeRequest(
    "/api/admin/records/delete",
    { recordId: recordId },
    "records",
  );
}

import { generalRequest } from "./genServ.js";
import { useAppStore } from "../stores/app.js";

// 管理员按账号或游戏名查询记录与分数
export function getAdminRecords(search) {
  return generalRequest("/api/admin/records/list", { search: search });
}

// 新建或修改记录，编号存在时修改，不存在时新建
export async function saveAdminRecord(record) {
  const result = await generalRequest("/api/admin/records/save", record);
  if (result.code === 0) {
    useAppStore().dataChanged("records");
  }
  return result;
}

// 删除记录，同时删除这次游玩对应的分数
export async function deleteAdminRecord(recordId) {
  const result = await generalRequest("/api/admin/records/delete", {
    recordId: recordId,
  });
  if (result.code === 0) {
    useAppStore().dataChanged("records");
  }
  return result;
}

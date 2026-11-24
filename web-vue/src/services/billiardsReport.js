import { generalRequest } from "./genServ.js";

// 读取当前账号由服务端完成结算的最近一盘人机战报
export function getBilliardsReport() {
  return generalRequest("/api/gameplay/billiards/report", {});
}

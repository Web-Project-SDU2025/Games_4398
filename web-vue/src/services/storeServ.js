import { generalRequest, writeRequest } from "./genServ.js";

// 读取当前账户的商城、余额和最近流水
export function getStore() {
  return generalRequest("/api/store/catalog", {});
}

// 后端按商品价格扣款，成功后更新游戏库缓存
export function purchaseGame(gameId) {
  return writeRequest("/api/store/purchase", { gameId: gameId }, "games");
}

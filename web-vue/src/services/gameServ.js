import { generalRequest, writeRequest } from "./genServ.js";
import { useAppStore } from "../stores/app.js";

// 获取游戏列表、游戏库或收藏，参数包括列表类型和查询文字
export function getGames(mode, search) {
  return generalRequest("/api/game/list", { mode: mode, search: search });
}

// 保存游戏资料，返回保存结果
export function saveGame(game) {
  return writeRequest("/api/game/save", game, "games");
}

// 管理员确认后删除游戏及关联记录，同时使游戏和记录缓存过期，切号后保留新账户版本
export async function deleteGame(gameId) {
  const store = useAppStore();
  const token = store.userInfo.token;
  const result = await writeRequest(
    "/api/game/delete",
    { gameId: gameId, removeRelated: true },
    "games",
  );
  if (result.code === 0 && token === store.userInfo.token) {
    store.dataChanged("records");
  }
  return result;
}

// 修改指定游戏的收藏状态，added 表示收藏或取消收藏
export function changeFavorite(gameId, added) {
  return writeRequest(
    "/api/game/collection",
    {
      gameId: gameId,
      mode: "favorites",
      added: added,
    },
    "games",
  );
}

// 获取游戏记录，返回当前用户可查看的记录
export function getRecords() {
  return generalRequest("/api/game/records", {});
}

// 保存一次游玩的游戏、分数和时长
export function saveRecord(record) {
  return writeRequest("/api/game/recordSave", record, "records");
}

// 隐藏当前账户的记录条目，统计数据继续由原记录提供
export function hideRecord(recordId) {
  return writeRequest(
    "/api/game/recordHide",
    { recordId: recordId },
    "records",
  );
}

// 恢复当前账户隐藏的记录，记录列表和最近游玩共用更新版本
export function restoreRecord(recordId) {
  return writeRequest(
    "/api/game/recordRestore",
    { recordId: recordId },
    "records",
  );
}

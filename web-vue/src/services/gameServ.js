import { generalRequest } from "./genServ";

// 获取游戏列表、游戏库或收藏，参数包括列表类型和查询文字。
export function getGames(mode, search) {
  return generalRequest("/api/game/list", { mode: mode, search: search });
}

// 保存游戏资料，返回保存结果。
export function saveGame(game) {
  return generalRequest("/api/game/save", game);
}

// 删除指定游戏，返回删除结果。
export function deleteGame(gameId) {
  return generalRequest("/api/game/delete", { gameId: gameId });
}

export function changeCollection(gameId, mode, added) {
  return generalRequest("/api/game/collection", {
    gameId: gameId,
    mode: mode,
    added: added,
  });
}

// 获取游戏记录，返回当前用户可查看的记录。
export function getRecords() {
  return generalRequest("/api/game/records", {});
}

// 保存一次游玩的游戏、分数和时长。
export function saveRecord(record) {
  return generalRequest("/api/game/recordSave", record);
}

// 删除指定游戏记录，返回删除结果。
export function deleteRecord(recordId) {
  return generalRequest("/api/game/recordDelete", { recordId: recordId });
}

import { installedGameInfo } from "../logic/gameRuntime.js";
import { readRegisteredGames } from "../../games/runtime/web/game-registry.mjs";

// 读取已安装的游戏配置，任一配置错误时显示具体提示，读取过程只访问站内文件
export async function getInstalledGames() {
  const games = await readRegisteredGames(window.fetch.bind(window));
  const result = [];
  for (const game of games) {
    result.push(installedGameInfo(game));
  }
  return result;
}

import { GAME_ID_PATTERN } from "../../games/runtime/web/game-manifest.mjs";
import {
  gameOwner,
  PLAYER_PAGE,
} from "../../games/runtime/web/game-session.mjs";

// 已校验的配置转换成列表资料，启动器与列表读取共用字段映射
export function installedGameInfo(game) {
  return {
    id: game.id,
    name: game.name,
    num: game.number,
    category: game.category,
    description: game.description,
    gamePath: "/games/" + game.id + "/game.json",
    cover: "/games/" + game.id + "/" + game.cover,
    capabilities: game.capabilities,
    runtime: game.runtime,
  };
}

// 根据后端保存的入口匹配已安装游戏，只有登记的配置才显示平台启动按钮
export function findInstalledGame(games, path) {
  for (const game of games) {
    if (game.gamePath === path) {
      return game;
    }
  }
  return null;
}

// 商城、游戏库和收藏共用数据库资料，安装清单只补充封面与运行能力
export function launcherGames(catalog, installedGames) {
  const result = [];
  for (const record of catalog) {
    const installed = findInstalledGame(installedGames, record.gamePath);
    const entry = {
      gameId: record.gameId,
      id: "",
      gamePath: record.gamePath,
      name: record.name,
      category: record.category,
      description: record.description,
      cover: "/favicon.svg",
      capabilities: { editor: false },
      installed: false,
      runtime: "java17",
      owned: record.owned === true,
      favorite: record.favorite === true,
      price: record.price,
      reward: record.reward,
    };
    if (installed) {
      entry.id = installed.id;
      entry.cover = installed.cover;
      entry.capabilities = installed.capabilities;
      entry.installed = true;
      entry.runtime = installed.runtime;
    }
    result.push(entry);
  }
  return result;
}

// 生成当前账户的游戏入口，用户目录与游戏目录分别使用独立参数
export function gameAddress(
  game,
  owner,
  editor,
) {
  if (!game || !GAME_ID_PATTERN.test(game.id)) {
    throw new Error("游戏未登记");
  }
  const parameters = new URLSearchParams();
  parameters.set("game", game.id);
  parameters.set("owner", gameOwner(owner));
  if (editor) {
    if (!game.capabilities || !game.capabilities.editor) {
      throw new Error("此游戏尚未提供编辑器入口");
    }
    parameters.set("mode", "editor");
  }
  let player = PLAYER_PAGE;
  if (game.runtime === "html5") {
    player = "/games/native-player.html";
  }
  return player + "?" + parameters.toString();
}

import {
  GAME_REGISTRY,
  GAME_ID_PATTERN,
  normalizeManifest,
} from "../../games/runtime/web/game-manifest.mjs";

export { GAME_REGISTRY, GAME_ID_PATTERN };

// 将配置文件转换成统一游戏资料，入口固定交给站内 Java 启动器
export function normalizeGame(configuration, manifestPath) {
  const game = normalizeManifest(configuration, manifestPath);
  return {
    id: game.id,
    name: game.name,
    num: game.number,
    category: game.category,
    description: game.description,
    gamePath: manifestPath,
    cover: "/games/" + game.id + "/" + game.cover,
    capabilities: game.capabilities,
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

// 生成当前账户的游戏入口，用户目录与游戏目录分别使用独立参数
export function gameAddress(game, owner, editor) {
  if (!game || !GAME_ID_PATTERN.test(game.id)) {
    throw new Error("游戏未登记");
  }
  const parameters = new URLSearchParams();
  parameters.set("game", game.id);
  parameters.set("owner", String(owner));
  if (editor) {
    if (!game.capabilities || !game.capabilities.editor) {
      throw new Error("此游戏尚未提供编辑器入口");
    }
    parameters.set("mode", "editor");
  }
  return "/games/java-player.html?" + parameters.toString();
}

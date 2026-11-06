import {
  GAME_REGISTRY,
  GAME_ID_PATTERN,
  normalizeManifest,
} from "./game-manifest.mjs";

// 注册表中的地址限定在游戏发布目录，目录结构与构建输出一致
const GAME_PREFIX = "/games/";
const MANIFEST_SUFFIX = "/game.json";

// 读取并校验注册表中的所有地址，重复条目与无效路径直接指出配置问题
export async function readGameRegistry(fetchResource) {
  const response = await fetchResource(GAME_REGISTRY, { cache: "no-store" });
  if (!response.ok) {
    throw new Error("游戏注册表读取失败");
  }
  const registry = await response.json();
  if (!Array.isArray(registry)) {
    throw new Error("游戏注册表应为配置地址数组");
  }
  const addresses = [];
  for (const address of registry) {
    if (
      typeof address !== "string" ||
      !address.startsWith(GAME_PREFIX) ||
      !address.endsWith(MANIFEST_SUFFIX)
    ) {
      throw new Error("游戏配置路径格式不正确");
    }
    const id = address.substring(
      GAME_PREFIX.length,
      address.length - MANIFEST_SUFFIX.length,
    );
    if (!GAME_ID_PATTERN.test(id) || addresses.includes(address)) {
      throw new Error("游戏配置编号无效或重复");
    }
    addresses.push(address);
  }
  return addresses;
}

// 读取一份已登记的配置，平台列表与游戏启动器使用相同的字段校验
async function readManifest(address, fetchResource) {
  const response = await fetchResource(address, { cache: "no-store" });
  if (!response.ok) {
    throw new Error("游戏配置读取失败：" + address);
  }
  const source = await response.json();
  return normalizeManifest(source, address);
}

// 返回全部已登记游戏的校验结果，配置较少时按清单顺序逐项读取
export async function readRegisteredGames(fetchResource) {
  const addresses = await readGameRegistry(fetchResource);
  const games = [];
  for (const address of addresses) {
    const game = await readManifest(address, fetchResource);
    games.push(game);
  }
  return games;
}

// 只加载当前选中的已登记游戏，直接访问未知编号时返回明确提示
export async function readRegisteredGame(id, fetchResource) {
  if (typeof id !== "string" || !GAME_ID_PATTERN.test(id)) {
    throw new Error("游戏编号格式不正确");
  }
  const addresses = await readGameRegistry(fetchResource);
  const address = GAME_PREFIX + id + MANIFEST_SUFFIX;
  if (!addresses.includes(address)) {
    throw new Error("此游戏尚未登记");
  }
  return readManifest(address, fetchResource);
}

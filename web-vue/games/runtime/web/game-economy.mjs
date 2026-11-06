// 平台登录资料在浏览器中的存储键名
const SESSION_KEY = "gameApp";
// 商城授权接口前缀，游戏事件统一由 game-play.mjs 维护
const STORE_PREFIX = "/api/store/";

// 读取当前账户凭证，运行期间使用开局时的身份，切号后由新页面重新授权
export function gameIdentity(storage) {
  const saved = JSON.parse(storage.getItem(SESSION_KEY) || "{}");
  if (!saved.userInfo || !saved.userInfo.loggedIn || !saved.userInfo.token) {
    throw new Error("请登录后从平台启动游戏");
  }
  return { token: saved.userInfo.token, userId: saved.userInfo.id };
}

// 使用平台 POST 格式查询商品授权，金额与持有关系由后端判断
export async function economyRequest(action, values, identity, fetchResource) {
  const response = await fetchResource(STORE_PREFIX + action, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer " + identity.token,
    },
    body: JSON.stringify({ data: values }),
  });
  if (!response.ok) {
    throw new Error("游戏授权服务暂时无法访问");
  }
  return response.json();
}

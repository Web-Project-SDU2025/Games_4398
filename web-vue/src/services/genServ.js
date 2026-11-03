import axios from "axios";
import { useAppStore } from "../stores/app";

// 统一发送业务请求，url 是接口地址，data 是业务参数，返回服务器响应内容
export async function generalRequest(url, data) {
  // 所有业务接口共用同一种请求结构和登录凭证，避免每个页面重复拼装
  const body = { data: data };
  const options = { headers: getAuthHeader() };
  const response = await axios.post(url, body, options);
  return response.data;
}

// 返回当前登录凭证，请求时读取可以避免使用上一个账户的令牌
export function getAuthHeader() {
  const store = useAppStore();
  return { Authorization: "Bearer " + store.userInfo.token };
}

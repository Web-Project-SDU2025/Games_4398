import axios from "axios";
import { getAuthHeader, generalRequest } from "./genServ.js";

const ANSWER_TIMEOUT = 65000;

// 查询真实后端配置状态，不为状态检查调用模型
export async function getAiStatus() {
  const result = await generalRequest("/api/ai/status", {});
  if (result.code !== 0) {
    throw new Error(result.msg || "无法读取百炼配置状态");
  }
  return result.data;
}

// 客服页面和宠物共用此接口，signal 用于停止等待，凭证只发给本项目后端
export async function askAi(conversationId, message, signal) {
  const body = {
    data: { conversationId: conversationId, message: message },
  };
  const options = {
    headers: getAuthHeader(),
    timeout: ANSWER_TIMEOUT,
    signal: signal,
  };
  try {
    const response = await axios.post("/api/ai/chat", body, options);
    const result = response.data;
    if (result.code !== 0) {
      throw new Error(result.msg || "百炼暂时无法回答，请重试");
    }
    if (!result.data || typeof result.data.reply !== "string") {
      throw new Error("百炼返回内容不完整，请重试");
    }
    return result.data.reply;
  } catch (error) {
    if (error.code === "ECONNABORTED") {
      throw new Error("回答超时，请稍后重试");
    }
    if (error.code === "ERR_NETWORK") {
      throw new Error("无法连接后端，请检查服务是否启动");
    }
    throw error;
  }
}

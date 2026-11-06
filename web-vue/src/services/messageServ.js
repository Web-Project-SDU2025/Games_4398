import { generalRequest } from "./genServ.js";

// 分页读取当前登录账户的消息，接收账户由后端认证身份决定
export function getMessages(filters) {
  return generalRequest("/api/messages/list", filters);
}

// 顶栏只读取未读数量，避免轮询重复下载正文
export function getUnreadMessages() {
  return generalRequest("/api/messages/unread", {});
}

// 标记当前账户的一条消息为已读
export function readMessage(messageId) {
  return generalRequest("/api/messages/read", { messageId: messageId });
}

// 标记操作开始前收到的消息为已读
export function readAllMessages() {
  return generalRequest("/api/messages/readAll", {});
}

// 管理员发布全站公告，操作编号供网络重试复用
export function publishAnnouncement(values) {
  return generalRequest("/api/admin/announcements/publish", values);
}

// 向管理员提交问题反馈，接收范围由服务端确定
export function submitFeedback(values) {
  return generalRequest("/api/messages/feedback", values);
}

// 修改当前账户有权编辑的公告或反馈
export function editMessage(values) {
  return generalRequest("/api/messages/edit", values);
}

// 管理员保存反馈处理决定和回复
export function saveFeedbackResponse(values) {
  return generalRequest("/api/messages/respond", values);
}

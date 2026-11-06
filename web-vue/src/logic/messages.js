import { ADMIN_PAGE_SIZE } from "./admin.js";
import { getErrorMessage } from "./account.js";

// 消息列表分页复用管理模块的默认值
export const MESSAGE_PAGE_SIZE = ADMIN_PAGE_SIZE;
// 顶栏未读轮询间隔，页面隐藏期间暂停请求
export const MESSAGE_POLL_MILLISECONDS = 30000;
// 标题和正文上限与后端消息规则对应
export const MESSAGE_TITLE_LIMIT = 80;
export const MESSAGE_CONTENT_LIMIT = 2000;
// 页码上限阻止极大偏移量占用数据库查询资源
export const MESSAGE_MAX_PAGE = 100000;
// 顶栏数量超过此值时采用加号标记
export const MESSAGE_BADGE_LIMIT = 99;
// 可选消息类型，列表和详情共用同一名称
export const MESSAGE_KINDS = [
  { value: "all", label: "全部消息" },
  { value: "account", label: "账户通知" },
  { value: "announcement", label: "公告" },
  { value: "feedback", label: "问题反馈" },
];
// 管理员回复反馈时可以选择的处理决定
export const FEEDBACK_DECISIONS = [
  { value: "will_fix", label: "会修复" },
  { value: "will_not_fix", label: "不会修复" },
  { value: "resolved", label: "已解决" },
];

// 将服务端类型转为中文标题，未知类型保留通用消息名称
export function messageKind(kind) {
  for (const item of MESSAGE_KINDS) {
    if (item.value === kind) {
      return item.label;
    }
  }
  return "消息";
}

// 将反馈处理状态转为中文文字
export function feedbackDecisionName(decision) {
  if (decision === "will_fix") {
    return "会修复";
  }
  if (decision === "will_not_fix") {
    return "不会修复";
  }
  if (decision === "resolved") {
    return "已解决";
  }
  return "待处理";
}

// 接口尚未载入时提示重新运行后端，其他错误继续显示实际原因
export function messageError(error) {
  if (
    error.response &&
    (error.response.status === 404 || error.response.status === 405)
  ) {
    return "请在 IDEA 中重启后端，启用消息系统";
  }
  if (
    error.response &&
    (error.response.status === 401 || error.response.status === 403)
  ) {
    return "当前登录已失效，请重新登录";
  }
  return getErrorMessage(error, "消息暂时无法读取，请稍后重试");
}

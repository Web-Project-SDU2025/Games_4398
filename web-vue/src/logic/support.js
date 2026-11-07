// 单条问题最多字符数，与 AI 入口保持一致
export const QUESTION_LIMIT = 2000;
// 本机最多保存的对话数
const HISTORY_LIMIT = 20;
// 每段对话最多保存的消息数
const MESSAGE_LIMIT = 80;
// 首条问题自动生成标题时保留的字符数
const AUTO_TITLE_LIMIT = 20;
// 重命名对话时的标题最多字符数
export const TITLE_LIMIT = 40;
// 单条历史缩略图允许的字符数，历史只保存小图以控制浏览器存储占用
const PREVIEW_LIMIT = 60000;
// 每条历史保存的图片数量上限
const HISTORY_IMAGE_LIMIT = 4;

// 复制安全的图片缩略图，历史内容只接收 JPEG Data URL 和文件名
function imagePreviews(images) {
  const result = [];
  if (!Array.isArray(images)) {
    return result;
  }
  for (const image of images.slice(0, HISTORY_IMAGE_LIMIT)) {
    if (
      image &&
      typeof image.name === "string" &&
      typeof image.preview === "string" &&
      image.preview.length <= PREVIEW_LIMIT &&
      /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(image.preview)
    ) {
      result.push({ name: image.name, preview: image.preview });
    }
  }
  return result;
}

// 每个账号使用自己的浏览器存储，提问时只发送当前问题和对话编号
function storageKey(userId) {
  return "4398-support-" + userId;
}

// 读取有效的会话结构，遇到旧数据或损坏内容时返回空列表
export function readConversations(userId) {
  try {
    const value = localStorage.getItem(storageKey(userId));
    const parsed = JSON.parse(value);
    const conversations = [];
    if (!Array.isArray(parsed)) {
      return conversations;
    }
    for (const item of parsed) {
      if (
        item &&
        typeof item.id === "string" &&
        typeof item.title === "string" &&
        Array.isArray(item.messages)
      ) {
        const messages = [];
        for (const message of item.messages) {
          if (
            message &&
            (message.role === "user" || message.role === "assistant") &&
            typeof message.content === "string"
          ) {
            const saved = { role: message.role, content: message.content };
            if (message.role === "user" && message.images) {
              saved.images = imagePreviews(message.images);
            }
            messages.push(saved);
          }
        }
        conversations.push({
          id: item.id,
          cloudId: readCloudId(item.cloudId),
          title: item.title,
          messages: messages.slice(-MESSAGE_LIMIT),
          multimodal: item.multimodal === true,
        });
      }
    }
    return conversations.slice(0, HISTORY_LIMIT);
  } catch (error) {
    return [];
  }
}

// 旧历史缺少云端编号时生成新编号，新编号开始独立上下文
function readCloudId(value) {
  if (typeof value === "string" && /^[0-9a-f-]{36}$/i.test(value)) {
    return value;
  }
  return crypto.randomUUID();
}

// 保存有限条会话，存储空间不足时让界面提示，当前对话仍可继续使用
export function saveConversations(userId, conversations) {
  if (conversations.length > HISTORY_LIMIT) {
    conversations.splice(HISTORY_LIMIT);
  }
  const recent = conversations.slice(0, HISTORY_LIMIT);
  localStorage.setItem(storageKey(userId), JSON.stringify(recent));
}

// 创建空会话，标题由首条提问生成
export function createConversation() {
  return {
    id: crypto.randomUUID(),
    cloudId: crypto.randomUUID(),
    title: "新对话",
    messages: [],
  };
}

// 只添加用户问题，回答必须等百炼接口成功后再添加
export function addQuestion(
  conversation,
  question,
  images,
) {
  const trimmed = question.trim();
  const text = trimmed.substring(0, QUESTION_LIMIT);
  const previews = imagePreviews(images);
  if (text === "" && previews.length === 0) {
    return false;
  }
  if (conversation.messages.length === 0) {
    conversation.title = text.substring(0, AUTO_TITLE_LIMIT);
    if (text === "") {
      conversation.title = "图片问题";
    }
  }
  const message = { role: "user", content: text };
  if (previews.length > 0) {
    message.images = previews;
  }
  conversation.messages.push(message);
  trimMessages(conversation);
  return true;
}

// 将已取得的百炼回复加入消息，并裁剪到历史条数上限
export function addReply(conversation, reply) {
  conversation.messages.push({
    role: "assistant",
    content: reply,
  });
  trimMessages(conversation);
}

// 将显示和存储的消息裁剪到上限，保留最近消息
function trimMessages(conversation) {
  if (conversation.messages.length > MESSAGE_LIMIT) {
    conversation.messages = conversation.messages.slice(-MESSAGE_LIMIT);
  }
}

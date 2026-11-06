import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import {
  MESSAGE_BADGE_LIMIT,
  MESSAGE_CONTENT_LIMIT,
  FEEDBACK_DECISIONS,
  MESSAGE_KINDS,
  MESSAGE_PAGE_SIZE,
  MESSAGE_TITLE_LIMIT,
  messageError,
  messageKind,
  feedbackDecisionName,
} from "../src/logic/messages.js";
import { assetOperation } from "../src/logic/admin.js";

// 读取实际 Vue 选项，消息请求由测试替身控制
function loadComponent(filename, services) {
  const url = new URL(filename, import.meta.url);
  const source = fs.readFileSync(url, "utf8");
  const start = source.indexOf("<script>") + 8;
  const end = source.indexOf("</script>", start);
  let script = source.substring(start, end);
  script = script.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  script = script.replace("export default", "const component =");
  script = script + "\nthis.component = component;";
  vm.createContext(services);
  vm.runInContext(script, services);
  const component = services.component;
  const state = component.data();
  for (const name of Object.keys(component.methods || {})) {
    state[name] = component.methods[name].bind(state);
  }
  state.$refs = {
    dialog: {
      // 测试只关心弹窗是否打开或关闭
      showModal: function showModal() {
        return;
      },
      // 关闭弹窗时不操作真实页面
      close: function close() {
        return;
      },
    },
  };
  state.events = [];
  // 收集弹窗发出的业务事件
  function emit(name) {
    state.events.push(name);
  }
  state.$emit = emit;
  return { component: component, state: state };
}

// 消息分类名称和分页大小复用固定配置，错误提示能识别旧后端
test("消息分类、分页与接口升级提示", function testMessageRules() {
  assert.equal(messageKind("account"), "账户通知");
  assert.equal(messageKind("announcement"), "公告");
  assert.equal(messageKind("feedback"), "问题反馈");
  assert.equal(feedbackDecisionName("pending"), "待处理");
  assert.equal(feedbackDecisionName("will_fix"), "会修复");
  assert.equal(feedbackDecisionName("will_not_fix"), "不会修复");
  assert.equal(feedbackDecisionName("resolved"), "已解决");
  assert.equal(messageKind("unknown"), "消息");
  assert.equal(MESSAGE_PAGE_SIZE, 20);
  assert.equal(MESSAGE_BADGE_LIMIT, 99);
  assert.equal(MESSAGE_TITLE_LIMIT, 80);
  assert.equal(MESSAGE_CONTENT_LIMIT, 2000);
  assert.match(messageError({ response: { status: 404 } }), /重启后端/);
  assert.match(messageError({ response: { status: 403 } }), /重新登录/);
  assert.match(messageError(new Error("网络故障")), /网络故障/);
});

// 消息列表只请求一页，阅读与全部已读刷新未读数量并隔离旧账户响应
test("消息查询、阅读和全部已读", async function testInboxActions() {
  const store = {
    userInfo: { id: 12, token: "admin-token", role: "ROLE_ADMIN" },
  };
  const calls = [];
  const requests = [];
  const responseRequests = [];
  let readResult = { code: 0 };
  let delayList = false;
  const pendingLists = [];
  let listResult = {
    code: 0,
    data: {
      messages: [
        {
          messageId: "announcement:one",
          kind: "announcement",
          title: "公告标题",
          senderName: "admin",
          createdAt: "2026-11-06T10:00:00Z",
          content: "公告正文",
          read: false,
        },
      ],
      total: 1,
      unreadCount: 1,
    },
  };
  const counter = {
    unread: 1,
    // 记录消息页要求刷新的未读状态
    refresh: async function refresh(force) {
      calls.push({ action: "refresh", force: force });
    },
  };
  // 返回测试控制的当前账户
  function useAppStore() {
    return store;
  }
  // 记录请求筛选，迟到响应可由测试单独完成
  async function getMessages(filters) {
    requests.push(filters);
    if (delayList) {
      return new Promise(waitForList);
    }
    return listResult;
  }
  // 保存列表请求的完成函数，测试按需要安排切号顺序
  function waitForList(resolve) {
    pendingLists.push(resolve);
  }
  // 模拟读取单条回执的服务
  async function readMessage(messageId) {
    calls.push({ action: "read", messageId: messageId });
    return readResult;
  }
  // 模拟全部已读服务
  async function readAllMessages() {
    calls.push({ action: "readAll" });
    return { code: 0 };
  }
  // 模拟管理员保存反馈决定和回复
  async function saveFeedbackResponse(values) {
    responseRequests.push(values);
    return {
      code: 0,
      data: {
        messageId: values.messageId,
        decision: values.decision,
        reply: values.reply,
        updatedAt: "2026-11-06T10:00:00Z",
      },
    };
  }
  // 模拟登录后的消息状态仓库
  function useMessageStore() {
    return counter;
  }
  const loaded = loadComponent("../src/views/Messages.vue", {
    MessageComposer: {},
    MESSAGE_PAGE_SIZE: MESSAGE_PAGE_SIZE,
    MESSAGE_MAX_PAGE: 100000,
    MESSAGE_CONTENT_LIMIT: MESSAGE_CONTENT_LIMIT,
    FEEDBACK_DECISIONS: FEEDBACK_DECISIONS,
    MESSAGE_KINDS: MESSAGE_KINDS,
    messageKind: messageKind,
    feedbackDecisionName: feedbackDecisionName,
    messageError: messageError,
    // 测试时间直接返回接口原文，时间格式逻辑由商城测试覆盖
    transactionTime: function transactionTime(value) {
      return value;
    },
    getMessages: getMessages,
    readMessage: readMessage,
    readAllMessages: readAllMessages,
    saveFeedbackResponse: saveFeedbackResponse,
    useAppStore: useAppStore,
    useMessageStore: useMessageStore,
  });
  const state = loaded.state;
  state.counter = counter;
  state.admin = true;
  state.selected = { kind: "announcement", messageId: "announcement:one" };
  assert.equal(loaded.component.computed.canEditSelected.call(state), true);
  state.selected = {
    kind: "feedback",
    senderId: 7,
    messageId: "feedback:7:one",
  };
  store.userInfo.id = 7;
  store.userInfo.role = "ROLE_GAME_USER";
  state.admin = false;
  assert.equal(loaded.component.computed.canEditSelected.call(state), true);
  store.userInfo.id = 12;
  store.userInfo.role = "ROLE_ADMIN";
  state.admin = true;
  assert.equal(loaded.component.computed.canEditSelected.call(state), false);
  state.selected = null;
  await state.query();
  assert.equal(requests.length, 1);
  assert.equal(requests[0].page, 1);
  assert.equal(requests[0].pageSize, 20);
  assert.equal(state.selected, null);
  const message = state.messages[0];
  await state.openMessage(message);
  assert.equal(message.read, true);
  assert.equal(calls[1].action, "read");
  assert.equal(calls[2].action, "refresh");
  await state.readAll();
  assert.equal(calls[3].action, "readAll");
  assert.equal(state.busy, false);
  assert.equal(state.selected, null);

  const resolvedFeedback = {
    messageId: "feedback:21:resolved",
    kind: "feedback",
    feedbackDecision: "resolved",
    adminReply: "问题已解决",
    read: true,
  };
  await state.openMessage(resolvedFeedback);
  assert.equal(state.replyDecision, "resolved");
  assert.equal(state.replyContent, "问题已解决");

  state.admin = true;
  state.selected = {
    messageId: "feedback:21:one",
    kind: "feedback",
    senderId: 21,
    read: true,
    feedbackDecision: "pending",
    adminReply: "",
  };
  state.replyDecision = "will_fix";
  state.replyContent = "**下个版本修复**";
  await state.respondToFeedback();
  assert.equal(responseRequests.length, 1);
  assert.equal(responseRequests[0].decision, "will_fix");
  assert.equal(state.selected.adminReply, "**下个版本修复**");
  assert.equal(state.selected.feedbackDecision, "will_fix");

  readResult = { code: 1, msg: "已读失败" };
  message.read = false;
  await state.openMessage(message);
  assert.equal(message.read, false);
  assert.equal(state.notice, "已读失败");

  delayList = true;
  listResult = { code: 0, data: { messages: [], total: 0, unreadCount: 0 } };
  const delayed = loaded.state.query();
  await Promise.resolve();
  store.userInfo.token = "next-account";
  pendingLists[0]({
    code: 0,
    data: { messages: [{ messageId: "old" }], total: 1 },
  });
  await delayed;
  assert.notEqual(state.messages[0].messageId, "old");
});

// 公告失败重试复用操作编号，标题或正文改变后重新生成编号
test("公告表单校验、幂等重试与成功清空", async function testMessageComposer() {
  const store = { userInfo: { token: "admin", role: "ROLE_ADMIN" } };
  const requests = [];
  // 返回测试切换控制的登录信息
  let succeed = false;
  // 返回测试控制的登录身份
  function useAppStore() {
    return store;
  }
  // 保存提交内容，并按测试阶段返回失败或成功
  async function publishAnnouncement(values) {
    requests.push({
      title: values.title,
      content: values.content,
      requestId: values.requestId,
    });
    if (!succeed) {
      throw new Error("网络故障");
    }
    return { code: 0, data: "announcement:12:one" };
  }
  // 公告和反馈编辑共用同一消息保存接口
  async function submitFeedback(values) {
    requests.push({
      title: values.title,
      content: values.content,
      requestId: values.requestId,
    });
    return { code: 0, data: "feedback:12:one" };
  }
  // 修改消息时记录编号和新正文
  async function editMessage(values) {
    requests.push({
      messageId: values.messageId,
      title: values.title,
      content: values.content,
    });
    return { code: 0, data: values.messageId };
  }
  const loaded = loadComponent("../src/components/MessageComposer.vue", {
    publishAnnouncement: publishAnnouncement,
    submitFeedback: submitFeedback,
    editMessage: editMessage,
    useAppStore: useAppStore,
    assetOperation: assetOperation,
    MESSAGE_TITLE_LIMIT: MESSAGE_TITLE_LIMIT,
    MESSAGE_CONTENT_LIMIT: MESSAGE_CONTENT_LIMIT,
    messageError: messageError,
  });
  const state = loaded.state;
  state.open("announcement");
  state.title = "周末活动";
  state.content = "欢迎参加";
  await state.save();
  assert.equal(state.notice, "网络故障");
  assert.equal(requests[0].title, "周末活动");
  succeed = true;
  await state.save();
  assert.equal(requests[0].requestId, requests[1].requestId);
  assert.equal(state.title, "");
  assert.equal(state.content, "");
  assert.equal(state.events.length, 1);
  state.open("announcement");
  state.title = "另一个标题";
  state.content = "另一个内容";
  await state.save();
  assert.notEqual(requests[1].requestId, requests[2].requestId);
  store.userInfo.token = "ordinary";
  store.userInfo.role = "ROLE_GAME_USER";
  state.open("announcement");
  assert.equal(requests.length, 3);
});

// 普通账户可提交反馈并修改自己提交的反馈正文
test(
  "反馈支持重试和发布后编辑",
  async function testFeedbackEdit() {
    const store = {
      userInfo: { id: 21, token: "player-token", role: "ROLE_GAME_USER" },
    };
    const requests = [];
    let failFirstRequest = true;
    // 返回当前提交账户的登录状态
    function useAppStore() {
      return store;
    }
    // 保留公告接口替身，反馈用例不会调用它
    async function publishAnnouncement() {
      return { code: 0 };
    }
    // 模拟反馈首次失败后重试成功
    async function submitFeedback(values) {
      requests.push({
        title: values.title,
        content: values.content,
        requestId: values.requestId,
      });
      if (failFirstRequest) {
        throw new Error("网络故障");
      }
      return { code: 0, data: "feedback:21:request" };
    }
    // 模拟提交者编辑已发送的反馈
    async function editMessage(values) {
      requests.push({
        messageId: values.messageId,
        title: values.title,
        content: values.content,
      });
      return { code: 0, data: values.messageId };
    }
    const loaded = loadComponent("../src/components/MessageComposer.vue", {
      publishAnnouncement: publishAnnouncement,
      submitFeedback: submitFeedback,
      editMessage: editMessage,
      useAppStore: useAppStore,
      assetOperation: assetOperation,
      MESSAGE_TITLE_LIMIT: MESSAGE_TITLE_LIMIT,
      MESSAGE_CONTENT_LIMIT: MESSAGE_CONTENT_LIMIT,
      messageError: messageError,
    });
    const state = loaded.state;
    state.open("feedback");
    state.title = "游戏商城";
    state.content = "**购买按钮**无法使用";
    await state.save();
    assert.equal(state.notice, "网络故障");
    failFirstRequest = false;
    await state.save();
    assert.equal(requests[0].requestId, requests[1].requestId);
    assert.equal(state.events.length, 1);

    const message = {
      messageId: "feedback:21:request",
      kind: "feedback",
      senderId: 21,
      title: "游戏商城",
      content: "**购买按钮**无法使用",
    };
    state.open("feedback", message);
    state.title = "游戏商城操作";
    state.content = "补充问题细节";
    await state.save();
    assert.equal(requests[2].messageId, message.messageId);
    assert.equal(requests[2].content, "补充问题细节");
  },
);

// 顶栏只显示未读数量，账户切换会丢弃旧轮询返回值
test("消息铃铛轮询按账户令牌隔离", async function testMessageBell() {
  const store = { userInfo: { loggedIn: true, token: "first" } };
  const requests = [];
  // 保存每次未读请求的完成函数
  function waitForUnread(resolve) {
    requests.push(resolve);
  }
  // 模拟单独进行的未读数量请求
  async function getUnreadMessages() {
    return new Promise(waitForUnread);
  }
  // 返回可切换的当前身份
  function useAppStore() {
    return store;
  }
  const sourceUrl = new URL("../src/stores/messages.js", import.meta.url);
  let source = fs.readFileSync(sourceUrl, "utf8");
  source = source.replace(/^import .*;\r?\n/gm, "");
  source = source.replace(
    "export const useMessageStore = defineStore",
    "const definition = defineStore",
  );
  source = source + "\nthis.definition = definition;";
  // 保留状态仓库声明，供测试创建一份独立实例
  function defineStore(name, definition) {
    return definition;
  }
  const context = {
    defineStore: defineStore,
    useAppStore: useAppStore,
    getUnreadMessages: getUnreadMessages,
    messageError: messageError,
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  const definition = context.definition;
  const state = definition.state();
  for (const name of Object.keys(definition.actions)) {
    state[name] = definition.actions[name].bind(state);
  }
  const original = state.refresh(false);
  store.userInfo.token = "second";
  const current = state.refresh(false);
  assert.equal(state.owner, "second");
  assert.equal(requests.length, 2);
  requests[0]({ code: 0, data: 99 });
  await Promise.resolve();
  requests[1]({ code: 0, data: 2 });
  await original;
  await current;
  assert.equal(state.unread, 2);
  assert.equal(state.notice, "");
});

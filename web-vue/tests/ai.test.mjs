import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import * as support from "../src/logic/support.js";
import * as pet from "../src/logic/pet.js";

// 读取共用聊天组件，网络由测试替身控制，不调用真实百炼
function loadChat(askAi) {
  const file = new URL("../src/components/SupportChat.vue", import.meta.url);
  const source = fs.readFileSync(file, "utf8");
  let script = source.substring(
    source.indexOf("<script>") + 8,
    source.indexOf("</script>")
  );
  script = script.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  script = script.replace("export default", "const component =");
  script = script + "\nthis.component = component;";
  const context = {
    support: support,
    askAi: askAi,
    AbortController: AbortController,
    crypto: crypto,
  };
  vm.createContext(context);
  vm.runInContext(script, context);
  const component = context.component;
  const state = component.data();
  state.conversation = support.createConversation();
  state.events = [];
  // 保存组件事件，用于确认停止和完成后都通知父级退出等待
  function emit(name, value) {
    state.events.push({ name: name, value: value });
  }
  // 测试没有页面滚动，保留一个明确的空替身
  function scrollMessages() {
    return;
  }
  // 模拟 Vue 在更新 DOM 后执行命名回调
  function nextTick(callback) {
    callback();
  }
  state.$emit = emit;
  state.$nextTick = nextTick;
  for (const name of Object.keys(component.methods)) {
    state[name] = component.methods[name].bind(state);
  }
  state.scrollMessages = scrollMessages;
  return { state: state, component: component };
}

// 慢请求期间再次点击发送不能重复插入问题，成功只添加真实回复
test("聊天等待期间防重复，成功后恢复输入", async function testChatSuccess() {
  let complete;
  let requests = 0;
  // 保存完成函数，模拟百炼仍在回答
  function waitForAnswer(resolve) {
    complete = resolve;
  }
  // 记录接口收到的内容和编号
  function askAi(id, question) {
    requests = requests + 1;
    assert.equal(id.length, 36);
    assert.equal(question, "五子棋规则");
    return new Promise(waitForAnswer);
  }
  const chat = loadChat(askAi).state;
  chat.question = "五子棋规则";
  const pending = chat.send();
  assert.equal(chat.pending, true);
  await chat.send();
  assert.equal(requests, 1);
  assert.equal(chat.conversation.messages.length, 1);
  complete("连成五子获胜");
  await pending;
  assert.equal(chat.pending, false);
  assert.equal(chat.question, "");
  assert.equal(chat.conversation.messages[1].content, "连成五子获胜");
});

// 停止和切换历史后，即使网络替身迟到，旧消息也不能写进新会话
test("停止或切换对话后丢弃迟到的模型回复", async function testChatCancellation() {
  let complete;
  let requestSignal;
  // 控制一次已经发出的请求何时返回
  function waitForAnswer(resolve) {
    complete = resolve;
  }
  // 测试故意忽略 abort，检查序号保护是否仍有效
  function askAi(id, question, signal) {
    requestSignal = signal;
    return new Promise(waitForAnswer);
  }
  const loaded = loadChat(askAi);
  const chat = loaded.state;
  chat.question = "游戏规则";
  const pending = chat.send();
  const previous = chat.conversation;
  const cloudId = previous.cloudId;
  chat.stop();
  assert.equal(requestSignal.aborted, true);
  assert.notEqual(previous.cloudId, cloudId);
  assert.equal(chat.pending, false);
  chat.conversation = support.createConversation();
  loaded.component.watch.conversation.call(chat, chat.conversation, previous);
  complete("不能出现的旧回复");
  await pending;
  assert.equal(previous.messages.length, 1);
  assert.equal(chat.conversation.messages.length, 0);
});

// 重试不复制问题，失败后换云端编号，避免云端重复接收同一轮上下文
test("回答失败后保留问题并支持重试", async function testChatRetry() {
  let failed = true;
  // 先模拟服务器繁忙，再返回正常回答
  async function askAi() {
    if (failed) {
      throw new Error("百炼服务较忙");
    }
    return "规则回答";
  }
  const chat = loadChat(askAi).state;
  const originalId = chat.conversation.cloudId;
  chat.question = "游戏规则";
  await chat.send();
  assert.equal(chat.pending, false);
  assert.equal(chat.failedQuestion, "游戏规则");
  assert.equal(chat.notice, "百炼服务较忙");
  assert.notEqual(chat.conversation.cloudId, originalId);
  failed = false;
  await chat.retry();
  assert.equal(chat.conversation.messages.length, 2);
  assert.equal(chat.failedQuestion, "");
});

// 存储和位置处理用实际模块，确认两个账号互不读取历史
test("账号历史隔离、旧数据兼容和宠物边界正确", function testStorageAndPet() {
  const values = new Map();
  // 在内存中替代浏览器存储
  function getItem(key) {
    return values.get(key);
  }
  // 只保存测试会话，不写入用户浏览器
  function setItem(key, value) {
    values.set(key, value);
  }
  globalThis.localStorage = { getItem: getItem, setItem: setItem };
  try {
    const conversation = support.createConversation();
    support.addQuestion(conversation, "游戏问题");
    support.saveConversations(1, [conversation]);
    assert.equal(support.readConversations(1)[0].cloudId, conversation.cloudId);
    assert.equal(support.readConversations(2).length, 0);
    setItem(
      "4398-support-3",
      '[null,{"id":"old","title":"旧历史","messages":[null]}]'
    );
    assert.equal(support.readConversations(3)[0].messages.length, 0);
    assert.equal(support.readConversations(3)[0].cloudId.length, 36);
    const position = pet.clampPosition(-20, 900, 104, 78, 375, 667);
    assert.equal(position.left, 12);
    assert.equal(position.top, 577);
    pet.saveSettings(1, { visible: false, greetings: false });
    assert.equal(pet.readSettings(1).visible, false);
    assert.equal(pet.readSettings(2).visible, true);
  } finally {
    delete globalThis.localStorage;
  }
});

import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import * as account from "../src/logic/account.js";
import * as navigation from "../src/logic/navigation.js";
import * as forms from "../src/logic/forms.js";
import * as support from "../src/logic/support.js";

// 提问只创建用户消息，真实回答由接口成功后添加，编辑记录不修改原对象
test("客服不再使用本地问答，消息和记录编辑保持独立", function testSupportAndRecords() {
  const conversation = support.createConversation();
  assert.equal(support.addQuestion(conversation, "   "), false);
  assert.equal(support.addQuestion(conversation, "游戏记录怎么添加"), true);
  assert.equal(conversation.messages.length, 1);
  support.addReply(conversation, "百炼测试回复：游戏记录");
  assert.equal(conversation.messages.length, 2);
  assert.ok(conversation.messages[1].content.includes("游戏记录"));
  assert.equal(conversation.cloudId.length, 36);
  const original = {
    recordId: 1,
    userId: 2,
    gameId: 3,
    score: 10,
    durationSeconds: 30,
  };
  const edited = forms.createRecordForm(original);
  edited.score = 99;
  assert.equal(original.score, 10);
  assert.equal(edited.recordId, 1);
});

// 反复刷新只保留最新动画，减少动态效果时使用更短的纯淡化
test("刷新动画支持取消、卸载和减少动态效果", function testMotion() {
  let reduced = false;
  const calls = [];
  // 返回项目使用的动画参数，不读取真实浏览器样式
  function getPropertyValue(name) {
    const values = {
      "--dur-short": "150ms",
      "--dur-reduced": "100ms",
      "--ease-out": "linear",
    };
    return values[name];
  }
  // 返回测试样式对象
  function getComputedStyle() {
    return { getPropertyValue: getPropertyValue };
  }
  // 模拟系统是否减少动态效果
  function matchMedia() {
    return { matches: reduced };
  }
  // 记录透明度关键帧和时长，取消时标记状态
  function animate(frames, options) {
    const record = { frames: frames, options: options, cancelled: false };
    // 同一元素再次刷新时取消前一个动画
    function cancel() {
      record.cancelled = true;
    }
    record.cancel = cancel;
    calls.push(record);
    return record;
  }
  // 当前没有后台请求，允许播放数据完成后的淡入
  function getAttribute() {
    return "false";
  }
  // 测试中的元素位于稳定页面，未参加路由过渡
  function closest() {
    return null;
  }
  const sourceUrl = new URL("../src/tools/motion.js", import.meta.url);
  let source = fs.readFileSync(sourceUrl, "utf8");
  source = source.replace(/export /g, "");
  source = source + "\nthis.directive = revealDirective;";
  const context = {
    getComputedStyle: getComputedStyle,
    document: { documentElement: {} },
    window: { matchMedia: matchMedia },
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  const element = {
    animate: animate,
    isConnected: true,
    getAttribute: getAttribute,
    closest: closest,
  };
  context.revealContent(element);
  assert.equal(calls[0].options.duration, 150);
  reduced = true;
  context.revealContent(element);
  assert.equal(calls[0].cancelled, true);
  assert.equal(calls[1].options.duration, 100);
  assert.equal(calls[1].frames[0].transform, undefined);
  context.directive.beforeUnmount(element);
  assert.equal(calls[1].cancelled, true);
  element.isConnected = false;
  context.revealContent(element);
  assert.equal(calls.length, 2);
});

// 读取列表页面的普通脚本，接口使用测试替身，不访问数据库
function loadListComponent(filename, services) {
  const url = new URL(filename, import.meta.url);
  const source = fs.readFileSync(url, "utf8");
  const start = source.indexOf("<script>") + "<script>".length;
  const end = source.indexOf("</script>", start);
  let script = source.substring(start, end);
  script = script.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  script = script.replace("export default", "const component =");
  script = script + "\nthis.component = component;";
  vm.createContext(services);
  vm.runInContext(script, services);
  return services.component;
}

// 手动控制请求完成时间，检查等待期间旧列表是否仍然存在
test("返回列表时保留原数据，请求失败也不清空已加载内容", async function testListRefresh() {
  let completeRequest;
  // 保存请求完成函数，测试可以模拟慢网络
  function waitForResponse(resolve) {
    completeRequest = resolve;
  }
  // 返回未完成的请求，检查列表等待时的状态
  function getGames() {
    return new Promise(waitForResponse);
  }
  // 版本来自状态仓库，测试不创建真实登录信息
  function useAppStore() {
    return { gameRevision: 0 };
  }
  const component = loadListComponent("../src/views/game/GameList.vue", {
    getGames: getGames,
    useAppStore: useAppStore,
  });
  const state = component.data();
  state.mode = "library";
  state.loaded = true;
  state.games = [{ gameId: 1, name: "保留的游戏" }];
  const request = component.methods.query.call(state);
  assert.equal(state.loading, true);
  assert.equal(state.loaded, true);
  assert.equal(state.games[0].name, "保留的游戏");
  completeRequest({ code: 1, msg: "测试失败" });
  await request;
  assert.equal(state.games[0].name, "保留的游戏");
  assert.equal(state.loading, false);
  const nextRequest = component.methods.query.call(state);
  completeRequest({ code: 0, data: [] });
  await nextRequest;
  assert.equal(state.loaded, true);
  assert.equal(state.games.length, 0);
});

// 初次激活、缓存返回、修改后失效和失败重试都使用实际列表方法验证
test("列表首次只读取一次，缓存未过期不刷新，失败后保留重试机会", async function testCacheRevision() {
  const store = { gameRevision: 0, recordRevision: 0 };
  let requests = 0;
  let failNext = false;
  // 使用同一对象模拟状态仓库中的版本变更
  function useAppStore() {
    return store;
  }
  // 记录真实查询次数，同时覆盖后端异常分支
  async function readList() {
    requests = requests + 1;
    if (failNext) {
      throw new Error("测试网络故障");
    }
    return { code: 0, data: [{ gameId: 1, recordId: 1 }] };
  }
  const pages = [
    { filename: "../src/views/game/GameList.vue", revision: "gameRevision" },
    {
      filename: "../src/views/game/GameRecords.vue",
      revision: "recordRevision",
    },
  ];
  for (const page of pages) {
    const component = loadListComponent(page.filename, {
      getGames: readList,
      getRecords: readList,
      useAppStore: useAppStore,
    });
    const state = component.data();
    state.mode = "catalog";
    // Vue 会绑定方法，这里明确绑定以便直接执行同一段逻辑
    state.query = component.methods.query.bind(state);
    const before = requests;
    await component.activated.call(state);
    await component.activated.call(state);
    assert.equal(requests, before + 1);
    store[page.revision] = store[page.revision] + 1;
    failNext = true;
    await component.activated.call(state);
    assert.equal(requests, before + 2);
    assert.equal(state.loading, false);
    assert.ok(state.notice.includes("加载失败"));
    failNext = false;
    await component.activated.call(state);
    await component.activated.call(state);
    assert.equal(requests, before + 3);
    assert.equal(state.loadedRevision, store[page.revision]);
  }
});

// 路由过渡未结束时，提示内容变化不能再给同一区域播放第二层淡化
test("页面过渡期间不重复播放内容动画", function testRevealDuringPageEnter() {
  const sourceUrl = new URL("../src/tools/motion.js", import.meta.url);
  let source = fs.readFileSync(sourceUrl, "utf8");
  source = source.replace(/export /g, "");
  source = source + "\nthis.directive = revealDirective;";
  let plays = 0;
  // 如错误触发动画，记录调用次数
  function animate() {
    plays = plays + 1;
  }
  // 模拟祖先正在执行页面入场
  function closest() {
    return {};
  }
  const context = {};
  vm.createContext(context);
  vm.runInContext(source, context);
  const element = { closest: closest, animate: animate };
  context.directive.updated(element, { value: "新提示", oldValue: "" });
  assert.equal(plays, 0);
});

// 检查标签去重、关闭回退和首页保护，避免切换页面时产生重复或空标签
test("页面标签不会重复，关闭当前标签回到相邻页面", function testTabs() {
  const tabs = navigation.createHomeTabs();
  navigation.rememberTab(tabs, "/GameCatalog", "游戏列表");
  navigation.rememberTab(tabs, "/GameCatalog", "游戏列表");
  navigation.rememberTab(tabs, "/Password", "修改密码");
  assert.equal(tabs.length, 3);
  const next = navigation.removeTab(tabs, "/Password", "/Password");
  assert.equal(next, "/GameCatalog");
  navigation.removeTab(tabs, "/MainPage", "/GameCatalog");
  assert.equal(tabs[0].path, "/MainPage");
});

// 验证菜单递归查找和展开状态，父子菜单共用同一份标题来源
test("菜单标题和折叠状态正确", function testMenus() {
  const menus = [
    { id: 1, sList: [{ name: "MainPage", title: "网站概览" }] },
    { id: 2, sList: [] },
  ];
  assert.equal(navigation.findMenuTitle(menus, "MainPage"), "首页");
  const closed = [1];
  navigation.updateGroup(closed, 1, true);
  navigation.updateGroup(closed, 1, false);
  navigation.updateGroup(closed, 1, false);
  assert.deepEqual(closed, [1]);
});

// 不增加密码强度限制，短密码仍允许，错误输入和长度超限会返回说明
test("密码只检查必要规则和最大长度", function testPasswords() {
  assert.equal(account.checkPasswordChange("old", "1", "1"), "");
  assert.notEqual(account.checkPasswordChange("", "1", "1"), "");
  assert.notEqual(account.checkPasswordChange("old", "old", "old"), "");
  assert.notEqual(account.checkPasswordChange("old", "1", "2"), "");
  const tooLong = "1".repeat(account.CHANGE_PASSWORD_LIMIT + 1);
  assert.notEqual(account.checkPasswordChange("old", tooLong, tooLong), "");
});

// 编辑表单必须与原对象分开，否则点击取消也可能改变列表文字
test("游戏、账户和树节点编辑不修改原对象", function testCopies() {
  const original = {
    gameId: 1,
    userId: 1,
    id: 1,
    name: "原名称",
    title: "原标题",
  };
  const game = forms.createGameForm(original);
  const user = forms.createAccountForm(original);
  const node = forms.createNodeForm(original);
  game.name = "已修改";
  user.name = "已修改";
  node.title = "已修改";
  assert.equal(original.name, "原名称");
  assert.equal(original.title, "原标题");
  assert.equal(forms.createRoleIds(true, true), "1,2");
  assert.equal(forms.createRoleIds(false, false), "");
});

// 在内存中提供接口替身，验证验证码和注册流程，不创建真实账户
test("注册成功后清空密码，验证码失败可以重试", async function testRegistration() {
  let failCaptcha = false;
  let request = null;
  // 返回固定验证码，失败时模拟网络中断
  async function getValidateCode() {
    if (failCaptcha) {
      throw new Error("测试网络故障");
    }
    return { validateCodeId: 5, img: "test-image" };
  }
  // 记录请求内容，确认没有发送界面状态和管理员角色
  async function registerUser(registration) {
    request = registration;
    return { code: 0 };
  }
  const sourceUrl = new URL("../src/logic/auth.js", import.meta.url);
  let source = fs.readFileSync(sourceUrl, "utf8");
  source = source.replace(/^import .*;\r?\n/gm, "");
  source = source.replace(/export /g, "");
  const context = {
    getValidateCode: getValidateCode,
    registerUser: registerUser,
    createRegistration: account.createRegistration,
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  const form = context.createLoginForm();
  form.username = "test";
  form.password = "1";
  form.confirmPassword = "1";
  form.mode = "register";
  await context.registerAccount(form);
  assert.equal(request.password, "1");
  assert.equal(request.submitting, undefined);
  assert.equal(request.role, undefined);
  assert.equal(form.mode, "login");
  assert.equal(form.password, "");
  assert.equal(form.validateCodeId, 5);
  failCaptcha = true;
  await context.refreshCaptcha(form);
  assert.equal(form.captchaLoading, false);
  assert.equal(form.captchaImage, "");
  failCaptcha = false;
  await context.refreshCaptcha(form);
  assert.equal(form.captchaImage, "test-image");
});

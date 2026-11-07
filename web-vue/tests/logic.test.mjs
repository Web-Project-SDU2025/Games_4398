import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import * as account from "../src/logic/account.js";
import * as navigation from "../src/logic/navigation.js";
import * as forms from "../src/logic/forms.js";
import * as support from "../src/logic/support.js";
import * as limits from "../src/logic/limits.js";
import * as dashboard from "../src/logic/dashboard.js";
import { coinAmount } from "../src/logic/store.js";
import {
  outcomeName,
  recordDescription,
  visibleRecords,
  recordActionError,
} from "../src/logic/records.js";
import {
  ADMIN_PAGE_SIZES,
  ADMIN_SEARCH_LIMIT,
  blockcoinFilters,
} from "../src/logic/admin.js";

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
  services.limits = limits;
  services.GameLauncher = {};
  services.GamePlayer = {};
  services.BlockcoinHistory = {};
  services.DashboardAccount = {};
  services.MessageBell = {};
  services.BlockcoinAdjustment = {};
  services.AccountGameLibrary = {};
  services.outcomeName = outcomeName;
  services.recordDescription = recordDescription;
  services.visibleRecords = visibleRecords;
  services.recordActionError = recordActionError;
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

// 身份切为普通用户时立即停止显示管理页面，首页继续按登录状态显示
test("切号期间按新角色阻止重建管理员页面", function testRoleRenderDuringSwitch() {
  const component = loadListComponent("../src/components/Navi.vue", {
    AccountSwitcher: {},
    Octorange: {},
    PointerTrail: {},
    InterfaceSettings: {},
    BackgroundMusic: {},
    MessageComposer: {},
  });
  const state = {
    $route: { meta: { adminOnly: true } },
    isAdmin: true,
    loggedIn: true,
  };
  assert.equal(component.computed.canViewCurrentPage.call(state), true);
  state.isAdmin = false;
  assert.equal(component.computed.canViewCurrentPage.call(state), false);
  state.$route.meta = {};
  assert.equal(component.computed.canViewCurrentPage.call(state), true);
  state.loggedIn = false;
  assert.equal(component.computed.canViewCurrentPage.call(state), false);
});

// 顶栏快捷入口打开反馈表单，提交成功后显示状态
test(
  "顶栏快捷反馈直接打开反馈弹窗",
  function testQuickFeedbackEntry() {
    const component = loadListComponent("../src/components/Navi.vue", {
      AccountSwitcher: {},
      Octorange: {},
      PointerTrail: {},
      InterfaceSettings: {},
      BackgroundMusic: {},
      MessageComposer: {},
    });
    let closedMenu = false;
    let openedKind = "";
    const state = {
      switchNotice: "",
      // 记录顶栏反馈入口收起菜单的行为
      closeMenu: function closeMenu() {
        closedMenu = true;
      },
      $refs: {
        quickFeedback: {
          // 记录共用反馈弹窗收到的消息类型
          open: function open(kind) {
            openedKind = kind;
          },
        },
      },
    };

    component.methods.openQuickFeedback.call(state);
    assert.equal(closedMenu, true);
    assert.equal(openedKind, "feedback");
    component.methods.feedbackSent.call(state, {
      kind: "feedback",
      editing: false,
    });
    assert.equal(state.switchNotice, "问题反馈已发送");
  },
);

// 管理列表在切号后丢弃迟到响应，当前账号请求失败时保留列表并反馈结果
test("切号后丢弃账户和游戏记录的迟到响应", async function testManagementAccountRace() {
  const store = { userInfo: { token: "admin-token" } };
  let complete;
  let reject;
  // 返回测试当前身份，令牌由测试控制切换时间
  function useAppStore() {
    return store;
  }
  // 保存请求完成入口，测试分别安排失败响应和成功响应
  function holdRequest(resolve, rejectRequest) {
    complete = resolve;
    reject = rejectRequest;
  }
  // 创建等待中的管理请求，原列表在等待期间继续保留
  function readList() {
    return new Promise(holdRequest);
  }
  for (const page of [
    { filename: "../src/views/account/AccountManage.vue", field: "accounts" },
    { filename: "../src/views/account/RecordManage.vue", field: "records" },
  ]) {
    const component = loadListComponent(page.filename, {
      useAppStore: useAppStore,
      getAccounts: readList,
      getAdminRecords: readList,
      getErrorMessage: account.getErrorMessage,
      coinAmount: coinAmount,
      RECORD_NUMBER_LIMIT: limits.RECORD_NUMBER_LIMIT,
    });
    const state = component.data();
    state[page.field] = [{ id: 1 }];
    store.userInfo.token = "admin-token";
    const failed = component.methods.query.call(state);
    store.userInfo.token = "player-token";
    reject(new Error("Forbidden"));
    await failed;
    assert.equal(state.notice, "");
    assert.equal(state.loading, false);
    store.userInfo.token = "next-admin-token";
    const delayed = component.methods.query.call(state);
    store.userInfo.token = "player-token";
    complete({ code: 0, data: [{ id: 2 }] });
    await delayed;
    assert.equal(state[page.field][0].id, 1);
    const currentFailure = component.methods.query.call(state);
    reject(new Error("network failure"));
    await currentFailure;
    assert.notEqual(state.notice, "");
  }
});

// 筛选快速变更与账户切换都丢弃旧流水，查询过程使用独立参数副本
test("收支管理保留最新筛选并隔离账户响应", async function testLedgerRequestRace() {
  const store = { userInfo: { token: "admin-token" } };
  const pending = [];
  // 返回当前令牌，测试按响应顺序模拟切号
  function useAppStore() {
    return store;
  }
  // 为每次请求保存独立完成入口，模拟后发请求先返回
  function getAdminBlockcoin(filters) {
    // Promise 执行器只登记本次请求，响应时机由测试指定
    function rememberRequest(resolve) {
      pending.push({ resolve: resolve, filters: filters });
    }
    return new Promise(rememberRequest);
  }
  const component = loadListComponent(
    "../src/views/account/BlockcoinManage.vue",
    {
      useAppStore: useAppStore,
      getAdminBlockcoin: getAdminBlockcoin,
      blockcoinFilters: blockcoinFilters,
      ADMIN_PAGE_SIZES: ADMIN_PAGE_SIZES,
      ADMIN_SEARCH_LIMIT: ADMIN_SEARCH_LIMIT,
      coinAmount: coinAmount,
      // 时间格式由独立逻辑测试负责，此处保留输入便于验证请求顺序
      transactionTime: function testTime(value) {
        return value;
      },
    },
  );
  const state = component.data();
  const first = component.methods.query.call(state);
  state.filters.userId = 12;
  const second = component.methods.query.call(state);
  assert.equal(pending[0].filters.userId, "");
  assert.equal(pending[1].filters.userId, 12);
  const response = {
    code: 0,
    data: { entries: [{ entryId: "latest" }], total: 1, page: 1, pageSize: 20 },
  };
  pending[1].resolve(response);
  await second;
  pending[0].resolve({ code: 0, data: { entries: [] } });
  await first;
  assert.equal(state.entries[0].entryId, "latest");
  const third = component.methods.query.call(state);
  store.userInfo.token = "another-admin";
  pending[2].resolve({ code: 0, data: { entries: [] } });
  await third;
  assert.equal(state.entries[0].entryId, "latest");
});

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

// 概览返回时使用缓存，游戏或记录变化后刷新，错误时保留原概览并允许重试
test("首页按游戏和记录版本刷新概览，返回缓存时直接显示", async function testHomeCache() {
  const store = {
    userInfo: { id: 2, token: "test-token" },
    gameRevision: 0,
    recordRevision: 0,
  };
  let requests = 0;
  let failNext = false;
  // 模拟当前账户和共享的数据版本
  function useAppStore() {
    return store;
  }
  // 使用空列表校验概览零值，失败时模拟网络异常
  async function readList() {
    requests = requests + 1;
    if (failNext) {
      throw new Error("测试网络故障");
    }
    return { code: 0, data: [] };
  }
  // 安装清单独立于后端统计请求，空清单按游戏名称展示预览
  async function readInstalledGames() {
    return [];
  }
  const component = loadListComponent("../src/views/MainPage.vue", {
    useAppStore: useAppStore,
    getGames: readList,
    getRecords: readList,
    dashboard: dashboard,
    account: account,
    DashboardActivity: {},
    getInstalledGames: readInstalledGames,
    coinAmount: coinAmount,
  });
  const state = component.data();
  state.userInfo = store.userInfo;
  state.loadDashboard = component.methods.loadDashboard.bind(state);
  await component.activated.call(state);
  const original = state.summary;
  assert.equal(requests, 2);
  await component.activated.call(state);
  assert.equal(requests, 2);
  assert.equal(state.summary, original);
  store.recordRevision = 1;
  failNext = true;
  await component.activated.call(state);
  assert.equal(state.summary, original);
  assert.ok(state.error.includes("测试网络故障"));
  assert.equal(state.loading, false);
  failNext = false;
  await component.activated.call(state);
  assert.equal(state.loadedRecordRevision, 1);
  store.gameRevision = 1;
  await component.activated.call(state);
  assert.equal(state.loadedGameRevision, 1);
});

// 管理菜单替换旧入口并按固定顺序显示，普通用户的侧栏只保留个人功能
test("管理区按账户、收支和记录顺序排列", function testSidebarManagement() {
  const original = [
    {
      id: 1,
      title: "我的账户",
      sList: [{ id: 11, name: "MainPage", title: "首页" }],
    },
    {
      id: 2,
      title: "系统管理",
      sList: [
        { id: 21, name: "MenuManage", title: "菜单管理" },
        { id: 23, name: "GameUserManage", title: "用户管理" },
      ],
    },
  ];
  const admin = navigation.createSidebarGroups(original, true);
  assert.equal(admin[1].sList.length, 3);
  assert.equal(admin[1].sList[0].name, "AccountManage");
  assert.equal(admin[1].sList[1].name, "BlockcoinManage");
  assert.equal(admin[1].sList[2].name, "RecordManage");
  assert.equal(original[1].sList[1].name, "GameUserManage");
  const ordinary = navigation.createSidebarGroups(original, false);
  assert.equal(ordinary.length, 1);
  const fallback = navigation.createSidebarGroups([], true);
  assert.equal(fallback[0].sList[0].name, "AccountManage");
});

// 路由过渡期间使用现有淡化，提示内容变化复用该过渡
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

// 检查标签去重、关闭回退和首页保留规则，覆盖重复与缺失标签
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
test("游戏和账户编辑不修改原对象", function testCopies() {
  const original = {
    gameId: 1,
    userId: 1,
    id: 1,
    name: "原名称",
    title: "原标题",
    levelReward: 27,
  };
  const game = forms.createGameForm(original);
  assert.equal(game.levelReward, 27);
  game.levelReward = 0;
  const user = forms.createAccountForm(original);
  game.name = "已修改";
  user.name = "已修改";
  assert.equal(original.name, "原名称");
  assert.equal(original.title, "原标题");
  assert.equal(original.levelReward, 27);
  assert.equal(forms.createGameForm(null).levelReward, 0);
});

// 资料字段保留固定顺序与空值，长签名由组件按原文字展示
test("账户概览整理基本资料并保留原字段", function testProfileDetails() {
  const profile = {
    email: "player@example.test",
    phone: "",
    signature: "草坪守卫",
    createTime: "2026-11-06 10:00:00",
  };
  const details = account.profileDetails(profile);
  assert.equal(details[0].label, "邮箱");
  assert.equal(details[0].value, "player@example.test");
  assert.equal(details[1].value, "—");
  assert.equal(details[2].value, "2026-11-06 10:00:00");
  assert.equal(details[3].value, "—");
  assert.equal(profile.signature, "草坪守卫");
});

// 两个记录页面都保留隐藏项，开关决定显示范围，恢复请求由各自权限接口处理
test("记录列表显示隐藏项并恢复显示", async function testRestoreRecordDisplay() {
  const store = { userInfo: { token: "current" } };
  const restored = [];
  let records = [
    { recordId: 4, hidden: true },
    { recordId: 5, hidden: false },
  ];
  // 模拟当前登录账户，测试全过程保持同一令牌
  function useAppStore() {
    return store;
  }
  // 记录恢复请求并改变后端返回的显示标记
  async function restore(recordId) {
    restored.push(recordId);
    records[0].hidden = false;
    return { code: 0 };
  }
  // 返回包括隐藏项的完整列表，统计层和界面筛选各自读取
  async function readRecords() {
    return { code: 0, data: records };
  }
  for (const filename of [
    "../src/views/game/GameRecords.vue",
    "../src/views/account/RecordManage.vue",
  ]) {
    records = [
      { recordId: 4, hidden: true },
      { recordId: 5, hidden: false },
    ];
    const component = loadListComponent(filename, {
      useAppStore: useAppStore,
      getRecords: readRecords,
      getAdminRecords: readRecords,
      restoreRecord: restore,
      restoreAdminRecord: restore,
      RECORD_NUMBER_LIMIT: limits.RECORD_NUMBER_LIMIT,
    });
    const state = component.data();
    state.query = component.methods.query.bind(state);
    await state.query();
    assert.equal(component.computed.displayedRecords.call(state).length, 1);
    state.showHidden = true;
    assert.equal(component.computed.displayedRecords.call(state).length, 2);
    await component.methods.changeVisibility.call(
      state,
      state.records[0],
      false,
    );
    state.showHidden = false;
    assert.equal(component.computed.displayedRecords.call(state).length, 2);
    assert.equal(state.updating, null);
  }
  assert.deepEqual(restored, [4, 4]);
  assert.match(recordActionError({ response: { status: 404 } }), /重启后端/);
  assert.match(recordActionError({ response: { status: 403 } }), /重新登录/);
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

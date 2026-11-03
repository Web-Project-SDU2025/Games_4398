import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// 使用实际状态仓库和路由方法，后端只提供测试身份，不访问真实账号
function loadSession(profileRequest) {
  let storeDefinition;
  const notices = [];
  // 保存仓库定义，测试直接调用项目原有的方法
  function defineStore(name, definition) {
    storeDefinition = definition;
    return definition;
  }
  // 返回非空菜单，检查身份同步和菜单加载的先后关系
  async function getMenuList() {
    return [{ id: 1, title: "我的账户", sList: [] }];
  }
  const stateContext = {
    defineStore: defineStore,
    getProfile: profileRequest,
    getMenuList: getMenuList,
    localStorage: {},
  };
  let source = fs.readFileSync(
    new URL("../src/stores/app.js", import.meta.url),
    "utf8"
  );
  source = source.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  source = source.replace("export const useAppStore", "const useAppStore");
  vm.createContext(stateContext);
  vm.runInContext(source, stateContext);
  const store = storeDefinition.state();
  for (const method of Object.keys(storeDefinition.actions)) {
    store[method] = storeDefinition.actions[method].bind(store);
  }
  store.userInfo = {
    loggedIn: true,
    id: 1,
    username: "player",
    perName: "旧昵称",
    token: "current-token",
    role: "ROLE_GAME_USER",
  };
  // 路由与仓库使用同一个当前账号，模拟 Pinia 的读取方式
  function useAppStore() {
    return store;
  }
  // 路由定义只需记录守卫，不创建真实浏览器地址
  function createRouter() {
    // 测试直接调用守卫，注册时不执行导航
    function beforeEach() {
      return;
    }
    return { beforeEach: beforeEach };
  }
  // 路由测试不修改 Node 环境的地址
  function createWebHistory() {
    return {};
  }
  // 记录错误提示，网络失败时应让用户知道没有完成身份检查
  function ElMessage(options) {
    notices.push(options.message);
  }
  const routeContext = {
    useAppStore: useAppStore,
    createRouter: createRouter,
    createWebHistory: createWebHistory,
    ElMessage: ElMessage,
  };
  let routeSource = fs.readFileSync(
    new URL("../src/router/index.js", import.meta.url),
    "utf8"
  );
  routeSource = routeSource.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  routeSource = routeSource.replace("export default router;", "");
  vm.createContext(routeContext);
  vm.runInContext(routeSource, routeContext);
  return {
    store: store,
    checkAccess: routeContext.checkAccess,
    notices: notices,
  };
}

// 浏览器保存的普通用户身份不能覆盖后端已经生效的管理员身份
test("旧登录身份会同步为真实管理员，系统菜单正常进入", async function testPromotion() {
  // 返回管理员身份，不依赖菜单名称推断权限
  async function getProfile() {
    return {
      code: 0,
      data: {
        userId: 1,
        username: "player",
        name: "新昵称",
        role: "ROLE_ADMIN",
      },
    };
  }
  const session = loadSession(getProfile);
  const result = await session.checkAccess({
    path: "/MenuManage",
    meta: { adminOnly: true },
  });
  assert.equal(result, true);
  assert.equal(session.store.userInfo.role, "ROLE_ADMIN");
  assert.equal(session.store.userInfo.perName, "新昵称");
  await session.store.setNavi();
  assert.equal(session.store.systemConfig.naviList.length, 1);
});

// 浏览器即使残留管理员标记，普通用户也不能因此进入系统管理
test("后端取消管理员身份后，旧缓存不能继续放行", async function testDemotion() {
  // 模拟角色已由管理员降为普通游戏用户
  async function getProfile() {
    return {
      code: 0,
      data: {
        userId: 1,
        username: "player",
        name: "玩家",
        role: "ROLE_GAME_USER",
      },
    };
  }
  const session = loadSession(getProfile);
  session.store.userInfo.role = "ROLE_ADMIN";
  const result = await session.checkAccess({
    path: "/DictionaryManage",
    meta: { adminOnly: true },
  });
  assert.equal(result, "/MainPage");
  assert.equal(session.store.userInfo.role, "ROLE_GAME_USER");
});

// 身份请求未结束就切号时，迟到的响应不能改动新账号，也不能完成旧导航
test("身份同步期间切号不会串改角色", async function testAccountRace() {
  let complete;
  // 保存完成函数，测试主动安排切号和响应的顺序
  function waitForProfile(resolve) {
    complete = resolve;
  }
  // 返回未完成的身份请求
  function getProfile() {
    return new Promise(waitForProfile);
  }
  const session = loadSession(getProfile);
  const navigation = session.checkAccess({
    path: "/MenuManage",
    meta: { adminOnly: true },
  });
  session.store.userInfo = {
    loggedIn: true,
    id: 2,
    username: "other",
    perName: "另一个账号",
    token: "other-token",
    role: "ROLE_GAME_USER",
  };
  complete({
    code: 0,
    data: { userId: 1, username: "player", name: "旧账号", role: "ROLE_ADMIN" },
  });
  assert.equal(await navigation, false);
  assert.equal(session.store.userInfo.username, "other");
  assert.equal(session.store.userInfo.role, "ROLE_GAME_USER");
});

// 网络失败保留当前登录，但管理页面不能凭旧身份直接放行
test("身份读取失败给出提示并取消管理页面跳转", async function testIdentityFailure() {
  // 模拟后端暂时无法连接
  async function getProfile() {
    throw new Error("网络故障");
  }
  const session = loadSession(getProfile);
  session.store.userInfo.role = "ROLE_ADMIN";
  const result = await session.checkAccess({
    path: "/AccountManage",
    meta: { adminOnly: true },
  });
  assert.equal(result, false);
  assert.equal(session.notices.length, 1);
  assert.equal(session.store.userInfo.token, "current-token");
});

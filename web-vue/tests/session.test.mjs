import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// 使用实际状态仓库和路由方法，后端只提供测试身份，不访问真实账号
function loadSession(profileRequest, switchRequest) {
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
    switchAccount: switchRequest,
    getMenuList: getMenuList,
    localStorage: {},
  };
  let source = fs.readFileSync(
    new URL("../src/stores/app.js", import.meta.url),
    "utf8",
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
  function showToast(message) {
    notices.push(message);
  }
  const routeContext = {
    useAppStore: useAppStore,
    createRouter: createRouter,
    createWebHistory: createWebHistory,
    showToast: showToast,
  };
  let routeSource = fs.readFileSync(
    new URL("../src/router/index.js", import.meta.url),
    "utf8",
  );
  routeSource = routeSource.replace(/import[\s\S]*?from\s+"[^"]+";/g, "");
  routeSource = routeSource.replace("export default router;", "");
  vm.createContext(routeContext);
  vm.runInContext(routeSource, routeContext);
  return {
    store: store,
    checkAccess: routeContext.checkAccess,
    routes: vm.runInContext("routes", routeContext),
    notices: notices,
  };
}

// 独立管理页绑定真实路由守卫，商城仍向所有登录角色开放
test("游戏资料管理和商城按管理员权限分流", async function testGameManagementAccess() {
  let role = "ROLE_GAME_USER";
  // 返回当前服务器角色，使测试可以验证普通玩家和管理员的访问结果
  async function getProfile() {
    return {
      code: 0,
      data: { userId: 1, username: "player", name: "玩家", role: role },
    };
  }
  const session = loadSession(getProfile);
  // 按路径查找应用实际注册的管理路由
  function isManagementPage(route) {
    return route.path === "/GameManage";
  }
  // 按路径查找应用实际注册的商城路由
  function isStorePage(route) {
    return route.path === "/GameCatalog";
  }
  const manage = session.routes.find(isManagementPage);
  const store = session.routes.find(isStorePage);
  assert.ok(manage, "应注册独立的游戏资料管理路由");
  assert.equal(manage.props.mode, "catalog");
  assert.equal(await session.checkAccess(manage), "/MainPage");
  assert.equal(
    await session.checkAccess({ path: store.path, meta: store.meta || {} }),
    true,
  );
  role = "ROLE_ADMIN";
  assert.equal(await session.checkAccess(manage), true);
});

// 浏览器旧身份按后端当前管理员身份更新
test("旧登录身份会同步为真实管理员，系统菜单正常进入", async function testPromotion() {
  // 返回服务器管理员身份，权限按角色字段判断
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
    path: "/AccountManage",
    meta: { adminOnly: true },
  });
  assert.equal(result, true);
  assert.equal(session.store.userInfo.role, "ROLE_ADMIN");
  assert.equal(session.store.userInfo.perName, "新昵称");
  assert.equal(session.store.profile.name, "新昵称");
  await session.store.setNavi();
  assert.equal(session.store.systemConfig.naviList.length, 1);
});

// 浏览器残留管理员标记时，后端普通角色使管理导航返回首页
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
    path: "/BlockcoinManage",
    meta: { adminOnly: true },
  });
  assert.equal(result, "/MainPage");
  assert.equal(session.store.userInfo.role, "ROLE_GAME_USER");
});

// 身份请求期间切号后丢弃旧响应，原导航返回取消状态
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
    path: "/RecordManage",
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
  assert.equal(session.store.profile, null);
});

// 右上角切号后读取目标账户的完整资料并显示对应角色
test(
  "切换账户后重新读取目标账户个人资料",
  async function testSwitchLoadsProfile() {
    let selectedUsername = "";
    // 返回目标账户资料供切号后的身份刷新使用
    async function getProfile() {
      return {
        code: 0,
        data: {
          userId: 9,
          username: selectedUsername,
          name: "目标管理员",
          role: "ROLE_ADMIN",
          signature: "目标账户签名",
          email: "admin@example.com",
          phone: "12345678",
          createTime: "2026-11-01 10:00:00",
          lastLoginTime: "2026-11-06 10:00:00",
        },
      };
    }
    // 模拟目标账户令牌和权限菜单
    async function switchRequest(username, token) {
      assert.equal(username, "admin");
      assert.equal(token, "current-token");
      selectedUsername = username;
      return {
        session: {
          id: 9,
          username: username,
          perName: "目标管理员",
          token: "target-token",
          role: "ROLE_ADMIN",
        },
        menus: [{ id: 9, title: "系统管理", sList: [] }],
      };
    }
    const session = loadSession(getProfile, switchRequest);

    await session.store.switchAccount("admin");

    assert.equal(session.store.userInfo.username, "admin");
    assert.equal(session.store.profile.signature, "目标账户签名");
    assert.equal(session.store.profile.email, "admin@example.com");
    assert.equal(session.store.profile.createTime, "2026-11-01 10:00:00");
    assert.equal(session.store.profile.lastLoginTime, "2026-11-06 10:00:00");
  },
);

// 目标账户资料读取失败时恢复切号前的身份和资料
test(
  "切号资料读取失败时保留原账户",
  async function testSwitchRestoresPreviousAccount() {
    // 模拟读取目标账户资料时发生网络错误
    async function getProfile() {
      throw new Error("资料读取失败");
    }
    // 提供已签发的目标账户令牌和权限菜单
    async function switchRequest() {
      return {
        session: {
          id: 9,
          username: "admin",
          perName: "目标管理员",
          token: "target-token",
          role: "ROLE_ADMIN",
        },
        menus: [{ id: 9, title: "系统管理", sList: [] }],
      };
    }
    const session = loadSession(getProfile, switchRequest);
    const previousProfile = {
      username: "player",
      signature: "原账户签名",
    };
    session.store.profile = previousProfile;

    await assert.rejects(
      session.store.switchAccount("admin"),
      /资料读取失败/,
    );

    assert.equal(session.store.userInfo.username, "player");
    assert.equal(session.store.userInfo.token, "current-token");
    assert.equal(session.store.profile, previousProfile);
    assert.equal(session.store.systemConfig.naviList.length, 0);
  },
);

// 身份读取失败时保留登录，并取消当前管理页面导航
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

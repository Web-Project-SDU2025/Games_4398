import { ADMIN_PAGES } from "./admin.js";

// 右下角时钟的刷新间隔，单位为毫秒
export const CLOCK_INTERVAL = 1000;

// 根据玩家使用顺序分组，实际可见入口仍来自账户的菜单
const PLAYER_GROUPS = [
  { id: "play", title: "玩游戏", pages: ["MainPage", "GameCatalog", "GameLibrary"] },
  { id: "activity", title: "我的游玩", pages: ["GameFavorites", "GameRecords"] },
  { id: "account", title: "我的账户", pages: ["GameUserProfile", "Password"] },
];
const LEGACY_ADMIN_PAGES = ["MenuManage", "DictionaryManage", "GameUserManage"];

/** 根据路由名查找管理入口，角色判断在整理菜单时统一处理 */
function findAdminPage(name) {
  for (const page of ADMIN_PAGES) {
    if (page.name === name) {
      return page;
    }
  }
  return null;
}

/** 提取玩家入口，去掉原管理入口，其他菜单保留原有分组和顺序 */
function collectPlayerPages(items, playerPages) {
  const remaining = [];
  for (const item of items) {
    if (playerPages.has(item.name)) {
      playerPages.set(item.name, item);
    } else if (!findAdminPage(item.name) && !LEGACY_ADMIN_PAGES.includes(item.name)) {
      if (item.name) {
        remaining.push(item);
      } else if (item.sList) {
        const children = collectPlayerPages(item.sList, playerPages);
        if (children.length > 0) {
          remaining.push(Object.assign({}, item, { sList: children }));
        }
      }
    }
  }
  return remaining;
}

/** 将玩家高频入口置顶，并为管理员添加独立管理分组 */
export function createSidebarGroups(groups, isAdmin) {
  const playerPages = new Map();
  for (const group of PLAYER_GROUPS) {
    for (const name of group.pages) {
      playerPages.set(name, null);
    }
  }
  const remaining = [];
  for (const group of groups) {
    const items = collectPlayerPages(group.sList || [], playerPages);
    if (items.length > 0) {
      remaining.push({ id: group.id, title: group.title, sList: items });
    }
  }
  const result = [];
  for (const group of PLAYER_GROUPS) {
    const items = [];
    for (const name of group.pages) {
      const item = playerPages.get(name);
      if (item) {
        items.push(item);
      }
    }
    if (items.length > 0) {
      result.push({ id: group.id, title: group.title, sList: items });
    }
  }
  if (isAdmin) {
    result.push({ id: "management", title: "系统管理", sList: ADMIN_PAGES });
  }
  return result.concat(remaining);
}

/** 创建首页标签，每个账户使用独立数组保存打开状态 */
export function createHomeTabs() {
  return [{ path: "/MainPage", title: "游戏厅" }];
}

/** 保留原菜单地址，统一玩家页面名称 */
export function getMenuTitle(item) {
  if (item.name === "GameCatalog") {
    return "游戏商城";
  }
  if (item.name === "MainPage") {
    return "游戏厅";
  }
  return item.title;
}

/** 在菜单及子菜单中查找页面标题；找不到时返回空字符串 */
export function findMenuTitle(items, name) {
  if (name === "Messages") {
    return "消息中心";
  }
  if (name === "AiSupport") {
    return "AI 客服";
  }
  if (name === "AccountManage" || name === "GameUserManage") {
    return "账户管理";
  }
  const adminPage = findAdminPage(name);
  if (adminPage) {
    return adminPage.title;
  }
  for (const item of items) {
    if (item.name === name) {
      return getMenuTitle(item);
    }
    if (item.sList) {
      const title = findMenuTitle(item.sList, name);
      if (title !== "") {
        return title;
      }
    }
  }
  return "";
}

/** 添加或更新页面标签，同一地址只保留一个标签 */
export function rememberTab(
  tabs,
  path,
  title,
) {
  for (const tab of tabs) {
    if (tab.path === path) {
      tab.title = title;
      return;
    }
  }
  tabs.push({ path: path, title: title });
}

/** 删除指定标签，返回接下来应显示的地址；首页不允许关闭 */
export function removeTab(
  tabs,
  path,
  currentPath,
) {
  if (path === "/MainPage") {
    return currentPath;
  }
  for (let index = 0; index < tabs.length; index = index + 1) {
    if (tabs[index].path === path) {
      tabs.splice(index, 1);
      if (currentPath !== path) {
        return currentPath;
      }
      let previousIndex = index - 1;
      if (previousIndex < 0) {
        previousIndex = 0;
      }
      return tabs[previousIndex].path;
    }
  }
  return currentPath;
}

/** 记录分组展开状态，数组里只保存已折叠的分组编号 */
export function updateGroup(
  closed,
  id,
  open,
) {
  const index = closed.indexOf(id);
  if (open) {
    if (index !== -1) {
      closed.splice(index, 1);
    }
  } else if (index === -1) {
    closed.push(id);
  }
}

/** 使用北京时间格式化底部时钟，各电脑使用相同的显示时区 */
export function formatClock(date) {
  return date.toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour12: false,
  });
}

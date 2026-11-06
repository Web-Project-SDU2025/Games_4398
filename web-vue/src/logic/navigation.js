import { ADMIN_PAGES } from "./admin.js";

// 右下角时钟的刷新间隔，单位为毫秒
export const CLOCK_INTERVAL = 1000;

/** 复制菜单并整理管理入口，使用角色控制账户管理，缺少管理分组时创建该分组 */
export function createSidebarGroups(groups, isAdmin) {
  const result = [];
  let managementGroup = null;
  for (const group of groups) {
    const copied = { id: group.id, title: group.title, sList: [] };
    for (const item of group.sList) {
      if (
        item.name === "MenuManage" ||
        item.name === "DictionaryManage" ||
        item.name === "AccountManage" ||
        item.name === "GameUserManage" ||
        item.name === "BlockcoinManage" ||
        item.name === "RecordManage"
      ) {
        managementGroup = copied;
        continue;
      }
      if (item.name === "GameCatalog") {
        copied.sList.push({
          id: item.id,
          name: item.name,
          title: "游戏商城",
          sList: item.sList,
        });
      } else {
        copied.sList.push(item);
      }
    }
    if (copied.sList.length > 0) {
      result.push(copied);
    }
  }
  if (isAdmin) {
    if (!managementGroup || !result.includes(managementGroup)) {
      managementGroup = { id: "management", title: "系统管理", sList: [] };
      result.push(managementGroup);
    }
    managementGroup.sList = [];
    for (const page of ADMIN_PAGES) {
      managementGroup.sList.push({
        id: page.id,
        name: page.name,
        title: page.title,
      });
    }
  }
  return result;
}

/** 创建首页标签，每个账户使用独立数组保存打开状态 */
export function createHomeTabs() {
  return [{ path: "/MainPage", title: "首页" }];
}

/** 保留原菜单地址，仅统一首页标题 */
export function getMenuTitle(item) {
  if (item.name === "GameCatalog") {
    return "游戏商城";
  }
  if (item.name === "MainPage") {
    return "首页";
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
  for (const page of ADMIN_PAGES) {
    if (page.name === name) {
      return page.title;
    }
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
export function rememberTab(tabs, path, title) {
  for (const tab of tabs) {
    if (tab.path === path) {
      tab.title = title;
      return;
    }
  }
  tabs.push({ path: path, title: title });
}

/** 删除指定标签，返回接下来应显示的地址；首页不允许关闭 */
export function removeTab(tabs, path, currentPath) {
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
export function updateGroup(closed, id, open) {
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

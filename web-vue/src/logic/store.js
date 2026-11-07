// 商城范围选项集中维护，价格与持有关系读取后端结果
export const STORE_MODES = [
  { value: "all", label: "全部游戏" },
  { value: "available", label: "待发现" },
  { value: "owned", label: "已拥有" },
];

// 按搜索、分类和持有关系筛选商品，空输入显示全部匹配项
export function filterProducts(
  products,
  search,
  category,
  mode,
) {
  const result = [];
  const keyword = search.trim().toLowerCase();
  for (const game of products) {
    const text = (
      game.name +
      " " +
      game.category +
      " " +
      game.description
    ).toLowerCase();
    if (keyword && !text.includes(keyword)) {
      continue;
    }
    if (category && game.category !== category) {
      continue;
    }
    if (mode === "owned" && !game.owned) {
      continue;
    }
    if (mode === "available" && game.owned) {
      continue;
    }
    result.push(game);
  }
  return result;
}

// 分类来自商品表，重复分类只显示一次
export function productCategories(products) {
  const result = [];
  for (const game of products) {
    if (game.category && !result.includes(game.category)) {
      result.push(game.category);
    }
  }
  return result;
}

// 金额显示整数与千位分隔，尚未加载时显示空位
export function coinAmount(amount) {
  if (!Number.isFinite(amount)) {
    return "—";
  }
  return amount.toLocaleString("zh-CN", { maximumFractionDigits: 0 });
}

// 流水使用北京时间，损坏日期返回空值
export function transactionTime(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) {
    return "";
  }
  return date.toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour12: false,
  });
}

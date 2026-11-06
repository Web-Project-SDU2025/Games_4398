// 管理列表每页条数选项，服务端按自己的分页上限再次校验
export const ADMIN_PAGE_SIZES = [20, 50, 100];
// 管理列表默认每页条数
export const ADMIN_PAGE_SIZE = 20;
// 流水原因的搜索文字上限，与服务端管理查询规则对应
export const ADMIN_SEARCH_LIMIT = 100;
// 管理员资产操作的原因长度上限
export const ADMIN_REASON_LIMIT = 60;
// 单次字块币操作数量上限，与服务端余额上限对应
export const ADMIN_COIN_LIMIT = 2000000000;
// 管理导航按固定顺序排列，账户管理始终位于首项
export const ADMIN_PAGES = [
  { id: "account-management", name: "AccountManage", title: "账户管理" },
  {
    id: "blockcoin-management",
    name: "BlockcoinManage",
    title: "字块币收支管理",
  },
  { id: "record-management", name: "RecordManage", title: "游戏记录管理" },
];

// 创建独立收支筛选对象，表单变化后从第一页重新查询
export function blockcoinFilters() {
  return {
    userId: "",
    direction: "all",
    search: "",
    startDate: "",
    endDate: "",
    page: 1,
    pageSize: ADMIN_PAGE_SIZE,
  };
}

// 创建新的资产操作编号，失败重试保留此对象，修改请求内容时创建新编号
export function assetOperation(values, previous) {
  const signature = JSON.stringify(values);
  if (previous && previous.signature === signature) {
    return previous;
  }
  return { signature: signature, requestId: crypto.randomUUID() };
}

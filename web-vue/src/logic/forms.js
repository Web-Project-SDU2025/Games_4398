// 创建游戏编辑数据，逐项复制可以保证点击取消时不会改变列表里的对象
export function createGameForm(game) {
  const form = {
    num: "",
    name: "",
    category: "",
    description: "",
    gamePath: "",
  };
  if (game) {
    form.gameId = game.gameId;
    form.num = game.num;
    form.name = game.name;
    form.category = game.category;
    form.description = game.description;
    form.gamePath = game.gamePath;
  }
  return form;
}

// 创建账户编辑数据，只复制允许编辑的资料，避免把角色或密码意外覆盖
export function createAccountForm(account) {
  const form = {
    username: "",
    password: "",
    name: "",
    email: "",
    phone: "",
    signature: "",
    role: "ROLE_GAME_USER",
  };
  if (account) {
    form.userId = account.userId;
    form.username = account.username;
    form.name = account.name;
    form.email = account.email;
    form.phone = account.phone;
    form.signature = account.signature;
    form.role = account.role;
  }
  return form;
}

// 复制树节点的可编辑字段，不复制子节点，取消编辑不会改动已经显示的树
export function createNodeForm(node) {
  const form = {
    pid: null,
    id: null,
    value: "",
    title: "",
    parentTitle: "",
    userTypeIds: "",
  };
  if (node) {
    form.pid = node.pid;
    form.id = node.id;
    form.value = node.value;
    form.title = node.title;
    form.parentTitle = node.parentTitle;
    form.userTypeIds = node.userTypeIds;
  }
  return form;
}

// 将勾选的角色整理为后端使用的编号字符串，避免在页面中重复拼接
export function createRoleIds(admin, gameUser) {
  const roles = [];
  if (admin) {
    roles.push("1");
  }
  if (gameUser) {
    roles.push("2");
  }
  return roles.join(",");
}

// 独立复制记录表单，两个管理视图共用字段，取消时不改变原记录
export function createRecordForm(record) {
  const form = {
    userId: "",
    gameId: "",
    score: 0,
    durationSeconds: 0,
    playedAt: "",
  };
  if (record) {
    form.recordId = record.recordId;
    form.userId = record.userId;
    form.gameId = record.gameId;
    form.score = record.score;
    form.durationSeconds = record.durationSeconds;
    form.playedAt = record.playedAt;
  }
  return form;
}

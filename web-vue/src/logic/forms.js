// 创建独立游戏表单，逐项复制字段，取消时保留列表原值
export function createGameForm(game) {
  const form = {
    num: "",
    name: "",
    category: "",
    description: "",
    gamePath: "",
    levelReward: 0,
  };
  if (game) {
    form.gameId = game.gameId;
    form.num = game.num;
    form.name = game.name;
    form.category = game.category;
    form.description = game.description;
    form.gamePath = game.gamePath;
    if (Number.isInteger(game.levelReward)) {
      form.levelReward = game.levelReward;
    }
  }
  return form;
}

// 创建账户表单，编辑时复制允许修改的资料，密码使用空值
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

// 独立复制记录表单，取消编辑时保留原记录的分数、时长与时间
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

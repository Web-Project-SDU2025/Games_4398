import { QUESTION_LIMIT } from "./support.js";

// 网站操作资料集中维护，每次请求使用同一份简短说明
const SITE_OPERATION_STEPS = [
  "以下是 4398 当前的网站操作说明，用它回答本站功能问题；说明操作步骤时不要声称已经代替用户完成操作",
  "4398 提供游戏商城、我的游戏库、收藏、游玩记录、字块币足迹、消息中心和界面设置",
  "购买游戏：打开游戏商城，查看商品并确认购买；购买后游戏进入我的游戏库，账户不能主动移出游戏，只能取消收藏",
  "字块币：账户首次使用商城时获得 100 BC；植物大战僵尸售价 100 BC，每次完成一个官方关卡奖励 15 BC",
  "开始游玩：从我的游戏库打开已拥有的游戏；关卡编辑器仅管理员可用",
  "查看记录：个人游戏记录可以隐藏或恢复显示，隐藏不会删除记录，也不改变统计和奖励",
  "修改界面：登录后从侧栏界面设置调整背景音乐、音量与进度，鼠标尾迹，以及登录背景动画开关和速度",
  "查看通知：消息中心接收账户变更和公告；管理员可在消息中心发布公告",
  "管理员入口包括账户管理、字块币收支管理和游戏记录管理，普通用户不能使用管理操作",
  "Octorange 可以解释游戏和网站操作步骤，但不能查看账户私人资料，也不能代替用户执行购买、修改或管理操作",
];

// 为用户问题附加网站操作资料，超过接口上限时保留原问题
export function createQuestionWithSiteGuide(question) {
  const guide = SITE_OPERATION_STEPS.join("\n");
  const label = "用户本次问题：";
  const fullQuestion = guide + "\n\n" + label + question;
  if (fullQuestion.length > QUESTION_LIMIT) {
    return question;
  }
  return fullQuestion;
}

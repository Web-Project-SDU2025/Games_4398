const HUNGRY_AT = 34;
const TIRED_AT = 25;
const FAMILIAR_AT = 65;
export const PET_DEFAULT_NAME = "小橙";
export const THOUGHT_ROTATION_MS = 6500;
const IDLE_LINES = [
  "我喜欢窝在垫子上，刚好能看见整间小屋",
  "我听见你回来了，还想跟你待一会儿",
];

// 第一句说当前最需要的照顾，其余几句轮流提起对应的状态
export function idlePetThought(room, index) {
  const slot = index % 4;
  if (slot === 0) {
    return petThought(room, "");
  }
  if (slot === 1) {
    if (room) {
      return petThought(room, "bowl");
    }
    return "我刚把爪子舔干净，想去窗边看看";
  }
  return IDLE_LINES[slot - 2];
}

// 宠物根据饱腹、精力、粮仓和亲密度说话，所有句子都以它自己的口吻表达
export function petThought(room, topic) {
  if (topic === "sleep") {
    return "我先侧着躺一会儿，醒了再陪你玩";
  }
  if (topic === "wake") {
    return "我睡醒啦，伸个懒腰就起来";
  }
  if (topic === "tidy-bowl") {
    return "我去把食碗叼回老地方，免得下次找不着";
  }
  if (topic === "tidy-toy") {
    return "我去把球拨回垫子边，下次还能接着玩";
  }
  if (topic === "star") {
    return "我在窗上藏了一颗小星星，你找到啦！";
  }
  if (topic === "window") {
    return "我喜欢趴在窗边晒太阳，玻璃上好像还有个小秘密";
  }
  if (topic === "plant") {
    return "我看见仙人掌晃了一下，差点以为它在跟我打招呼";
  }
  if (topic === "lamp-lit") {
    return "我看见灯亮啦，这下连我的小爪子都暖洋洋的";
  }
  if (topic === "lamp-dark") {
    return "我喜欢灯关掉以后的安静，刚好可以眯一会儿";
  }
  if (topic === "lamp") {
    return "我喜欢那盏暖暖的小灯，夜里看到它就很安心";
  }
  if (topic === "clock") {
    return "我听见咔哒一声，分针又往前走了一格";
  }
  if (topic === "floor") {
    return "我刚在地板上踩出一串小脚印，一转头就找不着了";
  }
  if (topic === "bowl" && room) {
    if (room.food === 0) {
      return "我把猫粮都吃完了，粮仓里一份也没剩";
    }
    return "我数过啦，粮仓里还有 " + room.food + " 份猫粮";
  }
  if (!room) {
    return "我在这里等你回来";
  }
  if (topic === "pet") {
    return "我喜欢你这样摸摸我";
  }
  if (topic === "feed") {
    if (room.fullness < HUNGRY_AT) {
      return "我刚吃了一份，还想再来一点";
    }
    return "我吃了几口，肚子舒服多啦，想舔舔爪子再陪你玩";
  }
  if (topic === "rest") {
    return "我刚眯了一小会儿，现在精神多啦";
  }
  if (topic === "rug") {
    return "我最喜欢这块软垫，趴在这里刚刚好";
  }
  if (topic === "toy") {
    return "我刚眨了下眼，球就自己滚过去啦";
  }
  if (room.fullness <= HUNGRY_AT) {
    if (room.food === 0) {
      return "我有点饿啦，刚去看了一眼，粮仓也空了";
    }
    return "我有点饿了，吃点东西再陪你玩好吗？";
  }
  if (room.energy <= TIRED_AT) {
    return "我跑累啦，想先在垫子上眯一会儿";
  }
  if (topic === "play") {
    return "我把球又拨过来啦，还想跟你玩一会儿";
  }
  if (room.bond >= FAMILIAR_AT) {
    return "我听到动静就知道是你，想再和你待一会儿";
  }
  return "我现在精神不错，想和你玩一会儿";
}

// 饱腹值低于或等于此值时，优先提醒喂食
const HUNGRY_AT = 34;
// 精力低于或等于此值时，优先提醒休息
const TIRED_AT = 25;
// 亲密度达到此值且状态良好时，使用较亲近的表达
const FAMILIAR_AT = 65;
// 小屋加载中或旧接口未返回昵称时显示的名字
export const PET_DEFAULT_NAME = "小橙";
// 小屋和浮动桌宠空闲时换一句自言自语的间隔，单位为毫秒
export const THOUGHT_ROTATION_MS = 6500;
// 不涉及数值的闲聊句子，排在状态和库存提示之后
const IDLE_LINES = [
  "我喜欢窝在垫子上，刚好能看见整间小屋",
  "我听见你回来了，还想跟你待一会儿",
];

// 第一句说当前最需要的照顾，其余几句轮流提起对应的状态
export function idlePetThought(room, index) {
  const slots = 2 + IDLE_LINES.length;
  const slot = index % slots;
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
  if (topic === "window-morning") {
    return "我看见湖上发亮啦，想再晒一会儿太阳";
  }
  if (topic === "window-evening") {
    return "我看见湖对岸的灯一盏盏亮了，天也快黑啦";
  }
  if (topic === "window-night") {
    return "我想从山边那颗开始，先往低处连，再连到右上方";
  }
  if (topic === "curtain-closed") {
    return "我把窗帘拉上啦，小屋里安静多了";
  }
  if (topic === "chime") {
    return "我看见风铃晃起来了，还以为湖上的风吹进来了";
  }
  if (topic === "star-next") {
    return "我记住这一颗啦，下一颗在哪儿呢";
  }
  if (topic === "star-retry") {
    return "我好像连岔了，这回从山边那颗开始吧";
  }
  if (topic === "keepsake") {
    return "我把今晚的星空画成明信片啦，想和你一起留着";
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
    return "我把窗景留在刚选的时段，指针过一会儿就会自己走回去";
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

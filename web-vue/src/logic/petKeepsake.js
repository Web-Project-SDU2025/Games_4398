// 纪念明信片按账户分开保存，不使用会随登录改变的令牌
const KEEPSAKE_KEY = "4398.pet.first-starlight.";
// 星位按连线顺序排列，画布与触屏按钮共用这三个名称
export const PET_STARS = [
  { name: "star-ridge", label: "山边的星" },
  { name: "star-lake", label: "低处的星" },
  { name: "star-high", label: "高处的星" },
];

// 返回点选后的连线进度，重复点亮已连接的星不会打断连线
export function advancePetStars(progress, name) {
  for (let index = 0; index < PET_STARS.length; index = index + 1) {
    if (PET_STARS[index].name === name) {
      if (index === progress) {
        return progress + 1;
      }
      if (index > progress) {
        return 0;
      }
      break;
    }
  }
  return progress;
}

// 读取当前账户首次发现的日期和昵称，损坏的记录按未收藏处理
export function readPetKeepsake(storage, account) {
  if (!account) {
    return null;
  }
  try {
    const saved = storage.getItem(KEEPSAKE_KEY + account);
    const card = JSON.parse(saved);
    if (card && typeof card.nickname === "string" && typeof card.date === "string") {
      if (card.nickname && Number.isFinite(Date.parse(card.date))) {
        return card;
      }
    }
  } catch (error) {
    return null;
  }
  return null;
}

// 首次发现时留下当天的昵称和时间，再次连星不会覆盖纪念日期
export function savePetKeepsake(storage, account, nickname, date) {
  const saved = readPetKeepsake(storage, account);
  if (saved) {
    return saved;
  }
  const card = { nickname: nickname, date: date.toISOString() };
  if (account) {
    storage.setItem(KEEPSAKE_KEY + account, JSON.stringify(card));
  }
  return card;
}

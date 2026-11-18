<template>
  <section class="pet-room" aria-label="桌宠小屋">
    <section class="pet-room-stage" :aria-label="petName + '的房间'">
      <div ref="stage" class="pet-room-canvas" data-testid="pet-scene"></div>
      <div v-if="sceneFailed" class="pet-room-fallback">
        <img src="/images/pet/idling.png" width="360" height="270" :alt="petName" />
        <span>当前浏览器无法打开 3D 画面</span>
      </div>
      <p class="pet-room-thought" :key="thought">{{ thought }}</p>
      <div v-if="!sceneFailed" class="pet-room-camera" role="group" aria-label="观察视角">
        <button type="button" title="向左转" aria-label="向左转" @click="turnLeft">
          <span class="i-ep-arrow-left" aria-hidden="true"></span>
        </button>
        <button type="button" title="重置视角" aria-label="重置视角" @click="resetView">
          <span class="i-ep-refresh" aria-hidden="true"></span>
        </button>
        <button type="button" title="向右转" aria-label="向右转" @click="turnRight">
          <span class="i-ep-arrow-right" aria-hidden="true"></span>
        </button>
      </div>
    </section>

    <aside class="pet-room-care" aria-label="养成与补给" :aria-busy="busy">
      <header class="pet-care-heading">
        <div class="pet-room-identity">
          <h2>{{ petName }} <small v-if="room" class="pet-room-level">Lv. {{ room.level }}</small></h2>
          <button v-if="room" type="button" :disabled="busy" aria-label="修改宠物昵称" title="修改宠物昵称" aria-haspopup="dialog" @click="openRename">
            <span class="i-ep-edit" aria-hidden="true"></span>
          </button>
        </div>
        <span v-if="room" class="pet-room-balance" aria-label="字块币余额">
          <span class="i-ep-coin" aria-hidden="true"></span>{{ room.balance }} BC
        </span>
      </header>
      <div v-if="room" class="pet-growth-summary">
        <div>
          <span>成长值</span>
          <strong>{{ room.growth }}</strong>
        </div>
        <p>基础 1 · 互动 {{ room.interactionPoints }} · 消费 {{ room.spentPoints }}</p>
      </div>
      <div v-if="room" class="pet-room-meters">
        <div v-for="meter in meters" :key="meter.name" class="pet-room-meter" :data-meter="meter.name">
          <label :for="meter.name">{{ meter.label }}</label>
          <strong>{{ room[meter.name] }} / {{ meter.max }}</strong>
          <progress :id="meter.name" :value="room[meter.name]" :max="meter.max"></progress>
        </div>
        <div class="pet-room-growth">
          <div class="pet-care-heading">
            <label for="pet-progress">等级经验</label>
            <strong v-if="room.level === room.maxLevel">已满级</strong>
            <strong v-else>{{ room.progress }} / {{ room.progressGoal }} XP</strong>
          </div>
          <progress id="pet-progress" :value="room.progress" :max="room.progressGoal"></progress>
        </div>
      </div>

      <section v-if="room" class="pet-care-section" aria-labelledby="pet-actions-title">
        <h3 id="pet-actions-title">陪伴</h3>
        <div class="pet-room-actions">
          <button type="button" :disabled="busy || !catReady" @click="petCat">
            <span class="i-ep-star" aria-hidden="true"></span>摸摸
          </button>
          <button type="button" :disabled="busy || !catReady || !room.canPlay" @click="playWithCat">
            <span class="i-ep-basketball" aria-hidden="true"></span>逗玩
          </button>
          <button type="button" :disabled="busy || !catReady || room.energy >= 100" @click="restCat">
            <span class="i-ep-moon" aria-hidden="true"></span>休息
          </button>
        </div>
      </section>

      <section v-if="room" class="pet-care-section" aria-labelledby="pet-shop-title">
        <h3 id="pet-shop-title">猫粮仓</h3>
        <div class="pet-food-row">
          <span class="pet-food-icon i-ep-bowl" aria-hidden="true"></span>
          <div><strong>猫粮</strong><small>饱腹 +{{ room.foodFill }} · 成长 +{{ room.foodExperience }} XP</small></div>
          <output aria-label="猫粮库存">{{ room.food }} 份</output>
        </div>
        <div class="pet-room-shop-actions">
          <button type="button" :disabled="busy || !catReady || !canFeed" @click="feedCat">
            <span class="i-ep-bowl" aria-hidden="true"></span>喂一份
          </button>
          <button type="button" :disabled="busy || !canBuy" @click="buyFood">
            <span class="i-ep-shopping-cart" aria-hidden="true"></span>购买 · {{ room.foodPrice }} BC
          </button>
        </div>
        <p v-if="!canBuy" class="pet-room-limit">{{ shopLimit }}</p>
      </section>
      <section class="pet-care-section" aria-labelledby="pet-explore-title">
        <h3 id="pet-explore-title">逛逛小屋</h3>
        <div class="pet-room-explore">
          <button v-for="item in roomSpots" :key="item.name" type="button" @click="exploreSpot(item.name)">
            <span :class="item.icon" aria-hidden="true"></span>{{ item.label }}
          </button>
          <button v-if="!sceneFailed" type="button" :disabled="!catReady || busy" @click="shiftToy">
            <span class="i-ep-rank" aria-hidden="true"></span>挪球
          </button>
          <button v-if="!sceneFailed" type="button" :disabled="!catReady || busy" @click="shiftBowl">
            <span class="i-ep-rank" aria-hidden="true"></span>挪碗
          </button>
        </div>
      </section>
      <p class="pet-room-status" role="status">{{ notice }}</p>
      <button v-if="room" type="button" class="pet-rank-open" @click="openLeaderboard">
        <span class="i-ep-trophy" aria-hidden="true"></span>成长排行榜
      </button>
      <button v-if="!room && !busy" type="button" @click="loadRoom">重新加载</button>
    </aside>
    <dialog ref="renameDialog" class="pet-rank-dialog" aria-labelledby="pet-rename-title">
      <form class="pet-rename-form" @submit.prevent="saveNickname">
        <h2 id="pet-rename-title">修改宠物昵称</h2>
        <label for="pet-nickname">昵称</label>
        <input id="pet-nickname" ref="nicknameInput" v-model="nicknameDraft" :maxlength="nicknameLimit" :aria-invalid="renameInvalid" :readonly="busy" autocomplete="off" autofocus />
        <p v-if="renameError" class="pet-rename-error" role="alert">{{ renameError }}</p>
        <div class="pet-rename-actions">
          <button type="button" :disabled="busy" @click="closeRename">取消</button>
          <button type="submit" :disabled="busy">保存</button>
        </div>
      </form>
    </dialog>
    <dialog ref="rankDialog" class="pet-rank-dialog" aria-labelledby="pet-rank-title" @close="clearLeaderboard">
      <header class="pet-care-heading">
        <h2 id="pet-rank-title">成长排行榜</h2>
        <button type="button" title="关闭排行榜" aria-label="关闭排行榜" @click="closeLeaderboard">
          <span class="i-ep-close" aria-hidden="true"></span>
        </button>
      </header>
      <p class="pet-rank-notice" role="status">{{ rankNotice }}</p>
      <button v-if="rankError" type="button" @click="loadLeaderboard">重试</button>
      <template v-if="board">
        <p v-if="board.own" class="pet-rank-own">
          {{ board.own.nickname }} · 我的名次 <strong>#{{ board.own.rank }}</strong> · {{ board.own.growth }} 成长值
        </p>
        <p v-if="board.leaders.length === 0">暂无上榜宠物</p>
        <table v-else class="pet-rank-table">
          <thead><tr><th scope="col">名次</th><th scope="col">宠物昵称</th><th scope="col">成长值</th></tr></thead>
          <tbody>
            <tr v-for="(leader, index) in board.leaders" :key="index" :class="{ 'pet-rank-me': leader.mine }">
              <td>{{ leader.rank }}</td>
              <td>{{ leader.nickname }}<small v-if="leader.mine">我</small></td>
              <td>{{ leader.growth }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { useAppStore } from "../stores/app.js";
import { useMessageStore } from "../stores/messages.js";
import { useWalletStore } from "../stores/wallet.js";
import { generalRequest } from "../services/genServ.js";
import { createPetScene } from "../components/pet/pet-scene.js";
import { idlePetThought, petThought, PET_DEFAULT_NAME, THOUGHT_ROTATION_MS } from "../logic/petSpeech.js";
import "../styles/pet-room.css";

const TURN_RADIANS = Math.PI / 6;
const THOUGHT_DURATION = 5500;
const METER_MAX = 100;
const roomSpots = [
  { name: "window", icon: "i-ep-grid", label: "窗户" },
  { name: "plant", icon: "i-ep-pear", label: "盆栽" },
  { name: "rug", icon: "i-ep-collection", label: "软垫" },
  { name: "bowl", icon: "i-ep-bowl", label: "食碗" },
  { name: "toy", icon: "i-ep-basketball", label: "玩具球" },
  { name: "floor", icon: "i-ep-map-location", label: "地板" },
  { name: "lamp", icon: "i-ep-sunny", label: "落地灯" },
  { name: "clock", icon: "i-ep-clock", label: "挂钟" },
];
const meters = [
  { name: "fullness", label: "饱腹", max: METER_MAX },
  { name: "bond", label: "亲密", max: METER_MAX },
  { name: "energy", label: "精力", max: METER_MAX },
];
const app = useAppStore();
const wallet = useWalletStore();
const messages = useMessageStore();
const stage = ref(null);
const room = ref(null);
const busy = ref(false);
const catReady = ref(true);
const sceneFailed = ref(false);
const notice = ref("正在载入小屋");
const rankDialog = ref(null);
const renameDialog = ref(null);
const nicknameInput = ref(null);
const nicknameDraft = ref("");
const renameError = ref("");
const board = ref(null);
const rankNotice = ref("");
const rankError = ref(false);
const thoughtTopic = ref("");
const thoughtIndex = ref(0);
let scene = null;
let requestId = "";
let requestNumber = 0;
let rankRequest = 0;
let thoughtTimer = 0;
let thoughtRotation = 0;
let purchaseTask = null;
let roomToken = "";
let leaving = false;
let settlement = null;

// 对话文字始终读取最新养成数值，未选物件时说出当前最需要的照顾
function readThought() {
  if (!thoughtTopic.value) {
    return idlePetThought(room.value, thoughtIndex.value);
  }
  return petThought(room.value, thoughtTopic.value);
}

const thought = computed(readThought);

// 使用服务端保存的昵称，旧数据尚未带昵称时仍显示小橙
function readPetName() {
  if (room.value && room.value.nickname) {
    return room.value.nickname;
  }
  return PET_DEFAULT_NAME;
}

// 昵称字数上限由服务器返回，旧后端没有提供限制时交给接口校验
function readNicknameLimit() {
  if (room.value) {
    return room.value.nicknameLimit;
  }
  return undefined;
}

// 保存失败时让输入框带上无效状态，错误文字也会播报
function readRenameInvalid() {
  return Boolean(renameError.value);
}

const petName = computed(readPetName);
const nicknameLimit = computed(readNicknameLimit);
const renameInvalid = computed(readRenameInvalid);

// 空闲时轮换气泡，不覆盖当前互动或睡眠的回应
function advanceThought() {
  if (document.hidden || thoughtTopic.value) {
    return;
  }
  if ((renameDialog.value && renameDialog.value.open) || (rankDialog.value && rankDialog.value.open)) {
    return;
  }
  thoughtIndex.value = thoughtIndex.value + 1;
}

// 打开改名弹窗时预填当前昵称，选中文字便于直接替换
function openRename() {
  nicknameDraft.value = petName.value;
  renameError.value = "";
  renameDialog.value.showModal();
  nicknameInput.value.select();
}

// 关闭改名弹窗，未保存的草稿不会影响名称
function closeRename() {
  if (renameDialog.value && renameDialog.value.open) {
    renameDialog.value.close();
  }
}

// 保存昵称并立即更新档案，下次打开榜单时由服务器返回同一个名字
async function saveNickname() {
  if (!room.value || busy.value) {
    return;
  }
  const nickname = nicknameDraft.value.trim();
  if (!nickname) {
    renameError.value = "请输入昵称";
    nicknameInput.value.focus();
    return;
  }
  const token = app.userInfo.token;
  const current = requestNumber;
  renameError.value = "";
  busy.value = true;
  try {
    const result = await generalRequest("/api/pet/rename", { nickname: nickname });
    if (current !== requestNumber || token !== app.userInfo.token) {
      return;
    }
    if (result.code === 0) {
      room.value.nickname = result.data;
      notice.value = "昵称已保存";
      closeRename();
    } else {
      renameError.value = result.msg || "昵称保存失败，请重试";
    }
  } catch (error) {
    if (current === requestNumber && token === app.userInfo.token) {
      renameError.value = "昵称保存失败，请重试";
    }
  } finally {
    if (current === requestNumber && token === app.userInfo.token) {
      busy.value = false;
    }
  }
}

// 短暂保留物件回应，之后恢复随当前状态变化的自言自语
function clearThought() {
  window.clearTimeout(thoughtTimer);
  thoughtTopic.value = "";
}

// 场景点按和照顾请求成功后都可以改变小橘正在说的话
function sayAbout(topic) {
  clearThought();
  thoughtTopic.value = topic;
  if (topic !== "sleep") {
    thoughtTimer = window.setTimeout(clearThought, THOUGHT_DURATION);
  }
}

// 归位和睡眠结束后重新开放照顾按钮
function setCatReady(ready) {
  catReady.value = ready;
}

// 用按钮挪开玩具球，触屏和键盘也能触发归位
function shiftToy() {
  if (scene) {
    scene.shiftProp("toy");
  }
}

// 用按钮挪开食碗，结果和鼠标长按拖走相同
function shiftBowl() {
  if (scene) {
    scene.shiftProp("bowl");
  }
}

// 不用鼠标也可以探索与画布相同的陈设
function exploreSpot(name) {
  if (scene) {
    scene.explore(name);
  } else {
    sayAbout(name);
  }
}

// 关闭榜单或切换账户时，不再接收上一次榜单请求的结果
function clearLeaderboard() {
  rankRequest = rankRequest + 1;
  board.value = null;
  rankNotice.value = "";
  rankError.value = false;
}

// 每次打开榜单都读取服务器最新名次，不用本地数值推算其他玩家
async function loadLeaderboard() {
  clearLeaderboard();
  const current = rankRequest;
  const token = app.userInfo.token;
  rankNotice.value = "正在加载排行榜";
  try {
    const result = await generalRequest("/api/pet/leaderboard", {});
    if (current !== rankRequest || token !== app.userInfo.token) {
      return;
    }
    if (result.code === 0) {
      board.value = result.data;
      rankNotice.value = "";
    } else {
      rankNotice.value = result.msg || "排行榜加载失败";
      rankError.value = true;
    }
  } catch (error) {
    if (current === rankRequest && token === app.userInfo.token) {
      rankNotice.value = "排行榜加载失败，请重试";
      rankError.value = true;
    }
  }
}

// 原生对话框负责焦点和 Escape 键，打开后再读取榜单
function openLeaderboard() {
  rankDialog.value.showModal();
  loadLeaderboard();
}

// 关闭榜单并立即丢弃仍在等待的请求
function closeLeaderboard() {
  if (rankDialog.value && rankDialog.value.open) {
    rankDialog.value.close();
  }
  clearLeaderboard();
}

// 只有库存有粮且饱腹还有空间时，才显示可用的喂食按钮
function readCanFeed() {
  const state = room.value;
  return state && state.food > 0 && state.fullness < METER_MAX;
}

// 价格和交易限额由服务端发回，按钮用它们提前提示是否可以买
function readCanBuy() {
  const state = room.value;
  return state && state.balance >= state.foodPrice && state.food < state.foodLimit;
}

// 说明购买不可用的具体原因，不把余额不足误报为库存问题
function readShopLimit() {
  const state = room.value;
  if (state && state.food >= state.foodLimit) {
    return "粮仓已满";
  }
  return "字块币不足";
}

const canFeed = computed(readCanFeed);
const canBuy = computed(readCanBuy);
const shopLimit = computed(readShopLimit);

// 请求完成时检查账户与编号，旧页面的回应不会覆盖新账户的状态
async function updateRoom(action, values, reaction) {
  if (busy.value) {
    return;
  }
  busy.value = true;
  const token = app.userInfo.token;
  requestNumber = requestNumber + 1;
  const number = requestNumber;
  try {
    const result = await generalRequest("/api/pet/" + action, values);
    if (token !== app.userInfo.token || number !== requestNumber) {
      return;
    }
    notice.value = result.msg || "";
    if (result.code !== 0) {
      if (action === "buy") {
        requestId = "";
      }
      return;
    }
    room.value = result.data;
    if (action === "interact") {
      sayAbout(values.action);
    }
    if (scene) {
      scene.setLevel(result.data.level);
      if (reaction) {
        scene.react(reaction);
      }
    }
    if (action === "buy") {
      requestId = "";
      wallet.refresh();
    }
  } catch (error) {
    if (number === requestNumber) {
      notice.value = "小屋暂时无法连接，请稍后重试";
      if (error.response && error.response.status === 404) {
        notice.value = "小屋接口尚未加载，请重启后端";
      }
    }
  } finally {
    if (number === requestNumber) {
      busy.value = false;
    }
  }
}

// 从服务器加载当前账户的小屋，首次进入也走同一个入口
function loadRoom() {
  return updateRoom("room", {}, "");
}

// 点击宠物模型和点击摸摸按钮使用同一养成动作
function petCat() {
  if (busy.value || (scene && !scene.react("pet"))) {
    return;
  }
  if (room.value) {
    return updateRoom("interact", { action: "pet" }, "");
  }
}

// 逗玩消耗精力和饱腹，服务器确认后再播放玩具动作
function playWithCat() {
  return updateRoom("interact", { action: "play" }, "play");
}

// 服务器恢复精力后，小橘侧躺睡一会儿再自己起身
function restCat() {
  return updateRoom("interact", { action: "rest" }, "rest");
}

// 喂食消耗粮仓中的一份猫粮，不再产生额外扣款
function feedCat() {
  return updateRoom("interact", { action: "feed" }, "feed");
}

// 请求中断后复用原订单号，服务端已扣款时不会再扣一次
function buyFood() {
  if (leaving || busy.value || !canBuy.value) {
    return;
  }
  if (!requestId) {
    requestId = window.crypto.randomUUID();
  }
  purchaseTask = updateRoom("buy", { requestId: requestId }, "");
  return purchaseTask;
}

// 等当前购买写入后再通知服务器汇总，直接关闭页面时让请求继续发送
async function settleRoom(token) {
  if (!token) {
    return;
  }
  try {
    const response = await fetch("/api/pet/settle", {
      method: "POST",
      headers: { Authorization: "Bearer " + token },
      keepalive: true,
      signal: AbortSignal.timeout(3000),
    });
    const result = await response.json();
    if (result.code === 0 && result.data && token === app.userInfo.token) {
      messages.refresh(true);
    }
  } catch (error) {
    // 网络不通时由服务器保留待通知批次，下次离开小屋时仍可结算
  }
}

// 切换页面前等正在购买的猫粮入库，防止依次点击购买和离开时漏算
async function leaveRoom() {
  if (settlement) {
    return settlement;
  }
  leaving = true;
  const token = roomToken;
  settlement = finishRoomPurchases(token, purchaseTask);
  try {
    await settlement;
  } finally {
    settlement = null;
    leaving = false;
  }
}

// 先等这次访问中已提交的购买结束，再汇总它所属账户的订单
async function finishRoomPurchases(token, task) {
  if (task) {
    await task;
  }
  await settleRoom(token);
}

// 关闭或刷新标签页时尽力发出请求，已支付的订单仍保存在服务器
function settleOnPageHide() {
  settleRoom(roomToken);
}

// 向左转一小步，方便不使用拖动的玩家观察模型
function turnLeft() {
  if (scene) {
    scene.rotate(-TURN_RADIANS);
  }
}

// 向右转一小步，与左转按钮使用相同的角度
function turnRight() {
  if (scene) {
    scene.rotate(TURN_RADIANS);
  }
}

// 将镜头恢复到正前方的默认观察位置
function resetView() {
  if (scene) {
    scene.resetView();
  }
}

// 创建画布失败时保留原画和照顾按钮，养成不依赖 WebGL
function openRoom() {
  roomToken = app.userInfo.token;
  thoughtRotation = window.setInterval(advanceThought, THOUGHT_ROTATION_MS);
  window.addEventListener("pagehide", settleOnPageHide);
  try {
    scene = createPetScene(stage.value, petCat, sayAbout, setCatReady);
  } catch (error) {
    sceneFailed.value = true;
  }
  loadRoom();
}

// 账户变化时清空旧的库存和订单，再加载新账户的小屋
function switchRoom() {
  leaveRoom();
  roomToken = app.userInfo.token;
  purchaseTask = null;
  clearThought();
  thoughtIndex.value = 0;
  closeRename();
  closeLeaderboard();
  requestNumber = requestNumber + 1;
  requestId = "";
  room.value = null;
  busy.value = false;
  notice.value = "正在载入小屋";
  if (app.userInfo.token) {
    loadRoom();
  }
}

// 读取当前令牌作为账户变化信号
function readToken() {
  return app.userInfo.token;
}

// 离开小屋后立即停止渲染，尚未完成的请求不再更新页面
function closeRoom() {
  window.removeEventListener("pagehide", settleOnPageHide);
  window.clearInterval(thoughtRotation);
  clearThought();
  closeRename();
  closeLeaderboard();
  requestNumber = requestNumber + 1;
  if (scene) {
    scene.dispose();
    scene = null;
  }
}

watch(readToken, switchRoom);
onBeforeRouteLeave(leaveRoom);
onMounted(openRoom);
onBeforeUnmount(closeRoom);
</script>

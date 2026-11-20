package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.PetCare;
import cn.edu.sdu.java.server.models.PetPurchaseBatch;
import cn.edu.sdu.java.server.models.PetRoom;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.PetRoomRepository;
import cn.edu.sdu.java.server.repositorys.PetCareRepository;
import cn.edu.sdu.java.server.repositorys.PetPurchaseBatchRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护桌宠养成和用品购买，所有写入与字块币商城共用同一个账户锁 */
@Service
public class PetRoomService {
    // 没有自定义昵称时，小屋和排行榜都显示这个名字
    private static final String DEFAULT_NICKNAME = "小橙";
    // 昵称长度上限与存储字段和输入框保持一致
    private static final int NICKNAME_LIMIT = 24;
    // 饱腹、精力和亲密度共用的满值
    private static final int MAX_METER = 100;
    // 首次进入小屋时宠物的饱腹值
    private static final int START_FULLNESS = 52;
    // 首次进入小屋时宠物的亲密度
    private static final int START_BOND = 20;
    // 首次进入小屋时宠物的精力
    private static final int START_ENERGY = 78;
    // 新存档获赠的猫粮份数
    private static final int START_FOOD = 2;
    // 粮仓能存放的最大份数
    private static final int MAX_FOOD = 30;
    // 每份猫粮的字块币价格，由服务端扣除
    private static final int FOOD_PRICE = 8;
    // 喂一份猫粮恢复的饱腹值，不超过满值
    private static final int FOOD_FILL = 24;
    // 喂一份猫粮获得的等级经验
    private static final int FOOD_EXPERIENCE = 16;
    // 每次逗玩消耗的精力
    private static final int PLAY_COST = 18;
    // 每次逗玩消耗的饱腹值
    private static final int PLAY_HUNGER = 12;
    // 每次休息恢复的精力
    private static final int REST_GAIN = 26;
    // 一次有效摸摸增加的亲密度
    private static final int PET_BOND = 4;
    // 一次逗玩增加的亲密度
    private static final int PLAY_BOND = 8;
    // 小橙总等级上限
    private static final int MAX_LEVEL = 100;
    // 每升一级所需的经验
    private static final int LEVEL_EXPERIENCE = 40;
    // 从一级升到满级需要的总经验，超过后不再累计等级经验
    private static final int MAX_EXPERIENCE = (MAX_LEVEL - 1) * LEVEL_EXPERIENCE;
    // 公开排行显示的人数，榜外还会附带本人名次
    private static final int LEADERBOARD_LIMIT = 20;
    // 两次有效摸摸至少间隔的毫秒数
    private static final long PET_INTERVAL = 5000;
    // 有效摸摸增加的成长值
    private static final int PET_POINTS = 2;
    // 逗玩增加的成长值
    private static final int PLAY_POINTS = 6;
    // 有效休息增加的成长值
    private static final int REST_POINTS = 1;
    // 喂食另计的成长值，不含等级经验
    private static final int FEED_POINTS = 5;
    private final PetRoomRepository rooms;
    private final PetCareRepository careRecords;
    private final PetPurchaseBatchRepository purchaseBatches;
    private final BlockcoinService coins;

    /** 注入桌宠仓库，扣款与记账由字块币服务统一处理 */
    public PetRoomService(PetRoomRepository rooms, BlockcoinService coins, PetCareRepository careRecords,
                          PetPurchaseBatchRepository purchaseBatches) {
        this.rooms = rooms;
        this.coins = coins;
        this.careRecords = careRecords;
        this.purchaseBatches = purchaseBatches;
    }

    /** 首次打开小屋时创建一只桌宠，送两份猫粮用于体验喂食 */
    private PetRoom findOrCreateRoom(User user) {
        int ownerId = user.getUserId();
        Optional<PetRoom> existing = rooms.findById(ownerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PetRoom room = new PetRoom();
        room.setUserId(ownerId);
        room.setFullness(START_FULLNESS);
        room.setBond(START_BOND);
        room.setEnergy(START_ENERGY);
        room.setFood(START_FOOD);
        return rooms.save(room);
    }

    /** 返回实时余额、库存和成长数值，价格和等级门槛由服务端计算 */
    private DataResponse snapshot(PetRoom room, BlockcoinWallet wallet, String message) {
        int experience = room.getExperience();
        int level = 1 + experience / LEVEL_EXPERIENCE;
        int progress = experience % LEVEL_EXPERIENCE;
        if (level == MAX_LEVEL) {
            progress = LEVEL_EXPERIENCE;
        }
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("nickname", rooms.readNickname(room.getUserId()).orElse(DEFAULT_NICKNAME));
        view.put("nicknameLimit", NICKNAME_LIMIT);
        view.put("meterMax", MAX_METER);
        view.put("fullness", room.getFullness());
        view.put("bond", room.getBond());
        view.put("energy", room.getEnergy());
        view.put("level", level);
        view.put("maxLevel", MAX_LEVEL);
        view.put("progress", progress);
        view.put("progressGoal", LEVEL_EXPERIENCE);
        view.put("food", room.getFood());
        view.put("foodLimit", MAX_FOOD);
        view.put("foodPrice", FOOD_PRICE);
        view.put("foodFill", FOOD_FILL);
        view.put("foodExperience", FOOD_EXPERIENCE);
        view.put("balance", wallet.getBalance());
        view.put("canPlay", room.getFullness() >= PLAY_HUNGER && room.getEnergy() >= PLAY_COST);
        PetCare care = careOf(room);
        long spent = rooms.spending(room.getUserId());
        long interaction = experience + care.getCarePoints();
        view.put("growth", 1 + interaction + spent);
        view.put("interactionPoints", interaction);
        view.put("spentPoints", spent);
        return CommonMethod.getReturnData(view, message);
    }

    /** 旧宠物第一次参与互动时才创建累计记录，初始分不需要迁移 */
    private PetCare careOf(PetRoom room) {
        Optional<PetCare> existing = careRecords.findById(room.getUserId());
        if (existing.isPresent()) {
            return existing.get();
        }
        PetCare care = new PetCare();
        care.setUserId(room.getUserId());
        return care;
    }

    /** 返回成长榜前二十位与当前账户名次，不返回账号或其他私人资料 */
    @Transactional(readOnly = true)
    public DataResponse leaderboard() {
        int ownerId = CommonMethod.getUserId();
        List<Map<String, Object>> leaders = new ArrayList<Map<String, Object>>();
        Map<String, Object> own = null;
        for (PetRoomRepository.RankRow row : rooms.leaderboard(ownerId, LEADERBOARD_LIMIT)) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            boolean mine = row.getOwnerId().equals(ownerId);
            String nickname = row.getNickname();
            if (nickname == null) {
                nickname = DEFAULT_NICKNAME;
            }
            view.put("nickname", nickname);
            view.put("growth", row.getScore());
            view.put("rank", row.getRankNumber());
            view.put("mine", mine);
            if (leaders.size() < LEADERBOARD_LIMIT) {
                leaders.add(view);
            }
            if (mine) {
                own = view;
            }
        }
        Map<String, Object> board = new LinkedHashMap<String, Object>();
        board.put("leaders", leaders);
        board.put("own", own);
        return CommonMethod.getReturnData(board);
    }

    /** 打开小屋时读取当前账户自己的宠物和钱包 */
    @Transactional
    public DataResponse readRoom() {
        User user = coins.account();
        return snapshot(findOrCreateRoom(user), coins.wallet(user), "");
    }

    /** 只更改当前账户的宠物昵称，返回小屋和榜单共用的名称 */
    @Transactional
    public DataResponse rename(String input) {
        if (input == null) {
            return CommonMethod.getReturnMessageError("请输入昵称");
        }
        String nickname = input.strip();
        if (nickname.isBlank()) {
            return CommonMethod.getReturnMessageError("请输入昵称");
        }
        if (nickname.length() > NICKNAME_LIMIT) {
            return CommonMethod.getReturnMessageError("昵称不能超过 " + NICKNAME_LIMIT + " 个字符");
        }
        for (int index = 0; index < nickname.length(); index = index + 1) {
            if (Character.isISOControl(nickname.charAt(index))) {
                return CommonMethod.getReturnMessageError("昵称不能包含控制字符");
            }
        }
        User user = coins.account();
        PetRoom room = findOrCreateRoom(user);
        rooms.flush();
        rooms.saveNickname(room.getUserId(), nickname);
        return CommonMethod.getReturnData(nickname);
    }

    /** 桌宠气泡只读取已有存档，不会因为悬停创建宠物或钱包 */
    @Transactional(readOnly = true)
    public DataResponse readMood() {
        Optional<PetRoom> found = rooms.findById(CommonMethod.getUserId());
        if (found.isEmpty()) {
            return CommonMethod.getReturnData(null);
        }
        PetRoom room = found.get();
        Map<String, Object> mood = new LinkedHashMap<String, Object>();
        mood.put("fullness", room.getFullness());
        mood.put("bond", room.getBond());
        mood.put("energy", room.getEnergy());
        mood.put("food", room.getFood());
        return CommonMethod.getReturnData(mood);
    }

    /** 购买一份猫粮，同一订单号重试时直接返回当前状态 */
    @Transactional
    public DataResponse buyFood(String requestId) {
        if (!validRequestId(requestId)) {
            return CommonMethod.getReturnMessageError("猫粮订单编号无效");
        }
        User user = coins.account();
        PetRoom room = findOrCreateRoom(user);
        BlockcoinWallet wallet = coins.wallet(user);
        String entryId = PetRoomRepository.PURCHASE_PREFIX + user.getUserId() + ":" + requestId;
        if (coins.hasPetPurchase(entryId)) {
            return snapshot(room, wallet, "猫粮已入库");
        }
        if (room.getFood() >= MAX_FOOD) {
            return CommonMethod.getReturnMessageError("猫粮库存已满");
        }
        if (!coins.spendOnPet(wallet, entryId, FOOD_PRICE, "猫粮")) {
            return CommonMethod.getReturnMessageError("字块币不足");
        }
        room.setFood(room.getFood() + 1);
        rooms.save(room);
        addPendingPurchase(user.getUserId());
        return snapshot(room, wallet, "猫粮已入库");
    }

    /** 购买成功后记入待通知批次，订单重试不会重复累计 */
    private void addPendingPurchase(int userId) {
        Optional<PetPurchaseBatch> pending = purchaseBatches.findById(userId);
        PetPurchaseBatch batch;
        if (pending.isPresent()) {
            batch = pending.get();
        } else {
            String batchId = UUID.randomUUID().toString();
            batch = new PetPurchaseBatch(userId, batchId);
        }
        batch.addFood(FOOD_PRICE);
        purchaseBatches.save(batch);
    }

    /** 离开小屋时只发送一条汇总消息，不再次扣款，重复调用不会重复发送 */
    @Transactional
    public DataResponse settlePurchases() {
        User user = coins.account();
        Optional<PetPurchaseBatch> pending = purchaseBatches.findById(user.getUserId());
        if (pending.isEmpty()) {
            return CommonMethod.getReturnData(false);
        }
        PetPurchaseBatch batch = pending.get();
        BlockcoinWallet wallet = coins.wallet(user);
        coins.notifyPetPurchase(wallet, batch.getBatchId(), batch.getSpent(), batch.getQuantity());
        purchaseBatches.delete(batch);
        return CommonMethod.getReturnData(true);
    }

    /** 订单号必须是标准 UUID，客户端无法指定支付金额或账户 */
    private boolean validRequestId(String requestId) {
        if (requestId == null || requestId.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(requestId).toString().equals(requestId);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /** 抚摸、玩耍、休息和喂食使用同一个已锁定的存档，不直接接受客户端写入数值 */
    @Transactional
    public DataResponse interact(String action) {
        User user = coins.account();
        PetRoom room = findOrCreateRoom(user);
        PetCare care = careOf(room);
        String message;
        int points;
        if ("pet".equals(action)) {
            long now = System.currentTimeMillis();
            if (now - care.getLastPetAt() < PET_INTERVAL) {
                return snapshot(room, coins.wallet(user), "我想再眯一小会儿，待会儿再摸我就能计入成长");
            }
            care.setLastPetAt(now);
            room.setBond(Math.min(MAX_METER, room.getBond() + PET_BOND));
            message = "我蹭了蹭你的手";
            points = PET_POINTS;
        } else if ("play".equals(action)) {
            if (room.getEnergy() < PLAY_COST || room.getFullness() < PLAY_HUNGER) {
                return CommonMethod.getReturnMessageError("我得先休息一下或吃点东西");
            }
            room.setEnergy(room.getEnergy() - PLAY_COST);
            room.setFullness(room.getFullness() - PLAY_HUNGER);
            room.setBond(Math.min(MAX_METER, room.getBond() + PLAY_BOND));
            message = "我扑到了玩具球";
            points = PLAY_POINTS;
        } else if ("rest".equals(action)) {
            if (room.getEnergy() >= MAX_METER) {
                return CommonMethod.getReturnMessageError("我现在精神很好");
            }
            room.setEnergy(Math.min(MAX_METER, room.getEnergy() + REST_GAIN));
            message = "我打了个盹";
            points = REST_POINTS;
        } else if ("feed".equals(action)) {
            if (room.getFood() < 1) {
                return CommonMethod.getReturnMessageError("粮仓里没有猫粮");
            }
            if (room.getFullness() >= MAX_METER) {
                return CommonMethod.getReturnMessageError("我已经吃饱啦");
            }
            int previousLevel = room.getExperience() / LEVEL_EXPERIENCE;
            room.setFood(room.getFood() - 1);
            room.setFullness(Math.min(MAX_METER, room.getFullness() + FOOD_FILL));
            room.setExperience(Math.min(MAX_EXPERIENCE, room.getExperience() + FOOD_EXPERIENCE));
            message = "我吃完了一份猫粮";
            points = FEED_POINTS;
            if (room.getExperience() / LEVEL_EXPERIENCE > previousLevel) {
                message = "我又长大一点啦";
            }
        } else {
            return CommonMethod.getReturnMessageError("不支持这项互动");
        }
        care.setCarePoints(care.getCarePoints() + points);
        rooms.save(room);
        careRecords.save(care);
        return snapshot(room, coins.wallet(user), message);
    }
}

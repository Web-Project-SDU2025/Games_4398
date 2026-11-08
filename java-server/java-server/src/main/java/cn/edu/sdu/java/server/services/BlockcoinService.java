package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.BlockcoinEntry;
import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.BlockcoinEntryRepository;
import cn.edu.sdu.java.server.repositorys.BlockcoinWalletRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.repositorys.GameRevocationRepository;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import cn.edu.sdu.java.server.util.AdminQueryRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字块币核心服务
 *
 * 管理整个平台的虚拟货币流转，包括购买游戏、通关奖励和管理员操作
 * 为了保证数据一致性，所有涉及金额变动的操作都会先锁定账户，然后在一个事务里完成
 * 比如用户购买游戏时，扣款、更新游戏库、记录流水这三步要么全成功要么全回滚
 */
@Service
public class BlockcoinService {
    private final UserRepository users;
    private final GameRepository games;
    private final BlockcoinWalletRepository wallets;
    private final BlockcoinEntryRepository entries;
    private final OfficialLevelCatalog levels;
    private final GameService gameService;
    private final GameRevocationRepository revocations;
    private final MessageService messages;

    /**
     * 依赖注入
     * 复用已有的用户和游戏仓库，新增钱包、流水、撤回记录等字块币专用的表
     */
    public BlockcoinService(UserRepository users, GameRepository games,
            BlockcoinWalletRepository wallets, BlockcoinEntryRepository entries,
            OfficialLevelCatalog levels, GameService gameService, GameRevocationRepository revocations,
            MessageService messages) {
        this.users = users;
        this.games = games;
        this.wallets = wallets;
        this.entries = entries;
        this.levels = levels;
        this.gameService = gameService;
        this.revocations = revocations;
        this.messages = messages;
    }

    /**
     * 获取当前登录账户并加锁
     * 用悲观锁防止并发修改，比如张三同时在两个浏览器购买游戏，加锁后第二个请求会等第一个完成
     */
    private User account() {
        return users.lockAccount(CommonMethod.getUserId()).orElseThrow();
    }

    /**
     * 获取或创建用户钱包
     * 首次使用商城的用户会自动获得100字块币的新手礼包，老用户直接返回原来的钱包
     */
    private BlockcoinWallet wallet(User user) {
        Optional<BlockcoinWallet> found = wallets.findById(user.getUserId());
        if (found.isPresent()) {
            return found.get();
        }
        BlockcoinWallet wallet = new BlockcoinWallet();
        wallet.setUserId(user.getUserId());
        wallet.setBalance(BlockcoinRules.INITIAL_BALANCE);
        wallets.save(wallet);
        record(wallet, "initial:" + user.getUserId(), BlockcoinRules.INITIAL_BALANCE, "初始字块币", "");
        return wallet;
    }

    /**
     * 记录一笔字块币流水
     * 每笔流水都有唯一ID防止重复记账，金额可正可负（正数是收入，负数是支出）
     * 同时会给用户发送系统通知，让大家知道自己的余额变化
     */
    private void record(BlockcoinWallet wallet, String id, long amount, String reason, String path) {
        BlockcoinEntry entry = new BlockcoinEntry();
        entry.setEntryId(id);
        entry.setUserId(wallet.getUserId());
        entry.setAmount(amount);
        entry.setBalanceAfter(wallet.getBalance());
        entry.setReason(reason);
        entry.setGamePath(path);
        entry.setCreatedAt(Instant.now());
        entries.save(entry);
        String change = Long.toString(amount);
        if (amount > 0) {
            change = "+" + change;
        }
        messages.notifyAccount("coins:" + id, wallet.getUserId(), "字块币变动",
            reason + "\n变动：" + change + " BC\n当前余额：" + wallet.getBalance() + " BC");
    }

    /**
     * 生成购买记录的唯一ID
     * 用账户ID和游戏ID拼接，这样同一个人多次点购买按钮也只会生成一条记录
     */
    private String purchaseId(User user, Game game) {
        return "purchase:" + user.getUserId() + ":" + game.getGameId();
    }

    /**
     * 检查用户是否拥有某个游戏
     * 优先检查有没有有效的撤回记录（管理员撤回了就不算拥有）
     * 然后看游戏库和购买记录，兼容老数据
     */
    public boolean owns(User user, Game game) {
        if (revocations.existsByUserIdAndGameIdAndRepurchasedAtIsNull(user.getUserId(), game.getGameId())) {
            return false;
        }
        for (Game owned : user.getGames()) {
            if (owned.getGameId().equals(game.getGameId())) {
                return true;
            }
        }
        return entries.existsById(purchaseId(user, game));
    }

    /**
     * 根据请求参数找到对应的游戏
     * 可以通过游戏ID或者游戏路径来查找，找不到就返回null
     */
    private Game product(DataRequest request) {
        Integer id = request.getInteger("gameId");
        String path = request.getString("gamePath");
        for (Game game : games.findAll()) {
            if (game.getGameId().equals(id) || (path != null && path.equals(game.getGamePath()))) {
                return game;
            }
        }
        return null;
    }

    /**
     * 组装钱包信息
     * 包含当前余额和最近20条流水，只取最近的防止老用户流水太多一次加载卡顿
     */
    private Map<String, Object> walletInfo(BlockcoinWallet wallet) {
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("balance", wallet.getBalance());
        info.put("currency", "字块币");
        info.put("currencyCode", "Blockcoin");
        info.put("initialBalance", BlockcoinRules.INITIAL_BALANCE);
        PageRequest page = PageRequest.of(0, BlockcoinRules.HISTORY_SIZE);
        info.put("history", entries.findByUserIdOrderByCreatedAtDesc(wallet.getUserId(), page));
        return info;
    }

    /**
     * 获取商城页面数据
     * 返回所有游戏、哪些已经买了、当前余额和最近流水
     * 首次使用会自动创建钱包并发放新手礼包
     */
    @Transactional
    public DataResponse catalog() {
        User user = account();
        BlockcoinWallet wallet = wallet(user);
        List<Map<String, Object>> products = new ArrayList<Map<String, Object>>();
        for (Game game : games.findAll()) {
            Map<String, Object> info = gameService.gameInfo(game);
            info.put("owned", owns(user, game));
            info.put("favorite", user.getFavorites().contains(game));
            info.put("price", BlockcoinRules.GAME_PRICE);
            info.put("reward", levels.reward(game));
            products.add(info);
        }
        Map<String, Object> result = walletInfo(wallet);
        result.put("games", products);
        return CommonMethod.getReturnData(result);
    }

    /**
     * 购买游戏
     * 在一个事务里完成扣款、加入游戏库、记录流水、标记重新购买这几步
     * 余额不足或者已经买过的话都不会重复扣款
     */
    @Transactional
    public DataResponse purchase(DataRequest request) {
        User user = account();
        BlockcoinWallet wallet = wallet(user);
        Game game = product(request);
        if (game == null) {
            return CommonMethod.getReturnMessageError("游戏已下架，请刷新商城");
        }
        boolean owned = owns(user, game);
        if (!owned && wallet.getBalance() < BlockcoinRules.GAME_PRICE) {
            return CommonMethod.getReturnMessageError("字块币不足");
        }
        if (!owned) {
            wallet.setBalance(wallet.getBalance() - BlockcoinRules.GAME_PRICE);
            wallets.save(wallet);
            String id = purchaseId(user, game);
            if (entries.existsById(id)) {
                id = id + ":" + UUID.randomUUID();
            }
            record(wallet, id, -BlockcoinRules.GAME_PRICE,
                "购买 " + game.getName(), game.getGamePath());
            revocations.markRepurchased(user.getUserId(), game.getGameId(), Instant.now());
        }
        if (!user.getGames().contains(game)) {
            user.getGames().add(game);
            users.save(user);
        }
        return CommonMethod.getReturnData(walletInfo(wallet), "游戏已加入游戏库");
    }

    /**
     * 验证游戏启动权限
     * 普通游戏需要先购买才能玩，关卡编辑器只有管理员能打开
     */
    @Transactional
    public DataResponse authorize(DataRequest request) {
        User user = account();
        Game game = product(request);
        if (game == null) {
            return CommonMethod.getReturnMessageError("游戏尚未在商城登记");
        }
        boolean editor = request.getBoolean("editor");
        boolean admin = "ROLE_ADMIN".equals(CommonMethod.getRoleName());
        if (editor && !admin) {
            return CommonMethod.getReturnMessageError("关卡编辑器仅管理员可用");
        }
        if (!editor && !owns(user, game)) {
            return CommonMethod.getReturnMessageError("请先在游戏商城购买此游戏");
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("userId", user.getUserId());
        result.put("editorAllowed", admin);
        return CommonMethod.getReturnData(result);
    }

    /**
     * 恢复游戏库
     * 用户已经拥有游戏但游戏库里找不到时，可以调这个接口重新加入游戏库
     * 不会重复扣款，只是同步一下归属关系
     */
    @Transactional
    public DataResponse restoreLibrary(DataRequest request) {
        User user = account();
        Game game = product(request);
        if (game == null || !owns(user, game)) {
            return CommonMethod.getReturnMessageError("请先在游戏商城购买此游戏");
        }
        if (!user.getGames().contains(game)) {
            user.getGames().add(game);
            users.save(user);
        }
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 发放通关奖励
     * 游戏会话结算时调用，会话ID保证同一局游戏不会重复发钱
     */
    @Transactional
    public long award(User user, Game game, String playId, String levelName, long amount) {
        String entryId = "reward:" + playId;
        return creditReward(user, game, entryId, levelName + "通关", amount);
    }

    /**
     * 发放台球成就奖励
     * 台球游戏用单独的业务编号，金额由后端根据成就类型计算，前端不能指定
     */
    @Transactional
    public long awardBilliards(User user, Game game, String eventId, String title, long amount) {
        return creditReward(user, game, "billiards:" + eventId, title, amount);
    }

    /**
     * 奖励入账的通用逻辑
     * 检查余额上限、记录流水、发送通知，同一个业务编号只会入账一次
     * 调用方必须已经持有账户锁，这样才能保证并发安全
     */
    private long creditReward(User user, Game game, String entryId, String title, long amount) {
        if (amount <= 0 || entries.existsById(entryId)) {
            return 0;
        }
        BlockcoinWallet wallet = wallet(user);
        if (wallet.getBalance() > BlockcoinRules.MAX_BALANCE - amount) {
            return 0;
        }
        wallet.setBalance(wallet.getBalance() + amount);
        wallets.save(wallet);
        record(wallet, entryId, amount, game.getName() + " · " + title, game.getGamePath());
        return amount;
    }

    /**
     * 组装管理员调整结果
     * 返回操作后的余额和流水ID，重复操作会复用之前的流水
     */
    private DataResponse adjustmentResult(BlockcoinEntry entry) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("userId", entry.getUserId());
        result.put("balance", entry.getBalanceAfter());
        result.put("entryId", entry.getEntryId());
        return CommonMethod.getReturnData(result, "字块币已调整");
    }

    /**
     * 管理员手动调整账户余额
     * 可以发放或收回字块币，需要填写原因
     * 用操作编号防止重复提交，用账户锁和事务保证数据一致性
     */
    @Transactional
    public DataResponse adjust(DataRequest request) {
        if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())) {
            return CommonMethod.getReturnMessageError("仅管理员可用");
        }
        int userId;
        long quantity;
        String requestId = request.getString("requestId");
        try {
            userId = Integer.parseInt(request.getString("userId"));
            quantity = Long.parseLong(request.getString("quantity"));
            if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)) {
                return CommonMethod.getReturnMessageError("操作编号不正确");
            }
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError("账户编号或数量不正确");
        }
        String reason = request.getString("reason");
        String direction = request.getString("direction");
        if (userId < 1 || quantity < 1 || quantity > BlockcoinRules.MAX_BALANCE) {
            return CommonMethod.getReturnMessageError("字块币数量超出范围");
        }
        if (reason == null || reason.isBlank()
                || reason.trim().length() > AdminQueryRules.OPERATION_REASON_LIMIT) {
            return CommonMethod.getReturnMessageError("请填写有效的调整原因");
        }
        if (!"grant".equals(direction) && !"reclaim".equals(direction)) {
            return CommonMethod.getReturnMessageError("请选择发放或收回");
        }
        Optional<User> actor = users.findById(CommonMethod.getUserId());
        if (actor.isEmpty() || !"ROLE_ADMIN".equals(actor.get().getUserType().getName())) {
            return CommonMethod.getReturnMessageError("管理员身份已变更");
        }
        Optional<User> target = users.lockAccount(userId);
        if (target.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        long amount = quantity;
        String action = "发放";
        if ("reclaim".equals(direction)) {
            amount = -quantity;
            action = "收回";
        }
        String detail = "管理员 " + actor.get().getUserName() + " " + action + "：" + reason.trim();
        String entryId = "adjust:" + actor.get().getUserId() + ":" + requestId;
        Optional<BlockcoinEntry> previous = entries.findById(entryId);
        if (previous.isPresent()) {
            BlockcoinEntry entry = previous.get();
            if (!entry.getUserId().equals(userId) || entry.getAmount() != amount
                    || !entry.getReason().equals(detail)) {
                return CommonMethod.getReturnMessageError("操作编号已用于其他调整");
            }
            return adjustmentResult(entry);
        }
        Optional<BlockcoinWallet> existing = wallets.findById(userId);
        long balance = BlockcoinRules.INITIAL_BALANCE;
        if (existing.isPresent()) {
            balance = existing.get().getBalance();
        }
        // 校验通过后才创建新钱包，失败请求保留账户和初始发放状态
        if (amount < 0 && balance < quantity) {
            return CommonMethod.getReturnMessageError("账户余额不足");
        }
        if (amount > 0 && balance > BlockcoinRules.MAX_BALANCE - quantity) {
            return CommonMethod.getReturnMessageError("字块币余额超出上限");
        }
        BlockcoinWallet wallet;
        if (existing.isPresent()) {
            wallet = existing.get();
        } else {
            wallet = wallet(target.get());
        }
        wallet.setBalance(balance + amount);
        wallets.save(wallet);
        record(wallet, entryId, amount, detail, "");
        BlockcoinEntry entry = new BlockcoinEntry();
        entry.setEntryId(entryId);
        entry.setUserId(userId);
        entry.setBalanceAfter(wallet.getBalance());
        return adjustmentResult(entry);
    }
}

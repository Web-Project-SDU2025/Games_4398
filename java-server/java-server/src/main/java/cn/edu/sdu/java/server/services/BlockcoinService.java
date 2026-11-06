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

/** 管理字块币、购买归属与正式通关结算，所有资金变动先锁账户并在同一事务提交 */
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

    /** 共用现有账户与游戏仓库，新仓库只保存资金流水和关卡尝试 */
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

    /** 读取并锁定当前真实账户，其他账户编号始终来自认证结果 */
    private User account() {
        return users.lockAccount(CommonMethod.getUserId()).orElseThrow();
    }

    /** 首次使用时发放一次初始余额，已存在钱包始终保留原余额 */
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

    /** 写入唯一编号的资金流水，金额、余额和原因由服务端生成 */
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

    /** 根据账户和游戏生成购买编号，相同请求重复执行时复用已有购买 */
    private String purchaseId(User user, Game game) {
        return "purchase:" + user.getUserId() + ":" + game.getGameId();
    }

    /** 有效撤回优先于旧购买记录，其他账户继续兼容原游戏库与购买归属 */
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

    /** 通过后端游戏编号或已登记入口读取商品，找不到时返回空结果 */
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

    /** 返回余额与有限条数的收支记录，避免账户使用时间增长后一次读取全部流水 */
    private Map<String, Object> walletInfo(BlockcoinWallet wallet) {
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("balance", wallet.getBalance());
        info.put("currency", "字块币");
        info.put("currencyCode", "Blockcoin");
        info.put("initialBalance", BlockcoinRules.INITIAL_BALANCE);
        info.put("levelReward", BlockcoinRules.LEVEL_REWARD);
        PageRequest page = PageRequest.of(0, BlockcoinRules.HISTORY_SIZE);
        info.put("history", entries.findByUserIdOrderByCreatedAtDesc(wallet.getUserId(), page));
        return info;
    }

    /** 返回商城商品、当前归属和钱包，首次使用按同一账户锁完成初始化 */
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
            info.put("reward", levels.reward(game.getGamePath()));
            products.add(info);
        }
        Map<String, Object> result = walletInfo(wallet);
        result.put("games", products);
        return CommonMethod.getReturnData(result);
    }

    /** 购买在同一事务内扣款、登记归属与保存流水，余额不足和重复购买都保持原余额 */
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

    /** 游戏启动时核对持有关系，管理员可单独打开编辑器，普通游戏仍按购买归属处理 */
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

    /** 原免费加入接口只恢复已持有游戏，新增归属通过明确的购买入口完成 */
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

    /** 通用游戏会话在账户锁内调用奖励记账，唯一流水防止重复发放 */
    @Transactional
    public long award(User user, Game game, String playId, String levelName, long amount) {
        String entryId = "reward:" + playId;
        if (amount <= 0 || entries.existsById(entryId)) {
            return 0;
        }
        BlockcoinWallet wallet = wallet(user);
        if (wallet.getBalance() > BlockcoinRules.MAX_BALANCE - amount) {
            return 0;
        }
        wallet.setBalance(wallet.getBalance() + amount);
        wallets.save(wallet);
        record(wallet, entryId, amount, game.getName() + " · " + levelName + "通关", game.getGamePath());
        return amount;
    }

    /** 返回调整后的余额和流水编号，重复请求直接复用原流水 */
    private DataResponse adjustmentResult(BlockcoinEntry entry) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("userId", entry.getUserId());
        result.put("balance", entry.getBalanceAfter());
        result.put("entryId", entry.getEntryId());
        return CommonMethod.getReturnData(result, "字块币已调整");
    }

    /** 管理员发放或收回指定数量，账户锁、唯一流水与事务共同保护余额 */
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

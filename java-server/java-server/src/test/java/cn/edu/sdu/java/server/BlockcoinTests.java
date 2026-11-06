package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.BlockcoinEntry;
import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.repositorys.BlockcoinEntryRepository;
import cn.edu.sdu.java.server.repositorys.BlockcoinWalletRepository;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRevocationRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.RecordVisibilityService;
import cn.edu.sdu.java.server.services.MessageService;
import cn.edu.sdu.java.server.services.OfficialLevelCatalog;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用独立内存替身检查字块币交易边界，课程数据库保持原值 */
class BlockcoinTests {
    private static final String RUN = "a88f3c18-67c7-46fa-bd50-66e8566b29a4";
    /** 管理员操作使用固定合法编号，重试应复用此编号 */
    private static final String OPERATION = "ce2e3140-c77f-4088-885a-e4c7988f0914";

    /** 测试完成后清理身份，避免下一用例继承登录状态 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 准备同一账户、商品与钱包，仓库替身保留本次用例的资金变化 */
    private Fixture fixture(long balance) {
        Fixture fixture = new Fixture();
        UserType type = new UserType();
        type.setName("ROLE_GAME_USER");
        fixture.user.setUserId(12);
        fixture.user.setUserName("wallet-test");
        fixture.user.setPassword("test");
        fixture.user.setUserType(type);
        fixture.game.setGameId(8);
        fixture.game.setGamePath(BlockcoinRules.PVZ_PATH);
        fixture.game.setName("植物大战僵尸");
        fixture.wallet.setUserId(12);
        fixture.wallet.setBalance(balance);
        when(fixture.users.lockAccount(12)).thenReturn(Optional.of(fixture.user));
        when(fixture.games.findAll()).thenReturn(List.of(fixture.game));
        when(fixture.wallets.findById(12)).thenReturn(Optional.of(fixture.wallet));
        UserDetailsImpl details = UserDetailsImpl.build(fixture.user);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return fixture;
    }

    /** 首次访问发放一百币，第二次读取已有钱包时不再发放 */
    @Test
    void initialCoinsAreGrantedOnce() {
        Fixture fixture = fixture(0);
        when(fixture.wallets.findById(12)).thenReturn(Optional.empty(), Optional.of(fixture.wallet));
        Map<?, ?> first = (Map<?, ?>) fixture.service.catalog().getData();
        assertEquals(100L, first.get("balance"));
        fixture.wallet.setBalance(100L);
        Map<?, ?> second = (Map<?, ?>) fixture.service.catalog().getData();
        assertEquals(100L, second.get("balance"));
        verify(fixture.wallets, times(1)).save(any(BlockcoinWallet.class));
        verify(fixture.entries, times(1)).save(any(BlockcoinEntry.class));
        verify(fixture.users, times(2)).lockAccount(12);
    }

    /** 正常购买扣一次款，重复点击和恢复游戏库继续使用原持有关系 */
    @Test
    void purchaseAndRetryChargeOnlyOnce() {
        Fixture fixture = fixture(100);
        DataRequest request = new DataRequest();
        request.add("gameId", 8);
        request.add("price", 0);
        assertEquals(0, fixture.service.purchase(request).getCode());
        assertEquals(0L, fixture.wallet.getBalance());
        assertTrue(fixture.user.getGames().contains(fixture.game));
        assertEquals(0, fixture.service.purchase(request).getCode());
        verify(fixture.wallets, times(1)).save(fixture.wallet);
        verify(fixture.entries, times(1)).save(any(BlockcoinEntry.class));
        verify(fixture.messages).notifyAccount(
            eq("coins:purchase:12:8"), eq(12), eq("字块币变动"), contains("-100 BC"));
    }

    /** 余额不足、未购买启动和原免费加入接口都保持余额及游戏库原值 */
    @Test
    void insufficientBalanceCannotBypassPurchase() {
        Fixture fixture = fixture(99);
        DataRequest request = new DataRequest();
        request.add("gameId", 8);
        assertEquals(1, fixture.service.purchase(request).getCode());
        assertEquals(1, fixture.service.authorize(request).getCode());
        assertEquals(1, fixture.service.restoreLibrary(request).getCode());
        assertEquals(99L, fixture.wallet.getBalance());
        assertTrue(fixture.user.getGames().isEmpty());
        verify(fixture.wallets, never()).save(any());
    }

    /** 通用会话只向账本传入服务器决定的金额，重复流水保持余额原值 */
    @Test
    void rewardEntryIsUnique() {
        Fixture fixture = fixture(0);
        fixture.user.getGames().add(fixture.game);
        assertEquals(15L, fixture.service.award(fixture.user, fixture.game, RUN, "第 1 关", 15));
        when(fixture.entries.existsById("reward:" + RUN)).thenReturn(true);
        assertEquals(0L, fixture.service.award(fixture.user, fixture.game, RUN, "第 1 关", 15));
        assertEquals(15L, fixture.wallet.getBalance());
    }

    /** 官方规则直接读取构建包中的关卡文件，摘要长度和最短时间均由素材产生 */
    @Test
    void officialCatalogReadsPublishedLevels() {
        OfficialLevelCatalog catalog = new OfficialLevelCatalog(new ObjectMapper());
        OfficialLevelCatalog.LevelRule rule = catalog.find(BlockcoinRules.PVZ_PATH, 0);
        assertNotNull(rule);
        assertEquals(64, rule.hash.length());
        assertTrue(rule.minimumSeconds >= BlockcoinRules.MIN_PLAY_SECONDS);
        assertNull(catalog.find(BlockcoinRules.PVZ_PATH, 99999));
    }

    /** 达到余额上限时保留原余额，游戏会话仍可保存通关记录 */
    @Test
    void rewardRespectsBalanceLimit() {
        Fixture fixture = fixture(BlockcoinRules.MAX_BALANCE);
        assertEquals(0L, fixture.service.award(fixture.user, fixture.game, RUN, "第 1 关", 15));
        assertEquals(BlockcoinRules.MAX_BALANCE, fixture.wallet.getBalance());
        verify(fixture.wallets, never()).save(any());
    }

    /** 切换为独立管理员身份，目标账户继续使用原玩家角色 */
    private void administrator(Fixture fixture) {
        User actor = new User();
        UserType type = new UserType();
        type.setName("ROLE_ADMIN");
        actor.setUserId(99);
        actor.setUserName("manager");
        actor.setPassword("test");
        actor.setUserType(type);
        when(fixture.users.findById(99)).thenReturn(Optional.of(actor));
        UserDetailsImpl details = UserDetailsImpl.build(actor);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 创建有效的调整请求，各用例只修改要验证的字段 */
    private DataRequest adjustment(String direction, long quantity) {
        DataRequest request = new DataRequest();
        request.add("userId", 12);
        request.add("direction", direction);
        request.add("quantity", quantity);
        request.add("reason", "活动调整");
        request.add("requestId", OPERATION);
        return request;
    }

    /** 发放与收回写入有符号流水，收回全部余额时允许恰好为零 */
    @Test
    void administratorAdjustmentsPreserveLedgerAndRetry() {
        Fixture fixture = fixture(100);
        administrator(fixture);
        DataRequest request = adjustment("grant", 50);
        assertEquals(0, fixture.service.adjust(request).getCode());
        assertEquals(150L, fixture.wallet.getBalance());
        ArgumentCaptor<BlockcoinEntry> saved = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(fixture.entries).save(saved.capture());
        BlockcoinEntry entry = saved.getValue();
        assertEquals(50L, entry.getAmount());
        assertTrue(entry.getReason().contains("管理员 manager 发放：活动调整"));
        when(fixture.entries.findById(entry.getEntryId())).thenReturn(Optional.of(entry));
        assertEquals(0, fixture.service.adjust(request).getCode());
        assertEquals(150L, fixture.wallet.getBalance());
        request.add("quantity", 51);
        assertEquals(1, fixture.service.adjust(request).getCode());
        request.add("quantity", 50);
        request.add("reason", "另一个原因");
        assertEquals(1, fixture.service.adjust(request).getCode());
        request.add("reason", "活动调整");
        request.add("userId", 13);
        when(fixture.users.lockAccount(13)).thenReturn(Optional.of(fixture.user));
        assertEquals(1, fixture.service.adjust(request).getCode());
        verify(fixture.wallets, times(1)).save(any());
        verify(fixture.entries, times(1)).save(any());
        verify(fixture.messages).notifyAccount(
            eq("coins:adjust:99:" + OPERATION), eq(12), eq("字块币变动"), contains("+50 BC"));

        Fixture reclaim = fixture(100);
        administrator(reclaim);
        assertEquals(0, reclaim.service.adjust(adjustment("reclaim", 100)).getCode());
        assertEquals(0L, reclaim.wallet.getBalance());
        ArgumentCaptor<BlockcoinEntry> expense = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(reclaim.entries).save(expense.capture());
        assertEquals(-100L, expense.getValue().getAmount());
    }

    /** 非法输入、余额不足、溢出和失效角色均在写入前返回 */
    @Test
    void invalidAdjustmentsKeepWalletAndLedger() {
        Fixture fixture = fixture(100);
        DataRequest request = adjustment("grant", 1);
        assertEquals(1, fixture.service.adjust(request).getCode());
        administrator(fixture);
        String[] invalidQuantities = {"0", "-1", "1.5", "2000000001", "abc"};
        for (String quantity : invalidQuantities) {
            request.add("quantity", quantity);
            assertEquals(1, fixture.service.adjust(request).getCode());
        }
        request = adjustment("reclaim", 101);
        assertEquals(1, fixture.service.adjust(request).getCode());
        request = adjustment("grant", 1);
        request.add("requestId", "invalid");
        assertEquals(1, fixture.service.adjust(request).getCode());
        request = adjustment("grant", 1);
        request.add("reason", " ");
        assertEquals(1, fixture.service.adjust(request).getCode());
        request.add("reason", "原".repeat(61));
        assertEquals(1, fixture.service.adjust(request).getCode());
        request = adjustment("other", 1);
        assertEquals(1, fixture.service.adjust(request).getCode());
        fixture.wallet.setBalance(BlockcoinRules.MAX_BALANCE);
        assertEquals(1, fixture.service.adjust(adjustment("grant", 1)).getCode());
        when(fixture.users.findById(99)).thenReturn(Optional.of(fixture.user));
        assertEquals(1, fixture.service.adjust(adjustment("grant", 1)).getCode());
        verify(fixture.wallets, never()).save(any());
        verify(fixture.entries, never()).save(any());
    }

    /** 新钱包仅在有效操作时发放初始金额，失败尝试保留初始化机会 */
    @Test
    void newWalletAdjustmentCreatesInitialEntryOnce() {
        Fixture fixture = fixture(0);
        administrator(fixture);
        when(fixture.wallets.findById(12)).thenReturn(Optional.empty());
        assertEquals(1, fixture.service.adjust(adjustment("reclaim", 101)).getCode());
        verify(fixture.wallets, never()).save(any());
        assertEquals(0, fixture.service.adjust(adjustment("reclaim", 100)).getCode());
        ArgumentCaptor<BlockcoinEntry> saved = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(fixture.entries, times(2)).save(saved.capture());
        List<BlockcoinEntry> rows = saved.getAllValues();
        assertEquals("initial:12", rows.get(0).getEntryId());
        assertEquals(100L, rows.get(0).getAmount());
        assertEquals(-100L, rows.get(1).getAmount());
        assertEquals(0L, rows.get(1).getBalanceAfter());
        verify(fixture.messages).notifyAccount(
            eq("coins:initial:12"), eq(12), eq("字块币变动"), contains("初始字块币"));
        when(fixture.entries.findById(rows.get(1).getEntryId())).thenReturn(Optional.of(rows.get(1)));
        assertEquals(0, fixture.service.adjust(adjustment("reclaim", 100)).getCode());
        verify(fixture.entries, times(2)).save(any());
    }

    /** 撤回覆盖旧购买和关联，重新购买另记一笔费用并结束有效撤回 */
    @Test
    void revocationRequiresFreshPurchaseAndRetainsOriginalEntry() {
        Fixture fixture = fixture(200);
        fixture.user.getGames().add(fixture.game);
        when(fixture.entries.existsById("purchase:12:8")).thenReturn(true);
        when(fixture.revocations.existsByUserIdAndGameIdAndRepurchasedAtIsNull(12, 8))
            .thenReturn(true);
        assertFalse(fixture.service.owns(fixture.user, fixture.game));
        DataRequest request = new DataRequest();
        request.add("gameId", 8);
        assertEquals(1, fixture.service.authorize(request).getCode());
        assertEquals(1, fixture.service.restoreLibrary(request).getCode());
        assertEquals(0, fixture.service.purchase(request).getCode());
        assertEquals(100L, fixture.wallet.getBalance());
        ArgumentCaptor<BlockcoinEntry> saved = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(fixture.entries).save(saved.capture());
        assertTrue(saved.getValue().getEntryId().startsWith("purchase:12:8:"));
        assertEquals(-100L, saved.getValue().getAmount());
        verify(fixture.revocations).markRepurchased(eq(12), eq(8), any(Instant.class));
        when(fixture.revocations.existsByUserIdAndGameIdAndRepurchasedAtIsNull(12, 8))
            .thenReturn(false);
        assertEquals(0, fixture.service.purchase(request).getCode());
        verify(fixture.entries, times(1)).save(any());
    }

    /** 每个用例使用全新对象，构造服务与生产环境保持相同依赖关系 */
    private static final class Fixture {
        private final User user = new User();
        private final Game game = new Game();
        private final BlockcoinWallet wallet = new BlockcoinWallet();
        private final UserRepository users = mock(UserRepository.class);
        private final GameRepository games = mock(GameRepository.class);
        private final BlockcoinWalletRepository wallets = mock(BlockcoinWalletRepository.class);
        private final BlockcoinEntryRepository entries = mock(BlockcoinEntryRepository.class);
        private final GameRevocationRepository revocations = mock(GameRevocationRepository.class);
        private final MessageService messages = mock(MessageService.class);
        private final OfficialLevelCatalog levels = mock(OfficialLevelCatalog.class);
        private final GameService gameService = new GameService(games, users,
            mock(GameRecordRepository.class), mock(GamePlayRepository.class), mock(RecordVisibilityService.class));
        private final BlockcoinService service =
            new BlockcoinService(users, games, wallets, entries, levels, gameService, revocations, messages);
    }
}

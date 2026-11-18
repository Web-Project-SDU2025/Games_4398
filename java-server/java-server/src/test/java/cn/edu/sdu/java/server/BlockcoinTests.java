package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.BlockcoinEntry;
import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.PetCare;
import cn.edu.sdu.java.server.models.PetPurchaseBatch;
import cn.edu.sdu.java.server.models.PetRoom;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.BlockcoinEntryRepository;
import cn.edu.sdu.java.server.repositorys.BlockcoinWalletRepository;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRevocationRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.repositorys.PetRoomRepository;
import cn.edu.sdu.java.server.repositorys.PetCareRepository;
import cn.edu.sdu.java.server.repositorys.PetPurchaseBatchRepository;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.RecordVisibilityService;
import cn.edu.sdu.java.server.services.MessageService;
import cn.edu.sdu.java.server.services.PetRoomService;
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

    /** 用真实的记账逻辑验证猫粮会入库，复用同一订单不会再次扣款 */
    @Test
    void petFoodPurchaseIsIdempotent() {
        Fixture fixture = fixture(20);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        room.setFood(2);
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        String entryId = "pet-food:12:" + OPERATION;
        when(fixture.entries.existsById(entryId)).thenReturn(false, true);
        PetCareRepository care = mock(PetCareRepository.class);
        when(rooms.spending(12)).thenReturn(8L);
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        DataResponse purchase = pets.buyFood(OPERATION);
        assertEquals(0, purchase.getCode());
        Map<?, ?> first = (Map<?, ?>) purchase.getData();
        assertEquals(9L, first.get("growth"));
        assertEquals(3, room.getFood());
        assertEquals(12L, fixture.wallet.getBalance());
        DataResponse repeated = pets.buyFood(OPERATION);
        assertEquals(0, repeated.getCode());
        assertEquals(9L, ((Map<?, ?>) repeated.getData()).get("growth"));
        assertEquals(3, room.getFood());
        assertEquals(12L, fixture.wallet.getBalance());
        ArgumentCaptor<BlockcoinEntry> entry = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(fixture.entries).save(entry.capture());
        assertEquals(entryId, entry.getValue().getEntryId());
        assertEquals(-8L, entry.getValue().getAmount());
        verify(rooms).save(room);
        verify(fixture.petBatches).save(any(PetPurchaseBatch.class));
        verifyNoInteractions(fixture.messages);
    }

    /** 两次购买合成一条站内消息，结算重试不重新发消息或扣款 */
    @Test
    void petPurchasesSendOneMessageOnLeaving() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository care = mock(PetCareRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        PetPurchaseBatch batch = new PetPurchaseBatch(12, OPERATION);
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        when(fixture.petBatches.findById(12)).thenReturn(Optional.of(batch), Optional.of(batch),
            Optional.of(batch), Optional.empty());
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        assertEquals(0, pets.buyFood(OPERATION).getCode());
        assertEquals(0, pets.buyFood(RUN).getCode());
        verifyNoInteractions(fixture.messages);
        assertEquals(14L, fixture.wallet.getBalance());
        assertEquals(true, pets.settlePurchases().getData());
        assertEquals(false, pets.settlePurchases().getData());
        assertEquals(14L, fixture.wallet.getBalance());
        verify(fixture.petBatches).delete(batch);
        verify(fixture.messages).notifyAccount("pet-settle:" + OPERATION, 12, "桌宠小屋消费结算",
            "猫粮：2 份\n支出：-16 BC\n当前余额：14 BC");
    }

    /** 余额不足时不写账也不补充库存，喂食只消耗已有的猫粮 */
    @Test
    void petCareUsesStockAndLevelsUpWithoutChargingAgain() {
        Fixture fixture = fixture(7);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        room.setFood(2);
        room.setFullness(50);
        room.setExperience(32);
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        PetCareRepository care = mock(PetCareRepository.class);
        PetCare points = new PetCare();
        when(care.findById(12)).thenReturn(Optional.of(points));
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        assertEquals(1, pets.buyFood(OPERATION).getCode());
        assertEquals(2, room.getFood());
        assertEquals(7L, fixture.wallet.getBalance());
        verify(rooms, never()).save(room);
        DataResponse fed = pets.interact("feed");
        Map<?, ?> view = (Map<?, ?>) fed.getData();
        assertEquals(2, view.get("level"));
        assertEquals(8, view.get("progress"));
        assertEquals(54L, view.get("growth"));
        assertEquals(1, room.getFood());
        assertEquals(74, room.getFullness());
        assertEquals(7L, fixture.wallet.getBalance());
        verify(fixture.entries, never()).save(any(BlockcoinEntry.class));
    }

    /** 从饥饿到快吃饱都能喂食，达到上限后不再消耗库存或记分 */
    @Test
    void petFeedingWorksUntilFullnessReachesLimit() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository records = mock(PetCareRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        room.setFood(3);
        room.setFullness(23);
        PetCare care = new PetCare();
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        when(records.findById(12)).thenReturn(Optional.of(care));
        PetRoomService pets = new PetRoomService(rooms, fixture.service, records, fixture.petBatches);

        assertEquals(0, pets.interact("feed").getCode());
        assertEquals(47, room.getFullness());
        room.setFullness(99);
        assertEquals(0, pets.interact("feed").getCode());
        assertEquals(100, room.getFullness());
        assertEquals(1, room.getFood());
        assertEquals(1, pets.interact("feed").getCode());
        assertEquals(1, room.getFood());
        assertEquals(32, room.getExperience());
        assertEquals(10, care.getCarePoints());
        verify(rooms, times(2)).save(room);
    }

    /** 新宠物从一点成长值起步，短时间重复抚摸不会再次记分 */
    @Test
    void petGrowthStartsAboveZeroAndRewardsCare() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository care = mock(PetCareRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        PetCare points = new PetCare();
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        when(care.findById(12)).thenReturn(Optional.of(points));
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        assertEquals(1L, ((Map<?, ?>) pets.readRoom().getData()).get("growth"));
        assertEquals(3L, ((Map<?, ?>) pets.interact("pet").getData()).get("growth"));
        assertEquals(3L, ((Map<?, ?>) pets.interact("pet").getData()).get("growth"));
        verify(care).save(points);
    }

    /** 气泡只读已有状态，未打开过小屋时也不创建宠物或钱包 */
    @Test
    void petMoodDoesNotCreateRoomOrWallet() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository care = mock(PetCareRepository.class);
        PetRoom room = new PetRoom();
        room.setFullness(24);
        room.setEnergy(43);
        room.setFood(0);
        when(rooms.findById(12)).thenReturn(Optional.empty(), Optional.of(room));
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        assertNull(pets.readMood().getData());
        Map<?, ?> mood = (Map<?, ?>) pets.readMood().getData();
        assertEquals(24, mood.get("fullness"));
        assertEquals(43, mood.get("energy"));
        assertEquals(0, mood.get("food"));
        assertFalse(mood.containsKey("balance"));
        verify(rooms, never()).save(any(PetRoom.class));
        verifyNoInteractions(care);
    }

    /** 昵称默认是小橙，自定义名称在服务器保存，不改变钱包和成长 */
    @Test
    void petNicknameDefaultsAndCanBeChanged() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository care = mock(PetCareRepository.class);
        PetRoom room = new PetRoom();
        room.setUserId(12);
        when(rooms.findById(12)).thenReturn(Optional.of(room));
        when(rooms.readNickname(12)).thenReturn(Optional.empty(), Optional.of("奶糖 🐾"));
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        Map<?, ?> initial = (Map<?, ?>) pets.readRoom().getData();
        assertEquals("小橙", initial.get("nickname"));
        assertEquals(1, pets.rename("  ").getCode());
        assertEquals(1, pets.rename("小橙\n奶糖").getCode());
        assertEquals(1, pets.rename("橙".repeat(25)).getCode());
        assertEquals("奶糖 🐾", pets.rename("  奶糖 🐾  ").getData());
        verify(rooms).saveNickname(12, "奶糖 🐾");
        Map<?, ?> renamed = (Map<?, ?>) pets.readRoom().getData();
        assertEquals("奶糖 🐾", renamed.get("nickname"));
        assertEquals(initial.get("growth"), renamed.get("growth"));
        verifyNoInteractions(fixture.entries);
    }

    /** 榜外账户仍能看到自己的名次和宠物昵称，公开结果不含账户资料 */
    @Test
    void leaderboardReturnsPublicScoresAndKeepsOwnPlace() {
        Fixture fixture = fixture(30);
        PetRoomRepository rooms = mock(PetRoomRepository.class);
        PetCareRepository care = mock(PetCareRepository.class);
        java.util.ArrayList<PetRoomRepository.RankRow> rows = new java.util.ArrayList<>();
        for (int position = 0; position < 20; position = position + 1) {
            rows.add(rankRow(30 + position, 1L, 50L));
        }
        PetRoomRepository.RankRow ownRow = rankRow(12, 21L, 4L);
        when(ownRow.getNickname()).thenReturn("奶糖");
        rows.add(ownRow);
        when(rooms.leaderboard(12, 20)).thenReturn(rows);
        PetRoomService pets = new PetRoomService(rooms, fixture.service, care, fixture.petBatches);

        Map<?, ?> board = (Map<?, ?>) pets.leaderboard().getData();
        List<?> leaders = (List<?>) board.get("leaders");
        assertEquals(20, leaders.size());
        Map<?, ?> first = (Map<?, ?>) leaders.get(0);
        assertEquals(1L, first.get("rank"));
        assertEquals("小橙", first.get("nickname"));
        assertFalse(first.containsKey("ownerId"));
        Map<?, ?> own = (Map<?, ?>) board.get("own");
        assertEquals("奶糖", own.get("nickname"));
        assertEquals(21L, own.get("rank"));
        assertEquals(4L, own.get("growth"));
        assertEquals(true, own.get("mine"));
    }

    /** 构造最小排行投影，不向测试结果提供登录账号或真实昵称 */
    private PetRoomRepository.RankRow rankRow(int userId, long rank, long growth) {
        PetRoomRepository.RankRow row = mock(PetRoomRepository.RankRow.class);
        when(row.getOwnerId()).thenReturn(userId);
        when(row.getRankNumber()).thenReturn(rank);
        when(row.getScore()).thenReturn(growth);
        return row;
    }

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

    /** 成就收入进入同一账户账本，重复业务编号和余额上限都不能多发字块币 */
    @Test
    void billiardsRewardCreditsOnceAndRespectsBalanceLimit() {
        Fixture fixture = fixture(100);
        assertEquals(5L, fixture.service.awardBilliards(fixture.user, fixture.game,
                "round:opening", "开门红", 5));
        assertEquals(105L, fixture.wallet.getBalance());
        ArgumentCaptor<BlockcoinEntry> entry = ArgumentCaptor.forClass(BlockcoinEntry.class);
        verify(fixture.entries).save(entry.capture());
        assertEquals("billiards:round:opening", entry.getValue().getEntryId());
        assertEquals("植物大战僵尸 · 开门红", entry.getValue().getReason());
        when(fixture.entries.existsById("billiards:round:opening")).thenReturn(true);
        assertEquals(0L, fixture.service.awardBilliards(fixture.user, fixture.game,
                "round:opening", "开门红", 5));
        fixture.wallet.setBalance(BlockcoinRules.MAX_BALANCE);
        assertEquals(0L, fixture.service.awardBilliards(fixture.user, fixture.game,
                "round:win", "获胜", 20));
        verify(fixture.entries, times(1)).save(any(BlockcoinEntry.class));
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
        private final PetPurchaseBatchRepository petBatches = mock(PetPurchaseBatchRepository.class);
        private final OfficialLevelCatalog levels = mock(OfficialLevelCatalog.class);
        private final GameService gameService = new GameService(games, users,
            mock(GameRecordRepository.class), mock(GamePlayRepository.class), mock(RecordVisibilityService.class), levels);
        private final BlockcoinService service =
            new BlockcoinService(users, games, wallets, entries, levels, gameService, revocations, messages);
    }
}

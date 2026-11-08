package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.GameplayService;
import cn.edu.sdu.java.server.services.OfficialLevelCatalog;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import cn.edu.sdu.java.server.util.GameplayRules;
import cn.edu.sdu.java.server.util.BilliardsRewards;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用内存替身检查通用记录、真实计时边界和奖励幂等性 */
class GameplayTests {
    /** 同一局测试固定编号，重复请求保持原编号 */
    private static final String RUN = "a88f3c18-67c7-46fa-bd50-66e8566b29a4";
    /** 测试游戏使用站内通用入口，名称与编号均可替换 */
    private static final String PATH = "/games/example/game.json";

    /** 开局冻结奖励金额，之后管理员改金额不改变已开始的关卡 */
    @Test
    void configuredRewardIsFrozenAtStart() {
        Fixture fixture = new Fixture();
        when(fixture.levels.reward(fixture.game)).thenReturn(27L);
        GamePlay play = fixture.start();
        assertEquals(27L, play.getRewardAmount());
        when(fixture.levels.reward(fixture.game)).thenReturn(40L);
        when(fixture.coins.award(fixture.user, fixture.game, play.getPlayId(), play.getLevelName(), 27L))
                .thenReturn(27L);
        Map<?, ?> result = (Map<?, ?>) fixture.service.finish(fixture.updateRequest(play, "passed")).getData();
        assertEquals(27L, result.get("reward"));
        verify(fixture.coins).award(fixture.user, fixture.game, play.getPlayId(), play.getLevelName(), 27L);
    }

    /** 零奖励只停发货币，关卡仍保持正式身份与游玩记录 */
    @Test
    void zeroConfiguredRewardDisablesOnlyCurrency() {
        Fixture fixture = new Fixture();
        when(fixture.levels.reward(fixture.game)).thenReturn(0L);
        GamePlay play = fixture.start();
        assertEquals(0L, play.getRewardAmount());
        assertEquals("official", play.getMode());
        assertEquals(0, fixture.service.finish(fixture.updateRequest(play, "passed")).getCode());
        verify(fixture.coins, never()).award(any(), any(), anyString(), anyString(), anyLong());
    }
    /** 每个测试结束清理身份 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 使用正整数主键模拟记录仓库保存结果 */
    private static final class AssignRecord implements Answer<GameRecord> {
        /** 保存时提供记录主键，其他字段继续使用真实业务服务设置的值 */
        @Override
        public GameRecord answer(InvocationOnMock invocation) {
            GameRecord record = invocation.getArgument(0);
            record.setRecordId(9);
            return record;
        }
    }

    /** 一套仓库对应一个账户与游戏，所有用例复用真实服务实现 */
    private static final class Fixture {
        private final UserRepository users = mock(UserRepository.class);
        private final GameRepository games = mock(GameRepository.class);
        private final GameRecordRepository records = mock(GameRecordRepository.class);
        private final GamePlayRepository plays = mock(GamePlayRepository.class);
        private final BlockcoinService coins = mock(BlockcoinService.class);
        private final OfficialLevelCatalog levels = mock(OfficialLevelCatalog.class);
        private final User user = new User();
        private final Game game = new Game();
        private final GameplayService service = new GameplayService(users, games, records, plays, coins, levels);

        /** 放入与生产环境相同的身份主体，查询与保存由仓库替身承接 */
        Fixture() {
            UserType type = new UserType();
            type.setName("ROLE_GAME_USER");
            user.setUserId(12);
            user.setUserName("player");
            user.setPassword("test");
            user.setUserType(type);
            game.setGameId(8);
            game.setName("示例游戏");
            game.setGamePath(PATH);
            user.getGames().add(game);
            when(users.lockAccount(12)).thenReturn(Optional.of(user));
            when(games.findAll()).thenReturn(List.of(game));
            when(games.findById(8)).thenReturn(Optional.of(game));
            when(coins.owns(user, game)).thenReturn(true);
            when(records.saveAndFlush(any())).thenAnswer(new AssignRecord());
            when(levels.find(PATH, 0)).thenReturn(new OfficialLevelCatalog.LevelRule("official", 2, 15));
            when(levels.reward(game)).thenReturn(15L);
            UserDetailsImpl details = UserDetailsImpl.build(user);
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        }

        /** 创建开局请求，客户端提交的金额只用于验证服务忽略它 */
        DataRequest beginRequest() {
            DataRequest request = new DataRequest();
            request.add("runId", RUN);
            request.add("gamePath", PATH);
            request.add("level", 0);
            request.add("hash", "official");
            request.add("reward", 999);
            return request;
        }

        /** 通过真实开局流程取得会话，随后安排经过时间而无需阻塞等待 */
        GamePlay start() {
            assertEquals(0, service.begin(beginRequest()).getCode());
            ArgumentCaptor<GamePlay> captured = ArgumentCaptor.forClass(GamePlay.class);
            verify(plays).save(captured.capture());
            GamePlay play = captured.getValue();
            play.setStartedAt(Instant.now().minusSeconds(20));
            play.setUpdatedAt(Instant.now().minusSeconds(10));
            when(plays.findById("12:" + RUN)).thenReturn(Optional.of(play));
            return play;
        }

        /** 后续请求携带开局返回的挑战码，默认提交十秒有效游玩 */
        DataRequest updateRequest(GamePlay play, String outcome) {
            DataRequest request = new DataRequest();
            request.add("runId", RUN);
            request.add("challenge", play.getChallenge());
            request.add("activeMilliseconds", 10000);
            request.add("outcome", outcome);
            return request;
        }
    }

    /** 胜利与奖励使用同一事务结果，响应丢失后的重试不会再次发奖 */
    @Test
    void victoryRecordsTimeAndAwardsOnce() {
        Fixture fixture = new Fixture();
        GamePlay play = fixture.start();
        GameRecord record = new GameRecord();
        when(fixture.records.findById(9)).thenReturn(Optional.of(record));
        when(fixture.coins.award(fixture.user, fixture.game, play.getPlayId(), play.getLevelName(), 15))
            .thenReturn(15L);
        DataRequest request = fixture.updateRequest(play, "passed");
        Map<?, ?> result = (Map<?, ?>) fixture.service.finish(request).getData();
        assertEquals("passed", play.getOutcome());
        assertEquals(10, record.getDurationSeconds());
        assertEquals(15L, result.get("reward"));
        assertEquals(0, fixture.service.finish(request).getCode());
        verify(fixture.coins, times(1)).award(fixture.user, fixture.game,
            play.getPlayId(), play.getLevelName(), 15);
        verify(fixture.records, times(1)).save(record);
    }

    /** 失败与中途离开保存实际时长和结果，账本保持原余额 */
    @Test
    void failureAndLeavingKeepRecordsWithoutReward() {
        for (String outcome : List.of("failed", "abandoned", "closed")) {
            Fixture fixture = new Fixture();
            GamePlay play = fixture.start();
            assertEquals(0, fixture.service.finish(fixture.updateRequest(play, outcome)).getCode());
            assertEquals(outcome, play.getOutcome());
            assertEquals(10000L, play.getActiveMilliseconds());
            verify(fixture.coins, never()).award(any(), any(), anyString(), anyString(), anyLong());
        }
    }

    /** 八球已确认的胜负不受退出消息影响，客户端也不能跳过判决自行上报通过 */
    @Test
    void billiardsCloseKeepsConfirmedResultAndRejectsInventedVictory() {
        for (String confirmed : List.of("passed", "failed")) {
            Fixture fixture = new Fixture();
            GamePlay play = fixture.start();
            fixture.game.setGamePath(BilliardsRewards.GAME_PATH);
            play.setRewardAmount(0L);
            play.setAwardedAmount(20L);
            play.setOutcome(confirmed);
            DataRequest close = fixture.updateRequest(play, "closed");
            Map<?, ?> result = (Map<?, ?>) fixture.service.finish(close).getData();
            assertEquals(confirmed, result.get("outcome"));
            assertEquals(confirmed, play.getOutcome());
            assertNotNull(play.getEndedAt());
            assertEquals(20L, play.getAwardedAmount());
            verify(fixture.coins, never()).award(any(), any(), anyString(), anyString(), anyLong());
        }
        Fixture fixture = new Fixture();
        GamePlay play = fixture.start();
        fixture.game.setGamePath(BilliardsRewards.GAME_PATH);
        play.setRewardAmount(0L);
        assertEquals(1, fixture.service.finish(fixture.updateRequest(play, "passed")).getCode());
        assertEquals("playing", play.getOutcome());
        assertNull(play.getEndedAt());
    }

    /** 心跳重复值只保持存活，虚报时长按服务器经过时间限额并拒绝回退 */
    @Test
    void heartbeatsUseCumulativeBoundedTime() {
        Fixture fixture = new Fixture();
        GamePlay play = fixture.start();
        DataRequest request = fixture.updateRequest(play, "");
        request.add("activeMilliseconds", 1000000);
        assertEquals(0, fixture.service.heartbeat(request).getCode());
        assertTrue(play.getActiveMilliseconds() <= 13000);
        long duration = play.getActiveMilliseconds();
        assertEquals(0, fixture.service.heartbeat(request).getCode());
        assertEquals(duration, play.getActiveMilliseconds());
        request.add("activeMilliseconds", 1);
        assertEquals(1, fixture.service.heartbeat(request).getCode());
        assertEquals(duration, play.getActiveMilliseconds());
    }

    /** 文件不匹配时仍能记录结果，但保存的奖励为零 */
    @Test
    void customLevelsKeepRecordsWithoutCurrency() {
        Fixture fixture = new Fixture();
        DataRequest request = fixture.beginRequest();
        request.add("hash", "edited");
        assertEquals(0, fixture.service.begin(request).getCode());
        ArgumentCaptor<GamePlay> captured = ArgumentCaptor.forClass(GamePlay.class);
        verify(fixture.plays).save(captured.capture());
        assertEquals(0L, captured.getValue().getRewardAmount());
    }

    /** 非持有人、异常凭证、过早结算、未知结果和过期会话分别返回错误 */
    @Test
    void invalidRequestsPreserveSessionAndWallet() {
        Fixture fixture = new Fixture();
        when(fixture.coins.owns(fixture.user, fixture.game)).thenReturn(false);
        assertEquals(1, fixture.service.begin(fixture.beginRequest()).getCode());
        verify(fixture.records, never()).saveAndFlush(any());
        when(fixture.coins.owns(fixture.user, fixture.game)).thenReturn(true);
        GamePlay play = fixture.start();
        DataRequest request = fixture.updateRequest(play, "passed");
        request.add("challenge", "wrong");
        assertEquals(1, fixture.service.finish(request).getCode());
        request.add("challenge", play.getChallenge());
        play.setStartedAt(Instant.now());
        assertEquals(1, fixture.service.finish(request).getCode());
        play.setStartedAt(Instant.now().minusSeconds(20));
        request.add("outcome", null);
        assertEquals(1, fixture.service.finish(request).getCode());
        play.setUpdatedAt(Instant.now().minusSeconds(GameplayRules.IDLE_SECONDS + 1));
        assertEquals(1, fixture.service.heartbeat(request).getCode());
        assertNull(play.getEndedAt());
        assertEquals(0L, play.getActiveMilliseconds());
        verify(fixture.coins, never()).award(any(), any(), anyString(), anyString(), anyLong());
    }

    /** 限制同一账户并发局数，重试原会话仍可取得原挑战码 */
    @Test
    void concurrentStartsAreLimitedAndRetriesReuseSession() {
        Fixture fixture = new Fixture();
        when(fixture.plays.countByUserIdAndEndedAtIsNullAndUpdatedAtAfter(eq(12), any()))
            .thenReturn((long) GameplayRules.MAX_ACTIVE_GAMES);
        assertEquals(1, fixture.service.begin(fixture.beginRequest()).getCode());
        when(fixture.plays.countByUserIdAndEndedAtIsNullAndUpdatedAtAfter(eq(12), any())).thenReturn(0L);
        GamePlay play = fixture.start();
        Map<?, ?> result = (Map<?, ?>) fixture.service.begin(fixture.beginRequest()).getData();
        assertEquals(play.getChallenge(), result.get("challenge"));
        verify(fixture.records, times(1)).saveAndFlush(any());
    }
}

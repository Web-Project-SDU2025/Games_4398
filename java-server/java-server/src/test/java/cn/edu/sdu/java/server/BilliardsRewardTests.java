package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.BilliardsRound;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.repositorys.BilliardsRoundRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.BilliardsJudge;
import cn.edu.sdu.java.server.services.BilliardsRewardService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import cn.edu.sdu.java.server.util.BilliardsRewards;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 只检查台球奖励判定与重试，不启动后端或连接真实数据库 */
class BilliardsRewardTests {
    /** 固定合法局号与会话编号，重复请求必须使用相同编号 */
    private static final String RUN = "a88f3c18-67c7-46fa-bd50-66e8566b29a4";
    private static final String ROUND = "ce2e3140-c77f-4088-885a-e4c7988f0914";

    /** 检查结束后清空测试身份，不影响下一项检查 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 六项成就各减四，单人获胜奖励仍为二十，数组顺序与稳定编号一致 */
    @Test
    void achievementAmountsAreReducedWithoutChangingVictory() {
        assertEquals(List.of(1L, 4L, 6L, 8L, 11L, 11L, 20L), BilliardsRewards.AMOUNTS);
    }

    /** 创建独立仓库替身，实际调用生产奖励服务 */
    private static final class Fixture {
        final UserRepository users = mock(UserRepository.class);
        final GameRepository games = mock(GameRepository.class);
        final GamePlayRepository plays = mock(GamePlayRepository.class);
        final BilliardsRoundRepository rounds = mock(BilliardsRoundRepository.class);
        final BlockcoinService coins = mock(BlockcoinService.class);
        final ObjectMapper mapper = new ObjectMapper();
        final User user = new User();
        final Game game = new Game();
        final GamePlay play = new GamePlay();
        final BilliardsRewardService service = new BilliardsRewardService(users, games, plays, rounds, coins, mapper);

        /** 放入已持有台球的测试用户和仍然有效的正式游玩会话 */
        Fixture() {
            UserType type = new UserType();
            type.setName("ROLE_GAME_USER");
            user.setUserId(12);
            user.setUserName("billiards-check");
            user.setPassword("test");
            user.setUserType(type);
            game.setGameId(8);
            game.setGamePath(BilliardsRewards.GAME_PATH);
            play.setPlayId("12:" + RUN);
            play.setGameId(8);
            play.setMode("official");
            play.setChallenge("ticket");
            play.setAwardedAmount(0L);
            play.setStartedAt(Instant.now().minusSeconds(30));
            play.setUpdatedAt(Instant.now());
            when(users.lockAccount(12)).thenReturn(Optional.of(user));
            when(games.findById(8)).thenReturn(Optional.of(game));
            when(plays.findById(play.getPlayId())).thenReturn(Optional.of(play));
            when(coins.owns(user, game)).thenReturn(true);
            UserDetailsImpl identity = UserDetailsImpl.build(user);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(identity, null, identity.getAuthorities()));
        }

        /** 参数序号零表示开盘，其余序号必须按真实击球顺序增加 */
        DataRequest request(int sequence) {
            DataRequest request = new DataRequest();
            request.add("runId", RUN);
            request.add("roundId", ROUND);
            request.add("challenge", "ticket");
            request.add("sequence", sequence);
            request.add("matchMode", "single");
            return request;
        }

        /** 通过真实服务开盘，再把保存对象交给后续重复请求查询 */
        BilliardsRound open() throws Exception {
            assertEquals(0, service.report(request(0)).getCode());
            ArgumentCaptor<BilliardsRound> captured = ArgumentCaptor.forClass(BilliardsRound.class);
            verify(rounds).save(captured.capture());
            BilliardsRound round = captured.getValue();
            when(rounds.findById(round.getRoundId())).thenReturn(Optional.of(round));
            return round;
        }
    }

    /** 服务器开球方只选零或一，同一盘重试不再次随机，也不能中途修改模式 */
    @Test
    void randomStarterIsStableAndModeIsImmutable() throws Exception {
        Fixture fixture = new Fixture();
        fixture.open();
        Map<?, ?> first = (Map<?, ?>) fixture.service.report(fixture.request(0)).getData();
        Map<?, ?> second = (Map<?, ?>) fixture.service.report(fixture.request(0)).getData();
        assertTrue(List.of(0, 1).contains(first.get("starter")));
        assertEquals(first.get("starter"), second.get("starter"));
        DataRequest changed = fixture.request(0);
        changed.add("matchMode", "local");
        assertEquals(1, fixture.service.report(changed).getCode());
        verify(fixture.rounds, times(1)).save(any(BilliardsRound.class));
    }

    /** 开球成就先保存，输掉比赛也在终局入账，终局重试只返回首次结果 */
    @Test
    void achievementRewardIsServerPricedAndIdempotent() throws Exception {
        Fixture fixture = new Fixture();
        BilliardsRound round = fixture.open();
        BilliardsJudge.State state = new BilliardsJudge.State();
        state.aiMatch = true;
        state.current = 0;
        round.setStateJson(fixture.mapper.writeValueAsString(state));
        DataRequest request = fixture.request(1);
        request.add("shot", shot(0, 1, List.of(1), List.of(), List.of()));
        request.add("amount", 9999);
        Map<?, ?> first = (Map<?, ?>) fixture.service.report(request).getData();
        Map<?, ?> retry = (Map<?, ?>) fixture.service.report(request).getData();
        assertEquals(0L, first.get("reward"));
        assertEquals(0L, retry.get("reward"));
        assertEquals(true, retry.get("repeated"));
        verify(fixture.coins, never()).awardBilliards(any(), any(), anyString(), anyString(), anyLong());
        assertEquals(0L, fixture.play.getAwardedAmount());
        BilliardsJudge.State pending = fixture.mapper.readValue(round.getStateJson(), BilliardsJudge.State.class);
        assertEquals(List.of("opening"), pending.pendingRewards);
        DataRequest ending = fixture.request(2);
        ending.add("shot", shot(0, 8, List.of(8), List.of(), List.of()));
        ending.add("amount", 9999);
        when(fixture.coins.awardBilliards(fixture.user, fixture.game,
                round.getRoundId() + ":settlement", "本局结算：开门红 +1", 1L)).thenReturn(1L);
        Map<?, ?> settled = (Map<?, ?>) fixture.service.report(ending).getData();
        Map<?, ?> repeated = (Map<?, ?>) fixture.service.report(ending).getData();
        assertEquals(1L, settled.get("reward"));
        assertEquals(1L, repeated.get("reward"));
        assertEquals(true, repeated.get("repeated"));
        verify(fixture.coins, times(1)).awardBilliards(fixture.user, fixture.game,
                round.getRoundId() + ":settlement", "本局结算：开门红 +1", 1L);
        assertEquals(1L, fixture.play.getAwardedAmount());
        BilliardsJudge.State ended = fixture.mapper.readValue(round.getStateJson(), BilliardsJudge.State.class);
        assertTrue(ended.ended);
        assertTrue(ended.pendingRewards.isEmpty());
    }

    /** 多个成就与单人获胜合并成一笔，服务只调用一次钱包入口 */
    @Test
    void achievementsAndVictoryAreSettledTogether() throws Exception {
        Fixture fixture = new Fixture();
        BilliardsRound round = fixture.open();
        BilliardsJudge.State state = wonPosition(true);
        state.pendingRewards.add("opening");
        state.pendingRewards.add("double");
        round.setStateJson(fixture.mapper.writeValueAsString(state));
        DataRequest ending = fixture.request(1);
        ending.add("shot", shot(0, 8, List.of(8), List.of(), List.of()));
        String titles = "开门红 +1、一杆双收 +4、获胜 +20";
        when(fixture.coins.awardBilliards(fixture.user, fixture.game,
                round.getRoundId() + ":settlement", "本局结算：" + titles, 25L)).thenReturn(25L);
        Map<?, ?> result = (Map<?, ?>) fixture.service.report(ending).getData();
        assertEquals(25L, result.get("reward"));
        assertEquals(titles, result.get("titles"));
        assertEquals(25L, fixture.play.getAwardedAmount());
        verify(fixture.coins, times(1)).awardBilliards(any(), any(), anyString(), anyString(), anyLong());
    }

    /** 旧版状态没有待结算字段时沿用空集合，不能把过去已发奖励再次发放 */
    @Test
    void legacyStateDoesNotRepayPreviouslyAwardedAchievements() throws Exception {
        Fixture fixture = new Fixture();
        BilliardsRound round = fixture.open();
        BilliardsJudge.State state = new BilliardsJudge.State();
        state.breaking = false;
        state.current = 0;
        state.earned = 1;
        com.fasterxml.jackson.databind.node.ObjectNode legacy = fixture.mapper.valueToTree(state);
        legacy.remove("pendingRewards");
        round.setStateJson(fixture.mapper.writeValueAsString(legacy));
        DataRequest ending = fixture.request(1);
        ending.add("shot", shot(0, 8, List.of(8), List.of(), List.of()));
        Map<?, ?> result = (Map<?, ?>) fixture.service.report(ending).getData();
        assertEquals(0L, result.get("reward"));
        verify(fixture.coins, never()).awardBilliards(any(), any(), anyString(), anyString(), anyLong());
    }

    /** 双人胜利、电脑胜利和单人胜利分别检查，只有登录玩家击败电脑产生获胜奖励 */
    @Test
    void victoryRewardRequiresBeatingAi() {
        ObjectMapper mapper = new ObjectMapper();
        for (boolean aiMatch : new boolean[]{false, true}) {
            BilliardsJudge.State state = wonPosition(aiMatch);
            List<String> earned = BilliardsJudge.apply(state,
                    mapper.valueToTree(shot(0, 8, List.of(8), List.of(), List.of())));
            assertEquals(aiMatch, earned.contains("win"));
        }
        BilliardsJudge.State computerWins = wonPosition(true);
        computerWins.current = 1;
        computerWins.groups = new int[]{2, 1};
        List<String> earned = BilliardsJudge.apply(computerWins,
                mapper.valueToTree(shot(1, 8, List.of(8), List.of(), List.of())));
        assertFalse(earned.contains("win"));
        assertEquals(20L, BilliardsRewards.AMOUNTS.get(BilliardsRewards.IDS.indexOf("win")));
    }

    /** 提前八号判负时不发成就，普通双收与三收按服务器球组区分 */
    @Test
    void foulsAndOpponentPotsDoNotProduceExtraRewards() {
        ObjectMapper mapper = new ObjectMapper();
        BilliardsJudge.State state = new BilliardsJudge.State();
        state.breaking = false;
        state.groups = new int[]{1, 2};
        List<String> earned = BilliardsJudge.apply(state,
                mapper.valueToTree(shot(0, 1, List.of(1, 2, 9), List.of(), List.of())));
        assertTrue(earned.contains("double"));
        assertFalse(earned.contains("triple"));
        BilliardsJudge.State earlyEight = new BilliardsJudge.State();
        earlyEight.breaking = false;
        earlyEight.groups = new int[]{1, 2};
        earlyEight.aiMatch = true;
        assertTrue(BilliardsJudge.apply(earlyEight,
                mapper.valueToTree(shot(0, 1, List.of(1, 2, 8), List.of(), List.of()))).isEmpty());
    }

    /** 创建只剩合法八号目标的局面，验证获胜奖励无需运行整场物理 */
    private static BilliardsJudge.State wonPosition(boolean aiMatch) {
        BilliardsJudge.State state = new BilliardsJudge.State();
        state.aiMatch = aiMatch;
        state.breaking = false;
        state.groups = new int[]{1, 2};
        for (int id = 1; id <= 7; id = id + 1) {
            state.active[id] = false;
        }
        return state;
    }

    /** 单杆事件只传球号证据，不包含客户端计算的金额和成就名称 */
    private static Map<String, Object> shot(int player, int first, List<Integer> pots,
            List<Integer> rails, List<Integer> banks) {
        return Map.of("player", player, "first", first, "pots", pots, "rails", rails,
                "bankPots", banks, "off", List.of(), "railAfter", true, "cueRailBefore", false);
    }
}

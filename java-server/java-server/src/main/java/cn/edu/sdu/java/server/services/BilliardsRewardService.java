package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.BilliardsRound;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.BilliardsRoundRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.BilliardsRewards;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.GameplayRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 台球奖励结算服务
 *
 * 处理美式八球游戏的成就奖励发放
 * 用账户锁、击球序号和唯一业务编号共同防止重复入账
 * 开球先手由服务器随机决定，客户端不能指定
 */
@Service
public class BilliardsRewardService {
    /** 服务端随机决定开球顺序，防止客户端选择对自己有利的先手 */
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final GameRepository games;
    private final GamePlayRepository plays;
    private final BilliardsRoundRepository rounds;
    private final BlockcoinService coins;
    private final ObjectMapper mapper;

    /**
     * 依赖注入
     * 复用认证、游戏归属和字块币账本，新增台球局状态仓库
     */
    public BilliardsRewardService(UserRepository users, GameRepository games, GamePlayRepository plays,
            BilliardsRoundRepository rounds, BlockcoinService coins, ObjectMapper mapper) {
        this.users = users;
        this.games = games;
        this.plays = plays;
        this.rounds = rounds;
        this.coins = coins;
        this.mapper = mapper;
    }

    /**
     * 接收击球报告并发放奖励
     * sequence=0表示开局，其他值表示第几杆
     * 奖励金额完全由服务器的成就规则决定，客户端不能指定
     * 同一击球序号重复提交会返回首次结果，不会重复发钱
     */
    @Transactional(rollbackFor = Exception.class)
    public DataResponse report(DataRequest request) throws Exception {
        User user = users.lockAccount(CommonMethod.getUserId()).orElseThrow();
        try {
            String run = request.getString("runId");
            String round = request.getString("roundId");
            Integer sequence = request.getInteger("sequence");
            if (run == null || !run.matches(GameplayRules.RUN_PATTERN)
                    || round == null || !round.matches(GameplayRules.RUN_PATTERN)
                    || sequence == null || sequence < 0 || sequence > BilliardsRewards.MAX_SHOTS) {
                throw new IllegalArgumentException("台球局号或击球序号不正确");
            }
            GamePlay play = plays.findById(user.getUserId() + ":" + run).orElseThrow(
                    new MissingPlay());
            Instant now = Instant.now();
            Game game = games.findById(play.getGameId()).orElseThrow();
            if (!BilliardsRewards.GAME_PATH.equals(game.getGamePath())
                    || !play.getChallenge().equals(request.getString("challenge"))
                    || play.getEndedAt() != null || !"official".equals(play.getMode())
                    || now.isAfter(play.getStartedAt().plusSeconds(BlockcoinRules.SESSION_SECONDS))
                    || now.isAfter(play.getUpdatedAt().plusSeconds(GameplayRules.IDLE_SECONDS))
                    || !coins.owns(user, game)) {
                throw new IllegalArgumentException("台球奖励会话无效或游戏归属已变更");
            }
            String id = play.getPlayId() + ":" + round;
            Optional<BilliardsRound> found = rounds.findById(id);
            if (sequence == 0) {
                String mode = request.getString("matchMode");
                if (mode == null || !List.of("single", "local").contains(mode)) {
                    throw new IllegalArgumentException("请选择单人或双人模式");
                }
                BilliardsRound opening;
                if (found.isEmpty()) {
                    BilliardsRound created = new BilliardsRound();
                    created.setRoundId(id);
                    created.setPlayId(play.getPlayId());
                    created.setSequence(0);
                    BilliardsJudge.State state = new BilliardsJudge.State();
                    state.aiMatch = "single".equals(mode);
                    state.current = RANDOM.nextInt(2);
                    state.starter = state.current;
                    created.setStateJson(mapper.writeValueAsString(state));
                    created.setRequestHash("");
                    created.setLastReward(0L);
                    created.setLastTitles("");
                    created.setUpdatedAt(now);
                    rounds.save(created);
                    opening = created;
                } else {
                    opening = found.get();
                }
                BilliardsJudge.State state = mapper.readValue(opening.getStateJson(), BilliardsJudge.State.class);
                if (state.aiMatch != "single".equals(mode)) {
                    throw new IllegalArgumentException("已开始的对局不能修改模式");
                }
                return CommonMethod.getReturnData(Map.of("reward", 0L, "titles",
                        "", "starter", state.starter, "matchMode", mode,
                        "ended", false));
            }
            if (found.isEmpty()) {
                throw new IllegalArgumentException("请先开始本盘台球");
            }
            BilliardsRound current = found.get();
            JsonNode shot = mapper.valueToTree(request.get("shot"));
            byte[] bytes = mapper.writeValueAsBytes(shot);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            BilliardsJudge.State state = mapper.readValue(current.getStateJson(), BilliardsJudge.State.class);
            if (sequence.equals(current.getSequence())) {
                if (!hash.equals(current.getRequestHash())) {
                    throw new IllegalArgumentException("同一击球序号不能提交不同结果");
                }
                return result(current.getLastReward(), current.getLastTitles(), true,
                        state.ended, state.winner);
            }
            if (sequence != current.getSequence() + 1) {
                throw new IllegalArgumentException("击球顺序不正确，请重新开始本盘");
            }
            if (now.isBefore(play.getStartedAt().plusSeconds(
                    (long) sequence * BilliardsRewards.MIN_SHOT_SECONDS))) {
                return CommonMethod.getReturnData(Map.of("retry", true, "reward", 0L));
            }
            List<String> earned = BilliardsJudge.apply(state, shot);
            // 每杆只记录新解锁的成就，金额由服务端规则计算
            // 本局未结束时不写钱包和消息，等整盘打完一次性结算
            for (String achievement : earned) {
                state.pendingRewards.add(achievement);
            }
            long total = 0;
            String titles = "";
            if (state.ended) {
                // 胜负与奖励一同保存，即使网页立即关闭也不会丢失已确认的结果
                String outcome = "failed";
                if (state.winner == 0) {
                    outcome = "passed";
                }
                play.setOutcome(outcome);
                List<String> details = new ArrayList<>();
                for (String achievement : state.pendingRewards) {
                    int index = BilliardsRewards.IDS.indexOf(achievement);
                    long amount = BilliardsRewards.AMOUNTS.get(index);
                    total = total + amount;
                    details.add(BilliardsRewards.TITLES.get(index) + " +" + amount);
                }
                titles = String.join("、", details);
                // 整盘使用同一个业务编号，所有成就在本局结束时一次性入账
                // 只生成一笔流水和一条站内通知，防止刷屏
                if (total > 0) {
                    total = coins.awardBilliards(user, game, id + ":settlement", "本局结算：" + titles, total);
                }
                state.pendingRewards.clear();
            }
            current.setSequence(sequence);
            current.setRequestHash(hash);
            current.setStateJson(mapper.writeValueAsString(state));
            current.setLastReward(total);
            current.setLastTitles(titles);
            current.setUpdatedAt(now);
            rounds.save(current);
            play.setAwardedAmount(play.getAwardedAmount() + total);
            play.setUpdatedAt(now);
            plays.save(play);
            return result(total, current.getLastTitles(), false, state.ended, state.winner);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }

    /**
     * 返回奖励结算结果
     * repeated标志位告诉客户端这是重复请求，不是新的入账
     */
    private DataResponse result(long amount, String titles, boolean repeated,
            boolean ended, int winner) {
        return CommonMethod.getReturnData(Map.of("reward", amount, "titles", titles,
                "repeated", repeated, "ended", ended, "winner", winner));
    }

    /**
     * 会话缺失异常
     * 当游戏会话不存在时提供明确的业务错误提示
     */
    private static final class MissingPlay implements java.util.function.Supplier<IllegalArgumentException> {
        /**
         * 返回会话不存在的异常
         * 只在仓库查询为空时被调用
         */
        @Override
        public IllegalArgumentException get() {
            return new IllegalArgumentException("游戏会话不存在");
        }
    }
}

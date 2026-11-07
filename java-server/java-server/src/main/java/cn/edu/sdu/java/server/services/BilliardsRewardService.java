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

/** 认证会话中的台球奖励结算，账户锁、击球序号和唯一账本编号共同防止重复收入 */
@Service
public class BilliardsRewardService {
    /** 服务端随机开球，不由客户端通过提交先手字段选择 */
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final GameRepository games;
    private final GamePlayRepository plays;
    private final BilliardsRoundRepository rounds;
    private final BlockcoinService coins;
    private final ObjectMapper mapper;

    /** 复用认证、游戏持有关系和原字块币账本，新增仓库只保存每盘台球状态 */
    public BilliardsRewardService(UserRepository users, GameRepository games, GamePlayRepository plays,
            BilliardsRoundRepository rounds, BlockcoinService coins, ObjectMapper mapper) {
        this.users = users;
        this.games = games;
        this.plays = plays;
        this.rounds = rounds;
        this.coins = coins;
        this.mapper = mapper;
    }

    /** 接收零号开盘或顺序击球报告，奖励金额始终由服务器成就规则决定 */
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
                return CommonMethod.getReturnData(Map.of("reward", 0L, "titles", "",
                        "starter", state.starter, "matchMode", mode));
            }
            if (found.isEmpty()) {
                throw new IllegalArgumentException("请先开始本盘台球");
            }
            BilliardsRound current = found.get();
            JsonNode shot = mapper.valueToTree(request.get("shot"));
            byte[] bytes = mapper.writeValueAsBytes(shot);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (sequence.equals(current.getSequence())) {
                if (!hash.equals(current.getRequestHash())) {
                    throw new IllegalArgumentException("同一击球序号不能提交不同结果");
                }
                return result(current.getLastReward(), current.getLastTitles(), true);
            }
            if (sequence != current.getSequence() + 1) {
                throw new IllegalArgumentException("击球顺序不正确，请重新开始本盘");
            }
            if (now.isBefore(play.getStartedAt().plusSeconds(
                    (long) sequence * BilliardsRewards.MIN_SHOT_SECONDS))) {
                return CommonMethod.getReturnData(Map.of("retry", true, "reward", 0L));
            }
            BilliardsJudge.State state = mapper.readValue(current.getStateJson(), BilliardsJudge.State.class);
            List<String> earned = BilliardsJudge.apply(state, shot);
            // 每杆只保存新解锁项目，金额仍由服务端读取，未结束时不写钱包和消息
            for (String achievement : earned) {
                state.pendingRewards.add(achievement);
            }
            long total = 0;
            String titles = "";
            if (state.ended) {
                List<String> details = new ArrayList<>();
                for (String achievement : state.pendingRewards) {
                    int index = BilliardsRewards.IDS.indexOf(achievement);
                    long amount = BilliardsRewards.AMOUNTS.get(index);
                    total = total + amount;
                    details.add(BilliardsRewards.TITLES.get(index) + " +" + amount);
                }
                titles = String.join("、", details);
                // 整盘使用唯一业务编号，一次入账只生成一笔流水和一条站内通知
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
            return result(total, current.getLastTitles(), false);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }

    /** 返回实际入账金额，重复请求仍返回首次结果但不会重新记账 */
    private DataResponse result(long amount, String titles, boolean repeated) {
        return CommonMethod.getReturnData(Map.of("reward", amount, "titles", titles, "repeated", repeated));
    }

    /** 仓库缺少会话时提供明确的业务错误，不通过匿名回调构造异常 */
    private static final class MissingPlay implements java.util.function.Supplier<IllegalArgumentException> {
        /** 返回无会话错误，只有真实仓库为空时调用 */
        @Override
        public IllegalArgumentException get() {
            return new IllegalArgumentException("游戏会话不存在");
        }
    }
}

package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.GameplayRules;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 所有游戏共用开局、计时和结束流程，关卡结果与奖励在同一事务中保存 */
@Service
public class GameplayService {
    private final UserRepository users;
    private final GameRepository games;
    private final GameRecordRepository records;
    private final GamePlayRepository plays;
    private final BlockcoinService coins;
    private final OfficialLevelCatalog levels;

    /** 复用现有账户、游戏、记录与账本，只新增会话状态仓库 */
    public GameplayService(UserRepository users, GameRepository games, GameRecordRepository records,
            GamePlayRepository plays, BlockcoinService coins, OfficialLevelCatalog levels) {
        this.users = users;
        this.games = games;
        this.records = records;
        this.plays = plays;
        this.coins = coins;
        this.levels = levels;
    }

    /** 请求中的整数按文字严格读取，小数和越界数值交给调用方返回输入错误 */
    private long number(DataRequest request, String name, long fallback) {
        String value = request.getString(name);
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(value);
    }

    /** 会话主键同时包含认证账户，跨账户请求只能查到各自的会话 */
    private String key(User user, DataRequest request) {
        String run = request.getString("runId");
        if (run == null || !run.matches(GameplayRules.RUN_PATTERN)) {
            throw new IllegalArgumentException("游戏会话编号不正确");
        }
        return user.getUserId() + ":" + run;
    }

    /** 根据已登记入口匹配商品，客户端名称、价格与奖励保持独立 */
    private Game game(DataRequest request) {
        String path = request.getString("gamePath");
        for (Game game : games.findAll()) {
            if (path != null && path.equals(game.getGamePath())) {
                return game;
            }
        }
        throw new IllegalArgumentException("游戏尚未登记");
    }

    /** 返回会话和计时参数，所有重试复用同一挑战码 */
    private DataResponse response(GamePlay play) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("challenge", play.getChallenge());
        result.put("recordId", play.getRecordId());
        result.put("outcome", play.getOutcome());
        result.put("durationSeconds", play.getActiveMilliseconds() / 1000);
        result.put("reward", play.getAwardedAmount());
        result.put("heartbeatMilliseconds", GameplayRules.HEARTBEAT_MILLISECONDS);
        return CommonMethod.getReturnData(result);
    }

    /** 只有拥有游戏的账户可以开局，试玩另行检查管理员身份 */
    private void access(User user, Game game, String mode) {
        if (!List.of("official", "practice").contains(mode)) {
            throw new IllegalArgumentException("游戏模式不正确");
        }
        if ("practice".equals(mode)) {
            if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())) {
                throw new IllegalArgumentException("试玩仅管理员可用");
            }
        } else if (!coins.owns(user, game)) {
            throw new IllegalArgumentException("请先购买游戏");
        }
    }

    /** 生成单局状态，官方文件匹配后才保存奖励，其他关卡照常记录结果 */
    private GamePlay create(User user, Game game, DataRequest request, String id, Instant now) {
        long levelValue = number(request, "level", -1);
        if (levelValue < -1 || levelValue > GameplayRules.MAX_LEVEL_NUMBER) {
            throw new IllegalArgumentException("关卡编号不正确");
        }
        Integer level = null;
        String levelName = "游戏";
        if (levelValue >= 0) {
            level = (int) levelValue;
            levelName = "第 " + (level + 1) + " 关";
        }
        String mode = request.getString("mode");
        if (mode == null) {
            mode = "official";
        }
        access(user, game, mode);
        OfficialLevelCatalog.LevelRule rule = levels.find(game.getGamePath(), level);
        long reward = 0;
        int minimum = 0;
        String hash = "";
        if (rule != null && rule.hash.equals(request.getString("hash")) && "official".equals(mode)) {
            reward = rule.reward;
            minimum = rule.minimumSeconds;
            hash = rule.hash;
        } else if (level != null && "official".equals(mode)) {
            mode = "custom";
        }
        GameRecord record = new GameRecord();
        record.setUser(user);
        record.setGame(game);
        record.setScore(0);
        record.setDurationSeconds(0);
        record.setPlayedAt(LocalDateTime.ofInstant(now, GameplayRules.RECORD_ZONE));
        records.saveAndFlush(record);
        GamePlay play = new GamePlay();
        play.setPlayId(id);
        play.setUserId(user.getUserId());
        play.setGameId(game.getGameId());
        play.setRecordId(record.getRecordId());
        play.setChallenge(UUID.randomUUID().toString());
        play.setLevelNumber(level);
        play.setLevelName(levelName);
        play.setLevelHash(hash);
        play.setMode(mode);
        play.setOutcome("playing");
        play.setMinimumSeconds(minimum);
        play.setRewardAmount(reward);
        play.setAwardedAmount(0L);
        play.setReportedMilliseconds(0L);
        play.setActiveMilliseconds(0L);
        play.setStartedAt(now);
        play.setUpdatedAt(now);
        return play;
    }

    /** 开局先锁账户并检查频率，同一编号重试只返回原会话 */
    @Transactional
    public DataResponse begin(DataRequest request) {
        User user = users.lockAccount(CommonMethod.getUserId()).orElseThrow();
        try {
            String id = key(user, request);
            Game game = game(request);
            Optional<GamePlay> previous = plays.findById(id);
            if (previous.isPresent()) {
                if (!previous.get().getGameId().equals(game.getGameId())) {
                    return CommonMethod.getReturnMessageError("会话与游戏不匹配");
                }
                return response(previous.get());
            }
            Instant now = Instant.now();
            if (plays.countByUserIdAndStartedAtAfter(user.getUserId(),
                    now.minusSeconds(BlockcoinRules.HOUR_SECONDS)) >= BlockcoinRules.RUNS_PER_HOUR) {
                return CommonMethod.getReturnMessageError("游戏启动过于频繁");
            }
            if (plays.countByUserIdAndEndedAtIsNullAndUpdatedAtAfter(user.getUserId(),
                    now.minusSeconds(GameplayRules.IDLE_SECONDS)) >= GameplayRules.MAX_ACTIVE_GAMES) {
                return CommonMethod.getReturnMessageError("同时运行的游戏已达到上限");
            }
            GamePlay play = create(user, game, request, id, now);
            plays.save(play);
            return response(play);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }

    /** 计算可以接受的累计时长，校验阶段保留实体原值 */
    private long acceptedDuration(GamePlay play, DataRequest request, Instant now) {
        long reported = number(request, "activeMilliseconds", play.getReportedMilliseconds());
        if (reported < play.getReportedMilliseconds() || reported > GameplayRules.MAX_PLAY_MILLISECONDS) {
            throw new IllegalArgumentException("游玩时长不正确");
        }
        long elapsed = Math.max(0, Duration.between(play.getUpdatedAt(), now).toMillis());
        long increase = Math.min(reported - play.getReportedMilliseconds(),
            elapsed + GameplayRules.CLOCK_GRACE_MILLISECONDS);
        long totalLimit = Math.max(0, Duration.between(play.getStartedAt(), now).toMillis())
            + GameplayRules.CLOCK_GRACE_MILLISECONDS;
        return Math.min(play.getActiveMilliseconds() + increase, totalLimit);
    }

    /** 自动记录与会话同步更新，用户已删除的展示记录继续保持删除状态 */
    private void updateRecord(GamePlay play) {
        Optional<GameRecord> found = records.findById(play.getRecordId());
        if (found.isPresent()) {
            GameRecord record = found.get();
            record.setDurationSeconds((int) (play.getActiveMilliseconds() / 1000));
            record.setPlayedAt(LocalDateTime.ofInstant(play.getUpdatedAt(), GameplayRules.RECORD_ZONE));
            records.save(record);
        }
    }

    /** 计时和结算共用身份、挑战码和过期检查，已结束会话直接返回原结果 */
    private DataResponse update(DataRequest request, boolean finish) {
        User user = users.lockAccount(CommonMethod.getUserId()).orElseThrow();
        try {
            Optional<GamePlay> found = plays.findById(key(user, request));
            if (found.isEmpty()) {
                return CommonMethod.getReturnMessageError("游戏会话不存在");
            }
            GamePlay play = found.get();
            if (!play.getChallenge().equals(request.getString("challenge"))) {
                return CommonMethod.getReturnMessageError("游戏会话凭证不正确");
            }
            if (play.getEndedAt() != null) {
                return response(play);
            }
            Instant now = Instant.now();
            if (now.isAfter(play.getStartedAt().plusSeconds(BlockcoinRules.SESSION_SECONDS))
                    || now.isAfter(play.getUpdatedAt().plusSeconds(GameplayRules.IDLE_SECONDS))) {
                return CommonMethod.getReturnMessageError("本局会话已结束");
            }
            String outcome = request.getString("outcome");
            if (finish && (outcome == null
                    || !List.of("passed", "failed", "abandoned", "closed").contains(outcome))) {
                return CommonMethod.getReturnMessageError("游戏结果不正确");
            }
            long duration = acceptedDuration(play, request, now);
            if (finish && "passed".equals(outcome) && play.getRewardAmount() > 0
                    && (now.isBefore(play.getStartedAt().plusSeconds(play.getMinimumSeconds()))
                        || duration < play.getMinimumSeconds() * 1000L)) {
                return CommonMethod.getReturnMessageError("本局结算时间不符合关卡规则");
            }
            Game game = games.findById(play.getGameId()).orElseThrow();
            if (!"practice".equals(play.getMode()) && !coins.owns(user, game)) {
                return CommonMethod.getReturnMessageError("游戏归属已变更");
            }
            play.setActiveMilliseconds(duration);
            play.setReportedMilliseconds(number(request, "activeMilliseconds", play.getReportedMilliseconds()));
            play.setUpdatedAt(now);
            if (finish) {
                play.setOutcome(outcome);
                play.setEndedAt(now);
                if ("passed".equals(outcome) && play.getRewardAmount() > 0) {
                    long amount = coins.award(user, game, play.getPlayId(), play.getLevelName(),
                        play.getRewardAmount());
                    play.setAwardedAmount(amount);
                }
            }
            updateRecord(play);
            plays.save(play);
            return response(play);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }

    /** 定期保存游玩时长，重复累计值只更新存活时间 */
    @Transactional
    public DataResponse heartbeat(DataRequest request) {
        return update(request, false);
    }

    /** 结束时一并保存结果、时长与奖励，事务失败时全部回滚 */
    @Transactional
    public DataResponse finish(DataRequest request) {
        return update(request, true);
    }
}

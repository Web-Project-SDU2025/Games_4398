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
import cn.edu.sdu.java.server.util.BilliardsRewards;
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

/**
 * 通用游戏会话管理
 *
 * 所有游戏（植物大战僵尸、台球等）都用这套统一的开局、心跳、结束流程
 * 这样设计的好处是新游戏接入时不用重复写这些逻辑，只需要调用这几个接口就行
 * 关卡结果和奖励发放在同一个数据库事务里完成，要么全成功要么全回滚
 */
@Service
public class GameplayService {
    private final UserRepository users;
    private final GameRepository games;
    private final GameRecordRepository records;
    private final GamePlayRepository plays;
    private final BlockcoinService coins;
    private final OfficialLevelCatalog levels;

    /**
     * 依赖注入
     * 复用用户、游戏、记录这些老表，只多加了plays表来保存游戏会话状态
     */
    public GameplayService(UserRepository users, GameRepository games, GameRecordRepository records,
            GamePlayRepository plays, BlockcoinService coins, OfficialLevelCatalog levels) {
        this.users = users;
        this.games = games;
        this.records = records;
        this.plays = plays;
        this.coins = coins;
        this.levels = levels;
    }

    /**
     * 从请求中解析整数参数
     * 必须是纯数字字符串才行，小数和超大数字会抛异常让调用方处理
     */
    private long number(DataRequest request, String name, long fallback) {
        String value = request.getString(name);
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(value);
    }

    /**
     * 生成会话唯一标识
     * 用账户ID和runId拼接，这样张三的会话和李四的会话不会冲突
     * 而且张三无法通过伪造runId来操作李四的游戏会话
     */
    private String key(User user, DataRequest request) {
        String run = request.getString("runId");
        if (run == null || !run.matches(GameplayRules.RUN_PATTERN)) {
            throw new IllegalArgumentException("游戏会话编号不正确");
        }
        return user.getUserId() + ":" + run;
    }

    /**
     * 根据游戏路径找到对应的游戏
     * 客户端告诉我们gamePath，我们从数据库里找到对应的游戏信息
     * 游戏名称、价格、奖励这些数据都以数据库为准，不信任客户端传的
     */
    private Game game(DataRequest request) {
        String path = request.getString("gamePath");
        for (Game game : games.findAll()) {
            if (path != null && path.equals(game.getGamePath())) {
                return game;
            }
        }
        throw new IllegalArgumentException("游戏尚未登记");
    }

    /**
     * 组装游戏会话信息返回给客户端
     * 包含挑战码、记录ID、游戏结果、时长、奖励等字段
     * 同一个runId多次请求会返回同样的挑战码，这样断线重连也能继续玩
     */
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

    /**
     * 检查游戏启动权限
     * 正式模式需要先购买游戏，试玩模式只有管理员能用
     */
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

    /**
     * 创建一个新的游戏会话
     * 会生成挑战码、记录ID、冻结奖励金额等信息
     * 只有官方关卡且文件哈希匹配的才能拿奖励，自定义关卡只记录不发钱
     * 这里"冻结"奖励金额是为了防止管理员中途改价格影响正在玩的局
     */
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
            // 开局冻结该游戏的金额，管理修改只影响之后的新局，客户端金额不能覆盖它
            reward = levels.reward(game);
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

    /**
     * 开局接口
     * 客户端要开始玩游戏时先调这个接口
     * 会检查是否频繁开局、是否同时开太多游戏等
     * 同一个runId重复请求会返回原来的会话，不会重复创建
     */
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

    /**
     * 计算实际游玩时长
     * 客户端会上报一个时长，但我们不能完全信任它（可能作弊改时长）
     * 所以会用服务器的时间戳来校验，只接受合理范围内的时长
     * 考虑到网络延迟和时钟误差，会给一些宽容度
     */
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

    /**
     * 同步更新游戏记录
     * 每次心跳或结算时都会更新对应的记录表，保持时长和时间同步
     * 如果用户在游戏库里删了这条记录，这里不会恢复它（尊重用户选择）
     */
    private void updateRecord(GamePlay play) {
        Optional<GameRecord> found = records.findById(play.getRecordId());
        if (found.isPresent()) {
            GameRecord record = found.get();
            record.setDurationSeconds((int) (play.getActiveMilliseconds() / 1000));
            record.setPlayedAt(LocalDateTime.ofInstant(play.getUpdatedAt(), GameplayRules.RECORD_ZONE));
            records.save(record);
        }
    }

    /**
     * 更新游戏会话（心跳和结算的通用逻辑）
     * finish=false表示只是心跳保活，finish=true表示这局游戏结束了
     * 会检查挑战码是否正确、会话是否超时、游戏归属是否变更等
     * 通关且有奖励的话会在这里调用字块币服务发钱
     */
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
            Game game = games.findById(play.getGameId()).orElseThrow();
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
            // 八球只信任单杆判决已保存的胜负，退出请求不能覆盖结果或自行宣称获胜
            if (finish && BilliardsRewards.GAME_PATH.equals(game.getGamePath())) {
                if ("passed".equals(play.getOutcome()) || "failed".equals(play.getOutcome())) {
                    outcome = play.getOutcome();
                } else if ("passed".equals(outcome) || "failed".equals(outcome)) {
                    return CommonMethod.getReturnMessageError("台球胜负尚未确认，请等待本盘结算");
                }
            }
            long duration = acceptedDuration(play, request, now);
            if (finish && "passed".equals(outcome) && play.getRewardAmount() > 0
                    && (now.isBefore(play.getStartedAt().plusSeconds(play.getMinimumSeconds()))
                        || duration < play.getMinimumSeconds() * 1000L)) {
                return CommonMethod.getReturnMessageError("本局结算时间不符合关卡规则");
            }
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

    /**
     * 心跳接口
     * 游戏运行过程中客户端会定期调这个接口上报时长，防止会话超时
     * 即使上报的时长和上次一样也没关系，只要调了就能续命
     */
    @Transactional
    public DataResponse heartbeat(DataRequest request) {
        return update(request, false);
    }

    /**
     * 结算接口
     * 游戏结束时调用，会保存结果（passed/failed/abandoned）、时长和奖励
     * 所有操作在一个事务里，失败的话会全部回滚
     */
    @Transactional
    public DataResponse finish(DataRequest request) {
        return update(request, true);
    }
}

package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.GameplayRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

/**
 * 游戏资料和个人关联管理
 *
 * 处理游戏的增删改查、用户游戏库、收藏和游戏记录
 * 所有写操作都用事务保证数据一致性
 */
@Service
public class GameService {
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameRecordRepository recordRepository;
    private final GamePlayRepository playRepository;
    private final RecordVisibilityService visibility;
    private final OfficialLevelCatalog levels;

    /**
     * 依赖注入
     * 保存游戏、用户和记录相关的仓库，共用实体转换方法
     */
    public GameService(GameRepository gameRepository, UserRepository userRepository,
                       GameRecordRepository recordRepository, GamePlayRepository playRepository,
                       RecordVisibilityService visibility, OfficialLevelCatalog levels) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.recordRepository = recordRepository;
        this.playRepository = playRepository;
        this.visibility = visibility;
        this.levels = levels;
    }

    /**
     * 获取当前登录用户
     * 从认证上下文获取用户ID，然后查数据库
     */
    public User currentUser() {
        return userRepository.findById(CommonMethod.getUserId()).orElseThrow();
    }

    /**
     * 组装游戏信息
     * 把游戏实体转成前端需要的Map格式，包含ID、名称、分类、介绍和奖励
     */
    public Map<String, Object> gameInfo(Game game) {
        Map<String, Object> info = new HashMap<>();
        info.put("gameId", game.getGameId());
        info.put("num", game.getNum());
        info.put("name", game.getName());
        info.put("category", game.getCategory());
        info.put("description", game.getDescription());
        info.put("gamePath", game.getGamePath());
        info.put("levelReward", levels.reward(game));
        return info;
    }

    /**
     * 查询游戏列表
     * 支持三种模式：全部游戏、游戏库、收藏
     * 可以按游戏名称或编号搜索
     */
    @Transactional(readOnly = true)
    public DataResponse list(DataRequest request) {
        String mode = request.getString("mode");
        String search = request.getString("search");
        if (search == null) {
            search = "";
        }
        User user = currentUser();
        List<Game> games = gameRepository.findAll();
        if ("library".equals(mode)) {
            games = user.getGames();
        } else if ("favorites".equals(mode)) {
            games = user.getFavorites();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Game game : games) {
            if (game.getName().contains(search) || game.getNum().contains(search)) {
                Map<String, Object> info = gameInfo(game);
                info.put("owned", user.getGames().contains(game));
                info.put("favorite", user.getFavorites().contains(game));
                results.add(info);
            }
        }
        return CommonMethod.getReturnData(results);
    }

    /**
     * 新增或修改游戏
     * 会检查游戏编号是否重复，重复的话保留原有资料
     * 通关奖励必须在0到最大值之间
     */
    @Transactional
    public DataResponse save(DataRequest request) {
        Integer gameId = request.getInteger("gameId");
        String num = request.getString("num");
        String name = request.getString("name");
        if (num == null || num.isBlank() || name == null || name.isBlank()) {
            return CommonMethod.getReturnMessageError("游戏编号和名称不能为空");
        }
        Long levelReward = null;
        if (request.get("levelReward") != null) {
            levelReward = request.getLong("levelReward");
            if (levelReward == null || levelReward < 0 || levelReward > GameplayRules.MAX_LEVEL_REWARD) {
                return CommonMethod.getReturnMessageError("通关奖励须为 0–" + GameplayRules.MAX_LEVEL_REWARD + " 的整数");
            }
        }
        Game game;
        if (gameId == null) {
            if (gameRepository.existsByNum(num)) {
                return CommonMethod.getReturnMessageError("游戏编号已经存在");
            }
            game = new Game();
        } else {
            game = gameRepository.findById(gameId).orElseThrow();
            if (!num.equals(game.getNum()) && gameRepository.existsByNum(num)) {
                return CommonMethod.getReturnMessageError("游戏编号已经存在");
            }
        }
        game.setNum(num);
        game.setName(name);
        game.setCategory(request.getString("category"));
        game.setDescription(request.getString("description"));
        game.setGamePath(request.getString("gamePath"));
        if (levelReward != null) {
            game.setLevelReward(levelReward);
        }
        gameRepository.save(game);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 删除游戏
     * 删除前要求管理员确认是否同时清理游戏记录、游戏库和收藏
     * 确认后会在同一事务里删除所有关联数据
     */
    @Transactional
    public DataResponse delete(DataRequest request) {
        Integer gameId = request.getInteger("gameId");
        if (gameId == null) {
            return CommonMethod.getReturnMessageError("请指定要删除的游戏");
        }
        Optional<Game> existing = gameRepository.findById(gameId);
        if (existing.isEmpty()) {
            return CommonMethod.getReturnMessageError("游戏已删除，请刷新列表");
        }
        Game game = existing.get();
        boolean removeRelated = request.getBoolean("removeRelated");
        List<User> users = userRepository.findAll();
        if (!removeRelated) {
            if (recordRepository.existsByGameGameId(gameId)) {
                return CommonMethod.getReturnMessageError("请确认同时清理此游戏的游玩记录、游戏库和收藏");
            }
            for (User user : users) {
                if (user.getGames().contains(game) || user.getFavorites().contains(game)) {
                    return CommonMethod.getReturnMessageError("请确认同时清理此游戏的游戏库和收藏");
                }
            }
        } else {
            // 先删关联再删游戏，所有操作在同一事务里要么全成功要么全回滚
            recordRepository.deleteByGameGameId(gameId);
            for (User user : users) {
                boolean changed = user.getGames().remove(game);
                boolean favoriteChanged = user.getFavorites().remove(game);
                if (changed || favoriteChanged) {
                    userRepository.save(user);
                }
            }
            userRepository.flush();
            recordRepository.flush();
        }
        gameRepository.delete(game);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 修改收藏
     * 游戏库由购买流程维护，这里只能改收藏
     */
    @Transactional
    public DataResponse changeCollection(DataRequest request) {
        String mode = request.getString("mode");
        if ("library".equals(mode)) {
            return CommonMethod.getReturnMessageError("游戏库由购买记录维护");
        }
        if (!"favorites".equals(mode)) {
            return CommonMethod.getReturnMessageError("未知的游戏列表");
        }
        User user = currentUser();
        Game game = gameRepository.findById(request.getInteger("gameId")).orElseThrow();
        List<Game> games = user.getFavorites();
        if (request.getBoolean("added")) {
            if (!games.contains(game)) {
                games.add(game);
            }
        } else {
            games.remove(game);
        }
        userRepository.save(user);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 组装游戏记录信息
     * 把记录实体转成前端需要的格式，包含用户、游戏、分数、时长和结果
     * 会话记录会额外显示通关状态和奖励金额
     */
    public Map<String, Object> recordInfo(GameRecord record) {
        Map<String, Object> info = gameInfo(record.getGame());
        info.put("recordId", record.getRecordId());
        info.put("userId", record.getUser().getUserId());
        info.put("username", record.getUser().getUserName());
        info.put("playerName", record.getUser().getName());
        info.put("score", record.getScore());
        info.put("durationSeconds", record.getDurationSeconds());
        info.put("playedAt", record.getPlayedAt());
        info.put("automatic", false);
        info.put("outcome", "manual");
        info.put("levelName", "");
        info.put("reward", 0);
        Optional<GamePlay> session = playRepository.findByRecordId(record.getRecordId());
        if (session.isPresent()) {
            GamePlay play = session.get();
            String outcome = play.getOutcome();
            if ("playing".equals(outcome)
                    && Instant.now().isAfter(play.getUpdatedAt().plusSeconds(GameplayRules.IDLE_SECONDS))) {
                outcome = "abandoned";
            }
            info.put("automatic", true);
            info.put("outcome", outcome);
            info.put("levelName", play.getLevelName());
            info.put("mode", play.getMode());
            info.put("reward", play.getAwardedAmount());
        }
        return info;
    }

    /**
     * 查询游戏记录
     * 管理员能看所有人的记录，普通用户只能看自己的
     */
    @Transactional(readOnly = true)
    public DataResponse records() {
        List<GameRecord> records;
        if ("ROLE_ADMIN".equals(CommonMethod.getRoleName())) {
            records = recordRepository.findAllByOrderByPlayedAtDesc();
        } else {
            records = recordRepository.findByUserUserIdOrderByPlayedAtDesc(CommonMethod.getUserId());
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (GameRecord record : records) {
            results.add(recordInfo(record));
        }
        markRecordVisibility(results);
        return CommonMethod.getReturnData(results);
    }

    /**
     * 保存游戏记录
     * 用户手动保存的记录，需要先加入游戏库才能保存
     */
    @Transactional
    public DataResponse saveRecord(DataRequest request) {
        Integer score = request.getInteger("score");
        Integer duration = request.getInteger("durationSeconds");
        if (score == null || score < 0 || duration == null || duration < 0) {
            return CommonMethod.getReturnMessageError("分数和时长必须是非负整数");
        }
        User user = currentUser();
        Game game = gameRepository.findById(request.getInteger("gameId")).orElseThrow();
        if (!user.getGames().contains(game)) {
            return CommonMethod.getReturnMessageError("请先将游戏加入游戏库");
        }
        GameRecord record = new GameRecord();
        record.setUser(user);
        record.setGame(game);
        record.setScore(score);
        record.setDurationSeconds(duration);
        record.setPlayedAt(LocalDateTime.now());
        recordRepository.save(record);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 标记记录的显示隐藏状态
     * 个人和管理列表共用，用当前查看账户的隐藏标记
     */
    public void markRecordVisibility(List<Map<String, Object>> rows) {
        visibility.mark(rows);
    }

    /**
     * 隐藏游戏记录
     * 记录本身还在，只是列表里不显示，资金和时长都保留
     */
    public DataResponse hideRecord(DataRequest request) {
        return visibility.hide(request);
    }

    /**
     * 恢复显示游戏记录
     * 把之前隐藏的记录重新显示出来
     */
    public DataResponse restoreRecord(DataRequest request) {
        return visibility.restore(request);
    }
}

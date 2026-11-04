package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 管理游戏和个人关联，写入操作使用事务保持数据一致 */
@Service
public class GameService {
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameRecordRepository recordRepository;

    /** 保存游戏、账户和记录仓库，相关操作共用实体转换方法 */
    public GameService(GameRepository gameRepository, UserRepository userRepository,
                       GameRecordRepository recordRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.recordRepository = recordRepository;
    }

    /**
     * 查询当前登录账户
     * @return 当前登录账户
     */
    public User currentUser() {
        return userRepository.findById(CommonMethod.getUserId()).orElseThrow();
    }

    /**
     * 将游戏对象整理成前端需要的资料对象
     * @param game 要转换的游戏
     * @return 包含游戏基本资料的 Map
     */
    public Map<String, Object> gameInfo(Game game) {
        Map<String, Object> info = new HashMap<>();
        info.put("gameId", game.getGameId());
        info.put("num", game.getNum());
        info.put("name", game.getName());
        info.put("category", game.getCategory());
        info.put("description", game.getDescription());
        info.put("gamePath", game.getGamePath());
        return info;
    }

    // 查询游戏列表、当前账户的游戏库或收藏
    /**
     * 根据模式查询全部游戏、当前用户的游戏库或收藏
     * @param request data 中可包含 mode 和 search
     * @return 游戏列表
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

    // 保存前校验编号是否重复，重复时保留原有资料
    /**
     * 新增或修改游戏资料
     * @param request data 中包含 gameId、游戏编号、名称、分类、介绍和入口路径
     * @return 保存成功或失败的响应
     */
    @Transactional
    public DataResponse save(DataRequest request) {
        Integer gameId = request.getInteger("gameId");
        String num = request.getString("num");
        String name = request.getString("name");
        if (num == null || num.isBlank() || name == null || name.isBlank()) {
            return CommonMethod.getReturnMessageError("游戏编号和名称不能为空");
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
        gameRepository.save(game);
        return CommonMethod.getReturnMessageOK();
    }

    // 删除没有被游戏库、收藏或游戏记录引用的游戏
    /**
     * 删除游戏，游戏仍被使用时不允许删除
     * @param request data 中包含 gameId
     * @return 删除成功或失败的响应
     */
    @Transactional
    public DataResponse delete(DataRequest request) {
        Game game = gameRepository.findById(request.getInteger("gameId")).orElseThrow();
        if (recordRepository.existsByGameGameId(game.getGameId())) {
            return CommonMethod.getReturnMessageError("游戏已有游玩记录，不能删除");
        }
        for (User user : userRepository.findAll()) {
            if (user.getGames().contains(game) || user.getFavorites().contains(game)) {
                return CommonMethod.getReturnMessageError("游戏已加入游戏库或收藏，不能删除");
            }
        }
        gameRepository.delete(game);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 修改当前用户的游戏库或收藏
     * @param request data 中包含 gameId、mode 和 added
     * @return 操作成功或失败的响应
     */
    @Transactional
    public DataResponse changeCollection(DataRequest request) {
        String mode = request.getString("mode");
        if (!"library".equals(mode) && !"favorites".equals(mode)) {
            return CommonMethod.getReturnMessageError("未知的游戏列表");
        }
        User user = currentUser();
        Game game = gameRepository.findById(request.getInteger("gameId")).orElseThrow();
        List<Game> games = user.getGames();
        if ("favorites".equals(mode)) {
            games = user.getFavorites();
        }
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
     * 将游戏记录对象整理成前端需要的资料对象
     * @param record 要转换的游戏记录
     * @return 包含账号、游戏、分数和时长的 Map
     */
    public Map<String, Object> recordInfo(GameRecord record) {
        Map<String, Object> info = gameInfo(record.getGame());
        info.put("recordId", record.getRecordId());
        info.put("userId", record.getUser().getUserId());
        info.put("username", record.getUser().getUserName());
        info.put("score", record.getScore());
        info.put("durationSeconds", record.getDurationSeconds());
        info.put("playedAt", record.getPlayedAt());
        return info;
    }

    // 管理员可以查看全部记录，游戏用户只查看自己的记录
    /**
     * 查询当前用户可以查看的游戏记录
     * @return 游戏记录列表
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
        return CommonMethod.getReturnData(results);
    }

    /**
     * 保存当前用户的一次游玩记录
     * @param request data 中包含 gameId、score 和 durationSeconds
     * @return 保存成功或失败的响应
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
     * 删除记录时校验归属，普通用户跨账户请求返回权限提示
     * @param request data 中包含 recordId
     * @return 删除成功或失败的响应
     */
    @Transactional
    public DataResponse deleteRecord(DataRequest request) {
        GameRecord record = recordRepository.findById(request.getInteger("recordId")).orElseThrow();
        if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())
                && !record.getUser().getUserId().equals(CommonMethod.getUserId())) {
            return CommonMethod.getReturnMessageError("不能删除其他用户的记录");
        }
        recordRepository.delete(record);
        return CommonMethod.getReturnMessageOK();
    }
}

package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理员记录服务
 *
 * 管理员统一维护游玩记录的游戏、分数、时长与时间
 */
@Service
public class AdminRecordService {
    private final GameRecordRepository records;
    private final GameRepository games;
    private final UserRepository users;
    private final GameService gameService;
    private final GamePlayRepository plays;

    /**
     * 依赖注入
     * 共用现有记录仓库和字段转换方法
     */
    public AdminRecordService(GameRecordRepository records, GameRepository games,
            UserRepository users, GameService gameService, GamePlayRepository plays) {
        this.records = records;
        this.games = games;
        this.users = users;
        this.gameService = gameService;
        this.plays = plays;
    }

    /**
     * 搜索记录列表
     * 按账号或游戏名搜索记录，管理员可查看全部用户的数据
     */
    @Transactional(readOnly = true)
    public DataResponse list(DataRequest request) {
        String search = request.getString("search");
        if (search == null) {
            search = "";
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameRecord record : records.findAllByOrderByPlayedAtDesc()) {
            if (record.getUser().getUserName().contains(search)
                    || record.getGame().getName().contains(search)) {
                result.add(gameService.recordInfo(record));
            }
        }
        gameService.markRecordVisibility(result);
        return CommonMethod.getReturnData(result);
    }

    /**
     * 读取非负整数
     * 小数和超范围数字返回null供调用方校验
     */
    private Integer readNumber(DataRequest request, String key) {
        String text = request.getString(key);
        try {
            int value = Integer.parseInt(text);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return null;
    }

    /**
     * 新增或修改记录
     * 管理员可以指定用户，但用户和游戏必须真实存在
     */
    @Transactional
    public DataResponse save(DataRequest request) {
        Integer userId = request.getInteger("userId");
        Integer gameId = request.getInteger("gameId");
        Integer score = readNumber(request, "score");
        Integer duration = readNumber(request, "durationSeconds");
        if (userId == null || gameId == null || score == null || duration == null) {
            return CommonMethod.getReturnMessageError("请选择账号和游戏，分数与时长须为非负整数");
        }
        Optional<User> user = users.findById(userId);
        Optional<Game> game = games.findById(gameId);
        if (user.isEmpty() || game.isEmpty()) {
            return CommonMethod.getReturnMessageError("账号或游戏不存在，请刷新后重试");
        }
        GameRecord record = new GameRecord();
        Integer recordId = request.getInteger("recordId");
        if (recordId != null) {
            if (plays.findByRecordId(recordId).isPresent()) {
                return CommonMethod.getReturnMessageError("自动记录由游戏结算维护");
            }
            Optional<GameRecord> found = records.findById(recordId);
            if (found.isEmpty()) {
                return CommonMethod.getReturnMessageError("记录不存在或已经删除");
            }
            record = found.get();
        }
        LocalDateTime playedAt = record.getPlayedAt();
        String time = request.getString("playedAt");
        if (time != null && !time.isBlank()) {
            try {
                playedAt = LocalDateTime.parse(time);
            } catch (DateTimeParseException exception) {
                return CommonMethod.getReturnMessageError("游玩时间格式不正确");
            }
        }
        if (playedAt == null) {
            playedAt = LocalDateTime.now();
        }
        record.setUser(user.get());
        record.setGame(game.get());
        record.setScore(score);
        record.setDurationSeconds(duration);
        record.setPlayedAt(playedAt);
        records.save(record);
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 隐藏记录
     * 管理员隐藏当前视图中的记录，其他账户继续使用自己的显示选择
     */
    public DataResponse hide(DataRequest request) {
        return gameService.hideRecord(request);
    }

    /**
     * 恢复记录显示
     * 恢复当前管理员的记录显示，玩家的显示选择继续独立保存
     */
    public DataResponse restore(DataRequest request) {
        return gameService.restoreRecord(request);
    }
}

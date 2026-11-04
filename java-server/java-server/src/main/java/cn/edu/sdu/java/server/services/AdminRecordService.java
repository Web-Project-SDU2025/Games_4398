package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 管理员维护游玩记录和其中的游戏分数，两种视图共用一份数据 */
@Service
public class AdminRecordService {
    private final GameRecordRepository records;
    private final GameRepository games;
    private final UserRepository users;
    private final GameService gameService;

    /** 共用记录仓库和转换方法，分数视图使用同一条记录 */
    public AdminRecordService(GameRecordRepository records, GameRepository games,
            UserRepository users, GameService gameService) {
        this.records = records;
        this.games = games;
        this.users = users;
        this.gameService = gameService;
    }

    /** 按账号或游戏名搜索记录，管理员可查看全部用户的数据 */
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
        return CommonMethod.getReturnData(result);
    }

    /** 读取非负整数，小数和超范围数字返回空值供调用方校验 */
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

    /** 新增或修改记录；管理员可以指定用户，但用户和游戏必须真实存在 */
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

    /** 按编号删除一条记录，分数视图也会同步移除这条数据 */
    @Transactional
    public DataResponse delete(DataRequest request) {
        Integer id = request.getInteger("recordId");
        if (id == null || !records.existsById(id)) {
            return CommonMethod.getReturnMessageError("记录不存在或已经删除");
        }
        records.deleteById(id);
        return CommonMethod.getReturnMessageOK();
    }
}

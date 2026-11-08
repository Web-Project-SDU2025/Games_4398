package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.GameRevocation;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.GameRevocationRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.AdminQueryRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员游戏库服务
 *
 * 管理员查看与撤回账户持有的游戏
 * 操作只改变归属并保留资金账本
 */
@Service
public class AdminGameLibraryService {
    private final UserRepository users;
    private final GameRepository games;
    private final GameRevocationRepository revocations;
    private final GamePlayRepository plays;
    private final BlockcoinService coins;
    private final GameService gameService;
    private final MessageService messages;

    /**
     * 依赖注入
     * 游戏资料与持有判断复用平台服务，撤回记录使用独立仓库
     */
    public AdminGameLibraryService(UserRepository users, GameRepository games,
            GameRevocationRepository revocations, GamePlayRepository plays,
            BlockcoinService coins, GameService gameService, MessageService messages) {
        this.users = users;
        this.games = games;
        this.revocations = revocations;
        this.plays = plays;
        this.coins = coins;
        this.gameService = gameService;
        this.messages = messages;
    }

    /**
     * 读取标识符
     * 严格读取正整数编号，小数和非法文字返回输入错误
     */
    private int identifier(DataRequest request, String name) {
        int value = Integer.parseInt(request.getString(name));
        if (value < 1) {
            throw new IllegalArgumentException("账户或游戏编号不正确");
        }
        return value;
    }

    /**
     * 列出用户游戏库
     * 按当前持有关系列出游戏，兼容旧购买归属并排除仍然有效的撤回
     */
    @Transactional(readOnly = true)
    public DataResponse list(DataRequest request) {
        try {
            int userId = identifier(request, "userId");
            Optional<User> found = users.findById(userId);
            if (found.isEmpty()) {
                return CommonMethod.getReturnMessageError("账户不存在");
            }
            List<Map<String, Object>> library = new ArrayList<Map<String, Object>>();
            User user = found.get();
            for (Game game : games.findAll()) {
                if (coins.owns(user, game)) {
                    library.add(gameService.gameInfo(game));
                }
            }
            return CommonMethod.getReturnData(library);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError("账户编号不正确");
        }
    }

    /**
     * 删除游戏关联
     * 删除目标游戏的关联时按游戏编号匹配，其余游戏和收藏保持原值
     */
    private void removeGame(List<Game> library, int gameId) {
        for (int index = library.size() - 1; index >= 0; index = index - 1) {
            if (library.get(index).getGameId().equals(gameId)) {
                library.remove(index);
            }
        }
    }

    /**
     * 停止游戏会话
     * 正在运行的会话标记为撤回，已完成的奖励与记录继续保留
     */
    private void stopSessions(int userId, int gameId, Instant now) {
        for (GamePlay play : plays.findByUserIdAndGameIdAndEndedAtIsNull(userId, gameId)) {
            play.setOutcome("revoked");
            play.setEndedAt(now);
            play.setUpdatedAt(now);
            plays.save(play);
        }
    }

    /**
     * 撤回游戏
     * 撤回使用目标账户锁，与购买和奖励串行处理，同一操作编号只执行一次
     */
    @Transactional
    public DataResponse revoke(DataRequest request) {
        if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())) {
            return CommonMethod.getReturnMessageError("仅管理员可用");
        }
        try {
            int userId = identifier(request, "userId");
            int gameId = identifier(request, "gameId");
            String id = request.getString("requestId");
            if (id == null || !UUID.fromString(id).toString().equals(id)) {
                return CommonMethod.getReturnMessageError("操作编号不正确");
            }
            String reason = request.getString("reason");
            if (reason == null || reason.isBlank()
                    || reason.trim().length() > AdminQueryRules.OPERATION_REASON_LIMIT) {
                return CommonMethod.getReturnMessageError("请填写有效的撤回原因");
            }
            Optional<User> actor = users.findById(CommonMethod.getUserId());
            if (actor.isEmpty() || !"ROLE_ADMIN".equals(actor.get().getUserType().getName())) {
                return CommonMethod.getReturnMessageError("管理员身份已变更");
            }
            Optional<User> found = users.lockAccount(userId);
            Optional<Game> selected = games.findById(gameId);
            if (found.isEmpty() || selected.isEmpty()) {
                return CommonMethod.getReturnMessageError("账户或游戏不存在");
            }
            String revocationId = actor.get().getUserId() + ":" + id;
            Optional<GameRevocation> previous = revocations.findById(revocationId);
            if (previous.isPresent()) {
                GameRevocation operation = previous.get();
                if (!operation.getUserId().equals(userId) || !operation.getGameId().equals(gameId)
                        || !operation.getReason().equals(reason.trim())) {
                    return CommonMethod.getReturnMessageError("操作编号已用于其他撤回");
                }
                return CommonMethod.getReturnMessageOK();
            }
            User user = found.get();
            Game game = selected.get();
            if (!coins.owns(user, game)) {
                return CommonMethod.getReturnMessageError("该游戏已不在此账户的游戏库中");
            }
            Instant now = Instant.now();
            GameRevocation operation = new GameRevocation();
            operation.setRevocationId(revocationId);
            operation.setUserId(userId);
            operation.setGameId(gameId);
            operation.setOperatorId(actor.get().getUserId());
            operation.setReason(reason.trim());
            operation.setCreatedAt(now);
            revocations.save(operation);
            removeGame(user.getGames(), gameId);
            users.save(user);
            stopSessions(userId, gameId, now);
            messages.notifyAccount("revoke:" + revocationId, userId, "游戏库变动",
                "管理员 " + actor.get().getUserName() + " 撤回了《" + game.getName()
                + "》\n原因：" + reason.trim() + "\n购买金额保持原结算");
            return CommonMethod.getReturnMessageOK();
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError("撤回参数不正确");
        }
    }
}

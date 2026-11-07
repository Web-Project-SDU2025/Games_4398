package billiards.rules;

import billiards.world.BallCatalog;
import billiards.world.BallGroup;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** 记录双方本局成就，读取已完成的判决，不改变进球规则和轮次 */
public final class MatchAchievements {
    /** 双收需要同杆打进的本方球数，单位为颗 */
    private static final int DOUBLE_POT_COUNT = 2;
    /** 三收需要同杆打进的最少本方球数，单位为颗 */
    private static final int TRIPLE_POT_COUNT = 3;
    /** 连下三城需要保持击球权并连续进球的杆数 */
    private static final int RUN_SHOT_COUNT = 3;
    /** 成就提示停留时间，单位为模拟毫秒 */
    private static final double NOTICE_MS = 4000.0;

    private final Set<Achievement> playerOne = EnumSet.noneOf(Achievement.class);
    private final Set<Achievement> playerTwo = EnumSet.noneOf(Achievement.class);
    private Player runPlayer;
    private int scoringShots;
    private String notice = "";
    private double remainingMs;

    /** 清空双方成就与连续进球记录，新开局和调试摆局时调用 */
    public void reset() {
        playerOne.clear();
        playerTwo.clear();
        runPlayer = null;
        scoringShots = 0;
        clearNotice();
    }

    /** 在下一杆开始时清除提示，避免旧成就遮挡新的击球画面 */
    public void clearNotice() {
        notice = "";
        remainingMs = 0.0;
    }

    /** 按模拟时间更新提示寿命，参数单位为毫秒 */
    public void tick(double elapsedMs) {
        if (remainingMs <= 0.0) {
            return;
        }
        remainingMs = remainingMs - elapsedMs;
        if (remainingMs <= 0.0) {
            clearNotice();
        }
    }

    /** 根据出杆前局面、事件记录和正式判决解锁成就，每人每项每局只提示一次 */
    public void accept(ShotContext context, ShotRecord record, ShotVerdict verdict) {
        clearNotice();
        if (verdict.isFoul()) {
            endRun();
            return;
        }

        BallGroup ownGroup = context.shooterGroup();
        if (ownGroup == null) {
            ownGroup = verdict.assignedGroup();
        }
        int ownPots = 0;
        boolean bankPot = false;
        // 开球未定组时只判断开门红，其他多球成就必须能确认球属于本方
        Set<Integer> countedBalls = new HashSet<>();
        for (int ballId : record.pocketedInOrder()) {
            if (ballId == BallCatalog.CUE_ID || ballId == BallCatalog.EIGHT_ID) {
                continue;
            }
            if (!context.objectBallsOnTableBefore().contains(ballId) || !countedBalls.add(ballId)) {
                continue;
            }
            if (context.breakShot() || BallCatalog.groupOf(ballId) == ownGroup) {
                ownPots = ownPots + 1;
                if (record.bankedBeforePocket(ballId)) {
                    bankPot = true;
                }
            }
        }
        if (ownPots == 0) {
            endRun();
            return;
        }

        Player shooter = context.shooter();
        if (runPlayer != shooter) {
            scoringShots = 0;
        }
        runPlayer = shooter;
        scoringShots = scoringShots + 1;
        StringBuilder unlocked = new StringBuilder();
        if (context.breakShot()) {
            unlock(shooter, Achievement.OPENING_POT, unlocked);
        } else {
            if (ownPots >= TRIPLE_POT_COUNT) {
                unlock(shooter, Achievement.TRIPLE_POT, unlocked);
            } else if (ownPots == DOUBLE_POT_COUNT) {
                unlock(shooter, Achievement.DOUBLE_POT, unlocked);
            }
            if (bankPot) {
                unlock(shooter, Achievement.BANK_POT, unlocked);
            }
            if (record.cueRailBeforeFirstContact()) {
                unlock(shooter, Achievement.KICK_POT, unlocked);
            }
        }
        if (scoringShots >= RUN_SHOT_COUNT) {
            unlock(shooter, Achievement.THREE_SHOT_RUN, unlocked);
        }
        if (unlocked.length() > 0) {
            notice = shooter.labelZh() + " · " + unlocked;
            remainingMs = NOTICE_MS;
        }
        if (!verdict.continues()) {
            endRun();
        }
    }

    /** 结束连续进球计数，换手或犯规后不能接着累计 */
    private void endRun() {
        runPlayer = null;
        scoringShots = 0;
    }

    /** 首次解锁时记录并追加标题，已有成就不重复提示 */
    private void unlock(Player player, Achievement achievement, StringBuilder unlocked) {
        if (!earned(player).add(achievement)) {
            return;
        }
        if (unlocked.length() > 0) {
            unlocked.append(" · ");
        }
        unlocked.append(achievement.title());
    }

    /** 按玩家返回内部成就集合，只供本类读写 */
    private Set<Achievement> earned(Player player) {
        if (player == Player.ONE) {
            return playerOne;
        }
        return playerTwo;
    }

    /** 返回玩家是否已在本局解锁指定成就 */
    public boolean has(Player player, Achievement achievement) {
        return earned(player).contains(achievement);
    }

    /** 返回当前提示文字，没有新成就或提示到期时返回空串 */
    public String notice() {
        return notice;
    }

    /** 返回指定玩家的本局成就标题，顺序固定，供结算页显示 */
    public String summary(Player player) {
        StringBuilder text = new StringBuilder();
        for (Achievement achievement : earned(player)) {
            if (text.length() > 0) {
                text.append(" / ");
            }
            text.append(achievement.title());
        }
        return text.toString();
    }
}

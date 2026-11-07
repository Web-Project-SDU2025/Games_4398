package billiards.rules;

import billiards.world.BallCatalog;
import billiards.world.BallGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 把「当时什么局面」+「这一杆发生了什么」判成一份 {@link ShotVerdict}
 *
 * <h2>判定的顺序是有讲究的</h2>
 * <ol>
 *   <li><b>先把八条犯规全查一遍</b>，一条不漏。不短路是因为一次犯规可能触发多条，
 *       全列出来玩家才看得全（「主球落袋、且球飞出台面」比只说一条有用）</li>
 *   <li><b>再看 8 号是不是本杆落袋</b>（且非开球）。是的话直接裁胜负，自由球判罚不再适用
 *       ——比赛都结束了</li>
 *   <li>否则按「无犯规 + 合法进球 = 续杆」定下一手</li>
 * </ol>
 *
 * <h2>赢球要求本杆零犯规，这不是苛刻</h2>
 * 「8 号和主球同杆落袋」在真实球台上极其常见，而它必须判负——否则只要瞄准 8 号猛打一杆，
 * 进了就赢、不进也无非是主球落袋，风险与收益完全不对等。所以赢的条件写成
 * 「8 号落袋 <b>且</b> 犯规清单为空 <b>且</b> 本方组已清空」，三个条件缺一不可；
 * 任何一个不满足都落到判负那一支
 */
public final class FoulDetector {

    /** 工具类只提供静态方法，不需要创建对象 */
    private FoulDetector() {
    }

    /** 判定一杆。纯函数：两个入参进去，一份判决书出来 */
    public static ShotVerdict judge(ShotContext context, ShotRecord record) {
        List<FoulType> fouls = new ArrayList<>();
        for (FoulType foul : FoulType.values()) {
            if (foul.detect(context, record)) {
                fouls.add(foul);
            }
        }

        boolean eightPotted = record.pocketed(BallCatalog.EIGHT_ID) && !context.breakShot();
        if (eightPotted) {
            return judgeEightBall(context, record, fouls);
        }

        // 开放台面上合法进球才定组：犯规的一杆即使进了球也不定组，开球那一杆也不定组
        BallGroup assigned = null;
        if (context.tableOpen() && !context.breakShot() && fouls.isEmpty()) {
            assigned = GroupAssigner.assignFrom(record.objectBallsPocketedInOrder());
        }

        // 本组球是否打进过至少一颗。台面未定组时，任何一颗目标球都算——因为这一杆打进的
        // 第一颗就要成为本方的组（见上面 assigned）
        boolean pottedOwn = pottedOwnBall(context, record);
        boolean continues = fouls.isEmpty() && pottedOwn;

        Player next = context.shooter();
        if (!continues) {
            next = context.shooter().opponent();
        }
        return new ShotVerdict(context.shooter(), fouls, assigned, next, continues, null);
    }

    /**
     * 8 号落袋的两种归宿：赢，或者输
     *
     * <p>判负的理由优先取更具体的那条：{@link FoulType#EIGHT_POCKETED_EARLY}（本方组还没清完）
     * 比 {@link FoulType#EIGHT_WITH_FOUL}（别的犯规）更贴近玩家的第一直觉，所以两条同时成立时
     * 报前者。两条都不成立时才是赢
     */
    private static ShotVerdict judgeEightBall(ShotContext context, ShotRecord record, List<FoulType> fouls) {
        boolean cleared = context.groupClearedAfter(record);
        if (fouls.isEmpty() && cleared) {
            MatchOutcome outcome = MatchOutcome.wonBy(context.shooter(), "8 号球入袋，本杆无犯规");
            return new ShotVerdict(context.shooter(), fouls, null, context.shooter(), false, outcome);
        }

        String reason;
        if (fouls.contains(FoulType.EIGHT_POCKETED_EARLY)) {
            reason = "8 号球提前落袋（本方还有球在台上）";
        } else if (fouls.contains(FoulType.EIGHT_WITH_FOUL)) {
            reason = "8 号球与犯规同杆落袋：" + firstOtherFoulLabel(fouls);
        } else {
            // 其余未满足获胜条件的结果统一判负，避免未知条件被当作合法获胜
            reason = "8 号球落袋，但本杆条件不满足";
        }
        MatchOutcome outcome = MatchOutcome.lostBy(context.shooter(), reason);
        return new ShotVerdict(context.shooter(), fouls, null, context.shooter().opponent(), false, outcome);
    }

    /** 查找八号球判负之外的第一项犯规名称，供结果文案说明原因 */
    private static String firstOtherFoulLabel(List<FoulType> fouls) {
        for (FoulType foul : fouls) {
            if (!foul.isEightBallVerdict()) {
                return foul.labelZh();
            }
        }
        return "其他犯规";
    }

    /** 本杆是否打进了「本方球」 */
    private static boolean pottedOwnBall(ShotContext context, ShotRecord record) {
        if (context.breakShot()) {
            // 开球这一杆还没有「本方球」可言：只要有一颗目标球落袋，开球权就留下
            // 8 号落袋也算——它会被摆回置球点，等于「没有球真的离开台面」，
            // 而 WPA 在这一条上同样把续杆权留给开球方
            return !record.objectBallsPocketedInOrder().isEmpty();
        }
        for (int ballId : record.objectBallsPocketedInOrder()) {
            if (ballId == BallCatalog.EIGHT_ID) {
                continue;
            }
            if (context.tableOpen() || BallCatalog.groupOf(ballId) == context.shooterGroup()) {
                return true;
            }
        }
        return false;
    }
}

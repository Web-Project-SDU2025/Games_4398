package billiards.rules;

import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一杆球<b>开始之前</b>的局面快照——判罚的另一半输入
 *
 * <p>{@link ShotRecord} 回答「发生了什么」，这里回答「当时是什么局面」。两者合起来才能判：
 * 「先碰到 3 号」是合规还是犯规，取决于击球方当时的组属；而组属在球停下来之后可能已经变了
 * （本杆刚好定组、刚好清组），所以必须在出杆前把局面<b>冻结</b>下来
 *
 * <p>这也是它做成只读对象 的原因：判罚函数拿到的是一个不会中途变化的事实集合，
 * 「判定看到一半局面被改了」这种 bug 在类型上就不存在
 *
 * <h2>为什么存「在台球号集合」而不是存 {@code List<Ball>}</h2>
 * 判罚只关心「某组还有没有球在台上」「8 号在不在台上」这类<b>集合</b>问题，不关心球在哪
 * 存球对象会让规则与可变状态绑在一起（球一滚，快照就变了），存球号则天然是快照
 */
public final class ShotContext {
    private final Player shooter;
    private final BallGroup shooterGroup;
    private final boolean breakShot;
    private final Set<Integer> objectBallsOnTableBefore;
    private final boolean eightOnTableBefore;

    /** 保存构造参数，字段只读以便安全传递状态 */
    public ShotContext(Player shooter, BallGroup shooterGroup, boolean breakShot, Set<Integer> objectBallsOnTableBefore, boolean eightOnTableBefore) {

        if (shooter == null) {
            throw new IllegalArgumentException("击球方不能为空");
        }
        objectBallsOnTableBefore = Collections.unmodifiableSet(new LinkedHashSet<>(objectBallsOnTableBefore));
            this.shooter = shooter;
        this.shooterGroup = shooterGroup;
        this.breakShot = breakShot;
        this.objectBallsOnTableBefore = objectBallsOnTableBefore;
        this.eightOnTableBefore = eightOnTableBefore;
    }

    /** 返回保存的击球方 */
    public Player shooter() {
        return shooter;
    }

    /** 返回保存的击球方组属 */
    public BallGroup shooterGroup() {
        return shooterGroup;
    }

    /** 返回保存的开球标记 */
    public boolean breakShot() {
        return breakShot;
    }

    /** 返回保存的出杆前在台球号 */
    public Set<Integer> objectBallsOnTableBefore() {
        return objectBallsOnTableBefore;
    }

    /** 返回保存的出杆前八号球状态 */
    public boolean eightOnTableBefore() {
        return eightOnTableBefore;
    }

    /** 由球列直接构造，省得每个调用点都自己筛一遍在台球 */
    public static ShotContext of(Player shooter, BallGroup shooterGroup, boolean breakShot,
                                 Iterable<Ball> balls) {
        Set<Integer> onTable = new LinkedHashSet<>();
        boolean eight = false;
        for (Ball ball : balls) {
            if (!ball.isOnTable() || ball.isCueBall()) {
                continue;
            }
            onTable.add(ball.id());
            if (ball.id() == BallCatalog.EIGHT_ID) {
                eight = true;
            }
        }
        return new ShotContext(shooter, shooterGroup, breakShot, onTable, eight);
    }

    /** 台面是否尚未定组（开球之后、还没有人合法进球） */
    public boolean tableOpen() {
        return shooterGroup == null;
    }

    /** 击球方的组属的对方组；台面未定组时返回 {@code null} */
    public BallGroup opponentGroup() {
        if (tableOpen()) {
            return null;
        }
        return shooterGroup.oppositeGroup();
    }

    /**
     * 本杆<b>开始前</b>本方组是否已经清空（只剩 8 号）
     *
     * <p>它决定 8 号是不是合法目标。用「开始前」而不是「结束后」：一杆的合法性由出杆那一刻
     * 的局面决定。同一杆里先打进本方最后一颗、再把 8 号带进去是<b>赢</b>（清组与进 8 同杆成立），
     * 那一杆的首次触球发生在本方球上，本来就合法
     */
    public boolean groupClearedBefore(ShotRecord record) {
        return !tableOpen() && countOnTable(shooterGroup, objectBallsOnTableBefore) == 0;
    }

    /**
     * 本杆结束后本方组是否已清空——把本杆打进的球扣掉再数
     *
     * <p>这是「同杆清组 + 进 8 号 = 赢」那条规则的全部实现：它不关心本组最后一颗是哪一杆进的，
     * 只问「这一杆打完，台上还有没有我的球」。于是「先清组再打 8」与「清组和 8 同杆进」
     * 自动走同一条判定，不需要为后者写特例
     */
    public boolean groupClearedAfter(ShotRecord record) {
        if (tableOpen()) {
            return false;
        }
        Set<Integer> remaining = new HashSet<>(objectBallsOnTableBefore);
        remaining.removeAll(record.pocketedInOrder());
        // 飞出台面的球会被重新摆回台上，所以不从这里扣——扣了会把「清组」判早了
        return countOnTable(shooterGroup, remaining) == 0;
    }

    /** 本杆的<b>合法目标球</b>集合（首次触球必须落在其中） */
    public Set<Integer> legalTargets(ShotRecord record) {
        Set<Integer> out = new LinkedHashSet<>();
        if (breakShot) {
            out.addAll(objectBallsOnTableBefore);
            return out;
        }
        if (tableOpen()) {
            for (int ballId : objectBallsOnTableBefore) {
                if (ballId != BallCatalog.EIGHT_ID) {
                    out.add(ballId);
                }
            }
            return out;
        }
        if (groupClearedBefore(record)) {
            if (objectBallsOnTableBefore.contains(BallCatalog.EIGHT_ID)) {
                out.add(BallCatalog.EIGHT_ID);
            }
            return out;
        }
        for (int ballIdValue : objectBallsOnTableBefore) {
            if (BallCatalog.groupOf(ballIdValue) == shooterGroup) {
                out.add(ballIdValue);
            }
        }
        return out;
    }

    /** 首次触球是否合法 */
    public boolean isLegalFirstContact(ShotRecord record) {
        if (!record.firstContactOccurred()) {
            return false;
        }
        return legalTargets(record).contains(record.firstContactBallId());
    }

    /** 统计给定球号集合中属于指定组的球数 */
    private static int countOnTable(BallGroup group, Set<Integer> ids) {
        int count = 0;
        for (int ballId : ids) {
            if (BallCatalog.groupOf(ballId) == group) {
                count = count + 1;
            }
        }
        return count;
    }
}

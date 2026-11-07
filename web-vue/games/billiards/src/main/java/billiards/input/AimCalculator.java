package billiards.input;

import billiards.physics.CollisionMath;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.PhysicsConfig;
import billiards.world.RailSegment;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.List;

/**
 * 瞄准预览的几何计算：从主球出发的那条射线最先撞上什么、主球球心会停在哪里、
 * 撞上之后两颗球各自往哪走
 *
 * <p>纯函数、无状态——给定台面与球位必有确定输出，所以能在自检里直接断言，不必先跑物理
 * 这也让它与渲染完全解耦：算出结果的时机（模拟线程）和用它的时机（EDT）可以不同，
 * 中间只隔一个 {@code AimView}
 *
 * <h2>「球心停在哪」与「射线打到哪」不是一回事</h2>
 * 这是这个类唯一值得一提的地方。射线与库边线的交点，是主球<b>边缘</b>碰到库边线的位置；
 * 而画瞄准线要的是主球<b>球心</b>停下处，比那个交点早 {@code R / |dir·n|}
 * 正射时差一个 R（看着不明显），斜 10° 入射时差 5.75R ≈ 63px——那时瞄准线会深深扎进
 * 库边里，看着就像球会陷进去。同理，撞球时的终点取的是「两球心距 2R」那个位置，
 * 也就是 {@link CollisionMath#rayCircleFirstHitTime} 传半径 2R 得到的 t，而不是传 R
 *
 * <h2>分离线为什么是这两条</h2>
 * 等质量、无旋转的两球碰撞里，目标球必定沿<b>两球心连线</b>被推出，主球则必定<b>近似垂直</b>
 * 于那条连线离开——这就是台球里经典的「90° 定律」，也是「用虚影球瞄准」这个操作之所以
 * 成立的全部理由：把虚影球摆到目标球心与袋口连线上，目标球就会朝袋口去
 *
 * <p>主球那条线不是近似的垂线，而是<b>直接套冲量公式</b>：
 * {@code v₁' = d − ((1+e)/2)(d·n)n}（{@code d} = 来向，{@code n} = 两球心连线）
 * 这样做有两个好处：一是它天然连续，从正撞到极薄的切球都不用分段讨论；二是它自带开球那
 * 一杆的正确答案——正撞（{@code d·n = 1}）时它退化成 {@code d(1−e)/2}，也就是「主球沿原
 * 方向慢慢跟进」，而不是画一条横着的线（正撞时主球绝不横着飞）。{@code e = 1} 时它恰好是
 * {@code d} 垂直于 {@code n} 的分量，那条教科书上的 90° 定律就自然落在里面
 */
public final class AimCalculator {

    /** 工具类只提供静态方法，不需要创建对象 */
    private AimCalculator() {
    }

    /**
     * 一次瞄准预览的全部几何
     *
     * @param ghost           首接触时主球球心的落点，也就是要画虚影球的地方
     *                        <b>{@code null} 表示这一杆不会撞到任何东西</b>——射线从袋口
     *                        缺口穿出台面就是这种情况，此时没有虚影球也没有分离线可画
     * @param targetId        被撞球的球号；撞库或没撞到为 {@code -1}
     * @param targetSeparation 目标球的离开方向（单位向量）；没撞到球时为 {@code null}
     * @param cueSeparation   主球的离开方向（单位向量）；没撞到球时为 {@code null}
     */
    public static final class Preview {
        private final Vec2 ghost;
        private final int targetId;
        private final Vec2 targetSeparation;
        private final Vec2 cueSeparation;

            /** 「什么都撞不到」 */
        public static final Preview NOTHING = new Preview(null, -1, null, null);

/** 保存构造参数，字段只读以便安全传递状态 */
        public Preview(Vec2 ghost, int targetId, Vec2 targetSeparation, Vec2 cueSeparation) {
            this.ghost = ghost;
            this.targetId = targetId;
            this.targetSeparation = targetSeparation;
            this.cueSeparation = cueSeparation;
        }

        /** 返回保存的虚影球位置 */
        public Vec2 ghost() {
            return ghost;
        }

        /** 返回保存的目标球号 */
        public int targetId() {
            return targetId;
        }

        /** 返回保存的目标球分离方向 */
        public Vec2 targetSeparation() {
            return targetSeparation;
        }

        /** 返回保存的主球分离方向 */
        public Vec2 cueSeparation() {
            return cueSeparation;
        }

        /** 这一杆是否会撞上东西（球或库边） */
        public boolean hitAnything() {
            return ghost != null;
        }

        /** 是否撞到了球（而非库边） */
        public boolean hitBall() {
            return targetId >= 0;
        }
    }

    /**
     * 求主球沿 {@code dir} 击出后的首次接触及其后续几何
     *
     * @param origin 主球球心
     * @param dir    单位方向向量
     * @param balls  场上全部球；主球自己会被跳过
     */
    public static Preview preview(Vec2 origin, Vec2 direction, Table table, List<Ball> balls) {
        if (direction.lengthSq() < 1e-18) {
            return Preview.NOTHING;
        }

        double best = Double.POSITIVE_INFINITY;
        int bestId = -1;
        Vec2 bestBallCentre = null;

        // ---- 球：两球心距 2R 即为接触，所以拿半径 2R 求交 ----
        for (Ball ball : balls) {
            if (ball.id() == BallCatalog.CUE_ID || !ball.isOnTable()) {
                continue;
            }
            double timeFraction = CollisionMath.rayCircleFirstHitTime(origin, direction, ball.position(), 2.0 * Ball.RADIUS);
            if (!Double.isNaN(timeFraction) && timeFraction < best) {
                best = timeFraction;
                bestId = ball.id();
                bestBallCentre = ball.position();
            }
        }

        // ---- 库边：交点再往回退 R/|dir·n|，才是球心停下处 ----
        for (RailSegment rail : table.rails()) {
            double normalProjection = direction.dot(rail.inwardNormal());
            if (normalProjection >= 0.0) {
                continue; // 朝台外走，撞不到这段库边的内侧
            }
            double timeFractionValue = CollisionMath.rayRailHitTime(
                    origin, direction, rail.a(), rail.b(), rail.inwardNormal());
            if (Double.isNaN(timeFractionValue)) {
                continue;
            }
            double centreStop = timeFractionValue - Ball.RADIUS / Math.abs(normalProjection);
            if (centreStop < 0.0) {
                centreStop = 0.0;
            }
            if (centreStop < best) {
                best = centreStop;
                bestId = -1;
                bestBallCentre = null;
            }
        }

        if (!Double.isFinite(best)) {
            return Preview.NOTHING;
        }

        Vec2 ghost = origin.plus(direction.times(best));

        if (bestBallCentre == null) {
            // 撞库：没有第二颗球，也就没有分离线
            return new Preview(ghost, -1, null, null);
        }

        // 目标球沿两球心连线离开。用球心减虚影球心而不是反过来，才是指向目标球外侧的方向
        // 取这条线时两球心距恰好是 2R，所以球心 = 虚影球心 + 2R × 这个方向——渲染器正是
        // 靠这条关系把目标球心重建出来的，自检里也按这条断言
        Vec2 targetSeparation = bestBallCentre.minus(ghost).normalized();
        if (targetSeparation.lengthSq() < 0.5) {
            // 两球心重合（理论上只有球位重叠时才会出现），连线方向无从谈起
            return new Preview(ghost, bestId, null, null);
        }

        // 主球离开方向：等质量碰撞的冲量公式本身，见类注释
        double along = direction.dot(targetSeparation);
        Vec2 cueSeparation = direction.minus(
                targetSeparation.times((1.0 + PhysicsConfig.BALL_RESTITUTION) * 0.5 * along));

        if (cueSeparation.lengthSq() < 1e-6) {
            // 恢复系数接近一时主球可能停住，此时不应生成没有实际方向的分离线
            return new Preview(ghost, bestId, targetSeparation, null);
        }

        return new Preview(ghost, bestId, targetSeparation, cueSeparation.normalized());
    }
}

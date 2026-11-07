package billiards.rules;

import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * 按标准摆法把 15 颗目标球摆到置球点右侧的三角形球架上，并放好主球
 *
 * <p>摆法本身是数据（{@link BallCatalog#standardRack()}），几何才是算法：每排向右
 * 推进 {@code spacing * √3/2}，排内上下均分。这样相邻球心距离恰好等于
 * {@code 2R + RACK_GAP}——三角形各边等长，是正三角形排列，因此静止时不会互相挤压
 *
 * <p>摆球不含任何随机量。真实比赛会随机打乱球号，但随机摆球会让自检无法复现，
 * 而本工程要求「同一杆跑两次结果位一致」，所以固定成一种合法排布
 */
public final class RackBuilder {

        /** 开球时主球的默认位置：开球区偏后一点的中线上。玩家可以在开球区内拖动它 */
    public static final double DEFAULT_BREAK_CUE_X =
            Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.15;

    /** 摆球后的球心间距。正三角形边长 */
    public static final double SPACING = 2.0 * Ball.RADIUS + PhysicsConfig.RACK_GAP;

/** 工具类只提供静态方法，不需要创建对象 */
    private RackBuilder() {
    }

    /**
     * 造出完整的一局球：下标 0 是主球，其余 15 颗依次按标准摆法就位
     */
    public static List<Ball> buildRack() {
        List<Ball> balls = new ArrayList<>(BallCatalog.BALL_COUNT);

        balls.add(new Ball(BallCatalog.CUE_ID,
                new Vec2(DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY())));

        int[] rack = BallCatalog.standardRack();
        double deltaX = SPACING * Math.sqrt(3.0) / 2.0;

        int index = 0;
        for (int row = 0; row < BallCatalog.RACK_ROWS; row = row + 1) {
            int size = BallCatalog.rowSize(row);
            for (int col = 0; col < size; col = col + 1) {
                double x = Layout.FOOT_SPOT_X + row * deltaX;
                // 每排相对中线上下均分：第 row 排的偏移是 (col - row/2)
                double y = Layout.FOOT_SPOT_Y + (col - row / 2.0) * SPACING;
                balls.add(new Ball(rack[index], new Vec2(x, y)));
                index = index + 1;
            }
        }
        return balls;
    }

    /** 从球列里取主球；找不到返回 {@code null} */
    public static Ball cueBall(List<Ball> balls) {
        return ballById(balls, BallCatalog.CUE_ID);
    }

    /** 按球号查找指定球，列表中不存在时返回 null */
    public static Ball ballById(List<Ball> balls, int ballId) {
        for (Ball ball : balls) {
            if (ball.id() == ballId) {
                return ball;
            }
        }
        return null;
    }

    /** 成对球心的最小间距，供自检断言摆球不重叠且间距符合预期 */
    public static double minPairDistance(List<Ball> balls) {
        double min = Double.MAX_VALUE;
        for (int index = 0; index < balls.size(); index = index + 1) {
            for (int otherIndex = index + 1; otherIndex < balls.size(); otherIndex = otherIndex + 1) {
                double distance = balls.get(index).position().distanceTo(balls.get(otherIndex).position());
                if (distance < min) {
                    min = distance;
                }
            }
        }
        return min;
    }
}

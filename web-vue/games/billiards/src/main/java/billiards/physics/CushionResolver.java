package billiards.physics;

import billiards.world.Ball;
import billiards.world.PhysicsConfig;
import billiards.world.RailSegment;
import billiards.world.ShotEventListener;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.List;

/**
 * 库边碰撞：把球推回相切，并按恢复系数反射法向速度
 *
 * <p>库边被切成 6 段（见 {@code Table}），所以这里没有「上边还是下边」「是不是袋口」
 * 这类分支——遍历每一段，段自己知道法向、起止与覆盖范围。球从袋口缺口穿过时，
 * 没有任何一段覆盖那个位置，于是它自然就过去了。角袋不需要一行特殊代码
 *
 * <p>法向冲量控制反弹，切向摩擦同时改变沿库边速度和侧旋
 * 两种冲量分开计算，使每一步的受力方向与恢复系数都能单独验证
 */
public final class CushionResolver {

    /** 工具类只提供静态方法，不需要创建对象 */
    private CushionResolver() {
    }

    /**
     * 解算所有在台球与所有库边段的接触
     *
     * <p>{@code covers} 的容差取一个球半径：库边末端因此带了个「端帽」，球贴着
     * 库边末端滚过时会被端帽推回台内，不会从末端和袋口之间的缝里漏出去
     */
    public static void resolve(List<Ball> balls, Table table, List<ShotEventListener> listeners) {
        List<RailSegment> rails = table.rails();

        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }
            for (int index = 0; index < rails.size(); index = index + 1) {
                RailSegment rail = rails.get(index);
                Vec2 position = ball.position();

                double distance = rail.signedDistanceTo(position);
                if (distance >= Ball.RADIUS) {
                    continue; // 没碰到
                }
                if (!rail.covers(position, Ball.RADIUS)) {
                    continue; // 这个位置根本没有库边（袋口缺口）
                }

                Vec2 normal = rail.inwardNormal();

                // 推回到刚好相切
                ball.setPosition(position.plus(normal.times(Ball.RADIUS - distance)));
                position = ball.position();

                // 反射：只反号并缩放法向分量
                Vec2 velocity = ball.velocity();
                double normalSpeed = velocity.dot(normal);
                if (normalSpeed < 0.0) {
                    double normalImpulse = -(1.0 + PhysicsConfig.CUSHION_RESTITUTION)
                            * normalSpeed;
                    Vec2 reflectedVelocity = velocity.plus(normal.times(normalImpulse));
                    reflectedVelocity = applyCushionFriction(
                            ball, rail, reflectedVelocity, normalImpulse);
                    ball.setVelocity(reflectedVelocity);
                    if (listeners != null) {
                        // 反射前法向分量是负的，取绝对值才是「撞上去有多重」
                        double impact = -normalSpeed;
                        for (ShotEventListener listener : listeners) {
                            listener.onCushionContact(ball.id(), index, impact);
                        }
                    }
                }
            }
        }
    }

    /** 库边切向摩擦改变球的侧向速度，并带动球绕竖直轴旋转 */
    private static Vec2 applyCushionFriction(Ball ball, RailSegment rail,
                                             Vec2 velocity, double normalImpulse) {
        // 先测接触点沿库边的滑动，再限制切向冲量，避免反弹凭空增加摩擦力
        Vec2 tangent = rail.tangent();
        double rotationDirection = tangent.cross(rail.inwardNormal());
        double contactSlip = velocity.dot(tangent)
                + Ball.RADIUS * ball.spinZ() * rotationDirection;
        double inverseMassAndSpin = 1.0
                + Ball.RADIUS * Ball.RADIUS
                / (PhysicsConfig.BALL_INERTIA_FACTOR * Ball.RADIUS * Ball.RADIUS);
        double tangentImpulse = -contactSlip / inverseMassAndSpin;
        double maximumImpulse = PhysicsConfig.CUSHION_FRICTION * normalImpulse;

        if (tangentImpulse > maximumImpulse) {
            tangentImpulse = maximumImpulse;
        }
        if (tangentImpulse < -maximumImpulse) {
            tangentImpulse = -maximumImpulse;
        }

        double spinChange = Ball.RADIUS * tangentImpulse * rotationDirection
                / (PhysicsConfig.BALL_INERTIA_FACTOR * Ball.RADIUS * Ball.RADIUS);
        ball.setSpin(ball.spinX(), ball.spinY(), ball.spinZ() + spinChange);
        return velocity.plus(tangent.times(tangentImpulse));
    }
}

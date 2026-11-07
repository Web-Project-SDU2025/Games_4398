package billiards.physics;

import billiards.world.Ball;
import billiards.world.PhysicsConfig;
import billiards.world.ShotEventListener;
import billiards.world.Vec2;

import java.util.List;

/**
 * 球与球的碰撞：等质量冲量 + 完全分离的位置修正
 *
 * <p>只有两个公式，但每一行都有理由：
 *
 * <ol>
 *   <li><b>法向冲量</b>：两球沿连心线交换速度分量，再由切向摩擦交换侧向速度和旋转
 *       摩擦会使斜切后的分离角度偏离无摩擦情况下的理想值</li>
 *   <li><b>{@code vRel <= 0} 时直接跳过</b>：这是「静止的摆球不会产生冲量」的原因
 *       少了这个守卫，一局开始时 15 颗相切球会互相弹开，台面瞬间炸成一团</li>
 *   <li><b>位置修正做完全分离</b>，而不是业界常见的「0.8 比例 + 松弛容差」
 *       那套 slop 方案是为了避免球与<b>静态几何体</b>（地面、墙）接触时抖动而存在的；
 *       本工程场面上只有球，没有静态体，完全分离既简单又不会抖</li>
 * </ol>
 *
 * <p>多个球挤在一起时一个球对可能被解算多次，所以外层做 {@code CONTACT_ITERATIONS}
 * 轮松弛。事件只在第 0 轮上报，避免同一对球刷出一串重复接触
 */
public final class CollisionResolver {

    /** 工具类只提供静态方法，不需要创建对象 */
    private CollisionResolver() {
    }

    /**
     * 解算场上所有在台球的碰撞
     *
     * @param iterations 松弛轮数
     * @param restitution 恢复系数（等质量下 {@code 1.0} 意味着正撞时击球完全停住）
     * @param listeners 接触事件接收者；可为 {@code null}
     */
    public static void resolve(List<Ball> balls, int iterations, double restitution,
                               List<ShotEventListener> listeners) {
        for (int iteration = 0; iteration < iterations; iteration = iteration + 1) {
            resolveIteration(balls, iteration, restitution, listeners);
        }
    }

    /** 解算一轮所有球对，只在第一轮通知外部碰撞事件 */
    private static void resolveIteration(List<Ball> balls, int iteration, double restitution,
                                         List<ShotEventListener> listeners) {
        List<ShotEventListener> listenersForRound = listeners;
        if (iteration != 0) {
            listenersForRound = null;
        }

        for (int firstIndex = 0; firstIndex < balls.size(); firstIndex = firstIndex + 1) {
            Ball firstBall = balls.get(firstIndex);
            if (!firstBall.isOnTable()) {
                continue;
            }

            for (int secondIndex = firstIndex + 1; secondIndex < balls.size(); secondIndex = secondIndex + 1) {
                Ball secondBall = balls.get(secondIndex);
                if (!secondBall.isOnTable()) {
                    continue;
                }
                resolvePair(firstBall, secondBall, restitution, listenersForRound);
            }
        }
    }

    /**
     * 解算一对球。可单独调用，自检 P1–P3 就是直接喂这一对球来验证冲量公式的
     *
     * @param listeners 为 {@code null} 则不上报接触
     */
    public static void resolvePair(Ball firstBall, Ball secondBall, double restitution,
                                   List<ShotEventListener> listeners) {
        // 大多数球对相距较远，先用坐标判断距离，只为真正接触的球创建向量
        double distanceX = secondBall.position().x - firstBall.position().x;
        double distanceY = secondBall.position().y - firstBall.position().y;
        double distSq = distanceX * distanceX + distanceY * distanceY;
        final double minDist = 2.0 * Ball.RADIUS;

        if (distSq >= minDist * minDist) {
            return; // 没接触
        }

        double distance = Math.sqrt(distSq);
        Vec2 delta = new Vec2(distanceX, distanceY);
        Vec2 normal = collisionNormal(delta, distSq, distance);

        // 先分开重叠球，再根据碰撞前速度决定是否需要冲量
        separateBalls(firstBall, secondBall, normal, minDist - distance);

        double normalSpeed = approachingSpeed(firstBall, secondBall, normal);
        if (normalSpeed <= 0.0) {
            return;
        }

        applyImpulse(firstBall, secondBall, normal, normalSpeed, restitution);
        applyTangentialFriction(firstBall, secondBall, normal, normalSpeed, restitution);
        notifyListeners(firstBall, secondBall, normal, normalSpeed, listeners);
    }

    /** 根据两球心差值计算碰撞法线，球心重合时固定朝右 */
    private static Vec2 collisionNormal(Vec2 delta, double distanceSquared, double distance) {
        if (distanceSquared < 1e-12) {
            return new Vec2(1.0, 0.0);
        }
        return new Vec2(delta.x / distance, delta.y / distance);
    }

    /** 将重叠的两球各自推开一半重叠距离 */
    private static void separateBalls(Ball firstBall, Ball secondBall,
                                      Vec2 normal, double overlap) {
        if (overlap <= 0.0) {
            return;
        }

        Vec2 correction = normal.times(overlap * 0.5);
        firstBall.setPosition(firstBall.position().minus(correction));
        secondBall.setPosition(secondBall.position().plus(correction));
    }

    /** 计算两球沿碰撞法线互相接近的速度，分离或静止时返回零或负值 */
    private static double approachingSpeed(Ball firstBall, Ball secondBall, Vec2 normal) {
        Vec2 relativeVelocity = firstBall.velocity().minus(secondBall.velocity());
        return relativeVelocity.dot(normal);
    }

    /** 按等质量球的恢复系数更新两球速度 */
    private static void applyImpulse(Ball firstBall, Ball secondBall, Vec2 normal,
                                     double normalSpeed, double restitution) {
        double impulse = (1.0 + restitution) * 0.5 * normalSpeed;
        firstBall.setVelocity(firstBall.velocity().minus(normal.times(impulse)));
        secondBall.setVelocity(secondBall.velocity().plus(normal.times(impulse)));
    }

    /** 用受限切向冲量交换两球的侧向速度与竖直轴旋转 */
    private static void applyTangentialFriction(Ball firstBall, Ball secondBall,
                                                Vec2 normal, double normalSpeed,
                                                double restitution) {
        // 接触点的切向速度决定摩擦方向，冲量上限由库仑摩擦系数给出
        Vec2 tangent = normal.perpendicular();
        Vec2 relativeVelocity = firstBall.velocity().minus(secondBall.velocity());
        double contactSlip = relativeVelocity.dot(tangent)
                + Ball.RADIUS * (firstBall.spinZ() + secondBall.spinZ());
        double inverseMassAndSpin = 2.0
                + 2.0 * Ball.RADIUS * Ball.RADIUS
                / (PhysicsConfig.BALL_INERTIA_FACTOR * Ball.RADIUS * Ball.RADIUS);
        double impulse = contactSlip / inverseMassAndSpin;
        double normalImpulse = (1.0 + restitution) * 0.5 * normalSpeed;
        double maximumImpulse = PhysicsConfig.BALL_CONTACT_FRICTION * normalImpulse;

        if (impulse > maximumImpulse) {
            impulse = maximumImpulse;
        }
        if (impulse < -maximumImpulse) {
            impulse = -maximumImpulse;
        }

        Vec2 tangentImpulse = tangent.times(impulse);
        firstBall.setVelocity(firstBall.velocity().minus(tangentImpulse));
        secondBall.setVelocity(secondBall.velocity().plus(tangentImpulse));

        double spinChange = Ball.RADIUS * impulse
                / (PhysicsConfig.BALL_INERTIA_FACTOR * Ball.RADIUS * Ball.RADIUS);
        firstBall.setSpin(firstBall.spinX(), firstBall.spinY(), firstBall.spinZ() - spinChange);
        secondBall.setSpin(secondBall.spinX(), secondBall.spinY(), secondBall.spinZ() - spinChange);
    }

    /** 把碰撞前的接近速度通知规则与声音监听者 */
    private static void notifyListeners(Ball firstBall, Ball secondBall, Vec2 normal,
                                        double normalSpeed,
                                        List<ShotEventListener> listeners) {
        if (listeners == null) {
            return;
        }

        Vec2 contactPoint = firstBall.position().plus(normal.times(Ball.RADIUS));
        for (ShotEventListener listener : listeners) {
            listener.onBallBallContact(firstBall.id(), secondBall.id(), contactPoint, normalSpeed);
        }
    }
}

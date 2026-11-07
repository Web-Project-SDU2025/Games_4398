package billiards.physics;

import billiards.world.Ball;
import billiards.world.PhysicsConfig;
import billiards.world.Vec2;

/** 更新台呢摩擦和球体运动，独立于碰撞事件与物理调度 */
public final class ClothMotion {

    /** 工具类只提供静态方法，不需要创建对象 */
    private ClothMotion() {
    }

    /** 按给定秒数更新球的滑动与滚动，返回球心在本步走过的距离 */
    public static double advance(Ball ball, double dtSub) {
        Vec2 slipVelocity = ball.surfaceSlipVelocity();
        double slipSpeed = slipVelocity.length();
        double remainingTime = dtSub;
        double travel = 0.0;

        if (slipSpeed > PhysicsConfig.STOP_SPEED) {
            // 先按接触点滑动速度推进，摩擦才能同时改变平移和旋转
            Vec2 slidingStart = ball.position();
            double slideTime = integrateSliding(ball, slipVelocity, slipSpeed, dtSub);
            remainingTime = dtSub - slideTime;
            travel = slidingStart.distanceTo(ball.position());
        } else {
            // 消除累计误差，让接下来的运动满足球底不打滑的条件
            ball.matchRollingSpin();
        }

        if (remainingTime > 0.0 && ball.isMoving()) {
            // 一个子步可能跨过滑动结束时刻，所以把剩余时间用于滚动阶段
            Vec2 start = ball.position();
            integrateRolling(ball, remainingTime);
            double rollingTravel = start.distanceTo(ball.position());
            travel = travel + rollingTravel;
        }

        if (ball.speed() <= PhysicsConfig.STOP_SPEED) {
            ball.stop();
        }
        return travel;
    }

    /** 滑动时，台呢摩擦同时改变球心速度与滚动角速度 */
    private static double integrateSliding(Ball ball, Vec2 slipVelocity,
                                           double slipSpeed, double dtSub) {
        double slidingDeceleration = PhysicsConfig.slidingFrictionDeceleration();
        double inertiaFactor = PhysicsConfig.BALL_INERTIA_FACTOR;
        double slipDeceleration = slidingDeceleration * (1.0 + 1.0 / inertiaFactor);
        double slipTime = slipSpeed / slipDeceleration;
        double usedTime = Math.min(dtSub, slipTime);

        Vec2 slipDirection = slipVelocity.times(1.0 / slipSpeed);
        Vec2 acceleration = slipDirection.times(-slidingDeceleration);
        Vec2 startPosition = ball.position();
        Vec2 startVelocity = ball.velocity();

        // 子步内摩擦加速度保持不变，使用位移公式可减少步长带来的误差
        Vec2 velocityTravel = startVelocity.times(usedTime);
        Vec2 accelerationTravel = acceleration.times(0.5 * usedTime * usedTime);
        Vec2 displacement = velocityTravel.plus(accelerationTravel);
        ball.setPosition(startPosition.plus(displacement));
        ball.setVelocity(startVelocity.plus(acceleration.times(usedTime)));

        double angularChange = slidingDeceleration * usedTime
                / (inertiaFactor * Ball.RADIUS);
        double spinX = ball.spinX() - slipDirection.y * angularChange;
        double spinY = ball.spinY() + slipDirection.x * angularChange;
        ball.setSpin(spinX, spinY, ball.spinZ());

        if (usedTime >= slipTime) {
            // 滑动结束后修正浮点误差，确保接下来确实满足纯滚动条件
            ball.matchRollingSpin();
        }
        return usedTime;
    }

    /** 纯滚动阶段只受较弱的滚动阻力影响 */
    private static void integrateRolling(Ball ball, double dtSub) {
        Vec2 velocity = ball.velocity();
        double speed = velocity.length();
        if (speed <= 0.0) {
            return;
        }

        // 滚动阻力只作用于已经满足无滑动条件的球
        double deceleration = PhysicsConfig.rollingResistanceDeceleration();
        double stopTime = speed / deceleration;
        double usedTime = Math.min(dtSub, stopTime);
        Vec2 direction = velocity.times(1.0 / speed);
        double averageSpeed = speed - 0.5 * deceleration * usedTime;
        Vec2 displacement = direction.times(averageSpeed * usedTime);
        Vec2 finalVelocity = direction.times(speed - deceleration * usedTime);

        ball.setPosition(ball.position().plus(displacement));
        ball.setVelocity(finalVelocity);
        ball.matchRollingSpin();

        double spinZ = ball.spinZ();
        double spinDeceleration = deceleration / Ball.RADIUS;
        double remainingSpin = Math.abs(spinZ) - spinDeceleration * usedTime;
        if (remainingSpin < 0.0) {
            remainingSpin = 0.0;
        }
        if (spinZ < 0.0) {
            remainingSpin = -remainingSpin;
        }
        ball.setSpin(ball.spinX(), ball.spinY(), remainingSpin);
    }

}

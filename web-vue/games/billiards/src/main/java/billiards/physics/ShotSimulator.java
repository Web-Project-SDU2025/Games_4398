package billiards.physics;

import billiards.world.PhysicsConfig;

/**
 * 无头推进到静止——只给自检用
 *
 * <p>刻意不自己写循环体：它反复调用的是与实时循环完全相同的 {@link PhysicsEngine#step()}
 * 因此自检里跑出来的轨迹就是玩家在窗口里看到的轨迹，「测试通过」这四个字才有意义
 *
 * <p>用<b>模拟</b>毫秒而非墙钟计时，所以同一杆无论机器多慢，步数都一样
 */
public final class ShotSimulator {

    /** 工具类只提供静态方法，不需要创建对象 */
    private ShotSimulator() {
    }

    /** 一杆跑完的结果 */
    public static final class Result {
        public final int steps;
        public final double simulatedMs;
        public final boolean rested;
        public final boolean timedOut;

        /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
        Result(int steps, double simulatedMs, boolean rested, boolean timedOut) {
            this.steps = steps;
            this.simulatedMs = simulatedMs;
            this.rested = rested;
            this.timedOut = timedOut;
        }
    }

    /** 一直步进到全部静止；超过 {@code maxSimMs} 模拟毫秒仍不停则强制停下 */
    public static Result simulateToRest(PhysicsEngine engine, double maxSimMs) {
        double elapsed = 0.0;
        int steps = 0;
        while (!engine.allAtRest() && elapsed < maxSimMs) {
            engine.step();
            elapsed = elapsed + PhysicsConfig.FIXED_STEP_MS;
            steps = steps + 1;
        }
        boolean rested = engine.allAtRest();
        if (!rested) {
            engine.stopAll();
        }
        return new Result(steps, elapsed, rested, !rested);
    }

    /** 用默认超时跑完一杆 */
    public static Result simulateToRest(PhysicsEngine engine) {
        return simulateToRest(engine, PhysicsConfig.SHOT_TIMEOUT_SIM_MS);
    }
}

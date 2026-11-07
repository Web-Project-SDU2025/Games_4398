package billiards.app;

import billiards.world.PhysicsConfig;

/**
 * 固定步累加器：把不规则的墙钟间隔换算成<b>整数个</b>固定步
 *
 * <p>这是整个工程确定性主张的落点。物理引擎本身没有任何时间来源——它只有一个
 * {@code step()}，每次前进精确的 {@link PhysicsConfig#FIXED_STEP_MS}。墙钟唯一的
 * 作用是在这里决定「这一帧该补几步」。于是同一杆在快机器和慢机器上跑出的轨迹
 * 位一致，只看补了多少步，而步数不影响每步内部的计算
 *
 * <p>单独成类是为了可测：喂它「一整块 1000ms」和「100 块 10ms」，总步数必须相同——
 * 这条断言就是「累加器不漏步、不漂移」的证明
 *
 * <p>刻意用整步计数而不是「把 dt 直接交给物理」：后者会让不同帧率下的积分结果不同，
 * 是这类游戏最常见的不确定性来源
 */
public final class FixedStepAccumulator {

    private final double stepMs;
    private final int maxStepsPerFrame;

    /** 尚未凑够一整步的余量（毫秒） */
    private double pendingMs;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public FixedStepAccumulator() {
        this(PhysicsConfig.FIXED_STEP_MS, PhysicsConfig.MAX_STEPS_PER_FRAME);
    }

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public FixedStepAccumulator(double stepMs, int maxStepsPerFrame) {
        if (stepMs <= 0.0) {
            throw new IllegalArgumentException("stepMs 必须为正: " + stepMs);
        }
        if (maxStepsPerFrame < 1) {
            throw new IllegalArgumentException("maxStepsPerFrame 必须 ≥ 1: " + maxStepsPerFrame);
        }
        this.stepMs = stepMs;
        this.maxStepsPerFrame = maxStepsPerFrame;
    }

    /**
     * 记入一段墙钟时间，返回本帧应当执行的固定步数（0 到 {@code maxStepsPerFrame}）
     *
     * <p>触顶时余量被<b>丢弃</b>而不是继续累积：否则一次卡顿（或调试断点）之后
     * 会连续补上百步，形成越补越慢的死亡螺旋。丢弃的代价是那段墙钟时间对应的
     * 模拟没有发生——对单机台球游戏而言，这比卡死好得多
     */
    public int advance(double wallDtMs) {
        if (wallDtMs <= 0.0) {
            return 0;
        }
        pendingMs = pendingMs + wallDtMs;

        int steps = 0;
        while (pendingMs >= stepMs && steps < maxStepsPerFrame) {
            pendingMs = pendingMs - stepMs;
            steps = steps + 1;
        }
        if (steps == maxStepsPerFrame) {
            pendingMs = 0.0;
        }
        return steps;
    }

    /** 返回尚未凑够一个固定步的模拟余量，单位为毫秒 */
    public double pendingMs() {
        return pendingMs;
    }

    /** 返回固定步长，单位为毫秒 */
    public double stepMs() {
        return stepMs;
    }

    /** 返回每帧最多追赶的步数 */
    public int maxStepsPerFrame() {
        return maxStepsPerFrame;
    }

    /** 清空累积状态，让下一次操作从初始值开始 */
    public void reset() {
        pendingMs = 0.0;
    }
}

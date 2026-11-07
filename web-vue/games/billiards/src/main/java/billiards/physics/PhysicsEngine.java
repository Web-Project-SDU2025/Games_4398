package billiards.physics;

import billiards.world.Ball;
import billiards.world.PhysicsConfig;
import billiards.world.ShotEventListener;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性物理引擎。实时循环与自检跑的是同一份 {@code step()}，不存在「测试专用路径」
 *
 * <h2>一次 step 做什么</h2>
 * <ol>
 *   <li>速度钳制：若某球快到「一个子步会走超过 {@code MAX_SUBSTEP_DISPLACEMENT}」，
 *       把<b>所有</b>球的速度按比例缩放。这一步让「每子步位移 ≤ 8px」成为真正的不变量，
 *       而不是一厢情愿的期望——静默截断子步数会让穿透悄悄回来</li>
 *   <li>按最快球决定子步数 {@code n = ceil(vMax · dt / 8)}，钳制后必然 {@code n ≤ MAX_SUBSTEPS}</li>
 *   <li>每个子步依次做：摩擦积分 → 球球碰撞（松弛若干轮）→ 库边 → 入袋
 *       入袋放最后，使被捕获的球在下一子步前就离场</li>
 * </ol>
 *
 * <h2>摩擦与积分为什么写在一起</h2>
 * 摩擦是恒定减速，所以一步内的位移有闭式解。若按「先用旧速度积分、再减速」（显式欧拉）
 * 或「先减速、再用新速度积分」（半隐式），总行程都会带上 {@code ±v0·h/2} 的系统偏差——
 * 对 {@code v0 = 1500}、{@code h = 0.005} 就是 ±3.75px，远超自检想达到的精度
 * 这里改用本步的<b>平均速度</b>积分，恒定减速下它是精确解，于是「滚动距离 = v0²/(2a)」
 * 可以用 0.05px 的容差硬断言。代价只是多一次乘加
 *
 * <h2>确定性</h2>
 * 迭代顺序固定（球按列表下标、球对按 {@code i < j}），热路径里没有 {@code HashMap}、
 * 没有 {@code Math.random}、没有墙钟读取。距离用 {@code Math.sqrt} 而非
 * {@code Math.hypot}（后者只保证 1 ulp 且各 JDK 实现有差异），
 * 积分循环里不出现 {@code atan2}。因此同样的输入必然给出位一致的输出
 */
public final class PhysicsEngine {

    private final Table table;
    private final List<Ball> balls;
    private final List<ShotEventListener> listeners = new ArrayList<>(4);

    // ---- 诊断（默认关闭，仅在自检与调试叠加层里打开） ----
    private boolean diagnosticsEnabled = false;
    private double worstSubstepTravel = 0.0;
    private double worstPairOverlap = 0.0;
    private int substepsLastStep = 0;
    private long totalSubsteps = 0L;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public PhysicsEngine(Table table, List<Ball> balls) {
        this.table = table;
        this.balls = balls;
    }

    // 访问

    /** 返回构造后不再改变的球台几何 */
    public Table table() {
        return table;
    }

    /** 返回模拟线程管理的球列表，界面应读取快照 */
    public List<Ball> balls() {
        return balls;
    }

    /** 注册物理事件监听者，事件发生时按注册顺序通知 */
    public void addListener(ShotEventListener listener) {
        listeners.add(listener);
    }

    /** 清空物理事件监听者，不修改球的运动 */
    public void clearListeners() {
        listeners.clear();
    }

    // 状态查询

    /** 当前最大速率（只算在台球） */
    public double maxSpeed() {
        double max = 0.0;
        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }
            double strength = ball.speed();
            if (strength > max) {
                max = strength;
            }
        }
        return max;
    }

    /**
     * 是否全部静止。这是良定义的条件而非永不终结的浮点比较——因为摩擦是恒定减速，
     * 速度在有限时间内严格降到零，并且低于 {@code STOP_SPEED} 时会被直接归零
     */
    public boolean allAtRest() {
        for (Ball ball : balls) {
            if (ball.isOnTable() && ball.isMoving()) {
                return false;
            }
        }
        return true;
    }

    /** 统计并返回仍在台上的球数 */
    public int ballsOnTable() {
        int count = 0;
        for (Ball ball : balls) {
            if (ball.isOnTable()) {
                count = count + 1;
            }
        }
        return count;
    }

    /** 立即停止所有在台球，模拟超时和重新开局时调用 */
    public void stopAll() {
        for (Ball ball : balls) {
            ball.stop();
        }
    }

    // 步进

    /** 推进一个固定步长 */
    public void step() {
        step(PhysicsConfig.FIXED_STEP_MS);
    }

    /**
     * 推进 {@code dtMs} 毫秒。墙钟只用来决定子步怎么切，不影响每个子步内部的计算
     */
    public void step(double dtMs) {
        if (dtMs <= 0.0) {
            return;
        }
        double dt = dtMs / 1000.0;

        clampSpeeds(dt);

        int substeps = (int) Math.ceil(maxSpeed() * dt / PhysicsConfig.MAX_SUBSTEP_DISPLACEMENT);
        if (substeps < 1) {
            substeps = 1;
        }
        if (substeps > PhysicsConfig.MAX_SUBSTEPS) {
            // 钳制之后不该发生；留着是为了让不变量被破坏时立刻可见，而不是悄悄穿透
            substeps = PhysicsConfig.MAX_SUBSTEPS;
        }
        substepsLastStep = substeps;
        totalSubsteps = totalSubsteps + substeps;

        double dtSub = dt / substeps;
        for (int substepIndex = 0; substepIndex < substeps; substepIndex = substepIndex + 1) {
            substep(dtSub);
        }
    }

    /** 推进一段子步时间，再依序处理碰撞、库边和落袋 */
    private void substep(double dtSub) {
        double travel = 0.0;
        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }
            double moved = ClothMotion.advance(ball, dtSub);
            if (moved > travel) {
                travel = moved;
            }
        }
        if (diagnosticsEnabled && travel > worstSubstepTravel) {
            worstSubstepTravel = travel;
        }

        CollisionResolver.resolve(balls, PhysicsConfig.CONTACT_ITERATIONS,
                PhysicsConfig.BALL_RESTITUTION, listeners);
        CushionResolver.resolve(balls, table, listeners);
        PocketResolver.resolve(balls, table, listeners);

        if (diagnosticsEnabled) {
            double overlap = maxPairOverlap();
            if (overlap > worstPairOverlap) {
                worstPairOverlap = overlap;
            }
        }
    }

    /**
     * 把任何「一个子步会走超过 {@code MAX_SUBSTEP_DISPLACEMENT}」的速度按比例压下来
     *
     * <p>正常参数下永不触发：{@code MAX_SUBSTEPS · MAX_SUBSTEP_DISPLACEMENT / dt}
     * 在 5ms 步长下是 12800 px/s，而满力开球才 3000 px/s。它是防穿透不变量的保险丝
     */
    private void clampSpeeds(double dt) {
        double limit = PhysicsConfig.MAX_SUBSTEPS * PhysicsConfig.MAX_SUBSTEP_DISPLACEMENT / dt;
        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }
            double speed = ball.speed();
            if (speed > limit) {
                ball.setVelocity(ball.velocity().times(limit / speed));
            }
        }
    }

    /** 场上最深的球球重叠量（px）。诊断用 */
    private double maxPairOverlap() {
        double worst = 0.0;
        final double minDist = 2.0 * Ball.RADIUS;
        for (int index = 0; index < balls.size(); index = index + 1) {
            Ball firstBall = balls.get(index);
            if (!firstBall.isOnTable()) {
                continue;
            }
            for (int otherIndex = index + 1; otherIndex < balls.size(); otherIndex = otherIndex + 1) {
                Ball secondBall = balls.get(otherIndex);
                if (!secondBall.isOnTable()) {
                    continue;
                }
                double overlap = minDist - firstBall.position().distanceTo(secondBall.position());
                if (overlap > worst) {
                    worst = overlap;
                }
            }
        }
        return worst;
    }

    // 诊断

    /**
     * 打开诊断统计。自检 P8 用它逐子步核对「位移 ≤ 8px、无残余重叠」这条不变量；
     * 调试叠加层用它显示子步数。关闭时不做任何额外计算
     */
    public void setDiagnosticsEnabled(boolean enabled) {
        this.diagnosticsEnabled = enabled;
    }

    /** 返回是否启用了额外的物理诊断统计 */
    public boolean isDiagnosticsEnabled() {
        return diagnosticsEnabled;
    }

    /** 清空已累计的位移、重叠和子步统计 */
    public void resetDiagnostics() {
        worstSubstepTravel = 0.0;
        worstPairOverlap = 0.0;
        totalSubsteps = 0L;
    }

    /** 自上次 {@link #resetDiagnostics()} 以来，单个子步内的最大位移（px） */
    public double worstSubstepTravel() {
        return worstSubstepTravel;
    }

    /** 自上次 {@link #resetDiagnostics()} 以来，任一子步结束时的最深球球重叠（px） */
    public double worstPairOverlap() {
        return worstPairOverlap;
    }

    /** 返回最近一个固定步使用的子步数 */
    public int substepsLastStep() {
        return substepsLastStep;
    }

    /** 返回诊断重置后累计执行的子步数 */
    public long totalSubsteps() {
        return totalSubsteps;
    }
}

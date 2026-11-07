package billiards.selfcheck;

import billiards.app.FixedStepAccumulator;
import billiards.physics.CollisionResolver;
import billiards.physics.CushionResolver;
import billiards.physics.PhysicsEngine;
import billiards.physics.ShotSimulator;
import billiards.rules.RackBuilder;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Pocket;
import billiards.world.RailSegment;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 物理检查 P1–P11
 *
 * <p>这些断言之所以能写成硬判断，是因为摩擦阶段、球体碰撞和库边反弹都有明确公式
 * 调参因此变成测量而不是猜，改了摩擦系数或恢复系数，对应的检查会给出新的期望值
 */
public final class PhysicsChecks {

    /** 工具类只提供静态方法，不需要创建对象 */
    private PhysicsChecks() {
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    public static void run(CheckHarness checks) {
        p1HeadOnElastic(checks);
        p2HeadOnRestitution(checks);
        p3CutAngle(checks);
        p4RollingDistance(checks);
        p5PocketCapture(checks);
        p6GrazingCushion(checks);
        p7CushionReflection(checks);
        p8NoTunnelingInvariant(checks);
        p9Determinism(checks);
        p10RackStability(checks);
        p11Conservation(checks);
    }

    // P1 / P2 / P3 —— 冲量公式，直接喂一对球给 CollisionResolver

    /** 检查P1 完全弹性正撞：击球停住，将验证结果记入传入的检查报告 */
    private static void p1HeadOnElastic(CheckHarness checks) {
        Ball striker = ballWithVelocity(0, 400.0, 280.0, 500.0, 0.0);
        Ball target = ballWithVelocity(1, 400.0 + 21.9, 280.0, 0.0, 0.0);

        Vec2 momentumBefore = momentumOf(striker, target);
        double energyBefore = kineticEnergyOf(striker, target);

        CollisionResolver.resolvePair(striker, target, 1.0, null);

        checks.checkNear("P1 完全弹性正撞：击球停住", 0.0, striker.speed(), 1e-9);
        checks.checkNear("P1 完全弹性正撞：目标球获得全部速度", 500.0, target.velocity().x, 1e-9);
        checkVectorNear(checks, "P1 完全弹性正撞：动量守恒", momentumBefore, momentumOf(striker, target), 1e-9);
        checks.checkNear("P1 完全弹性正撞：动能守恒", energyBefore, kineticEnergyOf(striker, target), 1e-6);
    }

    /** 检查P2 恢复系数 e：击球末速 = v0(1-e)/2，将验证结果记入传入的检查报告 */
    private static void p2HeadOnRestitution(CheckHarness checks) {
        final double restitution = PhysicsConfig.BALL_RESTITUTION;
        Ball striker = ballWithVelocity(0, 400.0, 280.0, 500.0, 0.0);
        Ball target = ballWithVelocity(1, 400.0 + 21.9, 280.0, 0.0, 0.0);

        Vec2 momentumBefore = momentumOf(striker, target);
        double energyBefore = kineticEnergyOf(striker, target);

        CollisionResolver.resolvePair(striker, target, restitution, null);

        checks.checkNear("P2 恢复系数 e：击球末速 = v0(1-e)/2", 500.0 * (1.0 - restitution) / 2.0,
                striker.velocity().x, 1e-9);
        checks.checkNear("P2 恢复系数 e：目标球末速 = v0(1+e)/2", 500.0 * (1.0 + restitution) / 2.0,
                target.velocity().x, 1e-9);
        checkVectorNear(checks, "P2 恢复系数 e：动量仍守恒", momentumBefore, momentumOf(striker, target), 1e-9);

        // 等质量正撞、目标静止时，动能比 = (1 + e²) / 2
        // 注意不是 e²——那是把「相对速度按 e 衰减」直接当成「动能按 e² 衰减」得出的，
        // 只在两球速度都非零的一般情形下才成立。e=1 时本式为 1，e=0 时为 0.5
        double expectedRatio = (1.0 + restitution * restitution) / 2.0;
        checks.checkNear("P2 恢复系数 e：动能比 = (1+e²)/2",
                expectedRatio, kineticEnergyOf(striker, target) / energyBefore, 1e-9);
    }

    /** 检查P3 斜切：目标球方向符合切向摩擦计算，将验证结果记入传入的检查报告 */
    private static void p3CutAngle(CheckHarness checks) {
        // 45° 斜切：法向取 45° 方向
        Vec2 normal = new Vec2(Math.sqrt(0.5), Math.sqrt(0.5));
        Ball striker = ballWithVelocity(0, 400.0, 280.0, 500.0, 0.0);
        Ball target = ballWithVelocity(1,
                400.0 + normal.x * 21.9, 280.0 + normal.y * 21.9, 0.0, 0.0);

        CollisionResolver.resolvePair(striker, target, 1.0, null);

        Vec2 firstVelocity = striker.velocity();
        Vec2 secondVelocity = target.velocity();

        checks.checkNear("P3 斜切：目标球方向符合切向摩擦计算",
                40.426079, Math.toDegrees(Math.atan2(secondVelocity.y, secondVelocity.x)), 1e-5);
        checks.check("P3 斜切：两球速度都小于入射速度",
                firstVelocity.length() < 500.0 && secondVelocity.length() < 500.0,
                String.format("击球 %.3f、目标 %.3f，入射 500", firstVelocity.length(), secondVelocity.length()));

        // 切向摩擦会改变两球的分离方向，因此检查加入摩擦后的夹角
        double cosAngle = firstVelocity.dot(secondVelocity) / (firstVelocity.length() * secondVelocity.length());
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosAngle))));
        checks.checkNear("P3 斜切：摩擦后的分离夹角符合计算", 85.426079, angle, 1e-5);
    }

    // P4 —— 滑动转为滚动后的停止距离与停止时间

    /** 检查无初始旋转的球从滑动进入滚动后的总行程与停止时间 */
    private static void p4RollingDistance(CheckHarness checks) {
        double slidingDeceleration = PhysicsConfig.slidingFrictionDeceleration();
        double slipDeceleration = slidingDeceleration
                * (1.0 + 1.0 / PhysicsConfig.BALL_INERTIA_FACTOR);
        double rollingDeceleration = PhysicsConfig.rollingResistanceDeceleration();
        double[] speeds = {40.0, 60.0, 80.0};

        for (double initialSpeed : speeds) {
            double startX = Layout.PLAYFIELD_LEFT + Ball.RADIUS + 1.0;
            Ball ball = ballWithVelocity(0, startX, Layout.playfieldCenterY(), initialSpeed, 0.0);
            PhysicsEngine engine = engineOf(ball);

            ShotSimulator.Result result = ShotSimulator.simulateToRest(engine);

            double rollingSpeed = initialSpeed
                    / (1.0 + PhysicsConfig.BALL_INERTIA_FACTOR);
            double slidingTime = initialSpeed / slipDeceleration;
            double slidingDistance = (initialSpeed + rollingSpeed) * 0.5 * slidingTime;
            double rollingDistance = rollingSpeed * rollingSpeed / (2.0 * rollingDeceleration);
            double expectedDistance = slidingDistance + rollingDistance;
            double actualDistance = ball.position().x - startX;
            checks.checkNear(String.format("P4 v0=%.0f 滑动后进入纯滚动，总行程符合公式", initialSpeed),
                    expectedDistance, actualDistance, 0.1);

            double rollingTime = rollingSpeed / rollingDeceleration;
            double expectedMs = (slidingTime + rollingTime) * 1000.0;
            checks.checkNear(String.format("P4 v0=%.0f 滑动与滚动阶段停止时间符合公式", initialSpeed),
                    expectedMs, result.simulatedMs, 2.0 * PhysicsConfig.FIXED_STEP_MS);

            checks.check(String.format("P4 v0=%.0f 确实停稳而非超时", initialSpeed),
                    result.rested && !result.timedOut);
        }
    }

    // P5 —— 瞄袋必须捕获 / 绝不穿透

    /** 检查P5 瞄袋必须捕获（6 袋 × 5 入射角 × 3 速度），将验证结果记入传入的检查报告 */
    private static void p5PocketCapture(CheckHarness checks) {
        double[] speeds = {800.0, 1800.0, PhysicsConfig.MAX_SHOT_SPEED};
        double[] aimAngles = {-2.0, -1.0, 0.0, 1.0, 2.0};

        int shots = 0;
        int captured = 0;
        for (Pocket pocket : new Table().pockets()) {
            for (double angle : aimAngles) {
                for (double speed : speeds) {
                    shots = shots + 1;
                    if (fireAtPocket(pocket, angle, speed)) {
                        captured = captured + 1;
                    }
                }
            }
        }
        checks.check("P5 瞄袋必须捕获（6 袋 × 5 入射角 × 3 速度）",
                captured == shots, captured + " / " + shots + " 次捕获");

        // 大角度偏射：允许打不进（那是真实的「打偏了」），但绝不允许穿透库边或飞出球台
        double[] wideAngles = {-14.0, -8.0, 8.0, 14.0};
        int tunneling = 0;
        for (Pocket pocket : new Table().pockets()) {
            for (double angle : wideAngles) {
                for (double speed : speeds) {
                    if (!fireAtPocket(pocket, angle, speed, true)) {
                        tunneling = tunneling + 1;
                    }
                }
            }
        }
        checks.check("P5 大角度偏射也不穿透库边、不飞出球台",
                tunneling == 0, tunneling + " 次异常");
    }

    /** 从袋口内侧发射测试球，返回是否正确落袋或保持在球台内 */
    private static boolean fireAtPocket(Pocket pocket, double angleDeg, double speed) {
        return fireAtPocket(pocket, angleDeg, speed, false);
    }

    /**
     * 袋口的「入口线」方向（由袋口指向台内）
     *
     * <p>角袋的入口是 <b>45° 对角线</b>，不是「袋心指向台面中心」那条线——后者是 26.9°，
     * 球会先撞在库边的端帽上被弹开。第一版就是在这里写错的，导致角袋几乎打不进
     * 中袋的入口则垂直于它所在的长边
     */
    private static Vec2 approachInward(Pocket pocket) {
        Vec2 center = pocket.center();
        double directionX = -1.0;
        if (center.x < Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5) {
            directionX = 1.0;
        }
        double directionY = -1.0;
        if (center.y < Layout.playfieldCenterY()) {
            directionY = 1.0;
        }
        if (pocket.kind() == Pocket.Kind.CORNER) {
            return new Vec2(directionX, directionY).withLength(1.0);
        }
        return new Vec2(0.0, directionY);
    }

    /**
     * 从袋口内侧 200px 处朝袋口发射一球
     *
     * @param requireContainment 为 true 时返回「全程未穿透库边且未越出外框」，
     *                           为 false 时返回「最终是否入袋」
     */
    private static boolean fireAtPocket(Pocket pocket, double angleDeg, double speed,
                                        boolean requireContainment) {
        Vec2 inward = approachInward(pocket);
        Vec2 start = pocket.center().plus(inward.times(200.0));

        Vec2 aim = pocket.center().minus(start).normalized()
                .rotated(Math.toRadians(angleDeg));

        Ball ball = ballWithVelocity(0, start.x, start.y, aim.x * speed, aim.y * speed);
        PhysicsEngine engine = engineOf(ball);
        Table table = engine.table();

        boolean clean = true;
        double simMs = 0.0;
        while (!engine.allAtRest() && simMs < PhysicsConfig.SHOT_TIMEOUT_SIM_MS) {
            engine.step();
            simMs = simMs + (PhysicsConfig.FIXED_STEP_MS);

            if (requireContainment) {
                for (Ball ballValue : engine.balls()) {
                    if (!ballValue.isOnTable()) {
                        continue;
                    }
                    if (isBeyondFrame(ballValue.position()) || maxRailPenetration(table, engine.balls()) > 1e-6) {
                        clean = false;
                    }
                }
            }
        }

        if (requireContainment) {
            return clean;
        }
        return !ball.isOnTable();
    }

    // P6 —— 擦库不得捕获

    /** 检查P6 贴库平行滚过中袋全程不被捕获（上下库边 × 3 速度），将验证结果记入传入的检查报告 */
    private static void p6GrazingCushion(CheckHarness checks) {
        // ---- P6a：贴库平行滚过中袋，全程不得被捕获 ----
        //
        // 按纯滚动阶段的减速度估算行程，球应经过中袋并在角袋前停下
        double[] grazeSpeeds = {230.0, 280.0, 330.0};
        int captured = 0;
        int runs = 0;
        for (double speed : grazeSpeeds) {
            captured = captured + (grazingRun(new Vec2(300.0, Layout.PLAYFIELD_TOP + Ball.RADIUS), 1.0, 0.0, speed));
            captured = captured + (grazingRun(new Vec2(300.0, Layout.PLAYFIELD_BOTTOM - Ball.RADIUS), 1.0, 0.0, speed));
            runs = runs + (2);
        }
        checks.check("P6 贴库平行滚过中袋全程不被捕获（上下库边 × 3 速度）",
                captured == 0, captured + " / " + runs + " 次被误捕获");

        // ---- P6b：捕获边界 ----
        int wrongOutside = 0;
        int wrongInside = 0;
        Table table = new Table();
        for (Pocket pocket : table.pockets()) {
            double catchR = pocket.catchRadius();
            Vec2 inward = approachInward(pocket);
            if (pocket.captures(pocket.center().plus(inward.times(catchR * 1.15)))) {
                wrongOutside = wrongOutside + 1;
            }
            if (!pocket.captures(pocket.center().plus(inward.times(catchR * 0.85)))) {
                wrongInside = wrongInside + 1;
            }
        }
        checks.check("P6 1.15 倍捕获半径处不捕获", wrongOutside == 0, wrongOutside + " 个袋口误判");
        checks.check("P6 0.85 倍捕获半径处捕获", wrongInside == 0, wrongInside + " 个袋口误判");

        // ---- P6c：贴库滚进角袋「必须」被捕获 ----
        //
        // 与 P6a 方向相反，是刻意的：角袋不设中袋那层保护，因为角袋缺口处两侧库边
        // 都被削掉了，球会一直滚到角落。把这条也写成断言，是为了让「角袋为什么可以
        // 捕获贴库球」成为一条被验证的设计，而不是看起来像漏掉了保护
        int missed = 0;
        int cornerRuns = 0;
        for (Pocket pocket : table.pockets()) {
            if (pocket.kind() != Pocket.Kind.CORNER) {
                continue;
            }
            Vec2 center = pocket.center();
            boolean left = center.x < Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5;
            boolean top = center.y < Layout.playfieldCenterY();
            double y = Layout.PLAYFIELD_BOTTOM - Ball.RADIUS;
            double x = Layout.PLAYFIELD_RIGHT - 260.0;
            double velocityX = 1.0;
            if (top) {
                y = Layout.PLAYFIELD_TOP + Ball.RADIUS;
            }
            if (left) {
                x = Layout.PLAYFIELD_LEFT + 260.0;
                velocityX = -1.0;
            }
            for (double speed : new double[]{900.0, 2000.0}) {
                cornerRuns = cornerRuns + 1;
                if (grazingRun(new Vec2(x, y), velocityX, 0.0, speed) == 0) {
                    missed = missed + 1;
                }
            }
        }
        checks.check("P6 贴库滚进角袋必被捕获（4 个角袋 × 2 速度）",
                missed == 0, missed + " / " + cornerRuns + " 次未落袋");
    }

    /** 沿一条直线滑行，返回是否被捕获（0 表示没被捕获） */
    private static int grazingRun(Vec2 start, double deltaX, double deltaY, double speed) {
        Vec2 direction = new Vec2(deltaX, deltaY).withLength(1.0);
        Ball ball = ballWithVelocity(0, start.x, start.y, direction.x * speed, direction.y * speed);
        PhysicsEngine engine = engineOf(ball);
        ShotSimulator.simulateToRest(engine);
        if (ball.isOnTable()) {
            return 0;
        }
        return 1;
    }

    // P7 —— 库边反射

    /** 检查P7 库边反射：切向摩擦带走一部分速度，将验证结果记入传入的检查报告 */
    private static void p7CushionReflection(CheckHarness checks) {
        final double restitution = PhysicsConfig.CUSHION_RESTITUTION;

        // 45° 撞上库边：法向按 e 反号，切向一字不变
        Ball ball = ballWithVelocity(0, 400.0, Layout.PLAYFIELD_TOP + 5.0, 300.0, -400.0);
        PhysicsEngine engine = engineOf(ball);
        CushionResolver.resolve(engine.balls(), engine.table(), null);

        double expectedUpperTangent = 214.28571428571428;
        checks.checkNear("P7 库边反射：切向摩擦带走一部分速度",
                expectedUpperTangent, ball.velocity().x, 1e-12);
        checks.checkNear("P7 库边反射：法向分量按恢复系数反号",
                -restitution * -400.0, ball.velocity().y, 1e-12);
        checks.checkNear("P7 库边反射：解算后恰好相切，无残余穿透",
                Layout.PLAYFIELD_TOP + Ball.RADIUS, ball.position().y, 1e-12);

        // 撞下库边（法向 -y），保证不是只对上库边成立
        Ball lower = ballWithVelocity(0, 400.0, Layout.PLAYFIELD_BOTTOM - 3.0, 250.0, 500.0);
        PhysicsEngine lowerEngine = engineOf(lower);
        CushionResolver.resolve(lowerEngine.balls(), lowerEngine.table(), null);
        double expectedLowerTangent = 178.57142857142856;
        checks.checkNear("P7 下库边反射：切向摩擦带走一部分速度",
                expectedLowerTangent, lower.velocity().x, 1e-12);
        checks.checkNear("P7 下库边反射：法向分量按恢复系数反号",
                -restitution * 500.0, lower.velocity().y, 1e-12);
        checks.checkNear("P7 下库边反射：解算后恰好相切",
                Layout.PLAYFIELD_BOTTOM - Ball.RADIUS, lower.position().y, 1e-12);
    }

    // P8 —— 无穿透不变量（整局开球逐子步核对）

    /** 检查开球在六十秒内自然停稳，未触发强制超时，将结果记入报告 */
    private static void p8NoTunnelingInvariant(CheckHarness checks) {
        List<Ball> balls = RackBuilder.buildRack();
        Ball cue = RackBuilder.cueBall(balls);
        // 满力正对球堆开球：这是全工程最快的球、最密的碰撞场景
        cue.setVelocity(new Vec2(PhysicsConfig.MAX_SHOT_SPEED, 0.0));

        PhysicsEngine engine = new PhysicsEngine(new Table(), balls);
        engine.setDiagnosticsEnabled(true);
        engine.resetDiagnostics();

        double simMs = 0.0;
        double worstPenetration = 0.0;
        boolean escaped = false;
        int steps = 0;

        while (!engine.allAtRest() && simMs < PhysicsConfig.SHOT_TIMEOUT_SIM_MS) {
            engine.step();
            simMs = simMs + (PhysicsConfig.FIXED_STEP_MS);
            steps = steps + 1;

            double penetration = maxRailPenetration(engine.table(), balls);
            if (penetration > worstPenetration) {
                worstPenetration = penetration;
            }
            for (Ball ball : balls) {
                if (ball.isOnTable() && isBeyondFrame(ball.position())) {
                    escaped = true;
                }
            }
        }

        checks.check("P8 开球 60 秒内停稳（未触发超时兜底）",
                engine.allAtRest() && simMs < PhysicsConfig.SHOT_TIMEOUT_SIM_MS,
                String.format("步数 %d，模拟 %.0f ms", steps, simMs));
        checks.check("P8 每子步位移从未超过 MAX_SUBSTEP_DISPLACEMENT",
                engine.worstSubstepTravel() <= PhysicsConfig.MAX_SUBSTEP_DISPLACEMENT + 1e-9,
                String.format("实测最大 %.6f px，上限 %.1f px",
                        engine.worstSubstepTravel(), PhysicsConfig.MAX_SUBSTEP_DISPLACEMENT));
        // 残余重叠不可能压到 0：位置修正对多个球对不满足交换律，松弛有限轮必有残留
        // （见 PhysicsConfig.CONTACT_ITERATIONS）。所以断言的是「残余小到不影响观感与
        // 后续解算」而不是 0：满力开球实测最深 1.14e-02 px，是球半径的 0.1%、远在亚像素
        // 之下，而且每个子步都会重新解算，残余不会跨步累积。0.05px 取实测值的 4.4 倍——
        // 紧到能抓住真正的 bug（比如漏掉位置修正会给出几十倍的量级），又不至于被浮点噪声误触
        checks.check("P8 每子步结束后残余球球重叠可忽略",
                engine.worstPairOverlap() <= 0.05,
                String.format("最深重叠 %.3e px（上限 0.05）", engine.worstPairOverlap()));
        checks.check("P8 全程无球穿透库边", worstPenetration <= 1e-6,
                String.format("最深穿透 %.3e px", worstPenetration));
        checks.check("P8 全程无球飞出球台", !escaped);
        checks.check("P8 开球后球仍在台上（未全部消失）", engine.ballsOnTable() > 0,
                engine.ballsOnTable() + " 颗在台");
    }

    // P9 —— 确定性

    /** 检查P9 同一开球两次的步数相同，将验证结果记入传入的检查报告 */
    private static void p9Determinism(CheckHarness checks) {
        List<Ball> first = RackBuilder.buildRack();
        RackBuilder.cueBall(first).setVelocity(new Vec2(2600.0, 140.0));
        PhysicsEngine engineA = new PhysicsEngine(new Table(), first);
        ShotSimulator.Result resultA = ShotSimulator.simulateToRest(engineA);

        List<Ball> second = RackBuilder.buildRack();
        RackBuilder.cueBall(second).setVelocity(new Vec2(2600.0, 140.0));
        PhysicsEngine engineB = new PhysicsEngine(new Table(), second);
        ShotSimulator.Result resultB = ShotSimulator.simulateToRest(engineB);

        checks.check("P9 同一开球两次的步数相同",
                resultA.steps == resultB.steps,
                resultA.steps + " vs " + resultB.steps);

        int mismatches = 0;
        for (int index = 0; index < first.size(); index = index + 1) {
            Ball ball = first.get(index);
            Ball ballValue = second.get(index);
            if (Double.doubleToLongBits(ball.position().x) != Double.doubleToLongBits(ballValue.position().x)
                    || Double.doubleToLongBits(ball.position().y) != Double.doubleToLongBits(ballValue.position().y)
                    || ball.isOnTable() != ballValue.isOnTable()) {
                mismatches = mismatches + 1;
            }
        }
        checks.check("P9 同一开球两次的最终位置按位相同", mismatches == 0,
                mismatches + " 颗球不一致");

        // 累加器：把同样的墙钟总量切成不同粒度喂进去，步数必须一致——这是
        // 「墙钟不渗进模拟」的直接证据
        FixedStepAccumulator fine = new FixedStepAccumulator(PhysicsConfig.FIXED_STEP_MS, 10_000);
        FixedStepAccumulator coarse = new FixedStepAccumulator(PhysicsConfig.FIXED_STEP_MS, 10_000);
        FixedStepAccumulator single = new FixedStepAccumulator(PhysicsConfig.FIXED_STEP_MS, 10_000);

        int fineSteps = 0;
        for (int indexValue = 0; indexValue < 1000; indexValue = indexValue + 1) {
            fineSteps = fineSteps + (fine.advance(1.0));
        }
        int coarseSteps = 0;
        for (int indexValueForI = 0; indexValueForI < 100; indexValueForI = indexValueForI + 1) {
            coarseSteps = coarseSteps + (coarse.advance(10.0));
        }
        int singleSteps = single.advance(1000.0);

        checks.check("P9 累加器：1000×1ms 与 100×10ms 与 1×1000ms 步数一致",
                fineSteps == coarseSteps && coarseSteps == singleSteps,
                String.format("%d / %d / %d", fineSteps, coarseSteps, singleSteps));
        checks.check("P9 累加器：1000ms 恰好 200 步且无余量残留",
                singleSteps == 200 && Math.abs(single.pendingMs()) < 1e-9,
                String.format("%d 步，余量 %.6f ms", singleSteps, single.pendingMs()));

        // 触顶时丢弃欠账，而不是攒成死亡螺旋
        FixedStepAccumulator capped = new FixedStepAccumulator(
                PhysicsConfig.FIXED_STEP_MS, PhysicsConfig.MAX_STEPS_PER_FRAME);
        int cappedSteps = capped.advance(1000.0);
        checks.check("P9 累加器：一帧超量时截断到上限并清空欠账",
                cappedSteps == PhysicsConfig.MAX_STEPS_PER_FRAME && capped.pendingMs() == 0.0,
                cappedSteps + " 步，余量 " + capped.pendingMs());
    }

    // P10 —— 摆球稳定性（摆好就是静止的，不会自己炸开）

    /** 检查P10 摆球后 1 秒模拟时间内全部保持静止，将验证结果记入传入的检查报告 */
    private static void p10RackStability(CheckHarness checks) {
        List<Ball> balls = RackBuilder.buildRack();
        double[] beforeX = new double[balls.size()];
        double[] beforeY = new double[balls.size()];
        for (int index = 0; index < balls.size(); index = index + 1) {
            beforeX[index] = balls.get(index).position().x;
            beforeY[index] = balls.get(index).position().y;
        }

        PhysicsEngine engine = new PhysicsEngine(new Table(), balls);
        for (int indexValue = 0; indexValue < 200; indexValue = indexValue + 1) {
            engine.step();
        }

        checks.check("P10 摆球后 1 秒模拟时间内全部保持静止", engine.allAtRest());

        int moved = 0;
        for (int indexValueForI = 0; indexValueForI < balls.size(); indexValueForI = indexValueForI + 1) {
            if (Double.doubleToLongBits(beforeX[indexValueForI]) != Double.doubleToLongBits(balls.get(indexValueForI).position().x)
                    || Double.doubleToLongBits(beforeY[indexValueForI]) != Double.doubleToLongBits(balls.get(indexValueForI).position().y)) {
                moved = moved + 1;
            }
        }
        checks.check("P10 摆球位置按位不变（静止接触不产生冲量）", moved == 0,
                moved + " 颗球发生位移");

        checks.check("P10 摆球最小间距大于球直径（初始不重叠）",
                RackBuilder.minPairDistance(balls) > 2.0 * Ball.RADIUS - 1e-9);
    }

    // P11 —— 守恒性（P1/P2 只覆盖了一对球，这里覆盖多体松弛）

    /**
     * 松弛解算是这套物理最可能出问题的地方：一对球的一次冲量当然等大反向（P1/P2 已证），
     * 但真正危险的是<b>一个子步内对同一对球解算多轮、且每颗球同时与多个邻居接触</b>时，
     * 反复施加冲量会不会悄悄造出动量或能量。冲量类解算器最经典的失败模式就是这个
     *
     * <p>测法是直接把<b>整堆球</b>喂给 {@link CollisionResolver#resolve}，在没有摩擦、
     * 没有库边的条件下测纯多体相位——摩擦会把动量变化搅进来，让「守恒」这句话无法判定
     * 主球初始与顶球重叠 0.4px，使解算器一进来就同时面对「位置修正 + 冲量 + 多接触」
     *
     * <p>为什么还必须有一条引擎级的动能断言：解算器守恒不等于整杆守恒。把一整杆开球跑完
     * 并逐步检查动能<b>单调不增</b>，才覆盖到「摩擦 + 球球 + 库边 + 速度钳制」的全部组合
     * 这四者都只会减能，所以任何一步出现增能都必然是 bug，而不是调参问题
     */
    private static void p11Conservation(CheckHarness checks) {
        List<Ball> balls = RackBuilder.buildRack();
        Ball cue = RackBuilder.cueBall(balls);
        Ball apex = RackBuilder.ballById(balls, BallCatalog.standardRack()[0]);
        checks.checkNotNull("P11 顶球存在", apex);
        if (cue == null || apex == null) {
            return;
        }

        cue.setPosition(new Vec2(apex.position().x - 2.0 * Ball.RADIUS + 0.4, apex.position().y));
        cue.setVelocity(new Vec2(PhysicsConfig.MAX_SHOT_SPEED, 0.0));

        Vec2[] momentumBefore = {momentumOf(balls.toArray(new Ball[0]))};
        double energyBefore = kineticEnergyOf(balls.toArray(new Ball[0]));

        // 4 轮 × CONTACT_ITERATIONS 次，远超一次解算所需，专门给「反复解算同一批接触」
        // 留下放大误差的机会
        double worstMomentumDrift = 0.0;
        double worstEnergyGain = 0.0;
        for (int round = 0; round < 4; round = round + 1) {
            CollisionResolver.resolve(balls, PhysicsConfig.CONTACT_ITERATIONS,
                    PhysicsConfig.BALL_RESTITUTION, null);
            worstMomentumDrift = Math.max(worstMomentumDrift,
                    momentumOf(balls.toArray(new Ball[0])).distanceTo(momentumBefore[0]));
            worstEnergyGain = Math.max(worstEnergyGain,
                    kineticEnergyOf(balls.toArray(new Ball[0])) - energyBefore);
        }

        checks.check("P11 多体松弛守恒动量（4 轮 × 6 次迭代，整堆球同时接触）",
                worstMomentumDrift <= 1e-9,
                String.format("最大漂移 %.3e，初动量 %s", worstMomentumDrift, momentumBefore[0]));
        checks.check("P11 多体松弛不增加动能",
                worstEnergyGain <= 1e-9,
                String.format("最大增能 %.3e", worstEnergyGain));

        // 主球末速必须等于闭式解 v0(1-e)/2。这一条比上面两条更强：它说明整堆球是被
        // 逐颗正确穿透的，而不是被当成一堵墙弹回来（当成墙会得到 -(1+15e)/16·v0，差两个数量级）
        final double restitution = PhysicsConfig.BALL_RESTITUTION;
        checks.checkNear("P11 正撞整堆后主球末速 = v0(1-e)/2（不是被墙弹回）",
                PhysicsConfig.MAX_SHOT_SPEED * (1.0 - restitution) / 2.0, cue.velocity().x, 1e-9);

        // ---- 引擎级：一整杆开球过程中动能单调不增 ----
        List<Ball> breakBalls = RackBuilder.buildRack();
        Ball breakCue = RackBuilder.cueBall(breakBalls);
        if (breakCue != null) {
            breakCue.setVelocity(new Vec2(PhysicsConfig.MAX_SHOT_SPEED, 0.0));
        }
        PhysicsEngine engine = new PhysicsEngine(new Table(), breakBalls);

        // 容差 1e-6 是实测出来的：能量量级 9e6，16 项求和的浮点噪声约 1e-8，
        // 而任何真实造能都会是 1e3 以上——两者差六个数量级，所以这个阈值既不误报也不漏报
        double previous = onTableKineticEnergy(breakBalls);
        double worstStepGain = 0.0;
        double elapsedMs = 0.0;
        while (!engine.allAtRest() && elapsedMs < PhysicsConfig.SHOT_TIMEOUT_SIM_MS) {
            engine.step();
            elapsedMs = elapsedMs + (PhysicsConfig.FIXED_STEP_MS);
            double now = onTableKineticEnergy(breakBalls);
            worstStepGain = Math.max(worstStepGain, now - previous);
            previous = now;
        }
        checks.check("P11 整杆开球过程中动能单调不增（解算器不造能）",
                worstStepGain <= 1e-6,
                String.format("最大单步增能 %.3e", worstStepGain));

        /*
         * 顺带记录一个不写成断言、但值得知道的事实：满力正撞球堆后主球会<b>回弹</b>，
         * 末速约 -0.1·v0。这不是 bug——把球堆看成质量 15m 的物体，反解等效恢复系数
         *     e_eff = (1 - 16·v_cue/v0) / 15 ≈ 0.17
         * 即球堆表现得像一个松散、可变形、恢复系数远低于单球的物体（真实球台开球正是如此）
         * 之所以不把「回弹速度」写成断言：它是多球接触的共同结果，改了摩擦系数或
         * 摆球间隙就会变；钉住它等于把调参锁死。这里钉住的是产生它的守恒律
         */
    }

    // 工具

    /** 根据球号、位置与初速度创建测试球 */
    private static Ball ballWithVelocity(int ballId, double x, double y, double velocityX, double velocityY) {
        Ball ball = new Ball(ballId, new Vec2(x, y));
        ball.setVelocity(new Vec2(velocityX, velocityY));
        return ball;
    }

    /** 用传入球建立真实物理引擎，供检查单球和多球运动 */
    private static PhysicsEngine engineOf(Ball... balls) {
        List<Ball> list = new ArrayList<>(Arrays.asList(balls));
        return new PhysicsEngine(new Table(), list);
    }

    /** 质量归一为 1，所以动量就是速度矢量和 */
    private static Vec2 momentumOf(Ball... balls) {
        Vec2 sum = Vec2.ZERO;
        for (Ball ball : balls) {
            sum = sum.plus(ball.velocity());
        }
        return sum;
    }

    /** 累加传入球的平动与转动动能，质量按一处理 */
    private static double kineticEnergyOf(Ball... balls) {
        double sum = 0.0;
        for (Ball ball : balls) {
            sum = sum + (totalKineticEnergyOf(ball));
        }
        return sum;
    }

    /** 计算球的平动与转动总动能，质量统一按 1 处理 */
    private static double totalKineticEnergyOf(Ball ball) {
        double rotationalInertia = PhysicsConfig.BALL_INERTIA_FACTOR
                * Ball.RADIUS * Ball.RADIUS;
        double spinSquared = ball.spinX() * ball.spinX()
                + ball.spinY() * ball.spinY()
                + ball.spinZ() * ball.spinZ();
        double translationalEnergy = 0.5 * ball.velocity().lengthSq();
        double rotationalEnergy = 0.5 * rotationalInertia * spinSquared;
        return translationalEnergy + rotationalEnergy;
    }

    /**
     * 场上在台球的总动能。落袋球按离场处理——它已经不参与后续模拟，
     * 把它的残余速度算进来会让「动能单调不增」这条断言失去意义
     */
    private static double onTableKineticEnergy(List<Ball> balls) {
        double sum = 0.0;
        for (Ball ball : balls) {
            if (ball.isOnTable()) {
                sum = sum + (totalKineticEnergyOf(ball));
            }
        }
        return sum;
    }

    /** 按给定距离容差比较向量，并把实际值记入失败说明 */
    private static void checkVectorNear(CheckHarness checks, String name, Vec2 expected, Vec2 actual, double tol) {
        checks.check(name, expected.distanceTo(actual) <= tol,
                String.format("期望 %s，实际 %s", expected, actual));
    }

    /** 返回球心是否已越过木框最外侧边界 */
    private static boolean isBeyondFrame(Vec2 position) {
        return position.x < Layout.outerLeft() || position.x > Layout.outerRight()
                || position.y < Layout.outerTop() || position.y > Layout.outerBottom();
    }

    /** 场上最深的「球陷进库边」量（px）。解算后应当为 0 */
    private static double maxRailPenetration(Table table, List<Ball> balls) {
        double worst = 0.0;
        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }
            for (RailSegment rail : table.rails()) {
                Vec2 position = ball.position();
                if (!rail.covers(position, Ball.RADIUS)) {
                    continue;
                }
                double penetration = Ball.RADIUS - rail.signedDistanceTo(position);
                if (penetration > worst) {
                    worst = penetration;
                }
            }
        }
        return worst;
    }
}

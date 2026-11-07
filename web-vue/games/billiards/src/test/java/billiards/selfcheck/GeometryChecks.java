package billiards.selfcheck;

import billiards.rules.RackBuilder;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Pocket;
import billiards.world.RailSegment;
import billiards.world.Table;
import billiards.world.Vec2;
import billiards.world.Viewport;

import java.util.List;

/**
 * 台面与摆球几何检查
 *
 * <p>这些是「不需要物理就能确定对错」的事实：比例、数量、法向、摆球合法性
 * 它们的价值在于一旦有人改了 {@link Layout} 的常量或摆了新球型，会立刻炸出来，
 * 而不是等物理调参时才发现是几何本身就错了
 *
 * <p>窗口 → 逻辑画布的映射（{@link Viewport}）也归在这里：它同样是纯粹的几何换算，
 * 与物理、渲染都无关——正因为它无状态、无 {@code java.awt} 依赖，才能在无头环境里
 * 用它自己的输入断言它自己
 */
public final class GeometryChecks {

    /** 工具类只提供静态方法，不需要创建对象 */
    private GeometryChecks() {
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    public static void run(CheckHarness checks) {
        Table table = new Table();

        checkTableShape(checks, table);
        checkPocketGeometry(checks, table);
        checkPocketSeal(checks, table);
        checkRack(checks, table);
        checkViewport(checks);
    }

    /**
     * 袋口密封扫描：沿四条库边线以 0.25px 步长采样，断言每个采样点要么被某段库边
     * 覆盖，要么落在某个袋口的捕获圆内
     *
     * <p>为什么这一维扫描就是决定性的：库边解算会把「被覆盖且陷进库边」的球推回台内，
     * 所以球只能从<b>未被覆盖</b>的位置穿过库边线。而球心一旦过了库边线还在往下走，
     * 它离袋心只会越来越远（袋心在台外），于是必然一路掉出外框。因此
     * 「库边线上每个未被覆盖的点都可捕获」等价于「袋口附近没有漏缝」，不需要做可达性分析
     *
     * <p>这条检查的作用是把 {@link Layout} 里那段袋口几何推导变成可执行的：改错
     * {@code CUSHION_GAP_HALF_*} 或 {@code POCKET_CATCH_RADIUS_*} 会立刻炸出来
     * 它抓到过一个真实缺陷——角袋处 4.4px 宽的漏缝，球能从角袋旁边的缝里滚出台面
     *
     * <p><b>采样范围是台面本身</b>（{@code PLAYFIELD_*}），不是外框矩形。库边线是台面
     * 的边界，木框那一段边线在物理上根本不存在——那里既没有库边也没有球，外框往外挪
     * 多少都不该让这条检查变红。2026-10-05 把 {@code FRAME_BAND} 从 22 放到 30 时，
     * 扫描曾经一路扫到外框边线（{@code x=12}），于是在台外 48px 处报了一处「漏缝」，
     * 而那个位置离角袋袋心 41.5px、本来就在捕获圈（36）之外，纯属采样范围划错
     * 当时它没炸是因为旧外框（20）那一段边线整好都落在角袋的捕获圈内，蒙对了；
     * 真正的漏缝（角袋旁那 4.4px）出现在 {@code x ≈ 60..88}，收窄后依然扫得到
     */
    private static void checkPocketSeal(CheckHarness checks, Table table) {
        final double step = 0.25;

        String leak = null;

        // 上、下两条长边：沿 x 采样
        double[] edgeY = {Layout.PLAYFIELD_TOP, Layout.PLAYFIELD_BOTTOM};
        for (double y : edgeY) {
            for (double x = Layout.PLAYFIELD_LEFT; x <= Layout.PLAYFIELD_RIGHT; x = x + (step)) {
                Vec2 position = new Vec2(x, y);
                if (!coveredByAnyRail(table, position) && !capturedByAnyPocket(table, position)) {
                    if (leak == null) {
                        leak = String.format("y=%.0f 上 x=%.2f", y, x);
                    }
                }
            }
        }

        // 左、右两条短边：沿 y 采样
        double[] edgeX = {Layout.PLAYFIELD_LEFT, Layout.PLAYFIELD_RIGHT};
        for (double x : edgeX) {
            for (double y = Layout.PLAYFIELD_TOP; y <= Layout.PLAYFIELD_BOTTOM; y = y + (step)) {
                Vec2 positionValue = new Vec2(x, y);
                if (!coveredByAnyRail(table, positionValue) && !capturedByAnyPocket(table, positionValue)) {
                    if (leak == null) {
                        leak = String.format("x=%.0f 上 y=%.2f", x, y);
                    }
                }
            }
        }
        checks.check("袋口密封扫描：库边线每个未覆盖点都可被捕获（无漏缝）",
                leak == null,
                describeLeak(leak));

        // 顺带断言窗口不是刚好卡住：密封余量至少 1px，否则改一点数值就翻车
        double cornerSealMargin =
                (Layout.PLAYFIELD_LEFT - Layout.POCKET_CENTER_OFFSET / Math.sqrt(2.0)
                        + Math.sqrt(Layout.POCKET_CATCH_RADIUS_CORNER * Layout.POCKET_CATCH_RADIUS_CORNER
                        - Layout.POCKET_CENTER_OFFSET * Layout.POCKET_CENTER_OFFSET / 2.0))
                        - (Layout.PLAYFIELD_LEFT + Layout.CUSHION_GAP_HALF_CORNER - Ball.RADIUS);
        checks.check("角袋密封余量 ≥ 1px",
                cornerSealMargin >= 1.0,
                String.format("余量 %.3fpx", cornerSealMargin));

        double sideSealMargin = Math.sqrt(
                Layout.POCKET_CATCH_RADIUS_SIDE * Layout.POCKET_CATCH_RADIUS_SIDE
                        - Layout.POCKET_CENTER_OFFSET * Layout.POCKET_CENTER_OFFSET)
                - (Layout.CUSHION_GAP_HALF_SIDE - Ball.RADIUS);
        checks.check("中袋密封余量 ≥ 1px",
                sideSealMargin >= 1.0,
                String.format("余量 %.3fpx", sideSealMargin));

        // 木框的外边线必须让开角袋的皮革环，否则袋口捅出木头外面、被贴图边界切出直弦
        // （2026-10-05 修的就是这个：FRAME_BAND 取 22 时的余量是 −6.07px）
        // 这条钉的是 Layout 自己的常量关系，与渲染无关，所以放在这里而不是渲染组
        double rimMargin = Layout.cornerRadius() - Layout.cornerPocketOuterRadius();
        checks.check("木框外边线让开角袋外圈 ≥ 1px",
                rimMargin >= 1.0,
                String.format("外边线离袋心 %.3fpx，角袋外圈 %.3fpx，余量 %.3fpx",
                        Layout.cornerRadius(), Layout.cornerPocketOuterRadius(), rimMargin));
    }

    /** 描述密封扫描发现的第一个漏点 */
    private static String describeLeak(String leak) {
        if (leak == null) {
            return "";
        }
        return "首处漏缝在 " + leak + "（球会从此滚出台面）";
    }

    /** 返回给定点是否位于任一库边的覆盖范围 */
    private static boolean coveredByAnyRail(Table table, Vec2 position) {
        for (RailSegment rail : table.rails()) {
            if (rail.covers(position, Ball.RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** 返回给定点是否进入任一袋口的捕获范围 */
    private static boolean capturedByAnyPocket(Table table, Vec2 position) {
        for (Pocket pocket : table.pockets()) {
            if (pocket.captures(position)) {
                return true;
            }
        }
        return false;
    }

    /** 检查台面长宽比恰为 2:1，将验证结果记入传入的检查报告 */
    private static void checkTableShape(CheckHarness checks, Table table) {
        checks.checkNear("台面长宽比恰为 2:1",
                2.0, Layout.playfieldWidth() / Layout.playfieldHeight(), 1e-9);

        checks.checkNear("开球线位于台面 1/4 处",
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.25, Layout.HEAD_STRING_X, 1e-9);

        checks.checkNear("置球点位于台面纵向中点",
                Layout.playfieldCenterY(), Layout.FOOT_SPOT_Y, 1e-9);

        checks.check("袋口数量为 6", table.pockets().size() == Layout.POCKET_COUNT,
                "实际 " + table.pockets().size());

        checks.check("库边切分为 6 段", table.rails().size() == 6,
                "实际 " + table.rails().size());

        // 用台面中心测法向，而不是线段中点——中点恰好落在库边线上，有符号距离恒为 0，
        // 拿它判断方向会永远失败（这里踩过一次）
        final Vec2 clothCenter = new Vec2(
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5,
                Layout.playfieldCenterY());

        boolean normalsInward = true;
        boolean normalsUnit = true;
        boolean normalsPerpendicular = true;
        boolean coversOwnSpan = true;
        for (RailSegment rail : table.rails()) {
            if (rail.signedDistanceTo(clothCenter) <= 0.0) {
                normalsInward = false;
            }
            if (Math.abs(rail.inwardNormal().length() - 1.0) > 1e-12) {
                normalsUnit = false;
            }
            if (Math.abs(rail.inwardNormal().dot(rail.tangent())) > 1e-12) {
                normalsPerpendicular = false;
            }
            if (!rail.covers(rail.a().plus(rail.b()).times(0.5), 1e-9)) {
                coversOwnSpan = false;
            }
        }
        checks.check("所有库边法向指向台内（以台面中心为参照）", normalsInward);
        checks.check("所有库边法向是单位向量", normalsUnit);
        checks.check("所有库边法向与切向量正交", normalsPerpendicular);
        checks.check("所有库边覆盖自身线段", coversOwnSpan);
    }

    /** 检查库边不侵入任何袋口捕获圆，将验证结果记入传入的检查报告 */
    private static void checkPocketGeometry(CheckHarness checks, Table table) {
        // 库边不得侵入任何袋口的捕获圆——否则球会在贴着库边滚过时被"吸"进袋
        // 角袋与中袋的捕获半径不同，所以逐个袋口取它自己的半径比较
        double worstMargin = Double.MAX_VALUE;
        String worstDetail = "";
        for (RailSegment rail : table.rails()) {
            for (Pocket pocketValue : table.pockets()) {
                double distance = rail.closestPoint(pocketValue.center()).distanceTo(pocketValue.center());
                if (distance - pocketValue.catchRadius() < worstMargin) {
                    worstMargin = distance - pocketValue.catchRadius();
                    worstDetail = String.format("%s #%d：库边最近 %.3f，捕获半径 %.1f",
                            pocketValue.kind().labelZh(), pocketValue.index(), distance, pocketValue.catchRadius());
                }
            }
        }
        checks.check("库边不侵入任何袋口捕获圆",
                worstMargin >= 0.0, worstDetail);

        // 这是「擦库不得捕获」的核心算术：沿库边平行滚动时球心恒在库边线内侧 R 处，
        // 到中袋袋心的最近距离是 R + POCKET_CENTER_OFFSET，必须大于中袋捕获半径
        double midX = Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5;
        Vec2 grazing = new Vec2(midX, Layout.PLAYFIELD_TOP + Ball.RADIUS);
        double grazingDist = grazing.distanceTo(table.pocket(1).center());
        checks.checkNear("贴库滚动球到中袋袋心的距离 = 半径 + 袋心外偏",
                Ball.RADIUS + Layout.POCKET_CENTER_OFFSET, grazingDist, 1e-9);
        checks.check("贴库平行滚动不会被中袋捕获",
                grazingDist > Layout.POCKET_CATCH_RADIUS_SIDE,
                String.format("距离 %.3f，中袋捕获半径 %.1f", grazingDist, Layout.POCKET_CATCH_RADIUS_SIDE));

        // 袋心确实在台面之外（外偏生效）
        boolean allOutside = true;
        for (Pocket pocketValueForP : table.pockets()) {
            Vec2 center = pocketValueForP.center();
            boolean outside = center.x < Layout.PLAYFIELD_LEFT || center.x > Layout.PLAYFIELD_RIGHT
                    || center.y < Layout.PLAYFIELD_TOP || center.y > Layout.PLAYFIELD_BOTTOM;
            if (!outside) {
                allOutside = false;
            }
        }
        checks.check("所有袋心位于台面之外", allOutside);

        int cornerCount = 0;
        int sideCount = 0;
        for (Pocket pocket : table.pockets()) {
            if (pocket.kind() == Pocket.Kind.CORNER) {
                cornerCount = cornerCount + 1;
            } else {
                sideCount = sideCount + 1;
            }
        }
        checks.check("角袋 4 个、中袋 2 个", cornerCount == 4 && sideCount == 2,
                "角袋 " + cornerCount + " 个，中袋 " + sideCount + " 个");
    }

    /** 检查摆球共 16 颗（1 主球 + 15 目标球），将验证结果记入传入的检查报告 */
    private static void checkRack(CheckHarness checks, Table table) {
        List<Ball> balls = RackBuilder.buildRack();

        checks.check("摆球共 16 颗（1 主球 + 15 目标球）",
                balls.size() == BallCatalog.BALL_COUNT, "实际 " + balls.size());

        boolean[] seen = new boolean[BallCatalog.BALL_COUNT];
        boolean idsUnique = true;
        for (Ball ball : balls) {
            if (ball.id() < 0 || ball.id() >= seen.length || seen[ball.id()]) {
                idsUnique = false;
            } else {
                seen[ball.id()] = true;
            }
        }
        checks.check("球号 0–15 各出现且仅出现一次", idsUnique);

        boolean allInside = true;
        for (Ball ballValue : balls) {
            if (!table.isInsidePlayfield(ballValue.position())) {
                allInside = false;
            }
        }
        checks.check("所有球心在台面内（已内缩一个半径）", allInside);

        double minGap = RackBuilder.minPairDistance(balls);
        checks.check("摆球两两不重叠",
                minGap >= 2.0 * Ball.RADIUS - 1e-9,
                String.format("最小间距 %.6f，需 ≥ %.1f", minGap, 2.0 * Ball.RADIUS));
        checks.checkNear("摆球最小间距等于 2R + RACK_GAP",
                2.0 * Ball.RADIUS + PhysicsConfig.RACK_GAP, minGap, 1e-6);

        int[] rack = BallCatalog.standardRack();

        Ball apex = RackBuilder.ballById(balls, rack[0]);
        checks.checkNotNull("顶球存在", apex);
        if (apex != null) {
            checks.checkNear("顶球球心位于置球点 X", Layout.FOOT_SPOT_X, apex.position().x, 1e-9);
            checks.checkNear("顶球球心位于台面中线", Layout.FOOT_SPOT_Y, apex.position().y, 1e-9);
        }

        int centerSlot = BallCatalog.rackIndex(2, 1);
        checks.check("8 号球位于第三排正中",
                rack[centerSlot] == BallCatalog.EIGHT_ID,
                "第三排正中槽位 #" + centerSlot + " 是 " + rack[centerSlot] + " 号");

        checks.check("底排两角为一全色一花色",
                BallCatalog.isLegalRackCorner(
                        rack[BallCatalog.RACK_BACK_LEFT_INDEX],
                        rack[BallCatalog.RACK_BACK_RIGHT_INDEX]),
                rack[BallCatalog.RACK_BACK_LEFT_INDEX] + " 号 与 "
                        + rack[BallCatalog.RACK_BACK_RIGHT_INDEX] + " 号");

        Ball cue = RackBuilder.cueBall(balls);
        checks.checkNotNull("主球存在", cue);
        if (cue != null) {
            checks.check("主球初始位置在开球区内", table.isInBreakZone(cue.position()),
                    "位置 " + cue.position() + "，开球线 x=" + Layout.HEAD_STRING_X);
            checks.checkNear("主球初始位置在台面纵向中点", Layout.playfieldCenterY(), cue.position().y, 1e-9);
        }

        // 球号 → 组属的映射不能串（9–15 是花色，1–7 是全色，8 单独，0 是主球）
        boolean groupingOk = BallCatalog.groupOf(0) == BallGroup.CUE
                && BallCatalog.groupOf(8) == BallGroup.EIGHT;
        for (int ballId = 1; ballId <= 7; ballId = ballId + 1) {
            groupingOk = groupingOk & (BallCatalog.groupOf(ballId) == BallGroup.SOLID);
        }
        for (int ballIdValue = 9; ballIdValue <= 15; ballIdValue = ballIdValue + 1) {
            groupingOk = groupingOk & (BallCatalog.groupOf(ballIdValue) == BallGroup.STRIPE);
        }
        checks.check("球号到组属的映射正确", groupingOk);

        checks.check("开球区在开球线左侧且留出一个半径",
                RackBuilder.DEFAULT_BREAK_CUE_X + Ball.RADIUS <= Layout.HEAD_STRING_X,
                "默认开球位 x=" + RackBuilder.DEFAULT_BREAK_CUE_X);
    }

    // 窗口 → 逻辑画布

    /**
     * {@link Viewport} 的映射检查
     *
     * <p>这个类只有两个消费者：{@code GamePanel} 用它把逻辑坐标画到窗口上，
     * {@code InputController} 用它把鼠标坐标折回逻辑坐标。两者各自调一次
     * {@code fit}，谁也看不见谁——<b>一旦正反两向对不上，症状是「点哪打哪」
     * 变成「点旁边打」，而光标瞄准线画在正确的位置上，看上去一切正常</b>
     * 演示时这种偏移很容易被当成手感问题，所以拿往返断言把它钉死
     *
     * <p>另外两条是设计约束，不是实现细节：<b>等比</b>（非等比会把球压成椭圆）
     * 与<b>不裁切</b>（整个逻辑画布必须落在窗口内，多出来的部分留边）
     */
    private static void checkViewport(CheckHarness checks) {
        checks.check("逻辑画布尺寸下映射是恒等的（1:1，缩放不引入任何偏移）",
                Viewport.fit(Layout.WINDOW_W, Layout.WINDOW_H).isIdentity(),
                Viewport.fit(Layout.WINDOW_W, Layout.WINDOW_H).toString());

        // 等比整数倍放大：两个方向都刚好翻倍，于是没有留边
        Viewport doubled = Viewport.fit(2 * Layout.WINDOW_W, 2 * Layout.WINDOW_H);
        checks.check("窗口恰为两倍时缩放恰为 2、无留边",
                doubled.scale() == 2.0 && doubled.offsetX() == 0.0 && doubled.offsetY() == 0.0,
                doubled.toString());

        // 宽高比与画布不同的窗口：取较小的一维，另一维留边
        // 2000×640 里高度是瓶颈（640/640 = 1 < 2000/1000 = 2），所以缩放是 1，
        // 左右各多出 (2000 − 1000) / 2 = 500px 的边
        Viewport wide = Viewport.fit(2000, Layout.WINDOW_H);
        checks.check("宽窗口按高度定缩放（1.0），横向左右各留 500px 边",
                wide.scale() == 1.0 && wide.offsetX() == 500.0 && wide.offsetY() == 0.0,
                wide.toString());

        // 等比这件事必须直接断言，不能只靠「缩放是个标量」——等比是设计选择，
        // 换成一个 scaleX/scaleY 的实现同样能编过，但球会变成椭圆
        Viewport odd = Viewport.fit(1920, 1080);
        double deltaX = odd.toScreen(new Vec2(100.0, 0.0)).x - odd.toScreen(Vec2.ZERO).x;
        double deltaY = odd.toScreen(new Vec2(0.0, 100.0)).y - odd.toScreen(Vec2.ZERO).y;
        checks.checkNear("横竖两个方向缩放系数相同（球不会被压成椭圆）", deltaX, deltaY, 1e-9);

        // 不裁切：逻辑画布的四角都要落在窗口内，且留边不为负
        boolean coversCanvas = true;
        boolean noCrop = true;
        int[][] sizes = {{1920, 1080}, {2000, 640}, {640, 440}, {1000, 640}, {3000, 2000}};
        for (int[] size : sizes) {
            Viewport viewport = Viewport.fit(size[0], size[1]);
            Vec2 topLeft = viewport.toScreen(Vec2.ZERO);
            Vec2 bottomRight = viewport.toScreen(new Vec2(Layout.WINDOW_W, Layout.WINDOW_H));
            if (topLeft.x < -1e-9 || topLeft.y < -1e-9) {
                coversCanvas = false;
            }
            if (bottomRight.x > size[0] + 1e-9 || bottomRight.y > size[1] + 1e-9) {
                noCrop = false;
            }
        }
        checks.check("各种窗口尺寸下逻辑画布都完整落在窗口内（不裁切）", noCrop && coversCanvas);

        // 往返：屏幕 → 逻辑 → 屏幕必须回到原点。这条直接对应「鼠标点击位置」
        // 与「渲染位置」是否自洽——两个方向用的都是同一个 Viewport 实例
        double worst = 0.0;
        for (int[] size : sizes) {
            Viewport viewportValue = Viewport.fit(size[0], size[1]);
            Vec2[] probes = {new Vec2(0, 0), new Vec2(size[0], size[1]),
                    new Vec2(size[0] * 0.5, size[1] * 0.5), new Vec2(1, 639)};
            for (Vec2 position : probes) {
                Vec2 back = viewportValue.toScreen(viewportValue.toLogical(position.x, position.y));
                worst = Math.max(worst, Math.max(Math.abs(back.x - position.x), Math.abs(back.y - position.y)));
            }
        }
        checks.checkNear("屏幕 → 逻辑 → 屏幕往返无损", 0.0, worst, 1e-9);

        // 尺寸还没定下来时（窗口未显示、刚构造）不能除零，也不能抛出
        checks.check("窗口尺寸为 0 时退化为恒等映射（不除零、不抛异常）",
                Viewport.fit(0, 0).isIdentity()
                        && Viewport.fit(-5, 100).isIdentity()
                        && Viewport.fit(100, 0).isIdentity());

        checks.checkNear("逻辑画布宽高比 = 窗口宽高比（缩放取等比的依据）",
                Layout.WINDOW_W / (double) Layout.WINDOW_H, Layout.aspectRatio(), 1e-12);
    }
}

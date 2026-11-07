package billiards.selfcheck;

import billiards.app.MatchSession;
import billiards.input.AimCalculator;
import billiards.input.ShotPower;
import billiards.render.AimView;
import billiards.render.BallView;
import billiards.render.CueRenderer;
import billiards.render.GameRenderer;
import billiards.render.GameStateView;
import billiards.render.HudRenderer;
import billiards.render.MatchView;
import billiards.render.OverlayRenderer;
import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.rules.RackBuilder;
import billiards.rules.RulesEngine;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Table;
import billiards.world.Vec2;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static billiards.selfcheck.UIFixtures.*;

/** 检查力度、瞄准几何和只读预览，不推进完整比赛 */
final class UIInputChecks {

    /**
     * 力度映射是纯函数，所以这里<!-- -->全部是精确断言，没有一条依赖计时
     *
     * <p>对比 M3：那时的 {@code PowerCharger} 要把时间戳喂进去，每条断言都得先想清楚
     * 「按住 600ms」在这次改动之后还是不是 600ms。现在「拉了 80px 是半力」就是一行等式
     */
    static void checkShotPower(CheckHarness checks) {
        double dead = Layout.PULL_DEAD_ZONE_PX;
        double max = Layout.PULL_MAX_PX;

        checks.check("拉距为 0 时力度为 0", ShotPower.powerFor(0.0) == 0.0);
        checks.check("拉距恰好等于死区时力度仍是 0（边界归死区，不归力度）",
                ShotPower.powerFor(dead) == 0.0);
        checks.check("光标拖到球的那一侧（拉距为负）力度为 0，不会变成负力度",
                ShotPower.powerFor(-500.0) == 0.0);
        checks.check("拉距是 NaN 时力度为 0（浮点坏值不许传播成力度）",
                ShotPower.powerFor(Double.NaN) == 0.0);

        checks.check("刚过死区就有一点力度（不是「要么 0 要么满」）",
                ShotPower.powerFor(dead + 0.5) > 0.0);

        checks.checkNear("死区与满力的正中间恰好是半力",
                0.5, ShotPower.powerFor((dead + max) * 0.5), 1e-12);
        checks.checkNear("拉满距离时力度为 1", 1.0, ShotPower.powerFor(max), 0.0);
        checks.checkNear("拉过头（拖到窗口外）力度封顶在 1，不溢出",
                1.0, ShotPower.powerFor(max * 100.0), 0.0);

        // 反解必须与正解互为逆函数——渲染与自检都靠它把「力度 x」摊回「该拉多远」
        checks.checkNear("反解：力度 0 对应拉距 = 死区", dead, ShotPower.pullFor(0.0), 0.0);
        checks.checkNear("反解：力度 1 对应拉距 = 满力距离", max, ShotPower.pullFor(1.0), 0.0);
        boolean roundTrip = true;
        double worst = 0.0;
        for (int index = 0; index <= 100; index = index + 1) {
            double profileValue = index / 100.0;
            double back = ShotPower.powerFor(ShotPower.pullFor(profileValue));
            worst = Math.max(worst, Math.abs(back - profileValue));
            if (Math.abs(back - profileValue) > 1e-12) {
                roundTrip = false;
            }
        }
        checks.check("反解与正解互为逆函数（101 个力度值往返一致）", roundTrip,
                String.format("最大偏差 %.3e", worst));

        // 单调：拉得越远力度越大。非单调的映射在手上表现为「拉过头反而变轻」
        boolean monotone = true;
        double previous = -1.0;
        for (int indexValue = 0; indexValue <= 400; indexValue = indexValue + 1) {
            double value = ShotPower.powerFor(indexValue / 400.0 * (max * 1.2));
            if (value < previous) {
                monotone = false;
                break;
            }
            previous = value;
        }
        checks.check("拉距→力度单调不减", monotone);
    }

    /** 检查力度到初速度的边界、单调性及区间换算 */
    static void checkSpeedMapping(CheckHarness checks) {
        double min = PhysicsConfig.MIN_SHOT_SPEED;
        double max = PhysicsConfig.MAX_SHOT_SPEED;

        checks.checkNear("力度 0 映射到最小初速（拉出死区就是一记轻推，不是没打）",
                min, ShotPower.speedFor(0.0), 0.0);
        checks.checkNear("力度 1 映射到最大初速", max, ShotPower.speedFor(1.0), 0.0);
        checks.checkNear("力度 0.5 映射到区间中点", (min + max) / 2.0, ShotPower.speedFor(0.5), 1e-12);

        // 超界值必须被夹住：力度条可能因为浮点误差给出 1.0000000001，
        // 让它乘进初速就等于悄悄突破了 MAX_SHOT_SPEED（进而突破子步位移不变量）
        checks.checkNear("小于 0 的力度被夹到最小", min, ShotPower.speedFor(-3.0), 0.0);
        checks.checkNear("大于 1 的力度被夹到最大", max, ShotPower.speedFor(7.0), 0.0);

        boolean monotone = true;
        double previous = Double.NEGATIVE_INFINITY;
        for (int index = 0; index <= 200; index = index + 1) {
            double value = ShotPower.speedFor(index / 200.0);
            if (value <= previous) {
                monotone = false;
                break;
            }
            previous = value;
        }
        checks.check("力度→初速严格单调递增", monotone);
    }

    /** 检查瞄准射线的首次接触位置与分离方向 */
    static void checkAimGeometry(CheckHarness checks) {
        Table table = new Table();
        Vec2 origin = new Vec2(200.0, 280.0);
        Vec2 alongX = new Vec2(1.0, 0.0);

        // ---- 正撞：虚影球停在两球心距 2R 处，不是球心重合处 ----
        Vec2 targetCentre = new Vec2(500.0, 280.0);
        List<Ball> withTarget = List.of(
                new Ball(BallCatalog.CUE_ID, origin),
                new Ball(1, targetCentre));
        AimCalculator.Preview ballHit = AimCalculator.preview(origin, alongX, table, withTarget);

        checks.check("瞄准：撞到球时报「有接触」且「撞的是球」",
                ballHit.hitAnything() && ballHit.hitBall());
        checks.checkNear("瞄准：虚影球球心距起点 300 − 2R（球心在球面上停住）",
                300.0 - 2.0 * Ball.RADIUS, ballHit.ghost().distanceTo(origin), 1e-9);
        checks.check("瞄准：报告出被撞的是哪一号球", ballHit.targetId() == 1,
                "targetId = " + ballHit.targetId());
        checks.checkNear("瞄准：虚影球心到目标球心恰好 2R（渲染器正是靠这条重建目标球心的）",
                2.0 * Ball.RADIUS, ballHit.ghost().distanceTo(targetCentre), 1e-9);

        checks.checkNotNull("正撞：给出目标球分离方向", ballHit.targetSeparation());
        checks.checkNotNull("正撞：给出主球分离方向", ballHit.cueSeparation());
        checks.checkNear("正撞：目标球沿球心连线出去（此处即 +x）",
                1.0, ballHit.targetSeparation().x, 1e-12);
        checks.checkNear("正撞：目标球分离方向是单位向量",
                1.0, ballHit.targetSeparation().length(), 1e-12);
        checks.checkNear("正撞：主球沿原方向跟进（恢复系数 < 1 才有，绝不是横着飞出去）",
                1.0, ballHit.cueSeparation().x, 1e-12);
        checks.checkNear("正撞：主球分离方向是单位向量",
                1.0, ballHit.cueSeparation().length(), 1e-12);

        // ---- 切球：90° 定律 ----
        // 目标球心相对瞄准线偏一个 R，于是两球心连线恰好是 30°，虚影球位置有闭式解
        Vec2 cutTarget = new Vec2(500.0, 280.0 + Ball.RADIUS);
        AimCalculator.Preview cut = AimCalculator.preview(origin, alongX, table,
                List.of(new Ball(BallCatalog.CUE_ID, origin), new Ball(3, cutTarget)));

        double halfGap = Math.sqrt(4.0 * Ball.RADIUS * Ball.RADIUS - Ball.RADIUS * Ball.RADIUS);
        checks.checkNear("切球：虚影球心 x = 500 − √3·R（接触点由两球心距 2R 定出）",
                500.0 - halfGap, cut.ghost().x, 1e-9);
        checks.checkNear("切球：虚影球心仍在瞄准线上（y 不变）", 280.0, cut.ghost().y, 1e-12);
        checks.checkNear("切球：虚影球心到目标球心仍是 2R",
                2.0 * Ball.RADIUS, cut.ghost().distanceTo(cutTarget), 1e-9);

        Vec2 normal = cut.targetSeparation();
        Vec2 center = cut.cueSeparation();
        checks.checkNear("切球：目标球分离方向 = 球心连线（x 分量 √3/2）",
                Math.sqrt(3.0) * 0.5, normal.x, 1e-12);
        checks.checkNear("切球：目标球分离方向 = 球心连线（y 分量 1/2）", 0.5, normal.y, 1e-12);
        checks.checkNear("切球：主球分离方向是单位向量", 1.0, center.length(), 1e-12);
        checks.check("切球：主球被弹向目标球连线的另一侧（目标球在下方，主球往上走）",
                center.y < 0.0, "c = " + center);
        checks.check("切球：主球分离方向不倒着飞（与来向夹角小于 90°）", center.dot(alongX) > 0.0,
                String.format("与来向夹角的余弦 %.6f", center.dot(alongX)));
        // 切向碰撞摩擦会略微改变分离方向，因此检查仍接近垂直，但不要求分量为零
        checks.check("切球：主球分离方向几乎垂直于球心连线（90° 定律）",
                Math.abs(center.dot(normal)) < 0.07,
                String.format("沿连线的分量 %.6f（0 即严格垂直）", center.dot(normal)));
        checks.check("切球：两条分离线确实不同向（否则就是把 90° 定律画丢了）",
                Math.abs(center.dot(normal)) < 0.99,
                String.format("两方向夹角的余弦 %.6f", center.dot(normal)));

        // ---- 空台撞库：虚影球停在球心离库边线 R 处 ----
        List<Ball> onlyCue = List.of(new Ball(BallCatalog.CUE_ID, origin));
        AimCalculator.Preview railHit = AimCalculator.preview(origin, alongX, table, onlyCue);
        double rawRight = Layout.PLAYFIELD_RIGHT - origin.x;      // 射线到库边线的交点
        checks.checkNear("瞄准：撞库距离 = 射线交点 − R（主球球心停下的位置）",
                rawRight - Ball.RADIUS, railHit.ghost().distanceTo(origin), 1e-9);
        checks.check("瞄准：撞库时报「有接触」但「没撞到球」",
                railHit.hitAnything() && !railHit.hitBall(), "targetId = " + railHit.targetId());
        checks.check("瞄准：撞库时没有分离线可画（没有第二颗球，分离无从谈起）",
                railHit.targetSeparation() == null && railHit.cueSeparation() == null);
        checks.check("瞄准：撞库终点落在台面内（瞄准线不会扎进库边里）",
                table.isInsidePlayfield(railHit.ghost()), "终点 " + railHit.ghost());

        // ---- 斜射：R / |dir·n| 这个修正必须真的做 ----
        // 正射时它等于 R，看着不明显；45° 入射时它是 1.414R，直接拿交点当终点会让
        // 瞄准线扎进库边 15.6px——正是「球像要陷进库边」的观感来源
        Vec2 diagonal = new Vec2(Math.sqrt(0.5), -Math.sqrt(0.5));
        AimCalculator.Preview oblique = AimCalculator.preview(origin, diagonal, table, onlyCue);
        double dropToTopRail = origin.y - Layout.PLAYFIELD_TOP;
        double expectedOblique = (dropToTopRail - Ball.RADIUS) / Math.sqrt(0.5);
        checks.checkNear("瞄准：45° 入射时终点按 R/|dir·n| 退让",
                expectedOblique, oblique.ghost().distanceTo(origin), 1e-6);
        checks.check("瞄准：斜射终点明显短于原始交点（证明确实退了）",
                oblique.ghost().distanceTo(origin) < dropToTopRail / Math.sqrt(0.5) - 10.0,
                String.format("退让后 %.3f，原始交点 %.3f",
                        oblique.ghost().distanceTo(origin), dropToTopRail / Math.sqrt(0.5)));

        // ---- 从袋口缺口穿出：什么都不撞。这是合法输入，不是错误 ----
        Vec2 centre = new Vec2(Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5,
                Layout.playfieldCenterY());
        AimCalculator.Preview throughMouth = AimCalculator.preview(
                centre, new Vec2(0.0, -1.0), table, List.of(new Ball(BallCatalog.CUE_ID, centre)));
        checks.check("瞄准：正对中袋缺口直射时报「什么都没撞到」，而不是乱给一个位置",
                !throughMouth.hitAnything() && throughMouth.ghost() == null
                        && !throughMouth.hitBall() && throughMouth.targetId() < 0,
                "ghost = " + throughMouth.ghost() + "，targetId = " + throughMouth.targetId());

        // ---- 零方向：调用方给坏值时返回「什么都没撞到」，而不是 NaN 传播出去 ----
        AimCalculator.Preview degenerate = AimCalculator.preview(origin, Vec2.ZERO, table, onlyCue);
        checks.check("瞄准：零方向向量被当成「什么都没撞到」处理",
                !degenerate.hitAnything() && degenerate.ghost() == null);
    }

    /**
     * 杆头位置是「力度 → 看得见的东西」这一段的全部内容
     *
     * <p>这里断言的是像素级精确的几何而不是「画出来了没有」：渐变画歪了没人看得出来，
     * 但杆头位置算错就是一眼假，而且它是玩家读力度的第二个渠道（第一个是力度条）
     */
    static void checkCueGeometry(CheckHarness checks) {
        Vec2 ball = new Vec2(300.0, 280.0);
        Vec2 right = new Vec2(1.0, 0.0);
        double rest = Ball.RADIUS + Layout.CUE_TIP_GAP_PX;

        checks.checkNear("球杆：待命时杆头离球心 R + 间隙（球面与杆头之间留出可见空隙）",
                ball.x - rest, CueRenderer.tipCentre(ball, right, 0.0).x, 0.0);
        checks.checkNear("球杆：待命时杆头不偏轴（仍在瞄准线上）",
                ball.y, CueRenderer.tipCentre(ball, right, 0.0).y, 0.0);
        checks.checkNear("球杆：满力时杆头退到 R + 间隙 + 后拉距离",
                ball.x - rest - Layout.CUE_PULL_BACK_PX,
                CueRenderer.tipCentre(ball, right, 1.0).x, 0.0);
        checks.checkNear("球杆：半力时后退量恰好是满力的一半（力度看得见地线性）",
                ball.x - rest - Layout.CUE_PULL_BACK_PX * 0.5,
                CueRenderer.tipCentre(ball, right, 0.5).x, 1e-12);

        // 绘制入口也限制力度范围，保证直接传入异常值时球杆仍位于主球后方
        checks.checkNear("球杆：负力度被夹到待命位（杆不会跑到球前面去）",
                ball.x - rest, CueRenderer.tipCentre(ball, right, -2.0).x, 0.0);
        checks.checkNear("球杆：超过满力的力度被夹住（杆不会退到更远）",
                ball.x - rest - Layout.CUE_PULL_BACK_PX,
                CueRenderer.tipCentre(ball, right, 5.0).x, 0.0);

        // 杆永远在瞄准方向的反面：朝上瞄，杆就该在球的下方（屏幕 y 更大）
        Vec2 center = new Vec2(0.0, -1.0);
        Vec2 tip = CueRenderer.tipCentre(ball, center, 0.0);
        checks.check("球杆：始终在瞄准方向的反面（朝上瞄时杆在球下方）",
                tip.y > ball.y && Math.abs(tip.x - ball.x) < 1e-12,
                String.format("杆头 (%.3f, %.3f)，球心 (%.3f, %.3f)", tip.x, tip.y, ball.x, ball.y));

        // 后拉本身必须「看得见」，否则球杆就只是装饰：满力与待命至少要差出半个球径
        checks.check("球杆：满力与待命的后拉距离差得足够明显（大于球半径）",
                Layout.CUE_PULL_BACK_PX > Ball.RADIUS,
                String.format("后拉 %.1fpx，球半径 %.1fpx", Layout.CUE_PULL_BACK_PX, Ball.RADIUS));
    }

    /** 检查快照的默认值、数组隔离和预览状态 */
    static void checkViewShape(CheckHarness checks) {
        BallView[] source = {
                new BallView(BallCatalog.CUE_ID, 100.0, 200.0, false),
                new BallView(5, 300.0, 400.0, true),
                new BallView(9, 500.0, 400.0, false),
        };
        GameStateView view = new GameStateView(source, null, 0.0, false, null);

        // 构造时拷贝数组：这是「发布出去的快照之后不可能被改」的第一道保证
        source[1] = new BallView(15, 999.0, 999.0, true);
        source[2] = new BallView(14, 999.0, 999.0, true);
        checks.check("快照构造时拷贝了数组（外部改写自己的数组影响不到它）",
                view.ball(1).id() == 5 && view.ball(1).x() == 300.0 && view.ball(2).id() == 9,
                "ball(1) = " + view.ball(1) + "，ball(2) = " + view.ball(2));

        checks.check("快照：在台球只数 onTable 的那些", view.ballsOnTable() == 1,
                "ballsOnTable = " + view.ballsOnTable());
        checks.check("快照：主球不在台时 cueBallOnTable() 为假、坐标为 NaN",
                !view.cueBallOnTable() && Double.isNaN(view.cueX()) && Double.isNaN(view.cueY()));
        checks.check("快照：传 null 瞄准线会被换成空对象（渲染器不必到处判空）",
                view.aim() != null && !view.aim().isPresent());
        checks.check("快照：传 null 状态文案会被换成空串", "".equals(view.status()));

        GameStateView empty = GameStateView.empty();
        checks.check("空快照：0 颗球、主球不在台、没有瞄准线",
                empty.ballCount() == 0 && !empty.cueBallOnTable() && !empty.aim().isPresent());

        // 瞄准线的三个查询口径要互不错位。注意「撞库」与「从袋口射出」是两回事：
        // 撞库时有虚影球（主球球心会停在库边前），从袋口射出时才什么都没有
        AimView nothing = AimView.NONE;
        AimView railHit = new AimView(new Vec2(1.0, 0.0), new Vec2(400.0, 280.0),
                new Vec2(400.0, 280.0), -1, null, null);
        AimView escape = new AimView(new Vec2(1.0, 0.0), new Vec2(1400.0, 280.0),
                null, -1, null, null);
        AimView ballContact = new AimView(new Vec2(1.0, 0.0), new Vec2(300.0, 280.0),
                new Vec2(300.0, 280.0), 7, new Vec2(0.6, 0.8), new Vec2(0.8, -0.6));

        checks.check("瞄准线空对象：三个口径全为假",
                !nothing.isPresent() && !nothing.hasGhost() && !nothing.hitBall());
        checks.check("瞄准线（撞库）：有虚影球但不是球接触",
                railHit.isPresent() && railHit.hasGhost() && !railHit.hitBall(),
                "targetId = " + railHit.targetId());
        checks.check("瞄准线（从袋口射出）：有方向、没有虚影球、没撞到球",
                escape.isPresent() && !escape.hasGhost() && !escape.hitBall());
        checks.check("瞄准线（撞球）：三个口径全为真",
                ballContact.isPresent() && ballContact.hasGhost() && ballContact.hitBall());
        checks.check("瞄准线：被撞球号一路带到了快照里", ballContact.targetId() == 7);
        checks.check("瞄准线：两条分离方向一路带到了快照里",
                ballContact.targetSeparation() != null && ballContact.cueSeparation() != null);
    }

    /** 检查工具只使用静态入口，不需要创建对象 */
    private UIInputChecks() {
    }
}

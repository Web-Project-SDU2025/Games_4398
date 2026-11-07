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

/** 使用真实鼠标与键盘入口检查对局流程、取消和快照隔离 */
final class UISessionChecks {

    /** 通过鼠标事件检查开赛、摆球、瞄准、拉杆与整杆结算 */
    static void checkMatchShot(CheckHarness checks) {
        MatchSession session;
        JPanel canvas;
        try {
            canvas = new JPanel();
            // 画布尺寸设成逻辑画布尺寸，于是缩放恰为 1、鼠标坐标与逻辑坐标逐位相同，
            // 下面那些「球心 − 100px」的整数夹具才是字面意义上的坐标。窗口能缩放之后
            // 这层映射是真实存在的（Viewport），夹具不写死尺寸就等于默认它是恒等——
            // 而那条默认本身由 GeometryChecks 的 Viewport 检查钉住
            canvas.setSize(Layout.WINDOW_W, Layout.WINDOW_H);
            session = new MatchSession();
            session.input().attach(canvas);
        } catch (Throwable error) {
            checks.check("对局：会话与鼠标事件源可在无头环境构造", false, error.toString());
            return;
        }
        checks.check("对局：会话与鼠标事件源可在无头环境构造", true);

        checkOpening(checks, session, canvas);
        checkInitialPlacement(checks, session, canvas);
        double cueX = session.view().cueX();
        double cueY = session.view().cueY();
        int pixelX = (int) cueX;
        int pixelY = (int) cueY;
        checkCursorDirection(checks, session, canvas, pixelX, pixelY);
        int pullX = checkPullGesture(checks, session, canvas, pixelX, pixelY);
        GameStateView rest = checkReleasedShot(
                checks, session, canvas, pixelY, pullX, cueX, cueY);
        checkShotBanner(checks, session, rest);
        checkCancels(checks, session, canvas, pixelX, pixelY, pullX);
    }

    /**
     * 三种「不算数」的收场：死区内松手、Esc、右键
     *
     * <p>它们都被归到一处，因为它们是同一件事的三条入口：<b>没打出去的杆必须什么都不留下</b>
     * 一个没收干净的拉杆状态会让下一杆带着上一杆的力度飞出去，而这种 bug 在演示时
     * 看起来就像「球自己动了」
     *
     * <p>每一段前面都先 {@link #resetAndPlace}：重摆之后主球要重新摆到开球线后，
     * 而<b>只有摆下去才谈得上瞄准</b>。这不是夹具的麻烦，正是真实玩家每次按 R 之后要做的事
     */
    static void checkCancels(CheckHarness checks, MatchSession session, JPanel canvas,
                                     int pixelX, int pixelY, int pullX) {
        // ---- R 重摆 ----
        resetAndPlace(session, canvas, pixelX, pixelY);
        checks.check("按 R 之后 16 颗球回到摆球位、力度归零、又轮到玩家 1",
                !session.view().shotInFlight() && session.view().power() == 0.0
                        && allInsidePlayfield(session.view())
                        && ballsVisible(session.view()) == BallCatalog.BALL_COUNT
                        && session.view().match().current() == Player.ONE,
                "画面上 " + ballsVisible(session.view()) + " 颗");

        // ---- 死区内松手：取消，不发球 ----
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                pixelX - 100, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        int tinyX = (int) (pixelX - 100 - Layout.PULL_DEAD_ZONE_PX * 0.5);
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                tinyX, pixelY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.check("拉杆不足死区时力度为 0", session.view().power() == 0.0);
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                tinyX, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("死区内松手不发球（点一下鼠标不该打出一杆）",
                !session.view().shotInFlight());
        checks.check("死区内松手后力度读数仍是 0", session.view().power() == 0.0);

        // ---- Esc 取消 ----
        resetAndPlace(session, canvas, pixelX, pixelY);
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                pixelX - 100, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pullX, pixelY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.checkNear("取消之前确实拉出了力度（否则下面两条是空过）",
                0.5, session.view().power(), 1e-12);
        checks.check("Esc 已绑定到取消动作（键位表里有它）",
                fireKey(canvas, KeyEvent.VK_ESCAPE));
        session.tickFixed();
        checks.check("Esc 取消后力度归零且没有发球",
                session.view().power() == 0.0 && !session.view().shotInFlight());
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                pullX, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("取消之后再松开左键也不会把这一杆打出去",
                !session.view().shotInFlight());

        // ---- 右键取消（按下即取消，不等松开）----
        resetAndPlace(session, canvas, pixelX, pixelY);
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                pixelX - 100, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pullX, pixelY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                pullX, pixelY, T0, MouseEvent.BUTTON3));
        session.tickFixed();
        checks.check("右键按下即取消（不用等松开）",
                session.view().power() == 0.0 && !session.view().shotInFlight());
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                pullX, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("右键取消后松开左键也不会发球", !session.view().shotInFlight());

        // ---- R 重摆把方向也复位 ----
        // 先把方向拧到一个非默认值，否则下面那条断言就成了「本来就对」
        resetAndPlace(session, canvas, pixelX, pixelY);
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pixelX, pixelY - 100, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.checkNear("（前置）方向现在是朝下 +y", 1.0, session.view().aim().dir().y, 1e-12);
        checks.check("R 已绑定到重摆动作（键位表里有它）", fireKey(canvas, KeyEvent.VK_R));
        session.tickFixed();
        checks.check("按 R 回到开球布置：主球要重新摆，不是立刻能打",
                session.view().match().phase() == GamePhase.BREAK_SETUP);
        checks.check("按 R 也把上一杆的判决与胜负清掉（新的一局）",
                session.view().match().outcome() == null
                        && session.view().match().pottedCount(Player.ONE) == 0
                        && session.view().match().pottedCount(Player.TWO) == 0);
        // 重新落位时把光标停在球心上：同一帧的「光标 → 方向」于是动不了方向，
        // 这条断言量的才是重摆、而不是光标
        resetAndPlace(session, canvas, pixelX, pixelY);
        checks.check("重摆把瞄准方向复位回默认 +x",
                session.view().aim().dir().x == 1.0 && session.view().aim().dir().y == 0.0,
                "dir = " + session.view().aim().dir());
        checks.check("重摆后力度归零",
                !session.view().shotInFlight() && session.view().power() == 0.0);

        checkRestartButton(checks, session, canvas, pixelX, pixelY);
    }

    /**
     * 「重新开始」按钮：画在哪、就点在哪；点下去就重开一局
     *
     * <p>它与 R 键是同一条路径的两个入口，但比键盘更值得钉住：按钮是<b>画</b>出来的，
     * 于是「按钮挪了 4px、热区没挪」这种错只会在鼠标上出现——玩家看到的是「点了没反应」，
     * 而他不知道该往哪儿挪一点。热区与矩形出自 {@link HudRenderer} 的同一处，这里把
     * 这条关系钉成断言
     */
    static void checkRestartButton(CheckHarness checks, MatchSession session, JPanel canvas,
                                           int pixelX, int pixelY) {
        Rectangle2D.Double button = HudRenderer.restartButtonRect();
        double bx = button.getCenterX();
        double by = button.getCenterY();

        checks.check("「重新开始」按钮整个落在球台之外（点它不会顺手打到球）",
                button.y >= Layout.PLAYFIELD_BOTTOM + Layout.CUSHION_BAND,
                describe(button));
        checks.check("按钮中心的命中判定为真、上方 4px 处为假（热区与画出来的矩形是同一个）",
                HudRenderer.hitsRestartButton(bx, by)
                        && !HudRenderer.hitsRestartButton(bx, button.y - 4.0));

        // 先把局面弄脏：打一杆，让球散开、判决出现
        pullShoot(session, canvas, pixelX - 100, pixelY, pixelX - 100 - (int) Layout.PULL_MAX_PX, pixelY);
        checks.check("（前置）按钮测试之前局面确实动过了（有球离位）",
                session.view().shotInFlight() || !allInsidePlayfield(session.view())
                        || session.view().match().phase() != GamePhase.AIMING,
                "阶段 " + session.view().match().phase());
        settleSession(session);

        click(session, canvas, (int) bx, (int) by);
        checks.check("点「重新开始」回到开球布置",
                session.view().match().phase() == GamePhase.BREAK_SETUP,
                "阶段 " + session.view().match().phase());
        checks.check("重开之后 16 颗球全部回到台面、胜负与托盘都清空",
                ballsVisible(session.view()) == BallCatalog.BALL_COUNT
                        && session.view().match().outcome() == null
                        && session.view().match().pottedCount(Player.ONE) == 0
                        && session.view().match().pottedCount(Player.TWO) == 0,
                "画面上 " + ballsVisible(session.view()) + " 颗");
        checks.check("点按钮这一下不会顺手打出一杆",
                !session.view().shotInFlight() && session.view().power() == 0.0);
        noteStatus(session.view());
    }

    /**
     * 从摆局到终局，一条条走 UI 真正会走的路
     *
     * <p>摆局用 {@link RulesEngine#arrange}（S 组也用同一个入口）：它摆的是<b>初始条件</b>，
     * 之后照样是真实物理、真实判定。这几段查的不是规则本身——规则有 S 组那 176 条钉着——
     * 而是<b>规则算出来的东西有没有被会话正确地变成画面上的字、颜色和托盘</b>
     */
    static void checkMatchFlow(CheckHarness checks) {
        MatchSession session;
        JPanel canvas;
        try {
            canvas = new JPanel();
            canvas.setSize(Layout.WINDOW_W, Layout.WINDOW_H);
            session = new MatchSession();
            session.input().attach(canvas);
        } catch (Throwable error) {
            checks.check("对局流程：会话与鼠标事件源可在无头环境构造", false, error.toString());
            return;
        }
        // 先把开赛那道门点掉，后面全部靠摆局
        click(session, canvas, CUE_X, CUE_Y);

        checkPottedTrays(checks, session, canvas);
        checkRestrictedFreeBall(checks, session, canvas);
        checkPlacementBoundary(checks, session, canvas);
        checkWinningShot(checks, session, canvas);
        // ---- 调试浮层 ----
        checks.check("D 已绑定到调试浮层（键位表里有它）", fireKey(canvas, KeyEvent.VK_D));
        session.tickFixed();
        MatchView debug = session.view().match();
        checks.check("按 D 打开调试浮层，而且浮层真的有内容（不是一个空框）",
                debug.debug() && debug.debugLineCount() >= 5,
                "行数 " + debug.debugLineCount());
        checks.check("调试浮层第一行报的就是当前阶段",
                debug.debugLineCount() > 0
                        && debug.debugLine(0).contains(debug.phase().name()),
                firstDebugLine(debug));
        checks.check("再按一次 D 关掉浮层", fireKey(canvas, KeyEvent.VK_D));
        session.tickFixed();
        checks.check("关掉之后浮层一行都不拼（关着的时候不产生任何字符串）",
                !session.view().match().debug()
                        && session.view().match().debugLineCount() == 0);
    }

    /** 返回调试浮层第一行；没有行时返回空串 */
    static String firstDebugLine(MatchView match) {
        if (match.debugLineCount() == 0) {
            return "";
        }
        return match.debugLine(0);
    }

    /**
     * 拿一份快照、继续推进模拟、再回头读那份旧快照——旧快照必须一字不变
     *
     * <p>这是整个线程模型的核心断言。如果快照里挂的是活的 {@code Ball}（或者数组没被拷贝），
     * 旧快照会跟着一起变，上面这条立刻失败。而同一个错误在真机上只表现为「偶尔看到球瞬移」，
     * 复现率随机器负载变化，靠演示是抓不到的
     */
    static void checkSnapshotIsolation(CheckHarness checks) {
        MatchSession session;
        JPanel canvas;
        try {
            canvas = new JPanel();
            canvas.setSize(Layout.WINDOW_W, Layout.WINDOW_H);   // 同 checkMatchShot：缩放 1:1
            session = new MatchSession();
            session.input().attach(canvas);
        } catch (Throwable error) {
            checks.check("快照隔离：会话与鼠标事件源可在无头环境构造", false, error.toString());
            return;
        }

        // 直接给主球一个速度不方便，就借鼠标事件走一遍正式路径（满力开球）
        // 开赛 → 落位 → 在主球左边按住、往左拖满 PULL_MAX_PX 松开：方向 +x、力度 1
        click(session, canvas, CUE_X, CUE_Y);        // 点掉开场浮层
        click(session, canvas, CUE_X, CUE_Y);        // 把主球摆到开球位
        checks.checkNear("（前置）主球已经摆到开球位", (double) CUE_X, session.view().cueX(), 1e-9);

        pullShoot(session, canvas,
                CUE_X - 100, CUE_Y, (int) (CUE_X - 100 - Layout.PULL_MAX_PX), CUE_Y);
        checks.checkNear("（前置）满力：力度读数是 100%", 1.0, session.view().power(), 1e-12);

        GameStateView earlier = session.view();
        checks.check("快照隔离：取到了球在飞的那一帧", earlier.shotInFlight());

        int count = earlier.ballCount();
        double[] samples = new double[count];
        double[] samplesValue = new double[count];
        for (int index = 0; index < count; index = index + 1) {
            samples[index] = earlier.ball(index).x();
            samplesValue[index] = earlier.ball(index).y();
        }

        for (int indexValue = 0; indexValue < 80; indexValue = indexValue + 1) {
            session.tickFixed();
        }
        GameStateView later = session.view();

        boolean identical = true;
        String firstDiff = "";
        for (int indexValueForI = 0; indexValueForI < count; indexValueForI = indexValueForI + 1) {
            // 关键是这里重新去读 earlier —— 那个快照对象在 80 步之前就发布出去了
            // 位级比较而不是容差：容差相等并不能排除「这份快照其实是活的、
            // 只是恰好还没挪远」
            if (Double.doubleToLongBits(samples[indexValueForI]) != Double.doubleToLongBits(earlier.ball(indexValueForI).x())
                    || Double.doubleToLongBits(samplesValue[indexValueForI]) != Double.doubleToLongBits(earlier.ball(indexValueForI).y())) {
                identical = false;
                if (firstDiff.isEmpty()) {
                    firstDiff = "球 " + earlier.ball(indexValueForI).id() + " 从 (" + samples[indexValueForI] + ", " + samplesValue[indexValueForI]
                            + ") 变成了 (" + earlier.ball(indexValueForI).x() + ", " + earlier.ball(indexValueForI).y() + ")";
                }
            }
        }
        checks.check("旧快照在模拟继续推进 80 步后逐位不变（不是活动视图）",
                identical, firstDiff);

        // 反过来也要成立：快照不是「永远不变」的死物，模拟确实在动
        int moved = 0;
        for (int indexValueForI = 0; indexValueForI < count; indexValueForI = indexValueForI + 1) {
            if (Double.doubleToLongBits(samples[indexValueForI]) != Double.doubleToLongBits(later.ball(indexValueForI).x())
                    || Double.doubleToLongBits(samplesValue[indexValueForI]) != Double.doubleToLongBits(later.ball(indexValueForI).y())) {
                moved = moved + 1;
            }
        }
        checks.check("但新快照确实动了（上面那条不是靠「谁都没动」蒙过去的）",
                moved >= 2, "80 步后有 " + moved + " 颗球位置变化");
    }

    /** 检查工具只使用静态入口，不需要创建对象 */
    private UISessionChecks() {
    }

    /** 检查开场点击只启动对局，不同时发出击球 */
    private static void checkOpening(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 开场浮层：局面已经摆好，但有一道门挡着 ----
        GameStateView initial = session.view();
        noteStatus(initial);
        checks.check("初始快照是 16 颗球且不在击球中",
                initial.ballCount() == BallCatalog.BALL_COUNT && !initial.shotInFlight());
        checks.check("开场时报 MATCH_START（浮层盖着，比赛还没开始）",
                initial.match().phase() == GamePhase.MATCH_START);
        checks.check("开场时主球在台上、位置就是默认开球位（不是一颗看不见的球）",
                initial.cueBallOnTable()
                        && Math.abs(initial.cueX() - RackBuilder.DEFAULT_BREAK_CUE_X) < 1e-9
                        && Math.abs(initial.cueY() - Layout.playfieldCenterY()) < 1e-9,
                String.format("主球 (%.4f, %.4f)", initial.cueX(), initial.cueY()));
        checks.check("开场时没有瞄准线（还没到能打的时候）", !initial.aim().isPresent());

        for (int index = 0; index < 8; index = index + 1) {
            session.tickFixed();
        }
        checks.check("不点浮层就一直停在 MATCH_START（开赛是一道真的门，不会自己过去）",
                session.view().match().phase() == GamePhase.MATCH_START);
        checks.check("开场空推几帧也不会把球推动",
                session.view().cueX() == initial.cueX() && !session.view().shotInFlight());

        // 门没开之前，一整套拉杆手势只应该把门打开，不该打出一杆
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                CUE_X - 100, CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        session.input().mouseDragged(drag(canvas, (int) (CUE_X - 100 - Layout.PULL_MAX_PX), CUE_Y));
        session.tickFixed();
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                (int) (CUE_X - 100 - Layout.PULL_MAX_PX), CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("开场浮层上做一整满力拉杆也不会打出一杆",
                !session.view().shotInFlight(), "阶段 " + session.view().match().phase());
        checks.check("而那一套手势确实把比赛开了（进入开球布置）",
                session.view().match().phase() == GamePhase.BREAK_SETUP);
        noteStatus(session.view());

    }

    /** 检查开球区拒绝非法落点，并在松手后放置主球 */
    private static void checkInitialPlacement(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 开球布置只认开球线后 ----
        int overLineX = (int) Layout.HEAD_STRING_X + 40;
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                overLineX, CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("开球布置时虚影跟着光标走（位置就是光标位置）",
                session.view().match().placing() && session.view().match().hasPlacement()
                        && session.view().match().placementX() == overLineX);
        checks.check("光标在开球线右侧 → 虚影判为放不下（画出来是红的）",
                !session.view().match().placementValid());
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                overLineX, CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("在开球线右侧松手会被拒绝：阶段仍是开球布置（球没落下去）",
                session.view().match().phase() == GamePhase.BREAK_SETUP);

        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                CUE_X, CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("光标在开球线左侧 → 虚影判为放得下（画出来是绿的）",
                session.view().match().placementValid());
        checks.check("拖放期间主球被摘出快照：屏幕上不会同时出现实体球与虚影",
                !session.view().cueBallOnTable() && Double.isNaN(session.view().cueX()));
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                CUE_X, CUE_Y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("落位成功：进入瞄准阶段", session.view().match().phase() == GamePhase.AIMING);
        checks.check("主球落在松手的位置，而不是刚才被拒的那个点",
                session.view().cueBallOnTable()
                        && Math.abs(session.view().cueX() - CUE_X) < 1e-9
                        && Math.abs(session.view().cueY() - CUE_Y) < 1e-9,
                String.format("主球 (%.4f, %.4f)", session.view().cueX(), session.view().cueY()));
        noteStatus(session.view());

    }

    /** 检查鼠标相对主球的方向与球心附近的方向保持 */
    private static void checkCursorDirection(CheckHarness checks, MatchSession session, JPanel canvas, int pixelX, int pixelY) {
        // ---- 方向是反的：光标在球的哪边，球就往反方向走（弹弓）----
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pixelX + 60, pixelY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        AimView reversed = session.view().aim();
        checks.checkNear("光标放在球的右边 → 球往左走（x 分量为 −1）", -1.0, reversed.dir().x, 1e-12);
        checks.checkNear("光标放在球的右边 → 球沿水平方向走（y 分量为 0）", 0.0, reversed.dir().y, 1e-12);

        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pixelX - 100, pixelY + 100, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        AimView slingshot = session.view().aim();
        checks.checkNear("光标放在球的左下 → 球往右上 45°（x 分量 +√0.5）",
                Math.sqrt(0.5), slingshot.dir().x, 1e-12);
        checks.checkNear("光标放在球的左下 → 球往右上 45°（y 分量 −√0.5）",
                -Math.sqrt(0.5), slingshot.dir().y, 1e-12);

        // 光标正好压在球心上时方向没有意义：必须保持上一次的方向，而不是跳回默认 +x
        // （玩家的手只是从球上划过去，方向不该突然翻掉）
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                pixelX, pixelY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.checkNear("光标压在球心上时保持上一次方向（不是跳回默认的 +x）",
                Math.sqrt(0.5), session.view().aim().dir().x, 1e-12);

    }

    /** 检查拉杆方向和力度可同时调整，返回半力松手位置 */
    private static int checkPullGesture(CheckHarness checks, MatchSession session, JPanel canvas, int pixelX, int pixelY) {
        // ---- 拉杆：按下定起点，之后方向与力度同时可调 ----
        //
        // 夹具：按下点 A 在主球左边 100px 处。选 A 而不是别的点，是为了下面那三个「离 A
        // 恰好 60px」的位置都能落在窗口内（x 最小到 32），而且位移两两构成 3-4-5 直角三角形
        // ——整段几何全是整数与精确的平方，断言才敢用 1e-12 的容差
        final int anchorX = pixelX - 100;
        final int anchorY = pixelY;
        final double swing = 60.0;

        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                anchorX, anchorY, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.checkNear("光标放在球的正左方 → 球朝正右方走",
                1.0, session.view().aim().dir().x, 1e-12);

        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                anchorX, anchorY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        checks.check("刚按下、还没拖：力度仍是 0（按下本身不是力度）", session.view().power() == 0.0);
        checks.check("拉杆期间瞄准线仍然给出（看得见自己要打哪）",
                session.view().aim().isPresent());

        // 三个离按下点 A 恰好 60px 的位置。它们到 A 的距离逐位相同，所以力度必须逐位相同；
        // 而它们相对主球的方位各不相同，所以方向必须各不相同
        //     Q1 = A + (−60,   0)   在主球正左方   → 球朝 +x
        //     Q2 = A + (−48, −36)   偏上           → 3-4-5 直角三角形，|Δ| = √(48²+36²) = 60
        //     Q3 = A + (  0, −60)   在主球正上方   → 球朝右下 45°
        int[][] swings = {{anchorX - (int) swing, anchorY}, {anchorX - 48, anchorY - 36}, {anchorX, anchorY - (int) swing}};

        for (int indexValue = 0; indexValue < swings.length; indexValue = indexValue + 1) {
            session.input().mouseDragged(drag(canvas, swings[indexValue][0], swings[indexValue][1]));
            session.tickFixed();
            checks.checkNear("绕按下点走 60px：力度只取决于走了多远（第 " + (indexValue + 1) + " 个方位）",
                    ShotPower.powerFor(swing), session.view().power(), 1e-12);
        }

        // 三个方位必须真的指向三个不同的方向，否则上面三条只是「同一个点测了三遍」
        // 这一条同时是「拉杆期间方向跟着光标走」的直接证据：旧的实现在这里会把方向锁死
        // 在按下那一刻的 +x 上，三条断言全炸
        session.input().mouseDragged(drag(canvas, swings[0][0], swings[0][1]));
        session.tickFixed();
        Vec2 dirQ1 = session.view().aim().dir();
        session.input().mouseDragged(drag(canvas, swings[2][0], swings[2][1]));
        session.tickFixed();
        Vec2 dirQ3 = session.view().aim().dir();
        checks.check("拉杆期间转手腕，瞄准方向跟着转（方向与力度不再互斥）",
                dirQ1.x > 0.99 && dirQ3.x > 0.5 && dirQ3.y > 0.5,
                "转了方向：Q1 = " + dirQ1 + "，Q3 = " + dirQ3);

        // 沿原方向往后拖 80px（拉距 = (死区 + 满力)/2）：力度恰好 0.5，方向不变
        double halfPull = (Layout.PULL_DEAD_ZONE_PX + Layout.PULL_MAX_PX) * 0.5;
        int pullX = (int) (anchorX - halfPull);
        session.input().mouseDragged(drag(canvas, pullX, pixelY));
        session.tickFixed();
        checks.checkNear("往后拖 80px 恰好是半力", 0.5, session.view().power(), 1e-12);
        checks.check("同一位置仍在球的正左方射线上，所以方向仍是 +x（力度变了、方向没变）",
                session.view().aim().dir().x == 1.0 && session.view().aim().dir().y == 0.0,
                "dir = " + session.view().aim().dir());

        // 再拖回起点：力度跟着回到 0。这是「力度是位置、不是已经过去的时长」的直接证据——
        // 旧的按住时长方案在这里只会一路涨上去，没有任何办法退回来
        session.input().mouseDragged(drag(canvas, anchorX, anchorY));
        session.tickFixed();
        checks.check("把光标拖回起点，力度跟着回到 0（可以反复微调，不是一次性时机）",
                session.view().power() == 0.0, "power = " + session.view().power());

        return pullX;
    }

    /** 检查松手、初速和停稳，返回这一杆结束时的快照 */
    private static GameStateView checkReleasedShot(CheckHarness checks, MatchSession session, JPanel canvas, int pixelY, int pullX, double cueX, double cueY) {
        // ---- 松开：半力击球 ----
        session.input().mouseDragged(drag(canvas, pullX, pixelY));
        session.tickFixed();
        double pullPower = session.view().power();
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                pullX, pixelY, T0, MouseEvent.BUTTON1));
        session.tickFixed();

        GameStateView flying = session.view();
        checks.check("松开后进入击球阶段", flying.shotInFlight());
        checks.check("球在飞时不再画瞄准线（连带球杆一起收起）", !flying.aim().isPresent());
        checks.checkNear("球在飞时力度条保留本杆读数（半力）", 0.5, flying.power(), 1e-12);

        // ---- 初速确实用上了这个力度 ----
        // 一帧只推进一个固定步，所以首步位移 ≈ 初速 × 步长（摩擦让实际值略小一点点）
        double deltaX = flying.cueX() - cueX;
        double deltaY = flying.cueY() - cueY;
        double expectedStep = ShotPower.speedFor(pullPower) * PhysicsConfig.FIXED_STEP_MS / 1000.0;
        checks.checkNear("首步位移 ≈ 半力初速 × 固定步长", expectedStep, deltaX, 0.02);
        checks.check("击球方向严格沿松手那一刻瞄准线的方向（横向位移恰为 0，所见即所得）",
                deltaY == 0.0, "dy = " + deltaY);

        // ---- 停稳 ----
        int ticks = 0;
        while (session.view().shotInFlight() && ticks < MAX_SHOT_TICKS) {
            session.tickFixed();
            ticks = ticks + 1;
        }
        GameStateView rest = session.view();
        checks.check("整杆在硬超时之前停稳（完整开球停得住）",
                !rest.shotInFlight() && ticks < MAX_SHOT_TICKS,
                "用了 " + ticks + " 步 = " + (ticks * PhysicsConfig.FIXED_STEP_MS) + " 模拟毫秒");
        checks.check("停稳后力度条归零", rest.power() == 0.0);
        checks.check("停稳后所有在台球都没出台面（没有球飞出、没有球陷进库边）",
                allInsidePlayfield(rest), describeOutliers(rest));

        return rest;
    }

    /** 检查判定横幅内容与持续时长，完成后进入下一手 */
    private static void checkShotBanner(CheckHarness checks, MatchSession session, GameStateView rest) {
        // ---- 停稳之后是「判定」，而且判定看得见 ----
        // 这一整段是 M6 的核心：判罚不再是「球一停就悄悄换手」，而是一个持续
        // VERDICT_BANNER_MS 的真实阶段，横幅把判决书原样显示出来
        noteStatus(rest);
        checks.check("停稳后进入判定阶段（判定占一段真实时间，不是一瞬间）",
                rest.match().phase() == GamePhase.RESOLVING,
                "阶段 " + rest.match().phase());
        checks.check("判定阶段盖出横幅", rest.match().hasBanner(),
                "横幅「" + rest.match().banner() + "」");
        checks.check("横幅的语气是三种结论之一（合法 / 犯规 / 胜负），不是中性",
                rest.match().bannerTone() != MatchView.Tone.NEUTRAL,
                "语气 " + rest.match().bannerTone());
        checks.check("HUD 状态行与横幅说的是同一句判决（同一份文案，两处显示）",
                rest.status().equals(rest.match().banner()),
                "状态行「" + rest.status() + "」横幅「" + rest.match().banner() + "」");

        int bannerTicks = 0;
        while (session.view().match().phase() == GamePhase.RESOLVING
                && bannerTicks < MAX_BANNER_TICKS) {
            session.tickFixed();
            bannerTicks = bannerTicks + 1;
        }
        checks.checkNear("横幅停留时长恰好是 Layout.VERDICT_BANNER_MS",
                Layout.VERDICT_BANNER_MS, bannerTicks * PhysicsConfig.FIXED_STEP_MS,
                PhysicsConfig.FIXED_STEP_MS);
        checks.check("横幅走完之后自己消失，并且已经进入下一手",
                !session.view().match().hasBanner()
                        && session.view().match().phase() != GamePhase.RESOLVING,
                "阶段 " + session.view().match().phase());
        noteStatus(session.view());

    }

    /** 检查定组、未定组时托盘中的球号和八号徽章 */
    private static void checkPottedTrays(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 落袋托盘 ----
        session.engine().arrange(List.of(
                        new RulesEngine.BallPlacement(BallCatalog.CUE_ID, 300, 280),
                        new RulesEngine.BallPlacement(3, 700, 280)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        session.tickFixed();
        MatchView tray = session.view().match();
        checks.check("定组之后：落袋的实色球进玩家 1 的托盘、花色球进玩家 2 的托盘",
                tray.pottedCount(Player.ONE) == 6 && tray.pottedCount(Player.TWO) == 7,
                "玩家1 " + tray.pottedCount(Player.ONE) + " 颗，玩家2 "
                        + tray.pottedCount(Player.TWO) + " 颗");
        checks.check("托盘里既没有主球也没有 8 号（前者不是「打进的球」，后者单独占中央徽章）",
                !trayHas(tray, Player.ONE, BallCatalog.CUE_ID)
                        && !trayHas(tray, Player.ONE, BallCatalog.EIGHT_ID)
                        && !trayHas(tray, Player.TWO, BallCatalog.CUE_ID)
                        && !trayHas(tray, Player.TWO, BallCatalog.EIGHT_ID));
        checks.check("8 号不在台上 → 中央徽章进入「已落袋」状态", !tray.eightOnTable());
        checks.check("托盘球号按升序（画出来的顺序与读出来的顺序是同一个）",
                trayAscending(tray, Player.ONE) && trayAscending(tray, Player.TWO));
        checks.check("任何一侧托盘的球数都不超过槽位数（不会画出卡外）",
                tray.pottedCount(Player.ONE) <= Layout.HUD_TRAY_SLOTS
                        && tray.pottedCount(Player.TWO) <= Layout.HUD_TRAY_SLOTS,
                "槽位 " + Layout.HUD_TRAY_SLOTS + " 个");

        session.engine().arrange(List.of(
                        new RulesEngine.BallPlacement(BallCatalog.CUE_ID, 300, 280),
                        new RulesEngine.BallPlacement(3, 700, 280)),
                Player.ONE, null, null, false);
        session.tickFixed();
        MatchView openTable = session.view().match();
        checks.check("组还没定的时候落袋的球两边托盘都不进（那时它还不属于任何人）",
                openTable.groupOf(Player.ONE) == null && openTable.pottedCount(Player.ONE) == 0
                        && openTable.pottedCount(Player.TWO) == 0);

    }

    /** 检查开球犯规后的自由球限制和提示颜色 */
    private static void checkRestrictedFreeBall(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 开球犯规 → 被限制在开球线后的自由球 ----
        session.engine().arrange(List.of(
                        new RulesEngine.BallPlacement(BallCatalog.CUE_ID, 650, 280),
                        new RulesEngine.BallPlacement(1, 720, 280)),
                Player.ONE, null, null, true);
        session.tickFixed();
        // 轻推开球（拉 32px ≈ 力度 0.1 → 初速 480），与 S3 那条夹具同一个局面
        pullShoot(session, canvas, 650, 280, 650 - 32, 280);
        runToResolving(session);
        checks.check("开球未达标：横幅的语气是「坏事」",
                session.view().match().bannerTone() == MatchView.Tone.FOUL,
                "横幅「" + session.view().match().banner() + "」");
        checks.check("犯规的那一杆，HUD 状态行的语气也变坏（不只是横幅的事）",
                session.view().match().statusTone() == MatchView.Tone.FOUL);
        settleSession(session);
        MatchView freeBall = session.view().match();
        noteStatus(session.view());
        checks.check("开球犯规的处罚是对手的自由球",
                freeBall.phase() == GamePhase.BALL_IN_HAND && freeBall.current() == Player.TWO,
                "阶段 " + freeBall.phase() + "，当前手 " + freeBall.current());
        checks.check("这个自由球被限制在开球线后（开球犯规与普通犯规的处罚不同）",
                freeBall.placementRestricted());
        checks.check("自由球阶段处于「正在拖放主球」状态", freeBall.placing());

        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                600, 280, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.check("光标在开球线右侧 → 自由球的虚影判为放不下（红）",
                !session.view().match().placementValid());
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                200, 280, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        checks.check("光标移到开球线左侧 → 虚影变绿", session.view().match().placementValid());
        checks.check("虚影位置就是光标位置（跟着手走）",
                session.view().match().placementX() == 200.0
                        && session.view().match().placementY() == 280.0,
                String.format("虚影 (%.1f, %.1f)",
                        session.view().match().placementX(), session.view().match().placementY()));

    }

    /** 检查拖球越出台面时虚影和显示区域仍然受限 */
    private static void checkPlacementBoundary(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 光标移出台面：虚影必须留在框内 ----
        // 木框、背景、HUD 都在窗口里，光标可以走到那儿；虚影若照着原始光标画，
        // 就成了「拖动主球能把它带出球台」——一个纯观感、数值上无从断言的 bug
        // （所以这里既查状态，也查像素）
        GameRenderer ghostRenderer = new GameRenderer();
        Table ghostTable = new Table();
        BufferedImage ghostInBounds = CheckImages.render(ghostRenderer, ghostTable, session.view());
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                600, Layout.WINDOW_H, T0, MouseEvent.NOBUTTON));
        session.tickFixed();
        BufferedImage ghostOutOfBounds = CheckImages.render(ghostRenderer, ghostTable, session.view());

        MatchView escaped = session.view().match();
        checks.check("光标拖到台面外（HUD 区）时虚影贴住台边，不再跟着出去",
                escaped.placementX() == 600.0
                        && escaped.placementY() == Layout.PLAYFIELD_BOTTOM - Ball.RADIUS,
                String.format("虚影 (%.1f, %.1f)", escaped.placementX(), escaped.placementY()));
        checks.check("光标在台面外时虚影一律判为放不下（松手用的是原始坐标，画成绿的就是撒谎）",
                !escaped.placementValid());

        // 像素层：台面以下那一条（HUD 与木框）一个像素都不该因为虚影而变
        int belowTable = CheckImages.diffCount(ghostInBounds, ghostOutOfBounds,
                0, (int) Layout.PLAYFIELD_BOTTOM + (int) Ball.RADIUS,
                Layout.WINDOW_W, Layout.WINDOW_H);
        checks.check("台面外的区域一个像素都不因虚影而变（球真的没出框）",
                belowTable == 0, "台面外不同像素 " + belowTable);
        // 配一条「画面确实变了」的断言，否则「虚影压根没画」也能让上面那条通过
        int insideTable = CheckImages.diffCount(ghostInBounds, ghostOutOfBounds,
                (int) Layout.PLAYFIELD_LEFT, (int) Layout.PLAYFIELD_TOP,
                (int) Layout.PLAYFIELD_RIGHT, (int) Layout.PLAYFIELD_BOTTOM);
        checks.check("同一时刻台面内的虚影确实移动了（上一条的差值不是「什么都没画」）",
                insideTable > 100, "台面内不同像素仅 " + insideTable);

        // 回到台内位置，后面那条「在开球线后松手」仍然按原样走
        session.input().mouseMoved(mouse(canvas, MouseEvent.MOUSE_MOVED,
                200, 280, T0, MouseEvent.NOBUTTON));
        session.tickFixed();

        click(session, canvas, 200, 280);
        checks.check("在开球线后松手 → 落位成功、进入瞄准",
                session.view().match().phase() == GamePhase.AIMING);
        checks.check("落位后主球回到快照里，且就在松手的位置",
                session.view().cueBallOnTable()
                        && Math.abs(session.view().cueX() - 200.0) < 1e-9,
                String.format("主球 x = %.4f", session.view().cueX()));
        checks.check("落位之后开球线限制解除（那是「这一杆」的处罚，不是整局的）",
                !session.view().match().placementRestricted());
        noteStatus(session.view());

    }

    /** 通过正式出杆检查清组后的八号球胜负和重新开始 */
    private static void checkWinningShot(CheckHarness checks, MatchSession session, JPanel canvas) {
        // ---- 清台后打进 8 号 → 终局（从 UI 一路打到胜利） ----
        session.engine().arrange(List.of(
                        new RulesEngine.BallPlacement(BallCatalog.CUE_ID, 500, 190),
                        new RulesEngine.BallPlacement(BallCatalog.EIGHT_ID, 500, 120)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        session.tickFixed();
        // 朝正上、拉 56px（力度 0.3 → 初速 1040，与 S13 的 SPEED_POT 同一档）
        pullShoot(session, canvas, 500, 190, 500, 190 + 56);
        checks.check("（前置）这一杆确实发出去了", session.view().shotInFlight());

        int winTicks = 0;
        while (session.view().match().phase() != GamePhase.GAME_OVER && winTicks < MAX_SHOT_TICKS) {
            session.tickFixed();
            winTicks = winTicks + 1;
        }
        MatchView over = session.view().match();
        noteStatus(session.view());
        checks.check("清台后打进 8 号 → 终局（浮层要盖住整屏）",
                over.phase() == GamePhase.GAME_OVER, "阶段 " + over.phase());
        MatchOutcome outcome = over.outcome();
        checks.checkNotNull("终局带着胜负结果", outcome);
        checks.check("胜方是击球方、负方是对手",
                outcome != null && outcome.winner() == Player.ONE
                        && outcome.loser() == Player.TWO,
                String.valueOf(outcome));
        checks.check("终局的状态行语气是「结果」，不是中性陈述",
                over.statusTone() == MatchView.Tone.WIN, "语气 " + over.statusTone());
        checks.check("终局时那行字告诉玩家怎么再来一局", !session.view().status().isBlank(),
                "状态行「" + session.view().status() + "」");

        // 终局上的点击＝再来一局（同一处也有 R 键）
        click(session, canvas, CUE_X, CUE_Y);
        checks.check("终局时点一下 = 再来一局：回到开球布置、胜负清空",
                session.view().match().phase() == GamePhase.BREAK_SETUP
                        && session.view().match().outcome() == null,
                "阶段 " + session.view().match().phase());
        checks.check("再来一局后 16 颗球重新摆好",
                ballsVisible(session.view()) == BallCatalog.BALL_COUNT,
                "画面上 " + ballsVisible(session.view()) + " 颗");
        noteStatus(session.view());

    }
}

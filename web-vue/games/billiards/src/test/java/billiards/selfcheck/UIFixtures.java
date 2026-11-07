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

/** 共享界面自检参数、事件构造和等待逻辑，避免各检查复制实现 */
final class UIFixtures {

    /**
     * 这一轮自检里会话<b>真正发布出去过</b>的 (阶段名, 状态文案) 组合
     *
     * <p>状态行是「后写的一句话」：长度由 {@link MatchSession} 按阶段拼出来，还可能在末尾
     * 挂上「· 上杆犯规：…」。在自检里抄一份文案模板去量宽度，改模板的那一刻这条断言就会
     * 安安静静地失效；所以量的必须是<b>真跑出来的那些</b>。收集在 {@link #noteStatus}，
     * 度量在 {@link #checkHudLayout}——两者靠 {@link #run} 的调用顺序衔接
     */
    static final List<String[]> OBSERVED_STATUS = new ArrayList<>();

    /**
     * 鼠标事件的 {@code when} 字段
     *
     * <p>它现在<b>不参与任何计算</b>——力度是拉距，与时间无关。仍然传一个真实的常数而不是 0，
     * 是为了让「这一杆有多重与这个数无关」这件事在自检里同样成立：下面这些断言在有它、没它、
     * 换成任意值的情况下都必须通过
     */
    static final long T0 = 1_000_000L;

    /** 球心相对台面边界允许的残余越界（px）。与 P8 的逐子步不变量同一个量级 */
    static final double EDGE_TOL = 1e-6;

    /** 整杆推进的步数上限：恰好等于硬超时，用来抓「球停不下来」 */
    static final int MAX_SHOT_TICKS = (int) (PhysicsConfig.SHOT_TIMEOUT_SIM_MS
            / PhysicsConfig.FIXED_STEP_MS);

    /** 判定横幅的步数上限。{@code VERDICT_BANNER_MS} 只需 280 步，留足余量即可 */
    static final int MAX_BANNER_TICKS = 1000;

    /**
     * 开球布置时把主球放下的那个点：就是默认开球位
     *
     * <p>取整数是为了让下面那些「球心 ± 100px」的夹具逐位相同——鼠标事件本来就是整数坐标，
     * 而缩放恰为 1 时组件坐标与逻辑坐标逐位相等
     */
    static final int CUE_X = (int) RackBuilder.DEFAULT_BREAK_CUE_X;

    static final int CUE_Y = (int) Layout.playfieldCenterY();

    /** 一次完整的点击：按下、松开，各推进一帧。开场浮层、落位、终局重开都走它 */
    static void click(MatchSession session, JPanel canvas, int x, int y) {
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                x, y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                x, y, T0, MouseEvent.BUTTON1));
        session.tickFixed();
    }

    /**
     * 这一帧画面上「有」几颗球：在台的，加上那颗<b>由虚影代表</b>的主球
     *
     * <p>不能直接数 {@link GameStateView#ballsOnTable()}。拖放主球时真主球会让位给虚影——
     * 否则屏幕上会同时出现两颗主球（见 {@code MatchSession.publish}）——于是它在快照里
     * 被标成不在台上。可画面上仍然恒有且只有一颗主球，只是这一颗的身份换成了虚影
     *
     * <p>「一局重新摆好了没有」这类断言量的是<b>画面上有没有凑齐一整套球</b>，所以必须把
     * 这次换算算进去；漏掉它就会把一张正确的画面判成缺了主球
     */
    static int ballsVisible(GameStateView view) {
        int count = view.ballsOnTable();
        if (view.match().hasPlacement() && !view.cueBallOnTable()) {
            count = count + 1;
        }
        return count;
    }

    /**
     * 重开一局并把主球摆回开球位——也就是「按 R 之后玩家接着要做的那一下」
     *
     * <p>M6 之后重开<b>不再直接回到可瞄准状态</b>：主球要重新摆。所以每一段测试之前都得走
     * 这一下，否则后面所有拉杆手势都会打在「开球布置」阶段上，一条也测不出来
     */
    static void resetAndPlace(MatchSession session, JPanel canvas, int x, int y) {
        session.resetMatch();
        session.tickFixed();
        click(session, canvas, x, y);
    }

    /**
     * 拉杆击球：在 {@code (ax, ay)} 按下，拖到 {@code (rx, ry)} 再松开
     *
     * <p>方向与力度都由<b>松手那一刻</b>的坐标定：方向是「从光标指向主球」，力度是
     * 「离按下点多远」。两个量因此可以各自独立地配出来，而这一点正是 M4 那次手势改动
     * 要保住的性质——所以夹具也照这个口径写，不另开一条捷径
     */
    static void pullShoot(MatchSession session, JPanel canvas,
                                  int anchorX, int anchorY, int releaseX, int releaseY) {
        session.input().mousePressed(mouse(canvas, MouseEvent.MOUSE_PRESSED,
                anchorX, anchorY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
        session.input().mouseDragged(drag(canvas, releaseX, releaseY));
        session.tickFixed();
        session.input().mouseReleased(mouse(canvas, MouseEvent.MOUSE_RELEASED,
                releaseX, releaseY, T0, MouseEvent.BUTTON1));
        session.tickFixed();
    }

    /** 一直推进到本杆停稳——也就是进入判定阶段（球飞完了才有判决书） */
    static void runToResolving(MatchSession session) {
        int ticks = 0;
        while (session.view().shotInFlight() && ticks < MAX_SHOT_TICKS) {
            session.tickFixed();
            ticks = ticks + 1;
        }
    }

    /** 走完判定横幅，进入下一手 */
    static void settleSession(MatchSession session) {
        int ticks = 0;
        while (session.view().match().phase() == GamePhase.RESOLVING
                && ticks < MAX_BANNER_TICKS) {
            session.tickFixed();
            ticks = ticks + 1;
        }
    }

    /** 记下这一帧真正发布出去的状态行，供 {@link #checkHudLayout} 量宽度 */
    static void noteStatus(GameStateView view) {
        OBSERVED_STATUS.add(new String[]{view.match().phase().labelZh(), view.status()});
    }

    /** 返回指定玩家的落袋托盘是否包含给定球号 */
    static boolean trayHas(MatchView match, Player player, int ballId) {
        for (int index = 0; index < match.pottedCount(player); index = index + 1) {
            if (match.potted(player, index) == ballId) {
                return true;
            }
        }
        return false;
    }

    /** 检查玩家托盘中的球号是否严格递增 */
    static boolean trayAscending(MatchView match, Player player) {
        for (int index = 1; index < match.pottedCount(player); index = index + 1) {
            if (match.potted(player, index) <= match.potted(player, index - 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 造一个真实的 {@link MouseEvent}
     *
     * <p>用 {@code MouseEvent} 而不是给会话开一个「注入指令」的后门：后门能测的只有后门
     *
     * <p>{@code source} 不能是 {@code null}（{@code EventObject} 会拒绝），但也不必是真正
     * 装了监听器的那个构件——这里只是为了满足构造签名，所以传一个临时构件即可
     *
     * <p>{@code when} 现在不参与任何计算，但仍然传真实值：{@code MouseEvent} 的构造签名
     * 要它，而「传什么值结论都一样」本身就是要被这组检查证实的事情之一
     */
    static MouseEvent mouse(Component source, int ballId, int x, int y, long when, int button) {
        return new MouseEvent(source, ballId, when, 0, x, y, 1, false, button);
    }

    /**
     * 按住左键时的一次移动
     *
     * <p>真实鼠标在按键按下时发的是 {@code MOUSE_DRAGGED} 而不是 {@code MOUSE_MOVED}，
     * 而拉杆整条链正是靠它驱动的。自检如果一律用 {@code MOUSE_MOVED}，
     * 「{@code GamePanel} 忘了把 {@code mouseDragged} 接上」这种错就会一路绿灯——
     * 一个只在真机上才复现的空档。<b>夹具要发真实世界里会发的那种事件</b>
     */
    static MouseEvent drag(Component source, int x, int y) {
        return mouse(source, MouseEvent.MOUSE_DRAGGED, x, y, T0, MouseEvent.BUTTON1);
    }

    /**
     * 走真实的键绑定触发一次快捷键
     *
     * <p>不是模拟按键，而是从 {@code InputMap} 里取出那个键位挂着的动作直接执行——
     * 于是「快捷键到底有没有绑上」这件事也一并被查了（返回 {@code false} 就是没绑上）
     */
    static boolean fireKey(JComponent canvas, int keyCode) {
        Object name = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(keyCode, 0));
        if (name == null) {
            return false;
        }
        javax.swing.Action action = canvas.getActionMap().get(name);
        if (action == null) {
            return false;
        }
        action.actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, "selfcheck"));
        return true;
    }

    /** 返回给定矩形是否完整落在逻辑画布内 */
    static boolean insideWindow(Rectangle2D.Double rectangle) {
        return rectangle.x >= 0.0 && rectangle.y >= 0.0
                && rectangle.x + rectangle.width <= Layout.WINDOW_W
                && rectangle.y + rectangle.height <= Layout.WINDOW_H;
    }

    /** 把矩形的位置和尺寸格式化为失败诊断文字 */
    static String describe(Rectangle2D.Double rectangle) {
        return String.format("[%.1f, %.1f, %.1f × %.1f]", rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    /** 所有在台球的球心都必须留在台面内缩一个半径的范围内 */
    static boolean allInsidePlayfield(GameStateView view) {
        return describeOutliers(view).isEmpty();
    }

    /** 列出越出台面的球号与位置，供几何检查说明失败原因 */
    static String describeOutliers(GameStateView view) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < view.ballCount(); index = index + 1) {
            BallView ballView = view.ball(index);
            if (!ballView.onTable()) {
                continue;
            }
            if (ballView.x() < Layout.PLAYFIELD_LEFT + Ball.RADIUS - EDGE_TOL
                    || ballView.x() > Layout.PLAYFIELD_RIGHT - Ball.RADIUS + EDGE_TOL
                    || ballView.y() < Layout.PLAYFIELD_TOP + Ball.RADIUS - EDGE_TOL
                    || ballView.y() > Layout.PLAYFIELD_BOTTOM - Ball.RADIUS + EDGE_TOL) {
                if (text.length() > 0) {
                    text.append("；");
                }
                text.append(String.format("球 %d @(%.3f, %.3f)", ballView.id(), ballView.x(), ballView.y()));
            }
        }
        return text.toString();
    }

    /** 检查工具只使用静态入口，不需要创建对象 */
    private UIFixtures() {
    }
}

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

/** 检查状态区布局和各图层是否正确绘制到画面 */
final class UIFrameChecks {

    /**
     * HUD 版面：每一块都得在窗口里、互相不压，而且每一句话都放得下
     *
     * <p>M6 把 HUD 从「一行状态 + 一条力度条」扩成三行（两张玩家卡 + 状态行 + 按钮与力度条），
     * 于是「放不下」这件事第一次真的有可能发生：卡里要塞下名字、组徽和 7 个托盘槽位，
     * 而其中两样的宽度是<b>量出来的字宽</b>。肉眼看只表现为「托盘压出了边框」，
     * 所以这里全部做成可算的断言
     */
    static void checkHudLayout(CheckHarness checks) {
        Rectangle2D.Double panel = HudRenderer.panelRect();
        Rectangle2D.Double bar = HudRenderer.powerBarRect();
        Rectangle2D.Double cardOne = HudRenderer.cardRect(Layout.hudCardOneX());
        Rectangle2D.Double cardTwo = HudRenderer.cardRect(Layout.hudCardTwoX());
        Rectangle2D.Double restart = HudRenderer.restartButtonRect();
        Rectangle2D.Double overlay = OverlayRenderer.overlayPanel();

        // 「画到窗口外面去了」在肉眼下只表现为「那一块不见了」，很难查；这里一次说清
        checks.check("HUD 底板整块落在窗口内", insideWindow(panel), describe(panel));
        checks.check("力度条整条落在窗口内", insideWindow(bar), describe(bar));
        checks.check("「重新开始」按钮整个落在窗口内", insideWindow(restart), describe(restart));
        checks.check("第一张玩家卡整个落在窗口内", insideWindow(cardOne), describe(cardOne));
        checks.check("第二张玩家卡整个落在窗口内", insideWindow(cardTwo), describe(cardTwo));
        checks.check("浮层板整个落在窗口内", insideWindow(overlay), describe(overlay));

        checks.check("两张玩家卡都在底板内",
                panel.contains(cardOne) && panel.contains(cardTwo));
        checks.check("两张玩家卡不重叠（HUD 加宽之后仍然分得开）",
                !cardOne.intersects(cardTwo),
                describe(cardOne) + " 与 " + describe(cardTwo));
        checks.check("力度条落在 HUD 底板内（不会压到面板边框上）", panel.contains(bar),
                describe(bar) + " 不在 " + describe(panel) + " 内");
        checks.check("按钮落在底板内", panel.contains(restart), describe(restart));
        checks.check("按钮与力度条不重叠（同一行里各占一段）", !restart.intersects(bar),
                describe(restart) + " 与 " + describe(bar));
        checks.check("HUD 底板不与球台重叠", panel.y >= Layout.PLAYFIELD_BOTTOM + Layout.CUSHION_BAND,
                "底板顶边 " + panel.y);

        // 三行恰好填满内容带：不是「看着差不多」，而是行高、行距、内边距逐项相加等于底板高
        // 少一个行距会留下一条缝，多一个会把力度条顶出底板——两种都只能靠算
        checks.checkNear("HUD 三行的总高恰好等于三个行高加两个行距",
                Layout.HUD_ROW_A_H + Layout.HUD_ROW_B_H + Layout.HUD_ROW_C_H
                        + Layout.HUD_ROW_GAP * 2,
                Layout.hudContentBottom() - Layout.hudRowATop(), 1e-9);
        checks.checkNear("内容带到底板上下边的留白相等（内边距只有一处定义）",
                Layout.hudRowATop() - panel.y,
                (panel.y + panel.height) - Layout.hudContentBottom(), 1e-9);

        // 中央的 8 号徽章夹在两张卡之间，得留得下它
        double gap = cardTwo.x - (cardOne.x + cardOne.width);
        checks.check("两张卡之间留得下中央的 8 号徽章",
                gap >= HudRenderer.EIGHT_MEDALLION,
                String.format("卡间距 %.1fpx，徽章 %.1fpx", gap, HudRenderer.EIGHT_MEDALLION));

        // ---- 宽度：字是量出来的，所以余量也得量 ----
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        try {
            String worstCard = "";
            double cardSlack = Double.MAX_VALUE;
            for (Player player : Player.values()) {
                for (BallGroup group : new BallGroup[]{null, BallGroup.SOLID, BallGroup.STRIPE}) {
                    String badge = HudRenderer.badgeText(group);
                    double slack = HudRenderer.cardSlack(graphics, player.labelZh(), badge);
                    if (slack < cardSlack) {
                        cardSlack = slack;
                        worstCard = player.labelZh() + " / " + badge;
                    }
                }
            }
            checks.check("玩家卡在最挤的组合下也放得下（名字 + 组徽 + 7 个托盘槽位）",
                    cardSlack >= 0.0,
                    String.format("最挤的是「%s」，余量 %.1fpx", worstCard, cardSlack));

            String worstStatus = "";
            String worstPhase = "";
            double statusSlack = Double.MAX_VALUE;
            int distinctPhases = 0;
            List<String> seen = new ArrayList<>();
            for (String[] pair : OBSERVED_STATUS) {
                String phaseLabel = pair[0];
                String status = pair[1];
                if (!seen.contains(phaseLabel)) {
                    seen.add(phaseLabel);
                    distinctPhases = distinctPhases + 1;
                }
                double slack = HudRenderer.statusSlack(graphics, phaseLabel, status);
                if (slack < statusSlack) {
                    statusSlack = slack;
                    worstStatus = status;
                    worstPhase = phaseLabel;
                }
            }
            checks.check("每一种真实出现过的状态行都放得下（阶段药丸 + 那句话不会顶到右沿）",
                    statusSlack >= 0.0,
                    String.format("最挤的是「%s / %s」，余量 %.1fpx", worstPhase, worstStatus, statusSlack));
            // 只量过一两种阶段的话，上面那条断言等于没查——HUD 上最长的几句话分别属于
            // 自由球、终局与开球布置
            checks.check("状态行样本覆盖了至少 5 个阶段（否则上面那条是空过）",
                    distinctPhases >= 5, "只覆盖了 " + distinctPhases + " 个：" + seen);
            boolean allStatusesPresent = true;
            for (String[] statusEntry : OBSERVED_STATUS) {
                if (statusEntry[1].isBlank()) {
                    allStatusesPresent = false;
                }
            }
            checks.check("每一条状态行都不是空串（否则玩家不知道该怎么操作）",
                    allStatusesPresent);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * 只改一个状态分量、连画两帧，断言两帧不同
     *
     * <p>这抓的是「状态算对了但没画」——虚影球算得有模有样却忘了调绘制、球杆根本没接进
     * {@code GameRenderer}、力度条永远画成 0%。这类 bug 在数值上没有任何可以断言的东西，
     * 只能比对像素
     */
    static void checkStateReachesScreen(CheckHarness checks) {
        GameRenderer renderer = new GameRenderer();
        Table table = new Table();
        AimView aim = CheckImages.aimAlongPositiveX(table);
        AimView noContact = CheckImages.withoutContact(aim);
        AimView escaped = CheckImages.aimThroughSidePocketMouth(table);

        BufferedImage bare = CheckImages.render(renderer, table,
                CheckImages.rackView(AimView.NONE, 0.0, false, ""));
        BufferedImage withAim = CheckImages.render(renderer, table,
                CheckImages.rackView(aim, 0.0, false, ""));
        BufferedImage lineOnly = CheckImages.render(renderer, table,
                CheckImages.rackView(noContact, 0.0, false, ""));

        int aimDiff = CheckImages.diffCount(bare, lineOnly);
        checks.check("瞄准线真的画到了屏幕上（画与不画两帧必须不同）",
                aimDiff > 100, "不同像素仅 " + aimDiff);

        int ghostDiff = CheckImages.diffCount(lineOnly, withAim);
        checks.check("虚影球与两条分离线真的画到了屏幕上（摘掉接触几何后画面必须变）",
                ghostDiff > 200, "不同像素仅 " + ghostDiff);

        // 上面那条把虚影球与分离线一起算进差值，虚影球一家就能把阈值撑过去
        // 下面这两条把它们<b>分开</b>问，各自独立成立
        //
        // 而且必须用斜切的那条线来问：正撞时两条分离线都与瞄准线重合（目标球沿瞄准线出去、
        // 主球也沿瞄准线跟进），三条线叠在一处，画了和没画在像素上完全一样
        AimView cut = CheckImages.aimCutShot(table);
        BufferedImage cutFull = CheckImages.render(renderer, table,
                CheckImages.rackView(cut, 0.0, false, ""));
        BufferedImage cutNoGhost = CheckImages.render(renderer, table,
                CheckImages.rackView(CheckImages.withoutContact(cut), 0.0, false, ""));
        BufferedImage cutNoLines = CheckImages.render(renderer, table,
                CheckImages.rackView(CheckImages.withoutSeparation(cut), 0.0, false, ""));

        checks.check("斜切时确实给出两条分离方向（几何前提）",
                cut.hitBall() && cut.targetSeparation() != null && cut.cueSeparation() != null);
        int cutGhostDiff = CheckImages.diffCount(cutNoGhost, cutFull);
        checks.check("斜切时虚影球真的画到了屏幕上", cutGhostDiff > 150, "不同像素仅 " + cutGhostDiff);
        int cutLineDiff = CheckImages.diffCount(cutNoLines, cutFull);
        checks.check("斜切时两条分离线真的画到了屏幕上（只摘掉分离线，差值必须还在）",
                cutLineDiff > 150, "不同像素仅 " + cutLineDiff);
        // 上面那条像素断言只有在「两条线真的偏出瞄准线」时才说明得了问题——否则它们全压在
        // 瞄准线底下，差值里的像素仍然只是瞄准线自己的。所以把这一点也钉成断言
        checks.check("斜切时两条分离线都明显偏出瞄准线（像素差才归因得到它们头上）",
                Math.abs(cut.targetSeparation().y) > 0.3 && Math.abs(cut.cueSeparation().y) > 0.3,
                String.format("目标球分离 y = %.3f，主球分离 y = %.3f",
                        cut.targetSeparation().y, cut.cueSeparation().y));

        // 球杆画在球的后方，而那块矩形里除了球杆没有别的东西会随瞄准变化——
        // 于是「那一块有没有像素变化」就直接回答了「球杆画没画出来」，
        // 不会被前面的虚线或虚影球蒙过去
        double cueX = RackBuilder.DEFAULT_BREAK_CUE_X;
        double cueY = Layout.playfieldCenterY();
        int boxX0 = 0;
        int boxY0 = (int) (cueY - 30.0);
        int boxX1 = (int) (cueX - 30.0);
        int boxY1 = (int) (cueY + 30.0);
        int cueDiff = CheckImages.diffCount(bare, lineOnly, boxX0, boxY0, boxX1, boxY1);
        checks.check("球杆真的画到了屏幕上（主球后方那一块必须出现杆身）",
                cueDiff > 300, "主球后方矩形内不同像素仅 " + cueDiff);

        BufferedImage fullPower = CheckImages.render(renderer, table,
                CheckImages.rackView(noContact, 1.0, false, ""));
        int powerDiff = CheckImages.diffCount(lineOnly, fullPower);
        checks.check("力度条与球杆都随力度变化（0% 与 100% 两帧必须不同）",
                powerDiff > 500, "不同像素仅 " + powerDiff);
        int cuePullDiff = CheckImages.diffCount(lineOnly, fullPower, boxX0, boxY0, boxX1, boxY1);
        checks.check("球杆的位置随力度变化（主球后方那一块的像素被挪动了）",
                cuePullDiff > 200, "主球后方矩形内不同像素仅 " + cuePullDiff);

        // 射线从袋口射出台面、什么都不撞：没有虚影球，但瞄准线仍然要画出来
        BufferedImage escapedFrame = CheckImages.render(renderer, table,
                CheckImages.rackView(escaped, 0.0, false, ""));
        checks.check("从袋口射出的那一帧确实没有虚影球（跑的是另一条分支）", !escaped.hasGhost());
        int escapeDiff = CheckImages.diffCount(bare, escapedFrame);
        checks.check("射线从袋口射出时瞄准线仍然画出来（不会整条凭空消失）",
                escapeDiff > 100, "不同像素仅 " + escapeDiff);

        // 力度为 0 时槽底也要画出来，否则玩家不知道这里有条、更不知道它多长
        BufferedImage idleWithStatus = CheckImages.render(renderer, table,
                CheckImages.rackView(AimView.NONE, 0.0, false, "按住左键往后拖定力度，拖时也能转方向"));
        int statusDiff = CheckImages.diffCount(bare, idleWithStatus);
        checks.check("状态行文字真的画到了屏幕上", statusDiff > 50, "不同像素仅 " + statusDiff);

        checkMatchOverlayReachesScreen(checks, renderer, table);
    }

    /**
     * M6 新增的那一层：开场提示、判定横幅、终局浮层、调试浮层、待放虚影、落袋托盘
     *
     * <p>这一组比前面的像素检查更要紧，因为它们是<b>这局到底怎么了</b>的唯一表达渠道：
     * 「谁赢了」「刚才为什么吹哨」「现在能把球放哪」全在浮层上。算对了而没画出来，
     * 数值自检全绿，玩家却只会看到一张静止的球台
     *
     * <p>每一对帧都只差一个字段（同一批球、同一个阶段），所以差值只可能来自那个东西
     * 这也正是 {@code CheckImages} 那族对局夹具存在的理由
     */
    static void checkMatchOverlayReachesScreen(CheckHarness checks, GameRenderer renderer,
                                                       Table table) {
        // 基准帧：一局正在瞄准的画面，任何浮层都不该出现
        BufferedImage play = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.match(GamePhase.AIMING)));
        BufferedImage start = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.match(GamePhase.MATCH_START)));
        int startDiff = CheckImages.diffCount(play, start);
        checks.check("开场提示真的画到了屏幕上（整屏被压暗，中间一块板子）",
                startDiff > 300000, "不同像素 " + startDiff);

        BufferedImage over = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "",
                CheckImages.matchGameOver(MatchOutcome.wonBy(Player.ONE, "本方球已清，8 号落袋"))));
        int overDiff = CheckImages.diffCount(play, over);
        checks.check("终局浮层真的画到了屏幕上（它必须是压在一切之上的一层）",
                overDiff > 300000, "不同像素 " + overDiff);

        // 横幅只横贯台面中央那一块，所以把比较范围也限制在那儿——否则整屏压暗的浮层
        // 一家就能把差值撑上去，横幅根本没画也照样通过
        BufferedImage banner = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "",
                CheckImages.matchWithBanner(GamePhase.RESOLVING, "开球未达标 · 对手获得自由球",
                        MatchView.Tone.FOUL)));
        double centerX = Layout.WINDOW_W * 0.5;
        double centerY = Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.5;
        int bannerDiff = CheckImages.diffCount(play, banner,
                (int) (centerX - 320), (int) (centerY - 40), (int) (centerX + 320), (int) (centerY + 40));
        checks.check("判定横幅真的画到了台面中央（限定在那一条带里比对）",
                bannerDiff > 5000, "台面中央不同像素仅 " + bannerDiff);

        BufferedImage debug = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.matchDebug()));
        int debugDiff = CheckImages.diffCount(play, debug,
                (int) Layout.DEBUG_PANEL_X - 4, (int) Layout.DEBUG_PANEL_Y - 4,
                (int) Layout.DEBUG_PANEL_X + 300, (int) Layout.DEBUG_PANEL_Y + 60);
        checks.check("调试浮层真的画到了屏幕上", debugDiff > 1000, "不同像素仅 " + debugDiff);

        // 待放虚影：同一位置、同一台球，只有合法性不同。绿与红必须画得不一样，
        // 否则玩家分不出「这儿放得下」和「这儿放不下」
        BufferedImage ghostOk = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.matchPlacing(600, 280, true, false)));
        BufferedImage ghostBad = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.matchPlacing(600, 280, false, false)));
        int ghostDiff = CheckImages.diffCount(ghostOk, ghostBad, 570, 250, 630, 310);
        checks.check("待放虚影会随「放得下 / 放不下」变色（绿与红必须画得不一样）",
                ghostDiff > 200, "虚影范围内不同像素仅 " + ghostDiff);

        // 落袋托盘：一侧装满、另一侧也装满，并且 8 号徽章进入「已落袋」
        BufferedImage tray = CheckImages.render(renderer, table, CheckImages.rackView(
                AimView.NONE, 0.0, false, "", CheckImages.matchWithTray(BallGroup.SOLID,
                        BallGroup.STRIPE, new int[]{1, 2, 3, 4, 5, 6, 7},
                        new int[]{9, 10, 11, 12, 13, 14, 15})));
        int trayDiff = CheckImages.diffCount(play, tray,
                (int) Layout.hudCardOneX(), (int) Layout.hudRowATop(),
                (int) (Layout.hudCardTwoX() + Layout.HUD_CARD_W),
                (int) (Layout.hudRowATop() + Layout.HUD_ROW_A_H));
        checks.check("落袋托盘与 8 号徽章真的画到了屏幕上（两张卡那一行必须变）",
                trayDiff > 500, "玩家卡那一行不同像素仅 " + trayDiff);

        // 差值只回答「变了没有」，回答不了「好不好看」。HUD 是 M6 唯一一处**纯靠看**才能
        // 验收的东西（字挤不挤、徽章压没压到托盘、横幅盖没盖住球），所以这里把上面每一帧
        // 都留一份 PNG，配同一个 -Dbilliards.screenshot 开关
        dumpFramesIfRequested(play, start, over, banner, debug, ghostOk, ghostBad, tray);
    }

    /**
     * 设了 {@code -Dbilliards.screenshot=<路径>} 就把 M6 这几帧一并写成 PNG
     *
     * <p>命名沿用 {@link RenderingChecks} 那一族：基础名给「一局正在打」，其余各加后缀
     * 默认不写文件，所以自检的纯粹性不受影响
     */
    static void dumpFramesIfRequested(BufferedImage play, BufferedImage start,
                                              BufferedImage over, BufferedImage banner,
                                              BufferedImage debug, BufferedImage ghostOk,
                                              BufferedImage ghostBad, BufferedImage tray) {
        String path = System.getProperty("billiards.screenshot");
        if (path == null || path.isBlank()) {
            return;
        }
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-play"), play);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-start"), start);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-over"), over);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-banner"), banner);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-debug"), debug);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-place-ok"), ghostOk);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-place-bad"), ghostBad);
        RenderingChecks.writePng(RenderingChecks.sibling(path, "-tray"), tray);
    }

    /** 检查工具只使用静态入口，不需要创建对象 */
    private UIFrameChecks() {
    }
}

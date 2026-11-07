package billiards.render;

import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.world.Ball;
import billiards.world.Layout;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 盖在最上面的那一层：待放主球的虚影、判定横幅、开场提示、终局面板、调试浮层
 *
 * <h2>为什么单独成一个类</h2>
 * 前三组渲染器（球台、球、HUD）画的是<b>局面</b>，这一层画的是<b>对局</b>——它回答的是
 * 「现在轮到谁、刚才判了什么、这局完没完」，与球在哪没有关系。放进 {@link HudRenderer}
 * 会得到一个既画托盘又盖满全屏的类；放进 {@link GameRenderer} 会把绘制顺序的说明淹掉
 *
 * <h2>绘制顺序（由 {@code GameRenderer} 保证）</h2>
 * <ol>
 *   <li><b>待放虚影</b>——在球与 HUD 之上。它跟着光标可以跑到 HUD 上，那时它会被画成红色
 *       （放不下）——<b>「能看见自己正在把球往一个放不了的地方拖」比把它藏起来有用</b></li>
 *   <li><b>判定横幅</b>——横贯台面的一条。它是 {@link GamePhase#RESOLVING} 的可视化，
 *       那个阶段存在的唯一理由就是让玩家看见判定</li>
 *   <li><b>调试浮层</b></li>
 *   <li><b>终局面板</b>——盖住整屏。它必须是最后一层：比赛已经结束了，底下发生什么都不重要</li>
 *   <li><b>开场提示</b>——同样盖住整屏，且排在终局之后：它只在 {@code MATCH_START} 出现，
 *       与终局互斥，顺序在这里只是把话说死</li>
 * </ol>
 */
public final class OverlayRenderer {

    /** 浮层压暗底色的不透明度。既要压得住底下的球台，又要让人隐约看见球还在那儿 */
    private static final float SCRIM_ALPHA = 0.62f;

    /** 判定横幅默认字号，单位为逻辑像素 */
    private static final float BANNER_FONT_PX = 18.0f;
    /** 长横幅文案允许缩小到的最小字号 */
    private static final float BANNER_MIN_FONT_PX = 13.0f;
    /** 开场和终局标题字号，单位为逻辑像素 */
    private static final float TITLE_FONT_PX = 30.0f;
    /** 终局原因字号，单位为逻辑像素 */
    private static final float REASON_FONT_PX = 16.0f;
    /** 开场提示字号，单位为逻辑像素 */
    private static final float HINT_FONT_PX = 15.0f;
    /** 开始和重开操作文字字号，单位为逻辑像素 */
    private static final float CTA_FONT_PX = 18.0f;
    /** 调试面板字号，单位为逻辑像素 */
    private static final float DEBUG_FONT_PX = 12.0f;

    /** 浮层内容内边距，单位为逻辑像素 */
    private static final double PANEL_PAD = 28.0;

    /** 标题徽章相对提示面板顶部的中心距离，单位为逻辑像素 */
    private static final double MEDAL_TOP = 46.0;

    /** 提示标题和正文首行的基线位置，相对面板顶部计算 */
    private static final double TITLE_TOP = 118.0;
    private static final double BODY_TOP = 158.0;

    /** 开场和终局操作文字的基线位置，相对面板顶部计算 */
    private static final double START_ACTION_TOP = 262.0;
    private static final double OUTCOME_ACTION_TOP = 238.0;

    /** 横幅图标与文字之间留出的宽度，单位为逻辑像素 */
    private static final double BANNER_ICON_SPACE = 36.0;

    /** 调试面板左侧强调条宽度，单位为逻辑像素 */
    private static final double DEBUG_ACCENT_WIDTH = 3.0;

    /** 成就提示距窗口顶部的位置，单位为逻辑像素 */
    private static final double ACHIEVEMENT_TOP = 88.0;
    /** 成就提示高度，单位为逻辑像素 */
    private static final double ACHIEVEMENT_HEIGHT = 42.0;
    /** 成就提示最大宽度，单位为逻辑像素 */
    private static final double ACHIEVEMENT_MAX_WIDTH = 820.0;
    /** 成就提示字号，单位为逻辑像素 */
    private static final float ACHIEVEMENT_FONT_PX = 15.0f;
    /** 结算页成就摘要基线相对面板顶部的位置，单位为逻辑像素 */
    private static final double ACHIEVEMENT_SUMMARY_TOP = 196.0;

    /** 根据传入的模型或只读快照绘制当前图层 */
    public void draw(Graphics2D graphics, GameStateView view) {
        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        MatchView match = view.match();

        drawPlacementGhost(graphics, match);

        if (match.hasBanner()) {
            drawBanner(graphics, match.banner(), match.bannerTone());
        }

        if (match.debug()) {
            drawDebug(graphics, match);
        }

        if (match.phase() != GamePhase.GAME_OVER && match.phase() != GamePhase.MATCH_START
                && !view.achievementNotice().isEmpty()) {
            drawAchievement(graphics, view.achievementNotice());
        }

        if (match.phase() == GamePhase.GAME_OVER) {
            drawOutcome(graphics, match.outcome(), view.achievementSummary());
        } else if (match.phase() == GamePhase.MATCH_START) {
            drawStartPrompt(graphics);
        }

        if (oldAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    // 待放虚影

    /** 主球待放位置的虚影：一圈比球略大的环，绿＝放得下、红＝放不下 */
    private void drawPlacementGhost(Graphics2D graphics, MatchView match) {
        if (!match.placing()) {
            return;
        }

        if (match.placementRestricted()) {
            graphics.setColor(Theme.WARN);
            graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{8.0f, 6.0f}, 0.0f));
            graphics.draw(new Line2D.Double(Layout.HEAD_STRING_X, Layout.PLAYFIELD_TOP,
                    Layout.HEAD_STRING_X, Layout.PLAYFIELD_BOTTOM));
        }

        if (!match.hasPlacement()) {
            return;
        }

        double radius = Ball.RADIUS + 3.0;
        double centerX = match.placementX();
        double centerY = match.placementY();
        Color tone = Theme.PLACEMENT_BAD;
        if (match.placementValid()) {
            tone = Theme.PLACEMENT_OK;
        }

        graphics.setColor(Theme.withAlpha(tone, 0x44));
        graphics.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0));
        graphics.setColor(tone);
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.draw(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0));

        // 合法位置用对勾、非法位置用叉号，避免只靠颜色区分放置结果
        double distance = 5.0;
        graphics.setStroke(new BasicStroke(1.5f));
        if (match.placementValid()) {
            Path2D.Double check = new Path2D.Double();
            check.moveTo(centerX - distance, centerY);
            check.lineTo(centerX - 1, centerY + distance);
            check.lineTo(centerX + distance + 2, centerY - distance);
            graphics.draw(check);
        } else {
            graphics.draw(new Line2D.Double(centerX - distance, centerY - distance,
                    centerX + distance, centerY + distance));
            graphics.draw(new Line2D.Double(centerX - distance, centerY + distance,
                    centerX + distance, centerY - distance));
        }
    }

    // 判定横幅

    /**
     * 横贯台面的一条横幅
     *
     * <p>字号会<b>往小里让</b>：判定文案的长短由规则引擎拼出来（一次犯几条就长几条），
     * 而横幅的宽度必须在台面里。与其裁字或者换行，不如把字号缩到放得下——一句话读起来
     * 小一点，总好过读不到后半句
     */
    private void drawBanner(Graphics2D graphics, String text, MatchView.Tone statusTone) {
        double centerX = Layout.WINDOW_W * 0.5;
        double centerY = Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.5;
        Color tone = HudRenderer.toneColor(statusTone);

        float size = BANNER_FONT_PX;
        Font font = Theme.uiFont(Font.BOLD, size);
        FontMetrics fontMetrics = graphics.getFontMetrics(font);
        while (size > BANNER_MIN_FONT_PX) {
            fontMetrics = graphics.getFontMetrics(font);
            if (fontMetrics.stringWidth(text) <= Layout.BANNER_MAX_W - PANEL_PAD * 2 - BANNER_ICON_SPACE) {
                break;
            }
            size = size - 1.0f;
            font = Theme.uiFont(Font.BOLD, size);
        }
        fontMetrics = graphics.getFontMetrics(font);

        double textWidth = fontMetrics.stringWidth(text);
        double width = Math.min(Layout.BANNER_MAX_W, textWidth + PANEL_PAD * 2 + BANNER_ICON_SPACE);
        double height = Layout.BANNER_H;
        Rectangle2D.Double box = new Rectangle2D.Double(centerX - width * 0.5, centerY - height * 0.5, width, height);

        Composite old = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        graphics.setColor(Theme.darken(Theme.HUD_PANEL, 0.25f));
        graphics.fill(round(box));
        graphics.setComposite(old);

        graphics.setColor(tone);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.draw(round(box));
        PromptDecoration.statusIcon(graphics, statusTone,
                box.x + PANEL_PAD - 4, centerY - 10, 20);
        graphics.setColor(Theme.withAlpha(tone, 85));
        graphics.draw(new Line2D.Double(box.x + 12, box.y + 8, box.x + 12, box.getMaxY() - 8));

        graphics.setFont(font);
        graphics.setColor(tone);
        graphics.drawString(text, (float) (centerX + BANNER_ICON_SPACE * 0.5 - textWidth * 0.5),
                (float) (centerY + (fontMetrics.getAscent() - fontMetrics.getDescent()) * 0.5));
    }

    // 开场提示

    /** 开场提示：一进来先说清楚这个游戏怎么玩，点一下开始 */
    private void drawStartPrompt(Graphics2D graphics) {
        Rectangle2D.Double panel = scrim(graphics, Theme.ACCENT);

        double centerX = panel.x + panel.width * 0.5;
        PromptDecoration.medal(graphics, centerX, panel.y + MEDAL_TOP, false, Theme.ACCENT);

        graphics.setFont(Theme.uiFont(Font.BOLD, TITLE_FONT_PX));
        graphics.setColor(Theme.TEXT);
        drawCentered(graphics, "美式八球", centerX, panel.y + TITLE_TOP);

        double y = panel.y + BODY_TOP;
        graphics.setFont(Theme.uiFont(Font.PLAIN, HINT_FONT_PX));
        graphics.setColor(Theme.TEXT_DIM);
        double placementHeight = drawCentered(
                graphics, "主球：拖到开球线后，松开落位", centerX, y);
        y = y + placementHeight;
        double shotHeight = drawCentered(
                graphics, "击球：光标放在球的另一侧，按住左键往后拖定力度，松开出杆",
                centerX, y);
        y = y + shotHeight;
        drawCentered(
                graphics, "拖的过程中绕球转手腕就能同时改瞄准方向　·　Esc 取消　·　R 重来",
                centerX, y);

        graphics.setFont(Theme.uiFont(Font.BOLD, CTA_FONT_PX));
        PromptDecoration.action(graphics, "点击开始", centerX,
                panel.y + START_ACTION_TOP, Theme.ACCENT);
    }

    // 终局面板

    /** 终局面板：谁赢了、为什么、怎么再来一局 */
    private void drawOutcome(Graphics2D graphics, MatchOutcome outcome, String achievementSummary) {
        Rectangle2D.Double panel = scrim(graphics, Theme.WARN);
        double centerX = panel.x + panel.width * 0.5;
        PromptDecoration.medal(graphics, centerX, panel.y + MEDAL_TOP, true, Theme.WARN);

        String title = "本局结束";
        if (outcome != null) {
            title = outcome.winner().labelZh() + " 获胜";
        }
        graphics.setFont(Theme.uiFont(Font.BOLD, TITLE_FONT_PX));
        graphics.setColor(Theme.WARN);
        drawCentered(graphics, title, centerX, panel.y + TITLE_TOP);

        graphics.setFont(Theme.uiFont(Font.PLAIN, REASON_FONT_PX));
        graphics.setColor(Theme.TEXT);
        // 理由直接取判决定下的那句话（MatchOutcome.reasonZh 的来源），不再另写一份文案：
        // 规则改了说法，浮层跟着改，不会出现「面板说一套、判定是另一套」
        if (outcome != null) {
            drawCentered(graphics, outcome.reasonZh(), centerX, panel.y + BODY_TOP);
        }

        if (!achievementSummary.isEmpty()) {
            graphics.setFont(Theme.uiFont(Font.PLAIN, ACHIEVEMENT_FONT_PX));
            graphics.setColor(Theme.WARN);
            drawCentered(graphics, achievementSummary, centerX, panel.y + ACHIEVEMENT_SUMMARY_TOP);
        }

        graphics.setFont(Theme.uiFont(Font.BOLD, CTA_FONT_PX));
        PromptDecoration.action(graphics, "点击或按 R 再来一局", centerX,
                panel.y + OUTCOME_ACTION_TOP, Theme.ACCENT);
    }

    /** 在上方显示短时成就提示，保留中央判罚横幅的位置和输入操作 */
    private void drawAchievement(Graphics2D graphics, String text) {
        Font font = Theme.uiFont(Font.BOLD, ACHIEVEMENT_FONT_PX);
        FontMetrics metrics = graphics.getFontMetrics(font);
        double available = ACHIEVEMENT_MAX_WIDTH - PANEL_PAD * 2 - BANNER_ICON_SPACE;
        if (metrics.stringWidth(text) > available) {
            float fittedSize = (float) (ACHIEVEMENT_FONT_PX * available / metrics.stringWidth(text));
            font = font.deriveFont(fittedSize);
            metrics = graphics.getFontMetrics(font);
        }
        double width = metrics.stringWidth(text) + PANEL_PAD * 2 + BANNER_ICON_SPACE;
        double left = (Layout.WINDOW_W - width) * 0.5;
        Rectangle2D.Double box = new Rectangle2D.Double(left, ACHIEVEMENT_TOP, width, ACHIEVEMENT_HEIGHT);
        graphics.setColor(Theme.HUD_PANEL);
        graphics.fill(round(box));
        graphics.setColor(Theme.WARN);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(box));
        double centerY = box.getCenterY();
        PromptDecoration.statusIcon(graphics, MatchView.Tone.WIN,
                left + PANEL_PAD - 4, centerY - 10, 20);
        graphics.setFont(font);
        graphics.setColor(Theme.TEXT);
        graphics.drawString(text, (float) (left + PANEL_PAD + BANNER_ICON_SPACE),
                (float) (centerY + (metrics.getAscent() - metrics.getDescent()) * 0.5));
    }

    // 调试浮层

    /** 量取调试文字的宽高后绘制面板，使各行落在面板内 */
    private void drawDebug(Graphics2D graphics, MatchView match) {
        graphics.setFont(Theme.uiFont(Font.PLAIN, DEBUG_FONT_PX));
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int lines = match.debugLineCount();
        double lineH = fontMetrics.getHeight();
        double width = 0.0;
        for (int index = 0; index < lines; index = index + 1) {
            width = Math.max(width, fontMetrics.stringWidth(match.debugLine(index)));
        }
        double pad = 8.0;
        Rectangle2D.Double box = new Rectangle2D.Double(
                Layout.DEBUG_PANEL_X, Layout.DEBUG_PANEL_Y,
                width + pad * 2, lines * lineH + pad * 2);

        Composite old = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        graphics.setColor(Theme.WINDOW_BG);
        graphics.fill(new Rectangle2D.Double(box.x, box.y, box.width, box.height));
        graphics.setComposite(old);

        graphics.setColor(Theme.WARN);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(box);
        graphics.setColor(Theme.withAlpha(Theme.WARN, 160));
        graphics.fill(new Rectangle2D.Double(box.x, box.y, DEBUG_ACCENT_WIDTH, box.height));

        graphics.setColor(Theme.TEXT);
        for (int indexValue = 0; indexValue < lines; indexValue = indexValue + 1) {
            graphics.drawString(match.debugLine(indexValue), (float) (box.x + pad),
                    (float) (box.y + pad + fontMetrics.getAscent() + indexValue * lineH));
        }
    }

    // 工具

    /** 压暗整屏并画出居中的板子。返回板子的矩形 */
    private static Rectangle2D.Double scrim(Graphics2D graphics, Color tone) {
        graphics.setColor(Theme.withAlpha(Theme.WINDOW_BG, Math.round(SCRIM_ALPHA * 255.0f)));
        graphics.fill(new Rectangle2D.Double(0, 0, Layout.WINDOW_W, Layout.WINDOW_H));

        Rectangle2D.Double panel = overlayPanel();
        // 阴影和内框都在提示区域内定位，不能改变输入命中范围
        graphics.setColor(Theme.withAlpha(Theme.WINDOW_BG, 105));
        graphics.fill(new RoundRectangle2D.Double(panel.x + 4, panel.y + 7,
                panel.width, panel.height, Layout.OVERLAY_PANEL_ARC, Layout.OVERLAY_PANEL_ARC));
        graphics.setColor(Theme.HUD_PANEL);
        graphics.fill(round(panel));
        graphics.setColor(Theme.HUD_BORDER);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(panel));
        PromptDecoration.frame(graphics, panel, tone);
        return panel;
    }

    /** 居中板的外框。几何来自 {@link Layout}，自检据此断言它整个落在窗口内 */
    public static Rectangle2D.Double overlayPanel() {
        return new Rectangle2D.Double(Layout.overlayPanelX(), Layout.overlayPanelY(),
                Layout.OVERLAY_PANEL_W, Layout.OVERLAY_PANEL_H);
    }

    /** 画一行水平居中的字，返回这一行占掉的高度（含行距） */
    private static double drawCentered(Graphics2D graphics, String text, double centerX, double baseline) {
        Font originalFont = graphics.getFont();
        FontMetrics fontMetrics = graphics.getFontMetrics();
        double availableWidth = Layout.OVERLAY_PANEL_W - PANEL_PAD * 2;
        // 按实际字宽缩小较长文案，防止中文字体差异使文字盖住装饰内框
        if (fontMetrics.stringWidth(text) > availableWidth) {
            double scale = availableWidth / fontMetrics.stringWidth(text);
            float fittedSize = (float) (originalFont.getSize2D() * scale);
            graphics.setFont(originalFont.deriveFont(fittedSize));
            fontMetrics = graphics.getFontMetrics();
        }
        graphics.drawString(text, (float) (centerX - fontMetrics.stringWidth(text) * 0.5), (float) baseline);
        double height = fontMetrics.getHeight();
        graphics.setFont(originalFont);
        return height;
    }

    /** 根据传入矩形和圆角参数创建圆角轮廓 */
    private static RoundRectangle2D.Double round(Rectangle2D.Double rectangle) {
        double arc = Layout.OVERLAY_PANEL_ARC;
        return new RoundRectangle2D.Double(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arc, arc);
    }
}

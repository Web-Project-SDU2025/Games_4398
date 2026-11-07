package billiards.render;

import billiards.rules.GamePhase;
import billiards.rules.Player;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 球台下方那条 HUD：双方托盘、组徽、当前手高亮、一行判定文案、力度条、重新开始
 *
 * <h2>版面是推出来的，不是摆出来的</h2>
 * 三行（A 托盘卡 / B 文案 / C 按钮与力度条）的位置全部由 {@link Layout} 里那几个
 * {@code hudRowXxx()} 给出，本类一个绝对坐标都不自己拼。理由是 M6 的版面元素会互相挤压：
 * 「托盘卡加高 4px」这种事如果只在渲染器里改，力度条会原地不动，看起来就是错位
 * 收在几何层之后，改一个行高整条 HUD 自己重排，自检也才能断言「三行不重叠、不越界」
 *
 * <h2>托盘为什么是七个固定槽位</h2>
 * 而不是「打进了几颗就画几颗」。固定槽位让<b>空缺本身也是信息</b>：玩家一眼能看出
 * 我方还剩几颗没进（空槽数），而「画了几颗」的版本里，进了一颗与进了三颗只差两个球的
 * 宽度，位置还会一路左移。槽位固定，球号就永远落在同一个地方
 *
 * <h2>当前手高亮与三角标</h2>
 * 两样一起给：卡片底色变亮（余光就能分辨），加上一个指向该卡的三角（正眼看时不用比较
 * 两块底色）。只做底色的话，在色弱与低对比度屏幕上「亮一点」和「暗一点」很难分辨；
 * 只做三角的话，一眼扫过去要先去找三角在哪。<b>形状信息与明度信息互为备份</b>
 */
public final class HudRenderer {

    /** 底板、卡片、按钮、药丸的圆角半径 */
    private static final double PANEL_ARC = 10.0;
    /** 玩家信息区圆角直径，单位为逻辑像素 */
    private static final double CARD_ARC = 8.0;
    /** 阶段标签圆角直径，单位为逻辑像素 */
    private static final double PILL_ARC = 12.0;

    /** 力度条圆角半径 */
    private static final double BAR_ARC = 8.0;

    /** 卡片内衬（卡片内沿到内容的距离） */
    private static final double CARD_PAD = 10.0;

    /** 卡片内「名字 → 组徽」「组徽 → 托盘」的间隙 */
    private static final double CARD_GAP = 8.0;
    /** 球组徽章与托盘之间的间隔，单位为逻辑像素 */
    private static final double BADGE_GAP = 10.0;

    /** B 行里阶段药丸与文案之间的间隙 */
    private static final double PILL_GAP = 12.0;

    /** 药丸左右内衬 */
    private static final double PILL_PAD = 10.0;

    /** 阶段标签图标的边长和右侧间隔，绘制与宽度检查共用这组参数 */
    private static final double STATUS_ICON_SIZE = 14.0;
    private static final double STATUS_ICON_GAP = 6.0;

    /** 力度读数相对力度条左沿的间隙 */
    private static final double LABEL_GAP = 14.0;

    /** 中央 8 号徽章的边长 */
    /**
     * 中央 8 号徽章的边长。它是 HUD 版面里唯一「夹在两张卡之间」的东西，
     * 所以自检要能拿到这个数去断言两张卡之间确实留得下它
     */
    public static final double EIGHT_MEDALLION = 42.0;

    /** 玩家名字字号，单位为逻辑像素 */
    private static final float NAME_FONT_PX = 15.0f;
    /** 球组徽章字号，单位为逻辑像素 */
    private static final float BADGE_FONT_PX = 13.0f;
    /** 阶段标签字号，单位为逻辑像素 */
    private static final float PILL_FONT_PX = 13.0f;
    /** 状态文字字号，单位为逻辑像素 */
    private static final float STATUS_FONT_PX = 15.0f;
    /** 力度读数字号，单位为逻辑像素 */
    private static final float POWER_FONT_PX = 15.0f;
    /** 重开按钮字号，单位为逻辑像素 */
    private static final float BUTTON_FONT_PX = 14.0f;

    /** 最宽的力度读数。位数变化时它不会动，所以版面按它留 */
    private static final String WIDEST_POWER_LABEL = "力度 100%";

    /** 「重新开始」按钮上的字 */
    public static final String RESTART_LABEL = "重新开始";

    /** 球的位图缓存。由 {@link GameRenderer} 传进来共用——再烘一份 16 张 3 倍超采样图纯属浪费 */
    private final BallSpriteCache sprites;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public HudRenderer(BallSpriteCache sprites) {
        this.sprites = sprites;
    }

    /** 根据传入的模型或只读快照绘制当前图层 */
    public void draw(Graphics2D graphics, GameStateView view) {
        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawPanel(graphics);
        drawPlayerCard(graphics, view.match(), Player.ONE, Layout.hudCardOneX());
        drawPlayerCard(graphics, view.match(), Player.TWO, Layout.hudCardTwoX());
        drawEightMedallion(graphics, view.match());
        drawStatusRow(graphics, view);
        drawRestartButton(graphics, view.match());
        drawPowerBar(graphics, view);

        if (oldAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    /** HUD 底板。由 {@link Layout} 的边界与内缩推出，不在这里写死数字 */
    public static Rectangle2D.Double panelRect() {
        return new Rectangle2D.Double(
                Layout.HUD_INSET, Layout.HUD_TOP + Layout.HUD_INSET,
                Layout.WINDOW_W - Layout.HUD_INSET * 2,
                Layout.HUD_BOTTOM - Layout.HUD_TOP - Layout.HUD_INSET * 2);
    }

    /** 力度条的完整外框（未填充部分） */
    public static Rectangle2D.Double powerBarRect() {
        return new Rectangle2D.Double(
                Layout.powerBarX(), Layout.powerBarY(),
                Layout.POWER_BAR_W, Layout.POWER_BAR_H);
    }

    /** 玩家卡的矩形。{@code cardX} 取自 {@link Layout#hudCardOneX()} / {@link Layout#hudCardTwoX()} */
    public static Rectangle2D.Double cardRect(double cardX) {
        return new Rectangle2D.Double(cardX, Layout.hudRowATop(), Layout.HUD_CARD_W, Layout.HUD_ROW_A_H);
    }

    /** 「重新开始」按钮的外框 */
    public static Rectangle2D.Double restartButtonRect() {
        return new Rectangle2D.Double(Layout.restartBtnX(), Layout.restartBtnY(),
                Layout.RESTART_BTN_W, Layout.HUD_ROW_C_H);
    }

    /**
     * 点是否落在「重新开始」按钮里。由 {@code MatchSession} 在按下时问它
     *
     * <p>判定放在渲染器里，是为了让按钮的<b>画</b>与<b>点</b>出自同一个矩形：分成两处写，
     * 迟早会出现「按钮挪了 4px、热区没挪」——那种错在演示时表现为「点了没反应」，
     * 而玩家不知道该往哪挪一点
     */
    public static boolean hitsRestartButton(double x, double y) {
        return restartButtonRect().contains(x, y);
    }

    // 底板与卡片

    /** 绘制状态区底板和边框，位置统一从布局参数取得 */
    private void drawPanel(Graphics2D graphics) {
        Rectangle2D.Double panel = panelRect();
        graphics.setColor(Theme.HUD_PANEL);
        graphics.fill(round(panel, PANEL_ARC));
        graphics.setColor(Theme.HUD_BORDER);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(panel, PANEL_ARC));
    }

    /** 绘制指定玩家的信息和落袋托盘，当前击球者同时显示高亮与标记 */
    private void drawPlayerCard(Graphics2D graphics, MatchView match, Player player, double cardX) {
        Rectangle2D.Double card = cardRect(cardX);
        boolean active = match.current() == player
                && match.phase() != GamePhase.MATCH_START
                && match.phase() != GamePhase.GAME_OVER;
        BallGroup group = match.groupOf(player);

        Color cardFill = Theme.darken(Theme.HUD_PANEL, 0.18f);
        if (active) {
            cardFill = Theme.HUD_PANEL_ACTIVE;
        }
        graphics.setColor(cardFill);
        graphics.fill(round(card, CARD_ARC));
        Color cardBorder = Theme.HUD_BORDER;
        float borderWidth = 1.0f;
        if (active) {
            cardBorder = Theme.ACCENT;
            borderWidth = 2.0f;
        }
        graphics.setColor(cardBorder);
        graphics.setStroke(new BasicStroke(borderWidth));
        graphics.draw(round(card, CARD_ARC));

        double cursor = card.x + CARD_PAD;
        double midY = card.y + card.height * 0.5;

        graphics.setFont(Theme.uiFont(Font.BOLD, NAME_FONT_PX));
        Color nameColor = Theme.TEXT_DIM;
        if (active) {
            nameColor = Theme.TEXT;
        }
        graphics.setColor(nameColor);
        graphics.drawString(player.labelZh(), (float) cursor, (float) centerBaseline(graphics, midY));
        cursor = cursor + graphics.getFontMetrics().stringWidth(player.labelZh()) + CARD_GAP;

        double badgeWidth = drawGroupBadge(graphics, group, cursor, midY);
        cursor = cursor + badgeWidth + BADGE_GAP;

        drawTray(graphics, match, player, cursor, midY);

        if (active) {
            drawTurnMarker(graphics, card, player);
        }
    }

    /**
     * 组徽：定了组画成一颗该组的球＋一行小字，没定组画一个空框
     *
     * <p>返回它占掉的宽度，好让调用方接着往右排——宽度是量出来的而不是拍的，
     * 因为「全色」与「花色」同宽但「未定组」更宽，写死会让托盘在两局之间跳动
     */
    private double drawGroupBadge(Graphics2D graphics, BallGroup group, double x, double midY) {
        String text = badgeText(group);
        graphics.setFont(Theme.uiFont(Font.BOLD, BADGE_FONT_PX));
        FontMetrics fontMetrics = graphics.getFontMetrics();
        double width = fontMetrics.stringWidth(text) + PILL_PAD * 2;
        double height = 20.0;

        Color badgeFill = Theme.darken(Theme.HUD_PANEL, 0.05f);
        if (group == null) {
            badgeFill = Theme.darken(Theme.HUD_PANEL, 0.3f);
        }
        graphics.setColor(badgeFill);
        graphics.fill(round(new Rectangle2D.Double(x, midY - height * 0.5, width, height), PILL_ARC * 0.6));
        Color badgeBorder = Theme.brighten(Theme.HUD_BORDER, 0.2f);
        if (group == null) {
            badgeBorder = Theme.HUD_BORDER;
        }
        graphics.setColor(badgeBorder);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(new Rectangle2D.Double(x, midY - height * 0.5, width, height), PILL_ARC * 0.6));

        Color badgeTextColor = Theme.TEXT;
        if (group == null) {
            badgeTextColor = Theme.TEXT_DIM;
        }
        graphics.setColor(badgeTextColor);
        graphics.drawString(text, (float) (x + PILL_PAD), (float) centerBaseline(graphics, midY));
        return width;
    }

    /** 托盘：固定 7 个槽位，落袋的球按球号升序填进去，空的画一个暗圈 */
    private void drawTray(Graphics2D graphics, MatchView match, Player player, double x, double midY) {
        int potted = match.pottedCount(player);
        for (int slot = 0; slot < Layout.HUD_TRAY_SLOTS; slot = slot + 1) {
            double centerX = x + Layout.HUD_TRAY_BALL_D * 0.5 + slot * Layout.HUD_TRAY_PITCH;
            double centerY = midY;
            if (slot < potted) {
                sprites.draw(graphics, match.potted(player, slot), centerX, centerY);
            } else {
                double radius = Layout.HUD_TRAY_BALL_D * 0.5;
                graphics.setColor(Theme.darken(Theme.HUD_PANEL, 0.32f));
                graphics.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0));
            }
        }
    }

    /**
     * 当前手的三角标：贴在卡片朝内的那一侧，尖指向卡片
     *
     * <p>玩家 1 的卡在左边，三角就画在它的<b>右</b>侧、尖朝左；玩家 2 反之
     */
    private void drawTurnMarker(Graphics2D graphics, Rectangle2D.Double card, Player player) {
        double height = 14.0;
        double width = 8.0;
        double midY = card.y + card.height * 0.5;
        Path2D.Double tri = new Path2D.Double();
        if (player == Player.ONE) {
            double tipX = card.x + card.width + 3.0;
            tri.moveTo(tipX, midY);
            tri.lineTo(tipX + width, midY - height * 0.5);
            tri.lineTo(tipX + width, midY + height * 0.5);
        } else {
            double tipX = card.x - 3.0;
            tri.moveTo(tipX, midY);
            tri.lineTo(tipX - width, midY - height * 0.5);
            tri.lineTo(tipX - width, midY + height * 0.5);
        }
        tri.closePath();
        graphics.setColor(Theme.ACCENT);
        graphics.fill(tri);
    }

    /**
     * 中央的 8 号徽章
     *
     * <p>它不只是装饰：8 号是本局唯一的决胜球，而它在哪一方的托盘里都不该出现
     * 摆在两卡正中间，一眼就能回答「8 号还在吗」。已落袋时画成暗色并划一道斜杠——
     * <b>「不在了」必须比「还在」更显眼</b>，否则玩家会在该打 8 号的时候去找它
     */
    private void drawEightMedallion(Graphics2D graphics, MatchView match) {
        double centerX = Layout.WINDOW_W * 0.5;
        double centerY = Layout.hudRowATop() + Layout.HUD_ROW_A_H * 0.5;
        double half = EIGHT_MEDALLION * 0.5;

        double medallionDarkness = 0.4f;
        if (match.eightOnTable()) {
            medallionDarkness = 0.05f;
        }
        graphics.setColor(Theme.darken(Theme.HUD_PANEL, (float) medallionDarkness));
        graphics.fill(new Ellipse2D.Double(centerX - half, centerY - half, EIGHT_MEDALLION, EIGHT_MEDALLION));
        Color medallionBorder = Theme.darken(Theme.HUD_BORDER, 0.4f);
        if (match.eightOnTable()) {
            medallionBorder = Theme.HUD_BORDER;
        }
        graphics.setColor(medallionBorder);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(new Ellipse2D.Double(centerX - half, centerY - half, EIGHT_MEDALLION, EIGHT_MEDALLION));

        if (match.eightOnTable()) {
            sprites.draw(graphics, BallCatalog.EIGHT_ID, centerX, centerY);
            return;
        }
        // 已落袋：压暗再划一道斜杠。「不在了」必须比「还在」更显眼，否则玩家会在该打
        // 8 号的时候满台找它
        Composite old = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        sprites.draw(graphics, BallCatalog.EIGHT_ID, centerX, centerY);
        graphics.setComposite(old);

        double distance = half * 0.9;
        graphics.setColor(Theme.FOUL);
        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Double(centerX - distance, centerY + distance, centerX + distance, centerY - distance));
    }

    // B 行：阶段药丸 + 判定文案

    /** 绘制阶段标签与状态文字，按字宽依次排布以防重叠 */
    private void drawStatusRow(Graphics2D graphics, GameStateView view) {
        MatchView match = view.match();
        double x = Layout.hudContentLeft();
        double midY = Layout.hudRowBTop() + Layout.HUD_ROW_B_H * 0.5;

        String label = match.phase().labelZh();
        double pillWidth = drawPhasePill(graphics, label, x, midY, match.statusTone());
        x = x + pillWidth + PILL_GAP;

        String status = view.status();
        if (status.isEmpty()) {
            return;
        }
        graphics.setFont(Theme.uiFont(Font.PLAIN, STATUS_FONT_PX));
        graphics.setColor(toneColor(match.statusTone()));
        graphics.drawString(status, (float) x, (float) centerBaseline(graphics, midY));
    }

    /** 绘制阶段标签并返回占用宽度，调用方据此摆放后续文字 */
    private double drawPhasePill(Graphics2D graphics, String label, double x,
                                 double midY, MatchView.Tone statusTone) {
        Color tone = toneColor(statusTone);
        graphics.setFont(Theme.uiFont(Font.BOLD, PILL_FONT_PX));
        FontMetrics fontMetrics = graphics.getFontMetrics();
        double iconSpace = STATUS_ICON_SIZE + STATUS_ICON_GAP;
        double width = fontMetrics.stringWidth(label) + PILL_PAD * 2 + iconSpace;
        double height = 22.0;

        graphics.setColor(Theme.darken(Theme.HUD_PANEL, 0.25f));
        graphics.fill(round(new Rectangle2D.Double(x, midY - height * 0.5, width, height), PILL_ARC * 0.7));
        graphics.setColor(tone);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(new Rectangle2D.Double(x, midY - height * 0.5, width, height), PILL_ARC * 0.7));

        PromptDecoration.statusIcon(graphics, statusTone,
                x + PILL_PAD, midY - STATUS_ICON_SIZE * 0.5, STATUS_ICON_SIZE);
        graphics.setColor(tone);
        graphics.drawString(label, (float) (x + PILL_PAD + iconSpace),
                (float) centerBaseline(graphics, midY));
        return width;
    }

    /**
     * 一张玩家卡的内容还剩多少余量（px）
     *
     * <p>卡里三样东西的宽度只有一样是常数（托盘 7 个槽位），另两样都是<b>量出来的字宽</b>
     * ——「玩家 1」与「花色 9–15」在不同字体下的宽度并不相同。所以卡宽不能拍，得有一条
     * 能算的断言；否则「换了台机器托盘压出边框」这种问题只能靠肉眼发现
     */
    public static double cardSlack(Graphics2D graphics, String name, String badgeText) {
        graphics.setFont(Theme.uiFont(Font.BOLD, NAME_FONT_PX));
        double used = graphics.getFontMetrics().stringWidth(name) + CARD_GAP;

        graphics.setFont(Theme.uiFont(Font.BOLD, BADGE_FONT_PX));
        used = used + graphics.getFontMetrics().stringWidth(badgeText) + PILL_PAD * 2 + BADGE_GAP;

        double trayWidth = (Layout.HUD_TRAY_SLOTS - 1) * Layout.HUD_TRAY_PITCH
                + Layout.HUD_TRAY_BALL_D;
        used = used + trayWidth;

        return Layout.HUD_CARD_W - CARD_PAD * 2 - used;
    }

    /** 某位玩家卡上组徽的文案。自检与渲染必须用同一份，否则量出来的余量对不上画出来的东西 */
    public static String badgeText(BallGroup group) {
        if (group == null) {
            return "未定组";
        }
        if (group == BallGroup.SOLID) {
            return "全色 1–7";
        }
        if (group == BallGroup.STRIPE) {
            return "花色 9–15";
        }
        return group.labelZh();
    }

    /** 文案还剩多少余量（px）：正数＝放得下，负数＝会画到内容带右沿之外 */
    public static double statusSlack(Graphics2D graphics, String phaseLabel, String status) {
        graphics.setFont(Theme.uiFont(Font.BOLD, PILL_FONT_PX));
        double x = Layout.hudContentLeft()
                + graphics.getFontMetrics().stringWidth(phaseLabel) + PILL_PAD * 2 + PILL_GAP
                + STATUS_ICON_SIZE + STATUS_ICON_GAP;

        graphics.setFont(Theme.uiFont(Font.PLAIN, STATUS_FONT_PX));
        return Layout.hudContentRight() - (x + graphics.getFontMetrics().stringWidth(status));
    }

    // C 行：按钮与力度条

    /** 绘制重开按钮，终局时使用强调色突出下一步操作 */
    private void drawRestartButton(Graphics2D graphics, MatchView match) {
        Rectangle2D.Double rectangle = restartButtonRect();
        // 终局时按钮变成主行动，用强调色；其余时候是普通按钮
        boolean primary = match.phase() == GamePhase.GAME_OVER;
        Color buttonFill = Theme.darken(Theme.HUD_PANEL, 0.2f);
        if (primary) {
            buttonFill = Theme.ACCENT;
        }
        graphics.setColor(buttonFill);
        graphics.fill(round(rectangle, CARD_ARC));
        Color buttonBorder = Theme.HUD_BORDER;
        if (primary) {
            buttonBorder = Theme.brighten(Theme.ACCENT, 0.3f);
        }
        graphics.setColor(buttonBorder);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(rectangle, CARD_ARC));

        graphics.setFont(Theme.uiFont(Font.BOLD, BUTTON_FONT_PX));
        FontMetrics fontMetrics = graphics.getFontMetrics();
        double tx = rectangle.x + (rectangle.width - fontMetrics.stringWidth(RESTART_LABEL)) * 0.5;
        Color buttonText = Theme.TEXT;
        if (primary) {
            buttonText = Theme.WINDOW_BG;
        }
        graphics.setColor(buttonText);
        graphics.drawString(RESTART_LABEL, (float) tx, (float) centerBaseline(graphics, rectangle.y + rectangle.height * 0.5));

        // 热键提示贴在按钮右上角外侧，不占按钮宽度：R 是给键盘用户的，鼠标用户不需要它
        graphics.setFont(Theme.uiFont(Font.PLAIN, 11.0f));
        graphics.setColor(Theme.TEXT_DIM);
        graphics.drawString("R", (float) (rectangle.x + rectangle.width + 8.0),
                (float) centerBaseline(graphics, rectangle.y + rectangle.height * 0.5));
    }

    /** 根据力度填充进度条，读数对齐方式固定以免布局跳动 */
    private void drawPowerBar(Graphics2D graphics, GameStateView view) {
        Rectangle2D.Double bar = powerBarRect();
        double power = clamp01(view.power());

        // 槽底：即使力度为 0 也画出来，让人知道这里有根条、以及它有多长
        graphics.setColor(Theme.darken(Theme.HUD_PANEL, 0.35f));
        graphics.fill(round(bar, BAR_ARC));

        if (power > 0.0) {
            Shape oldClip = graphics.getClip();
            graphics.clip(round(bar, BAR_ARC));
            // 渐变铺满整根槽而不是只铺填充段：这样同一力度对应的颜色永远一样，
            // 力度条才有「读数」的意义，而不是「一半绿一半红」的装饰
            graphics.setPaint(new LinearGradientPaint(
                    new Point2D.Double(bar.x, bar.y), new Point2D.Double(bar.x + bar.width, bar.y),
                    new float[]{0.0f, 0.55f, 1.0f},
                    new Color[]{Theme.POWER_LOW, Theme.POWER_MID, Theme.POWER_HIGH}));
            graphics.fill(new Rectangle2D.Double(bar.x, bar.y, bar.width * power, bar.height));
            graphics.setClip(oldClip);
        }

        graphics.setColor(Theme.HUD_BORDER);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(round(bar, BAR_ARC));

        // 读数右对齐贴着条的左边：位数变化（0% → 100%）时数字不会推着条一起动
        String label = String.format("力度 %d%%", Math.round(power * 100));
        graphics.setFont(Theme.uiFont(Font.BOLD, POWER_FONT_PX));
        Color powerColor = Theme.TEXT_DIM;
        if (power > 0.0) {
            powerColor = Theme.TEXT;
        }
        graphics.setColor(powerColor);
        FontMetrics fontMetrics = graphics.getFontMetrics();
        graphics.drawString(label, (float) (bar.x - LABEL_GAP - fontMetrics.stringWidth(label)),
                (float) centerBaseline(graphics, bar.y + bar.height * 0.5));
    }

    // 工具

    /** 语气 → 颜色。这是全工程唯一一处做这个翻译的地方 */
    static Color toneColor(MatchView.Tone tone) {
        if (tone == MatchView.Tone.LEGAL) {
            return Theme.LEGAL;
        }
        if (tone == MatchView.Tone.FOUL) {
            return Theme.FOUL;
        }
        if (tone == MatchView.Tone.WIN) {
            return Theme.WARN;
        }
        return Theme.TEXT_DIM;
    }

    /** 根据传入矩形和圆角参数创建圆角轮廓 */
    private static Shape round(Rectangle2D.Double rectangle, double arc) {
        return new RoundRectangle2D.Double(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arc, arc);
    }

    /**
     * 让文字的视觉中心落在 {@code centerY} 上的基线位置
     *
     * <p>用字体的 ascent/descent 算，而不是用字号乘以一个拍脑袋的系数：中文与数字的
     * descent 差别很大，系数法在不同字体的机器上会明显偏
     */
    private static double centerBaseline(Graphics2D graphics, double centerY) {
        FontMetrics fontMetrics = graphics.getFontMetrics();
        return centerY + (fontMetrics.getAscent() - fontMetrics.getDescent()) * 0.5;
    }

    /** 将传入数值限制在零到一之间，供力度或音量换算使用 */
    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}

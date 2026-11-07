package billiards.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** 共用提示装饰，只绘制图案，不读取输入或改变对局状态 */
final class PromptDecoration {
    /** 提示面板内框距外边缘的距离，单位为逻辑像素 */
    private static final double FRAME_INSET = 8.0;

    /** 内框角部强调线长度，单位为逻辑像素 */
    private static final double CORNER_LENGTH = 20.0;

    /** 大徽章直径，单位为逻辑像素 */
    private static final double MEDAL_SIZE = 54.0;

    /** 操作强调区域的高度和最小宽度，单位为逻辑像素 */
    private static final double ACTION_HEIGHT = 34.0;
    private static final double ACTION_MIN_WIDTH = 196.0;

    /** 操作文字两侧留白，单位为逻辑像素 */
    private static final double ACTION_PADDING = 20.0;

    /** 工具类只提供绘制方法，不需要创建对象 */
    private PromptDecoration() {
    }

    /** 给提示面板绘制内框和角部强调，参数为面板边界与状态颜色 */
    static void frame(Graphics2D graphics, Rectangle2D.Double panel, Color tone) {
        double left = panel.x + FRAME_INSET;
        double top = panel.y + FRAME_INSET;
        double right = panel.getMaxX() - FRAME_INSET;
        double bottom = panel.getMaxY() - FRAME_INSET;
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(Theme.withAlpha(tone, 40));
        graphics.draw(new RoundRectangle2D.Double(left, top, right - left, bottom - top, 8, 8));
        graphics.setColor(Theme.withAlpha(tone, 165));
        // 四角采用同一组长度，缩放后仍能保持对称而不占用正文空间
        corner(graphics, left, top, 1, 1);
        corner(graphics, right, top, -1, 1);
        corner(graphics, left, bottom, 1, -1);
        corner(graphics, right, bottom, -1, -1);
    }

    /** 从指定角点向内绘制两条强调线，方向参数只取正一或负一 */
    private static void corner(Graphics2D graphics, double x, double y, int horizontal, int vertical) {
        graphics.draw(new Line2D.Double(x, y, x + horizontal * CORNER_LENGTH, y));
        graphics.draw(new Line2D.Double(x, y, x, y + vertical * CORNER_LENGTH));
    }

    /** 绘制开场八号球或终局奖杯徽章，徽章周围的横线帮助标题居中 */
    static void medal(Graphics2D graphics, double centerX, double centerY, boolean winner, Color tone) {
        double radius = MEDAL_SIZE * 0.5;
        graphics.setColor(Theme.withAlpha(tone, 18));
        graphics.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, MEDAL_SIZE, MEDAL_SIZE));
        graphics.setColor(Theme.withAlpha(tone, 130));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(new Ellipse2D.Double(centerX - radius, centerY - radius, MEDAL_SIZE, MEDAL_SIZE));
        graphics.draw(new Line2D.Double(centerX - 110, centerY, centerX - radius - 12, centerY));
        graphics.draw(new Line2D.Double(centerX + radius + 12, centerY, centerX + 110, centerY));
        if (winner) {
            trophy(graphics, centerX, centerY, tone);
        } else {
            graphics.setColor(Theme.WINDOW_BG);
            graphics.fill(new Ellipse2D.Double(centerX - 18, centerY - 18, 36, 36));
            graphics.setColor(Theme.TEXT);
            graphics.fill(new Ellipse2D.Double(centerX - 10, centerY - 10, 20, 20));
            graphics.setFont(Theme.uiFont(Font.BOLD, 17.0f));
            graphics.setColor(Theme.WINDOW_BG);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString("8", (float) (centerX - metrics.stringWidth("8") * 0.5),
                    (float) (centerY + (metrics.getAscent() - metrics.getDescent()) * 0.5));
        }
    }

    /** 使用杯身、把手和底座绘制奖杯，图案与窗口字体无关 */
    private static void trophy(Graphics2D graphics, double centerX, double centerY, Color tone) {
        graphics.setColor(tone);
        graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double cup = new Path2D.Double();
        cup.moveTo(centerX - 10, centerY - 12);
        cup.lineTo(centerX + 10, centerY - 12);
        cup.lineTo(centerX + 8, centerY);
        cup.quadTo(centerX, centerY + 10, centerX - 8, centerY);
        cup.closePath();
        graphics.draw(cup);
        graphics.draw(new Arc2D.Double(centerX - 17, centerY - 10, 12, 15, 90, 180, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(centerX + 5, centerY - 10, 12, 15, -90, 180, Arc2D.OPEN));
        graphics.draw(new Line2D.Double(centerX, centerY + 5, centerX, centerY + 13));
        graphics.draw(new Line2D.Double(centerX - 9, centerY + 14, centerX + 9, centerY + 14));
    }

    /** 绘制操作文字的强调底纹，按实际字宽留白，不新增点击区域 */
    static void action(Graphics2D graphics, String text, double centerX, double baseline, Color tone) {
        FontMetrics metrics = graphics.getFontMetrics();
        double width = Math.max(ACTION_MIN_WIDTH, metrics.stringWidth(text) + ACTION_PADDING * 2);
        double centerY = baseline - (metrics.getAscent() - metrics.getDescent()) * 0.5;
        RoundRectangle2D.Double area = new RoundRectangle2D.Double(
                centerX - width * 0.5, centerY - ACTION_HEIGHT * 0.5,
                width, ACTION_HEIGHT, 8, 8);
        graphics.setColor(Theme.withAlpha(tone, 22));
        graphics.fill(area);
        graphics.setColor(Theme.withAlpha(tone, 120));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.draw(area);
        graphics.setColor(tone);
        graphics.drawString(text, (float) (centerX - metrics.stringWidth(text) * 0.5), (float) baseline);
    }

    /** 根据提示语气绘制检查、警告或胜利图标，参数大小统一按单位图形缩放 */
    static void statusIcon(Graphics2D graphics, MatchView.Tone tone, double x, double y, double size) {
        Graphics2D icon = (Graphics2D) graphics.create();
        try {
            icon.translate(x, y);
            icon.scale(size / 20.0, size / 20.0);
            icon.setColor(HudRenderer.toneColor(tone));
            icon.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (tone == MatchView.Tone.WIN) {
                icon.translate(5, 5);
                icon.scale(0.5, 0.5);
                trophy(icon, 10, 10, Theme.WARN);
            } else if (tone == MatchView.Tone.FOUL) {
                Path2D.Double warning = new Path2D.Double();
                warning.moveTo(10, 2);
                warning.lineTo(19, 18);
                warning.lineTo(1, 18);
                warning.closePath();
                icon.draw(warning);
                icon.draw(new Line2D.Double(10, 7, 10, 11));
                icon.draw(new Line2D.Double(10, 14, 10, 14.5));
            } else {
                icon.draw(new Ellipse2D.Double(2, 2, 16, 16));
                if (tone == MatchView.Tone.LEGAL) {
                    Path2D.Double check = new Path2D.Double();
                    check.moveTo(6, 10);
                    check.lineTo(9, 13);
                    check.lineTo(14, 7);
                    icon.draw(check);
                } else {
                    icon.draw(new Ellipse2D.Double(7, 7, 6, 6));
                }
            }
        } finally {
            icon.dispose();
        }
    }
}

package billiards.render;

import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Vec2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 画一颗球。全部程序化绘制，工程里没有任何图片素材
 *
 * <p>一颗球的画法分四层：台呢接触阴影 → 球体径向渐变（光源在左上）→ 花色球的横带 →
 * 中央号码盘与号码 → 高光与描边
 *
 * <h2>为什么拆成三个方法而不是一个 draw</h2>
 * {@link BallSpriteCache} 需要把同一套画法烘焙进位图——而且要在 3 倍尺寸上画再缩回来
 * （超采样，见 {@link BallSpriteCache} 的说明）。所以「球长什么样」这件事必须表达成
 * <b>半径可参数化</b>的三个片段：{@link #paintShadow}、{@link #paintBody}、
 * {@link #paintOutline}。传 {@code radius = Ball.RADIUS} 时它们逐像素等价于原先那一整段
 * 绘制的对应部分，因此烘焙与直画不可能画出两种球
 *
 * <p>为什么阴影与描边<b>不</b>烘焙进 sprite：见 {@link BallSpriteCache}——
 * 它们要和<b>透明度</b>打交道，而半透明图层先合成再整体调透明度，与逐层各自带透明度，
 * 数学上并不相等。虚影球那条路径因此仍走这里的矢量画法
 */
public final class BallRenderer {

    /** 号码盘半径相对球半径 */
    private static final double NUMBER_DISK_RATIO = 0.54;

    /** 花色带高度相对球直径 */
    private static final double STRIPE_BAND_RATIO = 0.60;

    /** 描边线宽相对球半径。烘焙时随半径一起放大，缩回来才是同样粗细 */
    private static final double OUTLINE_RATIO = 1.2 / 11.0;

    /** 正常球的描边不透明度（<b>背光那侧</b>的峰值，受光侧由它按比例推出来） */
    public static final int OUTLINE_ALPHA_OPAQUE = 130;

    /** 虚影球的描边不透明度，较淡的边缘用于区分预览与实体球 */
    public static final int OUTLINE_ALPHA_GHOST = 90;

    /**
     * 描边受光那侧的峰值不透明度占背光侧的多少
     *
     * <p>130 × 0.40 ≈ 52。这个比例不是拍的：它要让描边在左上角几乎消失、在右下角保持原本
     * 的实边。一圈<b>等浓度</b>的黑环是漫画的做法（贴纸感正来自它），真实的球在受光侧
     * 轮廓是被光「吃掉」的
     */
    private static final double OUTLINE_LIT_FRACTION = 0.40;

    /** 接触阴影椭圆的半轴（相对球半径）。3.2r 的横向直径才够从球后面露出来 */
    private static final double SHADOW_HALF_RATIO = 1.60;

    /** 接触阴影的纵向压扁系数：光在左上，影朝右下压扁 */
    private static final double SHADOW_FLATTEN = 0.78;

    /** 接触阴影中心的偏移（相对球半径），朝背光的右下 */
    private static final double SHADOW_OFFSET_X = 0.25;
    /** 球影向下偏移占球半径的比例 */
    private static final double SHADOW_OFFSET_Y = 0.45;

    /** 接触阴影核心的不透明度，边缘渐隐到 0（正圆硬边会读成描边变粗） */
    private static final int SHADOW_ALPHA = 130;

    /**
     * 画一颗球
     *
     * @param centerOverride 若不为 {@code null} 则用它当球心（画虚影球、落袋托盘时用）
     */
    public void draw(Graphics2D graphics, Ball ball, Vec2 centerOverride) {
        Vec2 center = ball.position();
        if (centerOverride != null) {
            center = centerOverride;
        }
        drawById(graphics, ball.id(), center, 1.0f);
    }

    /** 根据传入的模型或只读快照绘制当前图层 */
    public void draw(Graphics2D graphics, Ball ball) {
        draw(graphics, ball, null);
    }

    /**
     * 按球号与球心绘制。{@code alpha} 用于虚影球（半透明预览）
     *
     * <p>这是<b>矢量路径</b>：每一层都现画。台上一帧十几颗球走的是
     * {@link BallSpriteCache} 的位图路径；这条路留给「一帧最多两三个」的半透明预览，
     * 半透明预览保留逐层合成，保证透明度与叠放顺序正确
     */
    public void drawById(Graphics2D graphics, int ballId, Vec2 center, float alpha) {
        drawById(graphics, ballId, center, alpha, true);
    }

    /**
     * @param castShadow 是否画接触阴影。台面上要，HUD 托盘的迷你球不要——托盘底板
     *                   本来就是深色，再叠一层影只会让号码糊掉
     */
    private void drawById(Graphics2D graphics, int ballId, Vec2 center, float alpha, boolean castShadow) {
        final double radius = Ball.RADIUS;
        final double x = center.x;
        final double y = center.y;

        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        java.awt.Composite oldComposite = null;
        if (alpha < 1.0f) {
            oldComposite = graphics.getComposite();
            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        if (alpha >= 1.0f && castShadow) {
            paintShadow(graphics, x, y, radius);
        }
        paintBody(graphics, ballId, x, y, radius);
        int outlineAlpha = OUTLINE_ALPHA_GHOST;
        if (alpha >= 1.0f) {
            outlineAlpha = OUTLINE_ALPHA_OPAQUE;
        }
        paintOutline(graphics, x, y, radius, outlineAlpha);

        if (oldComposite != null) {
            graphics.setComposite(oldComposite);
        }
        if (oldAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    // 三个可参数化的绘制片段（球体的全部外观都在这里）

    /** 台呢上的接触阴影，让球看起来贴在台面上而不是飘着 */
    public void paintShadow(Graphics2D graphics, double x, double y, double radius) {
        double shadowX = x + radius * SHADOW_OFFSET_X;
        double shadowY = y + radius * SHADOW_OFFSET_Y;
        double shadowRadius = radius * SHADOW_HALF_RATIO;

        java.awt.geom.AffineTransform old = graphics.getTransform();
        graphics.translate(shadowX, shadowY);
        graphics.scale(1.0, SHADOW_FLATTEN);
        graphics.setPaint(new RadialGradientPaint(
                new Point2D.Double(0.0, 0.0), (float) shadowRadius,
                new float[]{0.0f, 0.55f, 1.0f},
                new Color[]{new Color(0, 0, 0, SHADOW_ALPHA),
                        new Color(0, 0, 0, (int) (SHADOW_ALPHA * 0.80)),
                        new Color(0, 0, 0, 0)}));
        graphics.fill(new Ellipse2D.Double(-shadowRadius, -shadowRadius, shadowRadius * 2, shadowRadius * 2));
        graphics.setTransform(old);
    }

    /**
     * 球体本身：底色渐变 + 花色带 + 号码盘与号码 + 高光。不含阴影与描边
     *
     * <p>全部尺寸都按 {@code r} 的比例给，所以传 {@code r = 3 × Ball.RADIUS} 画出来的
     * 就是同一颗球放大三倍——这正是超采样烘焙需要的东西
     */
    public void paintBody(Graphics2D graphics, int ballId, double x, double y, double radius) {
        Color base = BallCatalog.colorOf(ballId);
        boolean striped = BallCatalog.isStriped(ballId);
        boolean showNumber = ballId != BallCatalog.CUE_ID;

        Shape circle = new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2);

        // ---- 球体底色 ----
        Color bodyColor = base;
        if (striped) {
            bodyColor = new Color(0xF2, 0xF2, 0xEC);
        }
        graphics.setPaint(radialBody(x, y, radius, bodyColor));
        graphics.fill(circle);

        // ---- 花色球的横带 ----
        if (striped) {
            Shape oldClip = graphics.getClip();
            graphics.clip(circle);
            double bandH = radius * 2 * STRIPE_BAND_RATIO;
            graphics.setPaint(new LinearGradientPaint(
                    new Point2D.Double(x, y - bandH / 2), new Point2D.Double(x, y + bandH / 2),
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{Theme.darken(base, 0.28f), Theme.brighten(base, 0.24f), Theme.darken(base, 0.32f)}));
            graphics.fill(new Rectangle2D.Double(x - radius, y - bandH / 2, radius * 2, bandH));
            graphics.setClip(oldClip);
        }

        // ---- 号码盘与号码 ----
        if (showNumber) {
            double diskR = radius * NUMBER_DISK_RATIO;
            graphics.setColor(new Color(0xF7, 0xF7, 0xF2));
            graphics.fill(new Ellipse2D.Double(x - diskR, y - diskR, diskR * 2, diskR * 2));

            String label = String.valueOf(ballId);
            double numberScale = 1.02;
            if (label.length() > 1) {
                numberScale = 0.86;
            }
            float fontSize = (float) (radius * numberScale);
            graphics.setFont(Theme.uiFont(java.awt.Font.BOLD, fontSize));
            graphics.setColor(new Color(0x1A, 0x1A, 0x1A));

            java.awt.FontMetrics fontMetrics = graphics.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(label);
            // 用字体自身的 ascent/descent 居中，比用字号估算稳
            double baseline = y + (fontMetrics.getAscent() - fontMetrics.getDescent()) * 0.5;
            graphics.drawString(label, (float) (x - textWidth / 2.0), (float) baseline);
        }

        // ---- 高光 ----
        graphics.setPaint(new RadialGradientPaint(
                new Point2D.Double(x - radius * 0.34, y - radius * 0.38), (float) (radius * 0.72),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(255, 255, 255, 165), new Color(255, 255, 255, 0)}));
        graphics.fill(circle);
    }

    /** 球的描边。线宽按半径比例给，所以放大烘焙再缩回来粗细不变 */
    public void paintOutline(Graphics2D graphics, double x, double y, double radius, int alpha) {
        int litAlpha = (int) Math.round(alpha * OUTLINE_LIT_FRACTION);
        double distance = radius * 0.70710678;
        graphics.setPaint(new LinearGradientPaint(
                new Point2D.Double(x - distance, y - distance), new Point2D.Double(x + distance, y + distance),
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(0, 0, 0, litAlpha), new Color(0, 0, 0, alpha)}));
        graphics.setStroke(new BasicStroke((float) (OUTLINE_RATIO * radius)));
        graphics.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }

    /** 落袋托盘里的小球（HUD 用） */
    public void drawMini(Graphics2D graphics, int ballId, Vec2 center, double radius) {
        double scale = radius / Ball.RADIUS;
        java.awt.geom.AffineTransform old = graphics.getTransform();
        graphics.translate(center.x, center.y);
        graphics.scale(scale, scale);
        graphics.translate(-center.x, -center.y);
        drawById(graphics, ballId, center, 1.0f, false);
        graphics.setTransform(old);
    }

    /** 根据球心、半径和底色生成球面渐变，光源保持在左上方 */
    private static RadialGradientPaint radialBody(double x, double y, double radius, Color base) {
        return new RadialGradientPaint(
                new Point2D.Double(x - radius * 0.32, y - radius * 0.34), (float) (radius * 1.55),
                new float[]{0.0f, 0.52f, 1.0f},
                new Color[]{Theme.brighten(base, 0.52f), base, Theme.darken(base, 0.38f)});
    }

    /** 供 HUD 画「已落袋」托盘的圆角底板 */
    public static Shape traySlot(double x, double y, double size) {
        return new RoundRectangle2D.Double(x, y, size, size, size * 0.35, size * 0.35);
    }
}

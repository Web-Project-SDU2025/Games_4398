package billiards.render;

import billiards.world.Ball;
import billiards.world.Vec2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * 画瞄准辅助：一条虚线、一颗虚影球、两条分离线
 *
 * <p>所有几何都由 {@code AimCalculator} 在模拟线程算好、随 {@link AimView} 一起发布，
 * 渲染器这里不含任何判断（除了几个固定长度），它只负责画
 *
 * <p>四样东西的语义，按玩家看图的顺序：
 * <ol>
 *   <li><b>虚线</b>——主球球心要走的那条路。终点是<b>首接触时球心停下处</b>，
 *       不是「画到台边为止」，两者的差别见 {@link AimView}</li>
 *   <li><b>虚影球</b>——主球停在接触点时的位置。它刻意画成一个半透明的白盘而不是一张
 *       真球贴图：一来它本来就不是球，二来台面上已有 16 张画得很实的球，虚影必须一眼看
 *       出不是其中之一，否则玩家会去瞄那颗「多出来的球」</li>
 *   <li><b>目标球分离线</b>（蓝色）——被撞的球会沿两球心连线出去。把它对准袋口，
 *       就是所谓「用虚影球瞄准」</li>
 *   <li><b>主球切线</b>（淡白）——主球撞完往哪走，与上一条垂直。这条线短而淡，
 *       因为它是「顺带看一眼」的信息，抢了眼反而干扰</li>
 * </ol>
 * 后两条只有在撞到球时才画；撞库时没有第二颗球，也就没有分离可言
 */
public final class AimRenderer {

    /** 瞄准线线宽 */
    private static final float LINE_WIDTH = 1.6f;

    /** 虚影球描边线宽 */
    private static final float GHOST_STROKE = 1.2f;

    /** 分离线线宽 */
    private static final float SEPARATION_WIDTH = 1.8f;

    /** 虚线的实线段与空白段长度（px） */
    private static final float[] DASH = {9.0f, 7.0f};

    /** 目标球分离线的长度（px） */
    private static final double TARGET_LINE_LEN = 70.0;

    /** 主球切线的长度（px） */
    private static final double CUE_LINE_LEN = 48.0;

    /** 虚影球描边色。比填充亮，让它的轮廓在深色台呢上也看得清 */
    private static final Color GHOST_RING = new Color(0xFF, 0xFF, 0xFF, 0x8A);

    /**
     * @param cueX 主球球心 x
     * @param cueY 主球球心 y
     */
    public void draw(Graphics2D graphics, AimView aim, double cueX, double cueY) {
        if (!aim.isPresent()) {
            return;
        }

        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawAimLine(graphics, aim, cueX, cueY);
        drawGhost(graphics, aim);
        drawSeparations(graphics, aim);

        if (oldAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    /** 绘制主球到首次接触位置的提示线，末端避开虚影球 */
    private void drawAimLine(Graphics2D graphics, AimView aim, double cueX, double cueY) {
        Vec2 end = aim.lineEnd();
        if (end == null) {
            return;
        }

        double x2 = end.x;
        double y2 = end.y;

        if (aim.hasGhost()) {
            // 线画到虚影球的边上就停，别插进球心：虚线扎进球里会让虚影球看起来像被划掉了
            // 两球贴死时虚影球中心几乎就在主球中心上，这条线会退化成一个点，那就不画
            double deltaX = end.x - cueX;
            double deltaY = end.y - cueY;
            double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (length <= Ball.RADIUS) {
                return;
            }
            double sampleIndex = (length - Ball.RADIUS) / length;
            x2 = cueX + deltaX * sampleIndex;
            y2 = cueY + deltaY * sampleIndex;
        }

        // 虚线而不是实线：瞄准线只是提示，实线会像台面上多了一条印刷线
        graphics.setColor(Theme.AIM_LINE);
        graphics.setStroke(new BasicStroke(LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, DASH, 0.0f));
        graphics.draw(new Line2D.Double(cueX, cueY, x2, y2));
    }

    /** 在预计接触位置绘制虚影球，便于观察主球落点 */
    private void drawGhost(Graphics2D graphics, AimView aim) {
        Vec2 ghost = aim.ghost();
        if (ghost == null) {
            return;
        }
        Ellipse2D disk = new Ellipse2D.Double(
                ghost.x - Ball.RADIUS, ghost.y - Ball.RADIUS,
                Ball.RADIUS * 2.0, Ball.RADIUS * 2.0);
        graphics.setColor(Theme.AIM_GHOST);
        graphics.fill(disk);
        graphics.setColor(GHOST_RING);
        graphics.setStroke(new BasicStroke(GHOST_STROKE));
        graphics.draw(disk);
    }

    /** 绘制接触后两球的预计分离方向，几何由瞄准预览提供 */
    private void drawSeparations(Graphics2D graphics, AimView aim) {
        Vec2 ghost = aim.ghost();
        Vec2 targetSep = aim.targetSeparation();
        Vec2 cueSep = aim.cueSeparation();
        if (ghost == null || targetSep == null || cueSep == null) {
            return;
        }

        graphics.setStroke(new BasicStroke(SEPARATION_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // 目标球球心 = 虚影球球心 + 2R × 分离方向——接触时两球心恰好相距 2R，
        // 所以这个位置不必另存一个字段。线从球心画起，靠里的一段会被随后绘制的真球盖住，
        // 看上去就是「从这颗球里射出来」
        Vec2 targetCentre = ghost.plus(targetSep.times(Ball.RADIUS * 2.0));
        graphics.setColor(Theme.AIM_TARGET_LINE);
        graphics.draw(new Line2D.Double(targetCentre.x, targetCentre.y,
                targetCentre.x + targetSep.x * TARGET_LINE_LEN,
                targetCentre.y + targetSep.y * TARGET_LINE_LEN));

        graphics.setColor(Theme.AIM_CUE_LINE);
        graphics.draw(new Line2D.Double(ghost.x, ghost.y,
                ghost.x + cueSep.x * CUE_LINE_LEN,
                ghost.y + cueSep.y * CUE_LINE_LEN));
    }
}

package billiards.render;

import billiards.world.Ball;
import billiards.world.Table;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 把一帧画面按固定顺序拼出来
 *
 * <p>它是渲染包唯一的入口：{@code GamePanel} 只调这个方法，不去碰下面几个渲染器
 * 于是「一帧里什么压着什么」这件事只有一处定义，而不是散在调用方
 *
 * <h2>绘制顺序</h2>
 * <ol>
 *   <li>球台（外框 → 台呢 → 库边 → 袋口 → 标记线）</li>
 *   <li>瞄准线、虚影球、分离线——画在球下面，线从球底下钻出来，比压在球上好看</li>
 *   <li>球杆</li>
 *   <li>所有球的阴影</li>
 *   <li>所有球</li>
 *   <li>HUD（托盘、组徽、当前手、文案、力度条、重新开始）</li>
 *   <li>浮层（待放虚影 → 判定横幅 → 调试浮层 → 终局面板 / 开场提示）</li>
 * </ol>
 * 第 4、5 步分成两趟是刻意的：若逐球「先阴影后球体」，后画的球的阴影会盖在<b>先画的
 * 球</b>身上——球贴着球时能看到一圈脏边。先铺完全部阴影再放球，任何球的阴影都压不到
 * 别的球，代价只是多一趟循环。矢量路径的 {@code BallRenderer.draw} 没法这样分批，
 * 所以那条路（半透明虚影球）里阴影仍然是紧挨着球画的——它一帧最多出现两三个，
 * 且都在空旷处，撞不上这个问题
 *
 * <p>球杆排在第 3 位（在球<b>下面</b>）是权衡后的选择：真实球杆悬在台面上方，按物理
 * 该压在球上，但一根 300px 的木棍横穿整排球会非常吵，而球被杆挡住反而更像「杆在台面上」
 * 玩家看杆是为了读力度，那部分永远在空旷处，两个方案都不影响
 *
 * <p>接收 {@link GameStateView} 而不是球列表，是这个类能被无头自检调用的前提：
 * 造一个快照就能画，不需要先跑物理、不需要开窗
 */
public final class GameRenderer {

    private final TableRenderer tableRenderer = new TableRenderer();
    private final BallRenderer ballRenderer = new BallRenderer();
    private final BallSpriteCache spriteCache = new BallSpriteCache();
    private final AimRenderer aimRenderer = new AimRenderer();
    private final CueRenderer cueRenderer = new CueRenderer();
    private final HudRenderer hudRenderer = new HudRenderer(spriteCache);
    private final OverlayRenderer overlayRenderer = new OverlayRenderer();
    private BufferedImage shadow;

    /** 浏览器显示窗口前准备图像，让首次绘制只执行图像合成 */
    public void prepare(Table table) {
        if (RenderAssets.browserMode()) {
            tableRenderer.prepare(table);
            shadow = RenderAssets.load("shadow");
        }
    }

    /** 供自检核对「sprite 缓存确实建起来了」——它不是渲染路径上的东西，只是个出口 */
    public BallSpriteCache spriteCache() {
        return spriteCache;
    }

    /** 根据传入的模型或只读快照绘制当前图层 */
    public void draw(Graphics2D graphics, Table table, GameStateView view) {
        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object oldRender = graphics.getRenderingHint(RenderingHints.KEY_RENDERING);
        Object oldStroke = graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        Object oldInterp = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 球走位图（见 BallSpriteCache），而窗口缩放会让位图被拉伸。默认的最近邻在这种
        // 拉伸下会把圆边啃成锯齿，双线性至少让边缘保持连续。缩放恰为 1 时它是精确的
        // ——采样点正落在源像素中心，一个像素都不会被改动，所以无头自检那份逐像素比对
        // 的结论在有它之后依然成立
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        tableRenderer.draw(graphics, table);

        if (view.cueBallOnTable()) {
            aimRenderer.draw(graphics, view.aim(), view.cueX(), view.cueY());
            cueRenderer.draw(graphics, view.aim(), view.cueX(), view.cueY(), view.power());
        }

        drawShadows(graphics, view);
        drawBalls(graphics, view);

        hudRenderer.draw(graphics, view);

        // 浮层最后画：它盖在球台、球与 HUD 之上，是唯一「压住一切」的一层
        overlayRenderer.draw(graphics, view);

        restore(graphics, RenderingHints.KEY_ANTIALIASING, oldAA);
        restore(graphics, RenderingHints.KEY_RENDERING, oldRender);
        restore(graphics, RenderingHints.KEY_STROKE_CONTROL, oldStroke);
        restore(graphics, RenderingHints.KEY_INTERPOLATION, oldInterp);
    }

    /** 先绘制全部球影，避免后画球的阴影盖到其他球身上 */
    private void drawShadows(Graphics2D graphics, GameStateView view) {
        if (RenderAssets.browserMode() && shadow == null) {
            shadow = RenderAssets.load("shadow");
        }
        for (int index = 0; index < view.ballCount(); index = index + 1) {
            BallView ballView = view.ball(index);
            if (ballView.onTable()) {
                if (shadow != null) {
                    int left = (int) Math.round(ballView.x()) - shadow.getWidth() / 2;
                    int top = (int) Math.round(ballView.y()) - shadow.getHeight() / 2;
                    graphics.drawImage(shadow, left, top, null);
                } else {
                    ballRenderer.paintShadow(graphics, ballView.x(), ballView.y(), Ball.RADIUS);
                }
            }
        }
    }

    /** 按球号绘制在台球的缓存图案，落袋球不再显示 */
    private void drawBalls(Graphics2D graphics, GameStateView view) {
        for (int index = 0; index < view.ballCount(); index = index + 1) {
            BallView ballView = view.ball(index);
            if (ballView.onTable()) {
                spriteCache.draw(graphics, ballView.id(), ballView.x(), ballView.y());
            }
        }
    }

    /** 还原调用前的绘制设置，避免影响后续图层 */
    private static void restore(Graphics2D graphics, java.awt.RenderingHints.Key key, Object value) {
        if (value != null) {
            graphics.setRenderingHint(key, value);
        }
    }
}

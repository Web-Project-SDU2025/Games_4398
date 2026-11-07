package billiards.render;

import billiards.world.Ball;
import billiards.world.BallCatalog;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 16 颗球的位图缓存。启动时各烘焙一次，之后每帧只是一次 {@code drawImage}
 *
 * <h2>为什么要缓存</h2>
 * 矢量路径画一颗球要铺一次径向渐变、裁一次圆再铺一次线性渐变、画一次号码盘、
 * <b>渲染一次字符串</b>、再铺一次径向高光、最后描边。16 颗球 × 60 帧 = 每秒近千次
 * 文字排版与上千次渐变铺设。球的<em>外观只取决于球号</em>，与位置无关，所以这些工作
 * 每次都是重复计算。<b>烘焙成位图是这个项目里性价比最高的一处优化：把每帧的绘制成本
 * 从「与复杂度成正比」降到「与像素数成正比」</b>
 *
 * <h2>超采样</h2>
 * 位图是在 {@link #SUPERSAMPLE} 倍尺寸上画、再用面积平均缩回来的。3 倍下渲染的号码
 * 笔画有 9 个像素的信息量，缩回来得到的是真正的抗锯齿，而 1 倍下直接画 9 号字只能得到
 * 一个灰色糊团。这是「程序化美术」相对贴图的一个真实优势：<b>想多大就有多大</b>
 *
 * <h2>为什么阴影与描边不进 sprite</h2>
 * 这两样都要和透明度打交道，而「半透明图层先合成、再整体调透明度」与「逐层各自带
 * 透明度」在数学上并不相等：前者是 {@code α·(H over B) + (1−α)·C}，后者是
 * {@code α·H over (α·B over C)}，高光区域会差一截。虚影球要走半透明预览，所以把
 * 它们留在矢量路径里，两种画法各自都是对的。代价只是每帧多 16 次椭圆填充+描边，
 * 那点开销相对省下的文字排版可以忽略
 *
 * <h2>为什么按整数像素贴图</h2>
 * {@code drawImage} 的落点是取整的，所以球心位置会有最多 0.5px 的舍入。球直径 22px，
 * 0.5px 是 2.3%——而且模拟本身是 200Hz、渲染 60Hz，满速时一帧要走 50px，亚像素精度
 * 只在极慢的滚动里才谈得上。要拿回它得每帧做一次仿射重采样，代价是持续的重采样模糊
 * 这里选稳定：<b>宁可位置取整，也不要每帧不同的模糊</b>
 */
public final class BallSpriteCache {

    /** 烘焙时的放大倍数。3 倍足以让号码笔画得到真正的抗锯齿 */
    private static final int SUPERSAMPLE = 3;

    /**
     * sprite 尺寸相对球直径多出的边距（每侧）。描边线宽 1.2px 加抗锯齿外沿约 1.6px，
     * 取 2px 保证不切边
     */
    private static final int PADDING = 2;

    private final BufferedImage[] sprites = new BufferedImage[BallCatalog.BALL_COUNT];
    private final BallRenderer painter = new BallRenderer();
    private final int spriteSize;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public BallSpriteCache() {
        this.spriteSize = (int) Math.ceil(Ball.RADIUS * 2.0) + PADDING * 2;
        for (int ballId = 0; ballId < BallCatalog.BALL_COUNT; ballId = ballId + 1) {
            if (RenderAssets.browserMode()) {
                sprites[ballId] = RenderAssets.load("ball-" + ballId);
            } else {
                sprites[ballId] = bake(ballId);
            }
        }
    }

    /**
     * 把一颗球画进 {@code SUPERSAMPLE} 倍大的离屏位图，再面积平均缩回原尺寸
     *
     * <p>用 {@code SCALE_AREA_AVERAGING} 而不是双线性/双三次：整数倍缩小要的是「每个
     * 目标像素取源区域的面积平均」，那正是超采样的定义。双线性在 3 倍缩小时会只采样
     * 4 个源像素、漏掉大部分信息，等于白超采样
     */
    private BufferedImage bake(int ballId) {
        int maximum = spriteSize * SUPERSAMPLE;
        BufferedImage big = new BufferedImage(maximum, maximum, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = big.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double constantTerm = maximum / 2.0;
            double radius = Ball.RADIUS * SUPERSAMPLE;
            painter.paintBody(graphics, ballId, constantTerm, constantTerm, radius);
            painter.paintOutline(graphics, constantTerm, constantTerm, radius, BallRenderer.OUTLINE_ALPHA_OPAQUE);
        } finally {
            graphics.dispose();
        }

        // getScaledInstance 返回的 Image 可能是惰性的，落进 BufferedImage 才算真的算完，
        // 也才能让自检拿 getRGB 逐像素查
        Image scaled = big.getScaledInstance(spriteSize, spriteSize, Image.SCALE_AREA_AVERAGING);
        BufferedImage out = new BufferedImage(spriteSize, spriteSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D outG = out.createGraphics();
        try {
            outG.drawImage(scaled, 0, 0, null);
        } finally {
            outG.dispose();
        }
        return out;
    }

    /** 一颗球 sprite 的边长（含边距），画的时候按它对中 */
    public int spriteSize() {
        return spriteSize;
    }

    /** 取某号球的 sprite。返回的是缓存内的实例，调用方不得修改 */
    public BufferedImage sprite(int ballId) {
        return sprites[ballId];
    }

    /** 把某号球画到 {@code (cx, cy)} 为球心的位置 */
    public void draw(Graphics2D graphics, int ballId, double centerX, double centerY) {
        int half = spriteSize / 2;
        int x = (int) Math.round(centerX) - half;
        int y = (int) Math.round(centerY) - half;
        graphics.drawImage(sprites[ballId], x, y, null);
    }
}

package billiards.render;

import billiards.world.Layout;
import billiards.world.Pocket;
import billiards.world.RailSegment;
import billiards.world.Table;
import billiards.world.Vec2;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 球台整层的离屏位图：木质外框 → 台呢 → 库边 → 袋口 → 标记线
 *
 * <h2>为什么要烘焙</h2>
 * 这一层里没有任何东西会随帧变化——木框、台呢、库边、袋口、开球线全都只取决于
 * {@link Layout} 里的常量。可它偏偏是画面里面积最大的一层（外框矩形 960×520，
 * 占窗口七成以上）。每帧把五十万个像素重画一遍，其中大部分还是逐像素算出来的
 * 材质（木纹、绒面、晕影），是纯粹的重复劳动
 *
 * <p>烘焙成位图之后，每帧只剩一次 {@code drawImage}。这和 {@link BallSpriteCache}
 * 是同一个套路，区别只是那边烘 16 张小图、这边烘 1 张大图
 *
 * <h2>为什么木框用「距离参数」而不是逐块画</h2>
 * 木框是一个环。若按上/下/左/右四条矩形分别画，四个角上会各留一道接缝；
 * 而按「到外沿的距离」参数化，横跨框宽的那条明暗剖面在整个环上是连续的，倒角与
 * 受光带自然绕着台子走一圈。台呢同理：用归一化的椭圆距离做晕影，比铺一个矩形
 * 渐变要匀
 *
 * <h2>光只有一个方向</h2>
 * 全场光源固定在<b>左上</b>，与 {@link BallRenderer} 的接触阴影同一套假设
 * 木框环上每一处先取它朝外的法线，再与「指向光源」的方向点乘：于是上边与左边的
 * 木料比下边与右边亮，库边鼻子朝上、朝左的那两条也比朝下的那两条亮。这不是为了
 * 好看而随手加的——球台上的球影、球的高光已经假定光从左上来了，木框若是四边一样
 * 亮，看的人说不上来哪里不对，但会觉得「贴上去的」
 *
 * <h2>确定性</h2>
 * 材质噪声走的是一个纯函数（{@code hash(x, y)} 整数散列 + 值噪声），<b>不用
 * {@code Random}、不读时钟</b>。这一点是硬要求，不是风格偏好：自检里有一条
 * 「空快照与全落袋快照画出同一张图」的<b>整图逐像素相等</b>断言，任何逐帧漂移的
 * 噪声都会让它变红。噪声也不是「随便加点随机」——它也由自检钉住：台呢区域必须
 * 有几百种以上颜色、亮度标准差不为零，且中心必须比靠库边处亮（防止把晕影写反）
 *
 * <h2>画的顺序就是物理结构</h2>
 * 库边<b>逐段</b>画——因为库边在物理上就是按袋口缺口切开的 6 段，逐段画出来的
 * 缝隙正是球能穿过的真实缺口。最后才盖袋口圆。于是「画出来的和模拟的是同一个
 * 几何」，不会出现「看着有库边、实际能穿过去」这种只在简单实现里才有的错位
 */
public final class TableTexture {

    // 噪声种子。互不相同的任意常数，改了就等于换一张木纹/绒面

    /** 木框细纹种子，改变数值会改变固定纹理 */
    private static final int SEED_GRAIN = 0x51ED;
    /** 木框弯曲纹路种子 */
    private static final int SEED_WARP = 0x2C1B;
    /** 台呢绒毛纹理种子 */
    private static final int SEED_NAP = 0x7A43;
    /** 台呢织纹种子 */
    private static final int SEED_BRUSH = 0x19D7;
    /** 台呢缓慢色差纹理种子 */
    private static final int SEED_MOTTLE = 0x3E91;

    /** 指向光源的方向（左上）。与 {@link BallRenderer} 的阴影方向是同一个假设 */
    private static final double LIGHT_X = -0.70710678;
    /** 指向光源的竖直单位分量，与水平光向共同定义照明 */
    private static final double LIGHT_Y = -0.70710678;

    /**
     * 木框横截面的明暗剖面，从外沿（索引 0）到贴着台呢处（索引 5）等距采样
     *
     * <p>它就是一条「线脚」的横截面：最外一圈收暗边（木料转下去，受不到光），
     * 然后起坡、到 0.36 处是倒角受光的那条棱（唯一大于 1 的一档，往
     * {@link Theme#FRAME_HIGHLIGHT} 偏色），再落回本色，靠近库边处有一道沟，
     * 紧贴台呢的地方再压暗一点当作环境遮蔽
     */
    private static final double[] WOOD_PROFILE = {0.58, 0.90, 1.14, 1.00, 0.86, 0.68};

    /**
     * 库边横截面的明暗剖面，索引 0 在鼻尖（贴着球），索引 5 在贴着木框那一侧
     *
     * <p>它就是一条库边的横截面：鼻尖那条棱受光最亮 → 稍稍退一步上台面（
     * <b>一段平台</b>，索引 1–4 之间只降一点点，这就是「顶面」）→ 到贴着木框处塌进
     * 一道<b>暗沟</b>。没有这段平台，剖面就只是一条从亮到暗的斜坡，读起来是「一块斜着
     * 的绿布」而不是「一块有厚度的橡胶」——这正是「库边的高度其实没太做出来」
     *
     * <p>暗沟落在 u = 1（贴着木框）而不是更里面，有两个原因：真实球台的库边布就是在
     * 那儿收进木框的；而且它把最暗的一像素放在木框边上，视觉上正好把两者缝合起来
     */
    private static final double[] CUSHION_PROFILE = {1.16, 1.02, 0.96, 0.93, 0.88, 0.40};

    /**
     * 库边端头往袋口方向多铺这么多像素，让它的切口藏到袋口皮革环底下
     *
     * <p>这个数不是拍脑袋的：角袋袋心在 {@code (52.929, 52.929)}、洞口半径 36，沿
     * {@code y = 60} 只伸到 {@code x ≈ 88.2}；而库边段从
     * {@code PLAYFIELD_LEFT + CUSHION_GAP_HALF_CORNER = 97} 才开始。<b>中间那 9px 是木框</b>
     * ——实测在 {@code y = 50} 上 {@code x = 89..97} 全是木色、{@code x = 98} 才是绿的
     * 补 10px 之后库边铺到 {@code x = 87}，皮革环（半径 {@code r + POCKET_RIM_WIDTH} = 39）盖到 {@code x ≈ 91.3}，
     * 两者重叠，缝就没了。角上真正没被库边铺到的那块（{@code x ∈ [60,87]} 且
     * {@code y ∈ [42,60]}）整块都在皮革环的 39px 内，看不见
     *
     * <p>库边的物理段落<b>一个字没改</b>——只多画一点到袋口底下。球能穿过去的缺口仍然
     * 就是 {@link Table} 里那几个 {@code CUSHION_GAP_*}
     */
    private static final double CUSHION_JAW_EXTEND = 10.0;

    /**
     * 库边投在台呢上的落影：能照到的深度（px）与强度
     *
     * <p>光在左上，所以上库与左库把影投进台面，下库与右库的影投到台外。<b>这才是「高度」
     * 的来源</b>：加它之前，四条库边内侧的台呢是一样暗的（一圈对称的环境遮蔽 + 晕影），
     * 于是库里外没有任何方向线索，看起来就是画上去的一条绿带。实测加之前上侧 67.80 对
     * 下侧 67.34（上侧反而更亮），差值 0.46，而一条真正的落影给的是十几个亮度
     *
     * <p>强度 0.42 与 0.72 之间的差别，是「有方向」与「有深度」的差别。0.42 只做到了前者：
     * 影确实偏在受光的那两条边上，但它压下去的幅度（紧邻库边的台呢只暗到 77.6）比
     * {@link BallRenderer} 给一颗球的落影（实测暗 43）还浅——一颗球比一条库边矮得多
     * 落影太浅，眼睛就不觉得挡光的东西<b>高</b>，只当那块台呢本来就暗一点
     *
     * <p>0.72 是反推来的，不是试出来的：紧邻库边那一带（台呢边上第 1～7 像素）里，落影
     * 项的平均权重是 0.820、接触项是 0.357，两项叠加后该处亮度 = 88.6 - 39.0×强度
     * 要让「比开阔处（91.6）暗 28 以上」，强度需 ≥ 0.645；要让影再深一点、真正读成影子
     * 而不是接缝，取 0.72（对应该处约 59）。上限则由库边顶面（约 74）压着——影再深就会
     * 把库边自己的顶面吞掉，那和没有影一样读不出高度
     */
    private static final double CUSHION_SHADOW_REACH = 16.0;
    /** 库边投影强度，范围为零到一 */
    private static final double CUSHION_SHADOW_STRENGTH = 0.72;

    /** 四条库边内侧都有的接触暗角：深度与强度。它是对称的，只负责「接缝不干净」 */
    private static final double CONTACT_REACH = 6.0;
    /** 台呢与库边接触暗角强度，范围为零到一 */
    private static final double CONTACT_STRENGTH = 0.26;

    private final BufferedImage image;

    /** 台呢的绒面噪声，单位是<b>色阶</b>（不是百分比） */
    private static double clothNoise(double logicalX, double logicalY) {
        double nap = TextureMath.fbm(logicalX * 0.55, logicalY * 0.55, 2, SEED_NAP) - 0.5;
        double brush = TextureMath.valueNoise(logicalX * 0.90, logicalY * 0.06, SEED_BRUSH) - 0.5;
        double mottle = TextureMath.fbm(logicalX * 0.012, logicalY * 0.012, 2, SEED_MOTTLE) - 0.5;
        return nap * 13.0 + brush * 7.0 + mottle * 7.0;
    }

    /** 剖面覆盖的总宽度：木框带 + 库边带。库边缺口处露出来的木料也在这条剖面里 */
    private static double woodSpan() {
        return Layout.FRAME_BAND + Layout.CUSHION_BAND;
    }

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    private TableTexture(BufferedImage image) {
        this.image = image;
    }

    /**
     * 烘一张球台底图。几何全部取自 {@code table}，尺寸取自 {@link Layout} 的外框矩形
     *
     * <p>位图覆盖外框矩形那一片，但<b>四角要按球台真实的轮廓挖掉</b>：木框的外轮廓不是
     * 一个方正矩形，四个角让给了角袋（见 {@link #cornerCutouts}）。挖掉的那四块是透明的，
     * 露出窗口底色——这正是真实球台俯视图的样子
     */
    public static TableTexture bake(Table table) {
        int width = (int) Math.round(Layout.outerRight() - Layout.outerLeft());
        int height = (int) Math.round(Layout.outerBottom() - Layout.outerTop());

        // 木纹与绒面是逐像素算的，直接写进一个 int[] 再一次性交给位图
        // 逐像素 setRGB 每次都要走一遍颜色模型，五十万次会明显拖慢首次烘焙
        int[] pixels = new int[width * height];
        fillWood(pixels, width, height);
        fillCloth(pixels, width, height);
        fillCushions(pixels, width, height, table);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 唯一的坐标变换就是这一句：之后所有绘制都能照用逻辑坐标，
            // 与渲染器里其它地方的写法一致，不必到处减 outerLeft
            graphics.translate(-Layout.outerLeft(), -Layout.outerTop());

            paintClothEdge(graphics);
            paintCushionNoses(graphics, table);
            paintPockets(graphics, table);
            paintTableMarks(graphics);

            // 最后一步：把木框的四角按角袋收成圆角——真实球台的俯视图不是个方正矩形，
            // 角上那块让给了袋口
            //
            // 放在最后、并且用「挖」而不是让每个 fill 各自避开：逐像素的
            // fillWood/fillCloth 是按整块矩形铺的，它们无从知道角上有个圆角
            //
            // 用 AlphaComposite.Clear 而不是 DstIn：Java2D 只合成图形<b>覆盖到</b>的那些
            // 像素，DstIn 填一个「中间有洞」的形状时，没被覆盖到的像素压根不会被触碰——
            // 而偏偏要挖掉的就是那几块。Clear 直接把这四处抹成透明，正合本意；它也照样
            // 按覆盖率抗锯齿（实测边缘像素 alpha 是 11 这样的中间值），圆角是软的
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fill(cornerCutouts(table));
        } finally {
            graphics.dispose();
        }
        return new TableTexture(image);
    }

    /** 浏览器使用预生成的完整底图，保持原始尺寸与透明边缘 */
    public static TableTexture loadPrepared() {
        return new TableTexture(RenderAssets.load("table"));
    }

    /** 木框四角要被挖掉的那四块，拼成一个形状 */
    private static Shape cornerCutouts(Table table) {
        double rad = Layout.cornerRadius();
        Area cut = new Area();
        for (Pocket pocket : table.pockets()) {
            if (pocket.mouthRadius() != Layout.POCKET_MOUTH_RADIUS_CORNER) {
                continue;   // 中袋在长库正中，离外框边线远得很，不参与收角
            }
            double centerX = pocket.center().x;
            double centerY = pocket.center().y;
            double x0 = Layout.outerRight() - rad;
            if (centerX < Layout.PLAYFIELD_LEFT) {
                x0 = Layout.outerLeft();
            }
            double y0 = Layout.outerBottom() - rad;
            if (centerY < Layout.PLAYFIELD_TOP) {
                y0 = Layout.outerTop();
            }

            Area crescent = new Area(new Rectangle2D.Double(x0, y0, rad, rad));
            crescent.subtract(new Area(
                    new Ellipse2D.Double(centerX - rad, centerY - rad, rad * 2, rad * 2)));
            cut.add(crescent);
        }
        return cut;
    }

    /** 烘焙结果。返回的是缓存内的实例，调用方不得修改 */
    public BufferedImage image() {
        return image;
    }

    /** 把整层贴到逻辑坐标 {@code (outerLeft, outerTop)} 处 */
    public void draw(Graphics2D graphics) {
        graphics.drawImage(image,
                (int) Math.round(Layout.outerLeft()),
                (int) Math.round(Layout.outerTop()),
                null);
    }

    // 逐像素：木框

    /** 逐像素计算木框纹理与受光，四边共用距离参数以避免角落接缝 */
    private static void fillWood(int[] pixels, int width, int height) {
        double left = Layout.outerLeft();
        double outerTop = Layout.outerTop();
        double outerRight = Layout.outerRight();
        double outerBottom = Layout.outerBottom();
        double span = woodSpan();

        Color dark = Theme.darken(Theme.FRAME_WOOD, 0.38f);
        Color light = Theme.brighten(Theme.FRAME_WOOD, 0.18f);
        int darkRed = dark.getRed();
        int darkGreen = dark.getGreen();
        int darkBlue = dark.getBlue();
        int lightRed = light.getRed();
        int lightGreen = light.getGreen();
        int lightBlue = light.getBlue();
        int highlightRed = Theme.FRAME_HIGHLIGHT.getRed();
        int highlightGreen = Theme.FRAME_HIGHLIGHT.getGreen();
        int highlightBlue = Theme.FRAME_HIGHLIGHT.getBlue();

        // 台面区域整块会被台呢盖掉，不必浪费木纹的计算
        int skipX0 = (int) Math.round(Layout.PLAYFIELD_LEFT - left);
        int skipX1 = (int) Math.round(Layout.PLAYFIELD_RIGHT - left);
        int skipY0 = (int) Math.round(Layout.PLAYFIELD_TOP - outerTop);
        int skipY1 = (int) Math.round(Layout.PLAYFIELD_BOTTOM - outerTop);

        for (int pixelY = 0; pixelY < height; pixelY = pixelY + 1) {
            if (pixelY >= skipY0 && pixelY < skipY1) {
                int row = pixelY * width;
                java.util.Arrays.fill(pixels, row + skipX0, row + skipX1, TextureMath.pack(darkRed, darkGreen, darkBlue));
            }
            double logicalY = outerTop + pixelY + 0.5;
            for (int pixelX = 0; pixelX < width; pixelX = pixelX + 1) {
                if (pixelY >= skipY0 && pixelY < skipY1 && pixelX >= skipX0 && pixelX < skipX1) {
                    continue;
                }
                double logicalX = left + pixelX + 0.5;

                double distanceLeft = logicalX - left;
                double distanceRight = outerRight - logicalX;
                double distanceTop = logicalY - outerTop;
                double distanceBottom = outerBottom - logicalY;
                double dOut = Math.min(Math.min(distanceLeft, distanceRight), Math.min(distanceTop, distanceBottom));

                // 朝外的法线：从「哪几条边离得最近」直接读出来。角上会有两条同时取到，
                // 归一化之后就是那条对角方向，于是光在四个角上是平滑过渡的
                double normalX = 0;
                double normalY = 0;
                if (distanceLeft <= dOut + 1e-9) {
                    normalX = normalX - 1;
                }
                if (distanceRight <= dOut + 1e-9) {
                    normalX = normalX + 1;
                }
                if (distanceTop <= dOut + 1e-9) {
                    normalY = normalY - 1;
                }
                if (distanceBottom <= dOut + 1e-9) {
                    normalY = normalY + 1;
                }
                double normalLength = Math.sqrt(normalX * normalX + normalY * normalY);
                double lit = (normalX * LIGHT_X + normalY * LIGHT_Y) / normalLength;

                // 木纹沿木料长度方向走：上下两条横着，左右两条竖着。两条在这条
                // 对角线上换向——也就是真实画框的拼角（miter），不是缺陷
                boolean horizontal = distanceTop <= dOut + 1e-9 || distanceBottom <= dOut + 1e-9;
                double along = logicalY;
                double across = logicalX;
                if (horizontal) {
                    along = logicalX;
                    across = logicalY;
                }

                double grain = woodGrain(along, across);

                double red = darkRed + (lightRed - darkRed) * grain;
                double green = darkGreen + (lightGreen - darkGreen) * grain;
                double blue = darkBlue + (lightBlue - darkBlue) * grain;

                double profileValue = TextureMath.sampleProfile(WOOD_PROFILE, dOut / span);
                if (profileValue > 1.0) {
                    double highlightAmount = Math.min(1.0, (profileValue - 1.0) / 0.16) * 0.55;
                    red = red + (highlightRed - red) * highlightAmount;
                    green = green + (highlightGreen - green) * highlightAmount;
                    blue = blue + (highlightBlue - blue) * highlightAmount;
                }
                double tone = Math.min(profileValue, 1.0) * (1.0 + 0.16 * lit);

                pixels[pixelY * width + pixelX] = TextureMath.pack(red * tone, green * tone, blue * tone);
            }
        }
    }

    // 逐像素：台呢

    /** 逐像素填充台呢纹理、晕影和库边落影，纹理不随帧变化 */
    private static void fillCloth(int[] pixels, int width, int height) {
        double outerLeft = Layout.outerLeft();
        double outerTop = Layout.outerTop();
        double left = Layout.PLAYFIELD_LEFT;
        double top = Layout.PLAYFIELD_TOP;
        double right = Layout.PLAYFIELD_RIGHT;
        double bottom = Layout.PLAYFIELD_BOTTOM;
        double halfWidth = Layout.playfieldWidth() * 0.5;
        double halfHeight = Layout.playfieldHeight() * 0.5;
        double centerX = left + halfWidth;
        double centerY = top + halfHeight;

        // 两种暗角，两种暗色。必须分开：接触暗角四条边对称、只把接缝压下去一点，浅色就够；
        // 落影有方向、要读成「有东西挡住了光」，得深得多。原来两者共用 darken(CLOTH, 0.30)
        // 一个颜色，落影的深度就被焊死在这个下限上——实测紧邻库边的台呢最深只到 77.6，
        // 比一颗球的落影（暗 43）还浅。见 CUSHION_SHADOW_STRENGTH
        Color seamTint = Theme.darken(Theme.CLOTH, 0.30f);
        Color castTint = Theme.darken(Theme.CLOTH, 0.55f);
        int lightRed = Theme.CLOTH_LIGHT.getRed();
        int lightGreen = Theme.CLOTH_LIGHT.getGreen();
        int lightBlue = Theme.CLOTH_LIGHT.getBlue();
        int cR = Theme.CLOTH.getRed();
        int cG = Theme.CLOTH.getGreen();
        int cB = Theme.CLOTH.getBlue();
        int shadowRed = seamTint.getRed();
        int shadowGreen = seamTint.getGreen();
        int shadowBlue = seamTint.getBlue();
        int castRed = castTint.getRed();
        int castGreen = castTint.getGreen();
        int castBlue = castTint.getBlue();

        int x0 = (int) Math.round(left - outerLeft);
        int x1 = (int) Math.round(right - outerLeft);
        int y0 = (int) Math.round(top - outerTop);
        int y1 = (int) Math.round(bottom - outerTop);

        for (int pixelY = y0; pixelY < y1; pixelY = pixelY + 1) {
            double logicalY = outerTop + pixelY + 0.5;
            double normalY = (logicalY - centerY) / halfHeight;
            double dTop = logicalY - top;
            double dBottom = bottom - logicalY;
            // 到最近一条台呢边的像素距离（四条边一起取最小值），供接触暗角用
            double dNearestY = Math.min(dTop, dBottom);
            for (int pixelX = x0; pixelX < x1; pixelX = pixelX + 1) {
                double logicalX = outerLeft + pixelX + 0.5;
                double normalX = (logicalX - centerX) / halfWidth;

                // 晕影：正中最亮，往四边走越接近本色。用椭圆距离，横竖都不偏心
                double rad = Math.sqrt(normalX * normalX + normalY * normalY);
                double t01 = TextureMath.clamp(Math.pow(rad, 1.6), 0.0, 1.0);

                // 库边的落影：<b>只从上库与左库投进台面</b>。这两项是整块台呢上唯一
                // 「有方向」的暗度——它们才是库边有高度的证据（见 CUSHION_SHADOW_*）
                double dLeft = logicalX - left;
                double dRight = right - logicalX;
                double cast = (1.0 - TextureMath.smoothstep(TextureMath.clamp(dTop / CUSHION_SHADOW_REACH, 0.0, 1.0)))
                        + (1.0 - TextureMath.smoothstep(TextureMath.clamp(dLeft / CUSHION_SHADOW_REACH, 0.0, 1.0)));
                // 接触暗角：四条边都有，对称，只负责让台呢与库边的接缝「陷下去」
                double contact = 1.0 - TextureMath.smoothstep(TextureMath.clamp(
                        Math.min(dNearestY, Math.min(dLeft, dRight)) / CONTACT_REACH, 0.0, 1.0));
                double seamAmt = Math.min(1.0, contact * CONTACT_STRENGTH);
                double castAmt = Math.min(1.0, cast * CUSHION_SHADOW_STRENGTH);

                double red = lightRed + (cR - lightRed) * t01;
                double green = lightGreen + (cG - lightGreen) * t01;
                double blue = lightBlue + (cB - lightBlue) * t01;
                // 先压对称的接触暗角，再压有方向的落影：顺序不影响结果（两次都是朝暗处收敛），
                // 但按「接缝 → 影」写，读起来就是这两层暗角各自的来路
                red = red + (shadowRed - red) * seamAmt;
                green = green + (shadowGreen - green) * seamAmt;
                blue = blue + (shadowBlue - blue) * seamAmt;
                red = red + (castRed - red) * castAmt;
                green = green + (castGreen - green) * castAmt;
                blue = blue + (castBlue - blue) * castAmt;

                double distance = clothNoise(logicalX, logicalY);
                pixels[pixelY * width + pixelX] = TextureMath.pack(red + distance, green + distance, blue + distance);
            }
        }
    }

    // 图形层：台呢边、库边、袋口、标记

    /** 沿台呢边界绘制过渡阴影，突出库边与台面的高度差 */
    private static void paintClothEdge(Graphics2D graphics) {
        double left = Layout.PLAYFIELD_LEFT;
        double top = Layout.PLAYFIELD_TOP;
        double width = Layout.playfieldWidth();
        double height = Layout.playfieldHeight();

        // 库边内沿的一圈暗边，把台呢和库边分开
        graphics.setColor(Theme.withAlpha(Theme.darken(Theme.CLOTH, 0.55f), 180));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.draw(new java.awt.geom.Rectangle2D.Double(left, top, width, height));
    }

    /** 逐段铺库边。库边在物理上就是按袋口缺口切开的 6 段，逐段铺出来的缝隙正是球 能穿过的真实缺口——不是画上去的黑块，而是真的没有 */
    private static void fillCushions(int[] pixels, int width, int height, Table table) {
        double left = Layout.outerLeft();
        double outerTop = Layout.outerTop();
        double band = Layout.CUSHION_BAND;

        int baseR = Theme.CUSHION.getRed();
        int baseG = Theme.CUSHION.getGreen();
        int baseB = Theme.CUSHION.getBlue();

        for (RailSegment rail : table.rails()) {
            Vec2 segmentStart = rail.a();
            Vec2 segmentEnd = rail.b();
            Vec2 inward = rail.inwardNormal();
            Vec2 outward = inward.times(-1.0);

            // 库边自身的方向性明暗：朝上的那条库边背光、朝下的那条受光。幅度从 0.14 收到
            // 0.10——它和木框那一圈用的是同一个点乘，方向线索木框已经给足了，而它每收窄
            // 一点，受光的库边（也就是真正投出影、最需要被看清的那两条）就白捡回一点亮度
            double direction = 1.0 + 0.10 * (inward.x * LIGHT_X + inward.y * LIGHT_Y);

            // 端头往袋口方向各延长一段（见 CUSHION_JAW_EXTEND）：外接矩形跟着延长，
            // 于是铺出来的库边把切口塞到袋口的皮革环底下，不再露木框
            // 延长沿切向量走，而切向量与法向正交，所以下面的 across 一个字不用改
            Vec2 tangent = rail.tangent();
            Vec2 aExt = segmentStart.minus(tangent.times(CUSHION_JAW_EXTEND));
            Vec2 bExt = segmentEnd.plus(tangent.times(CUSHION_JAW_EXTEND));

            double x0 = Math.min(aExt.x, bExt.x) + Math.min(0.0, outward.x * band);
            double x1 = Math.max(aExt.x, bExt.x) + Math.max(0.0, outward.x * band);
            double y0 = Math.min(aExt.y, bExt.y) + Math.min(0.0, outward.y * band);
            double y1 = Math.max(aExt.y, bExt.y) + Math.max(0.0, outward.y * band);

            int ix0 = Math.max(0, (int) Math.ceil(x0 - left + 1e-6));
            int ix1 = Math.min(width, (int) Math.floor(x1 - left - 1e-6));
            int iy0 = Math.max(0, (int) Math.ceil(y0 - outerTop + 1e-6));
            int iy1 = Math.min(height, (int) Math.floor(y1 - outerTop - 1e-6));

            for (int pixelY = iy0; pixelY < iy1; pixelY = pixelY + 1) {
                double logicalY = outerTop + pixelY + 0.5;
                for (int pixelX = ix0; pixelX < ix1; pixelX = pixelX + 1) {
                    double logicalX = left + pixelX + 0.5;

                    // 跨带位置：0 在鼻子（贴着球），1 在后背
                    double across = (logicalX - segmentStart.x) * inward.x + (logicalY - segmentStart.y) * inward.y;
                    // 截面剖面（不是「亮到暗的一条坡」）：鼻尖的棱 → 顶面平台 → 贴着
                    // 木框处的暗沟。见 CUSHION_PROFILE
                    double profileValue = TextureMath.sampleProfile(CUSHION_PROFILE, -across / band) * direction;

                    double distance = clothNoise(logicalX, logicalY);
                    pixels[pixelY * width + pixelX] = TextureMath.pack(baseR * profileValue + distance, baseG * profileValue + distance, baseB * profileValue + distance);
                }
            }
        }
    }

    /**
     * 每条库边面向球的那条棱加一条亮线，做出立体感
     *
     * <p>亮线与库边本体一起延长同样的距离（见 {@link #CUSHION_JAW_EXTEND}）：只延长本体
     * 不延长亮线，袋口附近那 10px 就是一条没有棱的绿边，反而比原来更怪
     */
    private static void paintCushionNoses(Graphics2D graphics, Table table) {
        graphics.setColor(Theme.CUSHION_EDGE);
        graphics.setStroke(new BasicStroke(2.0f));
        for (RailSegment rail : table.rails()) {
            Vec2 tangent = rail.tangent();
            Vec2 segmentStart = rail.a().minus(tangent.times(CUSHION_JAW_EXTEND));
            Vec2 segmentEnd = rail.b().plus(tangent.times(CUSHION_JAW_EXTEND));
            graphics.draw(new Line2D.Double(segmentStart.x, segmentStart.y, segmentEnd.x, segmentEnd.y));
        }
    }

    /** 袋口。三层：台呢上的落影 → 皮革环 → 洞（含内壁的受光与背光） */
    private static void paintPockets(Graphics2D graphics, Table table) {
        for (Pocket pocket : table.pockets()) {
            Vec2 center = pocket.center();
            double radius = pocket.mouthRadius();

            paintPocketShadow(graphics, center, radius);
            paintPocketRim(graphics, center, radius);
            paintPocketInterior(graphics, center, radius);
        }
    }

    /** 绘制开球线与置球点，位置来自统一布局配置 */
    private static void paintTableMarks(Graphics2D graphics) {
        // 开球线
        graphics.setColor(Theme.TABLE_MARK);
        graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, new float[]{7.0f, 7.0f}, 0.0f));
        graphics.draw(new Line2D.Double(
                Layout.HEAD_STRING_X, Layout.PLAYFIELD_TOP,
                Layout.HEAD_STRING_X, Layout.PLAYFIELD_BOTTOM));

        // 置球点
        double radius = 3.0;
        graphics.setColor(Theme.TABLE_MARK);
        graphics.fill(new Ellipse2D.Double(
                Layout.FOOT_SPOT_X - radius, Layout.FOOT_SPOT_Y - radius, radius * 2, radius * 2));
    }

    // 确定性噪声与小工具

    /** 计算沿木料方向的细纹和粗纹，避免每段单独生成导致接缝 */
    private static double woodGrain(double along, double across) {
        double warp = TextureMath.valueNoise(along * 0.010, across * 0.10, SEED_WARP) - 0.5;
        double fine = TextureMath.fbm(along * 0.018, across * 0.42, 3, SEED_GRAIN);
        double coarse = TextureMath.fbm((along + warp * 46.0) * 0.055, across * 0.30, 2, SEED_GRAIN + 7);
        return TextureMath.clamp(0.58 * fine + 0.42 * coarse, 0.0, 1.0);
    }

    /** 绘制袋口外侧落影，先画阴影以免覆盖皮革边 */
    private static void paintPocketShadow(Graphics2D graphics, Vec2 center, double radius) {
            // ---- ① 袋口投在台呢上的落影 ----
            // 先画，压在皮革环底下。没有它，袋口是一枚「放」在台面上的硬币；有了它，
            // 袋口才是嵌进台面里的一个坑。内圈那条硬边（0.72 的渐变起点）藏在皮革环
            // 底下（0.72 × (r+9) < r+3），所以不必为它做软过渡
            double shadowRadius = radius + 9.0;
            double shadowX = center.x + radius * 0.22;
            double shadowY = center.y + radius * 0.26;
            graphics.setPaint(new RadialGradientPaint(
                    new Point2D.Double(shadowX, shadowY), (float) shadowRadius,
                    new float[]{0.72f, 1.0f},
                    new Color[]{new Color(0, 0, 0, 88), new Color(0, 0, 0, 0)}));
            graphics.fill(new Ellipse2D.Double(shadowX - shadowRadius, shadowY - shadowRadius, shadowRadius * 2, shadowRadius * 2));

    }

    /** 按统一环宽绘制袋口皮革，保持外观和木框尺寸对应 */
    private static void paintPocketRim(Graphics2D graphics, Vec2 center, double radius) {
            // ---- ② 外圈：袋口的皮革 / 金属边 ----
            //
            // 只有 0.889–1.0 这一段（半径 r 到 r + POCKET_RIM_WIDTH）露得出来——里面
            // 那截被洞口盖住。所以渐变的「实色段」必须一直撑到 0.96，只在最后一像素
            // 渐隐到透明：曾经把渐隐写成从 0.86 开始，恰好把能看见的那几像素全放进
            // 淡出区，结果皮革环整个消失，袋口看起来是「悬在布上的一枚圆片」
            //
            // 环宽取自 Layout：木框的宽度是靠这个外圈反解出来的（见 Layout.FRAME_BAND），
            // 写死成字面量就会出现「环悄悄变厚、于是又顶出木框」这种改一处忘一处的事
            double rim = radius + Layout.POCKET_RIM_WIDTH;
            graphics.setPaint(new RadialGradientPaint(
                    new Point2D.Double(center.x, center.y), (float) rim,
                    new float[]{0.0f, 0.80f, 0.96f, 1.0f},
                    new Color[]{Theme.darken(Theme.FRAME_WOOD, 0.30f),
                            Theme.darken(Theme.FRAME_WOOD, 0.40f),
                            Theme.darken(Theme.FRAME_WOOD, 0.66f),
                            new Color(0, 0, 0, 0)}));
            graphics.fill(new Ellipse2D.Double(center.x - rim, center.y - rim, rim * 2, rim * 2));

    }

    /** 把洞壁明暗裁在袋口内，使光斑不会溢到皮革边 */
    private static void paintPocketInterior(Graphics2D graphics, Vec2 center, double radius) {
            // ---- ③ 洞本身 ----
            // 正中看不到底，靠洞沿的内壁受一点光——所以是个径向渐变
            graphics.setPaint(new RadialGradientPaint(
                    new Point2D.Double(center.x, center.y), (float) radius,
                    new float[]{0.0f, 0.62f, 1.0f},
                    new Color[]{new Color(0x02, 0x03, 0x04), Theme.POCKET,
                            new Color(0x23, 0x27, 0x2C)}));
            Shape hole = new Ellipse2D.Double(center.x - radius, center.y - radius, radius * 2, radius * 2);
            graphics.fill(hole);

            // 内壁的明暗。裁进洞里画，于是它只管「洞壁上的光」，不会溢到皮革环上
            Shape oldClip = graphics.getClip();
            graphics.clip(hole);

            // 背光的左上内壁：一块偏心的软黑斑（光从左上斜射进来，暗部不在正中）
            double darkX = center.x - radius * 0.30;
            double darkY = center.y - radius * 0.34;
            double darkR = radius * 1.05;
            graphics.setPaint(new RadialGradientPaint(
                    new Point2D.Double(darkX, darkY), (float) darkR,
                    new float[]{0.0f, 1.0f},
                    new Color[]{new Color(0, 0, 0, 190), new Color(0, 0, 0, 0)}));
            graphics.fill(new Ellipse2D.Double(darkX - darkR, darkY - darkR, darkR * 2, darkR * 2));

            // 受光的右下内壁：一层很淡的冷灰，而且<b>贴着洞沿</b>
            //
            // 位置是这条的关键。第一版把斑心放在 0.68r 处，于是洞的正中偏右下多了一块
            // 亮调——那正好是球体高光的位置，整个洞立刻读成「一颗灰球卡在角上」
            // 洞壁受光的部分在<b>远侧洞沿</b>：斑心挪到 0.85r、半径收到 0.5r，它就只润
            // 湿最外一圈，正中仍是黑的。刻意做成「斑」而不是「弧」——一条弧一出来就又
            // 是描边（见本方法的说明）
            double litX = center.x + radius * 0.58;
            double litY = center.y + radius * 0.62;
            double litR = radius * 0.50;
            graphics.setPaint(new RadialGradientPaint(
                    new Point2D.Double(litX, litY), (float) litR,
                    new float[]{0.0f, 1.0f},
                    new Color[]{new Color(0x46, 0x4E, 0x57, 55),
                            new Color(0x46, 0x4E, 0x57, 0)}));
            graphics.fill(new Ellipse2D.Double(litX - litR, litY - litR, litR * 2, litR * 2));

            graphics.setClip(oldClip);
    }
}

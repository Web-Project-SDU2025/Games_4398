package billiards.selfcheck;

import billiards.render.AimView;
import billiards.render.BallRenderer;
import billiards.render.BallSpriteCache;
import billiards.render.BallView;
import billiards.render.GameRenderer;
import billiards.render.GameStateView;
import billiards.render.TableTexture;
import billiards.render.Theme;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Layout;
import billiards.world.Pocket;
import billiards.world.RailSegment;
import billiards.world.Table;
import billiards.world.Vec2;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * 渲染检查：在无头环境下把整张球台画进内存位图，然后断言「确实画了东西」
 *
 * <p>这类检查抓的是一整类静默失败——渲染器抛异常被吞掉、颜色配成和背景一样、
 * 变换没恢复导致后面全画到屏幕外。这些在肉眼下都表现为「窗口是黑的」，
 * 但在无头位图里就是「非背景像素数为 0」，可以硬断言
 *
 * <p>画的是<b>生产的 {@link GameRenderer}</b>，喂给它一份 {@link GameStateView}——
 * 也就是说这条检查跑的就是游戏里那一整条绘制路径（含 sprite 缓存、HUD、瞄准线），
 * 而不是另写一段「差不多」的渲染代码。旧的版本自己 new 几个渲染器逐个调，
 * 那样即使 {@code GameRenderer} 的绘制顺序写错了也查不出来
 */
public final class RenderingChecks {

    /** 整张图里至少应有这么大比例的非背景像素。台面占窗口面积的 40% 以上 */
    private static final double MIN_COVERAGE = 0.25;

    /** 一张 sprite 至少要有这么多不透明像素，才算真的画出球了 */
    private static final int MIN_OPAQUE_PIXELS = 100;

    /** 一张 sprite 至少要有这么多不同颜色，才算不是纯色块（渐变 / 号码 / 高光） */
    private static final int MIN_DISTINCT_COLORS = 40;

    /**
     * 台呢与木框的「不是平涂」阈值
     *
     * <p>这几个数不是拍的：加材质之前，台呢内部随便框一块都是<b>恰好一种颜色、
     * 亮度标准差 0.0</b>，木框整圈<b>只有 4 种颜色</b>（底色 + 一圈内沿亮边 + 抗锯齿
     * 的两个过渡色）。阈值取在两者之间，于是它同时是一条回归线：谁把材质改回平涂，
     * 它当场变红
     *
     * <p>颜色数取 150 而不是贴着实测值（约 290）取：这条检查要抓的是「平涂回来了」，
     * 那是三个数量级的落差；贴着当前值取只会让以后每调一次材质都得改常数
     */
    private static final int MIN_CLOTH_COLORS = 150;
    private static final double MIN_CLOTH_LUMA_SD = 1.5;
    private static final int MIN_WOOD_COLORS = 60;

    /**
     * 球的接触阴影：球下方必须至少有这么多格明显暗于台呢，而且必须渐隐
     *
     * <p>这两个数是<b>量出来的</b>。加材质那一版（M9）的阴影椭圆只有 {@code 1.9r × 1.75r}，
     * 比球本身（直径 {@code 2r}）还小，整块被球盖住，实测只剩 {@code y = 球心 + r} 这
     * <b>一个</b>像素是暗的——看起来就是描边粗了一点点，不是影子。所以这里同时钉住
     * 「有几个像素」与「是不是渐隐」：单像素的硬边会同时违反这两条
     */
    private static final double SHADOW_MIN_DROP = 10.0;
    private static final int SHADOW_MIN_PIXELS = 3;
    private static final int SHADOW_PROBE_ROWS = 5;

    /**
     * 袋口内壁沿半径方向的亮度跳变上限
     *
     * <p>M9 版在洞口里叠了两条 {@code Arc2D} 描边（一条黑 alpha150 / 3.2px，一条
     * {@code #3A4047} alpha120 / 1.8px）。它们画在一个已经很暗的径向渐变<b>上面</b>，
     * 于是变成两条悬空的灰弧——实测半径方向单像素跳变约 20 个亮度，而一条连续的径向
     * 明暗只有 1–3。阈值取在两者之间
     */
    private static final double POCKET_MAX_STEP = 8.0;

    /** 库边落影的方向性：朝光那两条库边内侧的台呢必须比背光那两条内侧暗这么多 */
    private static final double CUSHION_CAST_MIN_DROP = 3.0;

    /**
     * 库边顶面相对「紧挨着它的台呢」至少要亮这么多
     *
     * <p>这条量的是<b>明暗顺序</b>，不是某个绝对亮度：库边是台面上<b>抬起来的一条棱</b>，
     * 它的顶面受光、旁边那道缝背光，所以顶面必须比缝亮。反过来（顶面比缝暗）在眼睛里
     * 就是一道<b>凹槽</b>——而凹槽正是「库边的高度做不出来」的成因：一块比它脚下还暗的
     * 表面，无论它的内部渐变做得多讲究，都只会读成「台面上铺了一条暗色带」
     *
     * <p>阈值 10 与实测值（修复后 14.83）之间留了约三分之一的余量。留余量是有意的：
     * 它要抓的是「机制被改回去」（改回去会是<b>负二十几</b>，仍然一抓一个准），而不是
     * 「数值抖了零点几」——判据一抖，人就会去调阈值而不是去看画面
     */
    private static final double CUSHION_TOP_MIN_LIFT = 10.0;

    /**
     * 贴着库边那道缝的台呢相对开阔台呢至少要暗这么多
     *
     * <p>平视角度为零（正俯视）时，一块抬起来的东西<b>唯一</b>的高度线索就是它投在脚下
     * 的影，所以这道影必须真的够深。参照物取同一条竖线往里 30px 的开阔台呢：30 &gt; 落影的
     * 作用半径（{@link TableTexture} 的 {@code CUSHION_SHADOW_REACH} = 16），所以那一带里
     * <b>一点落影都没有</b>，是干净的对照组
     *
     * <p>两个采样带之间晕影本身贡献约 6 个亮度（台呢本来就越靠边越暗）——这个 6 不是估的：
     * 拿背光的下库量同样的两条带，那边没有落影，差值就是晕影加接触暗角，约 5～7
     * 阈值取 22（实测修复后 30.56），同样留了约四分之一的余量，理由见上一条
     */
    private static final double CUSHION_SHADOW_MIN_DEPTH = 22.0;

    /** 工具类只提供静态方法，不需要创建对象 */
    private RenderingChecks() {
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    public static void run(CheckHarness checks) {
        checks.check("图形环境为无头模式（自检不应开窗）",
                java.awt.GraphicsEnvironment.isHeadless());

        GameRenderer renderer = new GameRenderer();
        Table table = new Table();

        AimView aim = CheckImages.aimAlongPositiveX(table);
        BufferedImage image = CheckImages.render(renderer, table,
                CheckImages.rackView(aim, 0.0, false, "按住左键往后拖定力度，拖时也能转方向"));
        checks.checkNotNull("整台渲染产出位图", image);

        int nonBackground = CheckImages.nonBackgroundCount(image);
        double coverage = nonBackground / (double) (image.getWidth() * image.getHeight());
        checks.check("整台渲染覆盖率达到阈值",
                coverage >= MIN_COVERAGE,
                String.format("非背景像素占比 %.1f%%，阈值 %.1f%%", coverage * 100, MIN_COVERAGE * 100));

        // 台呢中心必须被涂成台呢色——画布尺寸/坐标搞错时这条会先炸
        int background = Theme.WINDOW_BG.getRGB();
        int clothCenter = image.getRGB(
                (int) (Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5),
                (int) (Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.5));
        checks.check("台面中心着色为台呢色或球色（非背景）", clothCenter != background);

        checkSpriteCache(checks, renderer);
        checkVectorPath(checks);
        checkTableMaterials(checks, table);
        checkCornerPocketSilhouette(checks, table);
        checkLightingAndJoints(checks, renderer, table);
        checkEmptyViewRenders(checks, renderer, table);

        // 满力那一帧与待命帧只差一个力度，正好用来在报告里并排展示「球杆确实后拉了」
        BufferedImage pulled = CheckImages.render(renderer, table,
                CheckImages.rackView(aim, 1.0, false, "按住左键往后拖定力度，拖时也能转方向"));
        // 斜切那一帧与正撞帧对照：正撞时两条分离线跟瞄准线重合，看不出来；斜切才看得见 90°
        BufferedImage cut = CheckImages.render(renderer, table,
                CheckImages.rackView(CheckImages.aimCutShot(table), 0.0, false,
                        "按住左键往后拖定力度，拖时也能转方向"));
        dumpScreenshotIfRequested(image, pulled, cut);
    }

    /**
     * 设了 {@code -Dbilliards.screenshot=<路径>} 就把无头渲染结果写成 PNG
     *
     * <p>这是给实验报告插图用的：不需要真的开窗、不需要截图工具，命令行就能批量产出
     * 可复现的界面图。默认不写文件，所以也不影响自检的纯粹性
     *
     * <p>写三张：待命的一帧、满力拉杆的一帧（{@code -pull}）、斜切的一帧（{@code -cut}）
     * 球杆的后拉幅度与两条分离线的方向都只能靠看——前者要两张并排才比得出来，
     * 后者在正撞的那两张里根本看不出来（三条线重合）。一个开关出三张，报告插图就齐了
     */
    private static void dumpScreenshotIfRequested(BufferedImage idle, BufferedImage pulled,
                                                  BufferedImage cut) {
        String path = System.getProperty("billiards.screenshot");
        if (path == null || path.isBlank()) {
            return;
        }
        writePng(path, idle);
        writePng(sibling(path, "-pull"), pulled);
        writePng(sibling(path, "-cut"), cut);
    }

    /** 给一个文件名加上后缀，扩展名保持不动（{@code a/b.png} → {@code a/b-suffix.png}） */
    static String sibling(String path, String suffix) {
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (dot < 0 || dot < slash) {
            return path + suffix;
        }
        return path.substring(0, dot) + suffix + path.substring(dot);
    }

    /**
     * 把一帧写成 PNG。{@code UIChecks} 导出 M6 的 HUD / 浮层那些帧时也走这里
     *
     * <p>只留一份实现是有意的：截图路径与目录创建若各写一份，迟早会出现「某一族截图
     * 悄悄不在那个目录里」。导出失败只警告不抛——报告插图是副产品，不该让自检变红
     */
    static void writePng(String path, BufferedImage image) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            javax.imageio.ImageIO.write(image, "png", file);
            System.out.println("  [img]  已导出界面截图: " + file.getAbsolutePath());
        } catch (Exception error) {
            System.err.println("  [warn] 截图导出失败: " + error);
        }
    }

    /**
     * sprite 缓存检查
     *
     * <p>台上的球走的是位图路径，所以「球画出来了吗」这个问题实际问的是
     * 「烘焙出来的位图里有东西吗」。另外还要确认 16 张图<b>互不相同</b>——
     * 全部烘成同一张（比如循环里用了错的球号）在肉眼下是「16 颗球长得一样」，
     * 在数值上就是指纹全部相同
     */
    private static void checkSpriteCache(CheckHarness checks, GameRenderer renderer) {
        BallSpriteCache cache = renderer.spriteCache();
        checks.checkNotNull("GameRenderer 持有 sprite 缓存", cache);
        if (cache == null) {
            return;
        }

        int expectedSize = (int) Math.ceil(Ball.RADIUS * 2.0) + 4;
        checks.check("sprite 边长 = 球直径 + 两侧描边余量",
                cache.spriteSize() == expectedSize,
                "spriteSize = " + cache.spriteSize() + "，期望 " + expectedSize);

        int opaqueOk = 0;
        int coloredOk = 0;
        Set<Long> fingerprints = new HashSet<>();
        for (int ballId = 0; ballId < BallCatalog.BALL_COUNT; ballId = ballId + 1) {
            BufferedImage sprite = cache.sprite(ballId);
            if (sprite == null) {
                continue;
            }
            fingerprints.add(CheckImages.fingerprint(sprite));

            int opaque = 0;
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < sprite.getHeight(); y = y + 1) {
                for (int x = 0; x < sprite.getWidth(); x = x + 1) {
                    int argb = sprite.getRGB(x, y);
                    if (((argb >>> 24) & 0xFF) > 200) {
                        opaque = opaque + 1;
                        colors.add(argb);
                    }
                }
            }
            if (opaque > MIN_OPAQUE_PIXELS) {
                opaqueOk = opaqueOk + 1;
            }
            if (colors.size() > MIN_DISTINCT_COLORS) {
                coloredOk = coloredOk + 1;
            }
        }

        checks.check("16 颗球的 sprite 都有足量不透明像素",
                opaqueOk == BallCatalog.BALL_COUNT,
                "仅 " + opaqueOk + " / " + BallCatalog.BALL_COUNT);
        checks.check("16 颗球的 sprite 都不是纯色块（有渐变 / 号码 / 高光）",
                coloredOk == BallCatalog.BALL_COUNT,
                "仅 " + coloredOk + " / " + BallCatalog.BALL_COUNT);
        checks.check("16 张 sprite 两两不同（没有把同一颗球烘 16 遍）",
                fingerprints.size() == BallCatalog.BALL_COUNT,
                "只有 " + fingerprints.size() + " 种不同的图");
    }

    /**
     * 矢量路径（{@code BallRenderer.drawById}）也要能画出球
     *
     * <p>台上的球走位图，这条路留给半透明预览（虚影球）与 HUD 落袋托盘
     * 它现在还不是主路径，但正因为不是主路径，坏了更不容易被发现——所以单独钉一条
     */
    private static void checkVectorPath(CheckHarness checks) {
        BallRenderer renderer = new BallRenderer();
        int cell = (int) Math.ceil(Ball.RADIUS * 2) + 4;

        int drawn = 0;
        for (int ballId = 0; ballId < BallCatalog.BALL_COUNT; ballId = ballId + 1) {
            BufferedImage image = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.drawById(graphics, ballId, new Vec2(cell / 2.0, cell / 2.0), 1.0f);
            } finally {
                graphics.dispose();
            }
            int opaque = 0;
            for (int y = 0; y < cell; y = y + 1) {
                for (int x = 0; x < cell; x = x + 1) {
                    if (((image.getRGB(x, y) >>> 24) & 0xFF) > 200) {
                        opaque = opaque + 1;
                    }
                }
            }
            if (opaque > MIN_OPAQUE_PIXELS) {
                drawn = drawn + 1;
            }
        }
        checks.check("矢量路径（虚影球 / 落袋托盘）也能画出全部 16 颗球",
                drawn == BallCatalog.BALL_COUNT,
                "仅 " + drawn + " / " + BallCatalog.BALL_COUNT);
    }

    /**
     * 台呢与木框有没有材质，以及底图是不是可复现
     *
     * <h2>为什么这几条必须存在</h2>
     * 「画面平不平」本来是只能靠看的事，而这一族检查之所以能自动化，靠的是把
     * 「平」翻译成一个可测量的性质：<b>一片区域里的颜色数与亮度标准差</b>
     * 平涂的台呢是「7200 个像素、1 种颜色、标准差恰好 0.0」，而任何真实的绒面、
     * 晕影或木纹都会让这两个数离开零。差值型断言（「两帧必须不同」）看不见这件事——
     * 它只回答「变了没有」，不回答「画得像不像一块布」
     *
     * <h2>为什么还要钉住晕影的方向</h2>
     * 「有几百种颜色」一条噪声场也能满足。再钉一条「中心比靠边处亮」，才能把
     * 「有明暗」与「明暗是朝对的方向走的」分开——否则把晕影写反了（四角变亮）照样全绿
     *
     * <h2>为什么确定性要单独钉</h2>
     * 木纹与绒面都是逐像素算出来的噪声。一旦它沾上时间或随机种子，整图逐像素相等的
     * 那条断言（「空快照与全落袋快照画出同一张图」）会开始随机变红，而那种红很难查
     * 与其等它去别处炸，不如在这里直接问：烘两次，是不是同一张图
     */
    private static void checkTableMaterials(CheckHarness checks, Table table) {
        TableTexture texture = TableTexture.bake(table);
        BufferedImage image = texture.image();

        int expectedW = (int) Math.round(Layout.outerRight() - Layout.outerLeft());
        int expectedH = (int) Math.round(Layout.outerBottom() - Layout.outerTop());
        checks.check("球台底图尺寸 = 外框矩形",
                image.getWidth() == expectedW && image.getHeight() == expectedH,
                image.getWidth() + "×" + image.getHeight() + "，期望 " + expectedW + "×" + expectedH);

        // 台呢：取台面中部偏下一块，四边都离库边至少一成台宽，避开边线也避开球位
        RegionStats cloth = statsLogical(image,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.30,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.72,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.70,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.95);
        checks.check("台呢不是平涂（有绒面与晕影）",
                cloth.distinct >= MIN_CLOTH_COLORS && cloth.lumaSd >= MIN_CLOTH_LUMA_SD,
                String.format("%d 种颜色（阈值 %d），亮度标准差 %.2f（阈值 %.2f）",
                        cloth.distinct, MIN_CLOTH_COLORS, cloth.lumaSd, MIN_CLOTH_LUMA_SD));

        // 晕影方向：正中心一小块 vs 左上角靠近库边的一小块的**平均亮度**
        RegionStats centre = statsLogical(image,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.47,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.43,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.53,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.57);
        RegionStats corner = statsLogical(image,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.02,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.05,
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.09,
                Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.19);
        checks.check("台呢中心比靠库边处亮（晕影方向正确）",
                centre.meanLuma > corner.meanLuma + 0.5,
                String.format("中心 %.2f，靠边 %.2f", centre.meanLuma, corner.meanLuma));

        // 木框：取上边框正中一段。避开两个角袋与上中袋（它们会盖住这段木头）
        RegionStats wood = statsLogical(image,
                Layout.outerLeft() + (Layout.outerRight() - Layout.outerLeft()) * 0.21,
                Layout.outerTop() + 4,
                Layout.outerLeft() + (Layout.outerRight() - Layout.outerLeft()) * 0.42,
                Layout.outerTop() + Layout.FRAME_BAND - 2);
        checks.check("木框不是平涂（有木纹与倒角）",
                wood.distinct >= MIN_WOOD_COLORS,
                wood.distinct + " 种颜色（阈值 " + MIN_WOOD_COLORS + "）");

        // 可复现：再烘一张，必须逐像素相同
        BufferedImage again = TableTexture.bake(new Table()).image();
        int rebakeDiff = CheckImages.diffCount(image, again);
        checks.check("球台底图可复现（两次烘焙逐像素相同）",
                rebakeDiff == 0, "两次烘焙有 " + rebakeDiff + " 个像素不同");
    }

    /**
     * 角袋不能被贴图边界切掉，木框的四角要顺着袋口收成圆角
     *
     * <h2>用户是怎么发现这件事的</h2>
     * 「四个底袋都被截掉了一部分，很奇怪」——贴图是一块矩形位图，而角袋的袋心在台面
     * 角点<b>之外</b>（沿对角线偏 {@link Layout#POCKET_CENTER_OFFSET}），洞口又必须
     * 等于捕获半径（不能画小，否则球会在还没碰到洞沿时被吸进去）。于是袋口的外圈是否
     * 捅出外框边线，完全由 {@link Layout#FRAME_BAND} 决定：取 22 时每个角袋向外捅出
     * 6.07px，被位图边界切出一道笔直的弦——看上去像台面被削掉一个角
     *
     * <h2>为什么这两条能拍成断言</h2>
     * 切没切是「像素还在不在」：沿洞口与皮革环采样一圈，每个采样点都必须落在位图内、
     * 而且是不透明的。凸不凸也能落到数上：沿对角线从袋心往外走，圆角弧<b>外</b>侧那点
     * 必须透明，弧<b>内</b>侧那点必须不透明——一句话就钉住了轮廓的位置
     * 两条都只看合成结果，不关心它是由 {@code Area}、裁剪还是逐像素做出来的
     */
    private static void checkCornerPocketSilhouette(CheckHarness checks, Table table) {
        BufferedImage image = TableTexture.bake(table).image();

        // ① 洞口 + 皮革环整圈都还在位图里，且不透明
        //    采样半径取到外圈（洞口 + 皮革环），因为被切掉的正是外面那一圈；
        //    只看洞口的话，6.07px 的越界里还有 3.07px 切在洞口上，会漏掉一半
        int samples = 360;
        String bad = null;
        for (Pocket pocket : table.pockets()) {
            if (pocket.mouthRadius() != Layout.POCKET_MOUTH_RADIUS_CORNER) {
                continue;   // 中袋离外框边线远，本来就切不到
            }
            double rad = pocket.mouthRadius() + Layout.POCKET_RIM_WIDTH;
            for (int index = 0; index < samples && bad == null; index = index + 1) {
                double th = 2.0 * Math.PI * index / samples;
                double lx = pocket.center().x + rad * Math.cos(th);
                double ly = pocket.center().y + rad * Math.sin(th);
                int pixelX = (int) Math.round(lx - Layout.outerLeft());
                int pixelY = (int) Math.round(ly - Layout.outerTop());
                if (pixelX < 0 || pixelY < 0 || pixelX >= image.getWidth() || pixelY >= image.getHeight()) {
                    bad = String.format("袋%d 外圈在 (%.2f,%.2f) 处落到贴图外", pocket.index(), lx, ly);
                } else if ((image.getRGB(pixelX, pixelY) >>> 24) == 0) {
                    bad = String.format("袋%d 外圈在 (%.2f,%.2f) 处是透明的（被切掉）",
                            pocket.index(), lx, ly);
                }
            }
        }
        checks.check("角袋外圈整圈都在贴图内且不透明（没被边界切）",
                bad == null, describeProblem(bad));

        // ② 四角的轮廓：沿对角线从袋心往外，圆角弧外侧透明、内侧不透明
        //    两点各自离弧 2px，中间就是那条弧——轮廓挪到别处这条立刻红
        double rad = Layout.cornerRadius();
        double probe = 2.0;
        String shape = null;
        for (Pocket pocketValue : table.pockets()) {
            if (pocketValue.mouthRadius() != Layout.POCKET_MOUTH_RADIUS_CORNER) {
                continue;
            }
            // 沿「袋心 → 台外那个角」的对角线往外量（角袋的袋心在台面角点之外）
            double ux = 1.0 / Math.sqrt(2.0);
            double uy = 1.0 / Math.sqrt(2.0);
            if (pocketValue.center().x < Layout.PLAYFIELD_LEFT) {
                ux = -ux;
            }
            if (pocketValue.center().y < Layout.PLAYFIELD_TOP) {
                uy = -uy;
            }

            if (alphaAtLogical(image, pocketValue.center().x + (rad + probe) * ux,
                    pocketValue.center().y + (rad + probe) * uy) != 0) {
                shape = String.format("袋%d 圆角弧外侧（对角 %+.1fpx 处）还不是透明的，角没有收圆",
                        pocketValue.index(), rad + probe);
            } else if (alphaAtLogical(image, pocketValue.center().x + (rad - probe) * ux,
                    pocketValue.center().y + (rad - probe) * uy) == 0) {
                shape = String.format("袋%d 圆角弧内侧（对角 %+.1fpx 处）被挖穿了，木头挖多了",
                        pocketValue.index(), rad - probe);
            }
            if (shape != null) {
                break;
            }
        }
        checks.check("木框四角按角袋外圈收成圆角（弧外透明、弧内不透明）",
                shape == null, describeProblem(shape));
    }

    /** 空问题描述转换为空串，非空描述保持原样 */
    private static String describeProblem(String problem) {
        if (problem == null) {
            return "";
        }
        return problem;
    }

    /**
     * 光有没有落到「小物件」上：球的接触阴影、袋口内壁、库边与袋口的接缝、库边的落影
     *
     * <h2>为什么这四条要单独钉</h2>
     * 它们和 {@link #checkTableMaterials} 是同一个病的两种症状。那边是<b>大面上没有光</b>，
     * 这边是<b>小物件上的光做错了</b>：球的阴影因为一个「直径当半径」的算术错整块藏在球
     * 背后（只剩 1px）、袋口里的两条描边成了悬空的弧、库边与袋口之间露着 9px 木框、
     * 而库边在台呢上一个影都不投（所以它没有厚度）
     *
     * <p>四条都能落到数上，所以四条都能自动化：影子的<b>像素个数与渐隐方向</b>、洞内的
     * <b>径向跳变</b>、接缝处的<b>色相</b>（木色 R&gt;G，台呢与库边 G&gt;R）、落影的
     * <b>左右不对称</b>。这些在肉眼下分别是「球像贴纸」「洞里两道灰痕」「库里嵌着一块木头」
     * 「库边是画上去的一条绿带」——四种都只能靠看发现，但不等于不能拍成断言
     */
    private static void checkLightingAndJoints(CheckHarness checks, GameRenderer renderer, Table table) {
        checkBallShadow(checks, renderer, table);
        checkPocketInterior(checks, table);
        checkCushionMeetsPocket(checks, table);
        checkCushionCastShadow(checks, table);
        checkCushionRelief(checks, table);
    }

    /**
     * 库边必须读成<b>抬起来的一条棱</b>，而不是台面上的一道暗槽
     *
     * <p>这一条是「库边的高度做不出来」的直接量化。它比 {@link #checkCushionCastShadow}
     * 严格一层：那条只要求「上侧比下侧暗」——一个<b>方向</b>，只要差值超过 3 个亮度就绿，
     * 于是一条又浅又对称的暗角也能蒙过去。这条要求的是<b>次序与深度</b>：
     *
     * <ol>
     *   <li>库边<b>顶面</b>要比紧挨着它的台呢亮（↑ 棱，不是 ↓ 槽）；</li>
     *   <li>紧挨着库边那道缝要比开阔台呢明显暗（影得像影）</li>
     * </ol>
     *
     * <p>两条都是「相对量」，不写死绝对亮度，所以换主题色不会误伤；反过来说，它们唯一
     * 认的就是库边与其脚下台呢的<b>大小关系</b>——而那正是眼睛判断「这里是高是低」用的东西
     */
    private static void checkCushionRelief(CheckHarness checks, Table table) {
        BufferedImage image = TableTexture.bake(table).image();
        double width = Layout.playfieldWidth();

        // 取中段：避开两个中袋（袋心在长库正中、半径 24）与四个角袋，也避开 x = 280 的开球线
        double x0 = Layout.PLAYFIELD_LEFT + width * 0.28;
        double x1 = Layout.PLAYFIELD_LEFT + width * 0.42;

        // 库边顶面：库边带宽 18，取中间那一段（避开鼻尖的亮棱与贴木框的暗沟）
        double topSurface = meanLumaLogical(image, x0, Layout.PLAYFIELD_TOP - 14,
                x1, Layout.PLAYFIELD_TOP - 4);
        // 紧挨着库边那道缝：库边内侧 1..7px
        double beside = meanLumaLogical(image, x0, Layout.PLAYFIELD_TOP + 1,
                x1, Layout.PLAYFIELD_TOP + 7);
        // 开阔台呢：同一条竖线再往里 30..36px
        double open = meanLumaLogical(image, x0, Layout.PLAYFIELD_TOP + 30,
                x1, Layout.PLAYFIELD_TOP + 36);

        checks.check("库边顶面比紧挨着它的台呢亮（库边是抬起的棱，不是一道凹槽）",
                topSurface > beside + CUSHION_TOP_MIN_LIFT,
                String.format("库边顶面 %.2f，紧邻台呢 %.2f（要求至少亮 %.1f）",
                        topSurface, beside, CUSHION_TOP_MIN_LIFT));
        checks.check("库边的落影够深（贴着库边的台呢比开阔处明显暗）",
                beside < open - CUSHION_SHADOW_MIN_DEPTH,
                String.format("紧邻台呢 %.2f，开阔台呢 %.2f（要求至少暗 %.1f）",
                        beside, open, CUSHION_SHADOW_MIN_DEPTH));
    }

    /**
     * 球的接触阴影：球的正下方必须有一段<b>连续多像素</b>、且<b>渐隐</b>的暗带
     *
     * <p>夹具是一颗孤零零的球摆在台面正中——台上没有任何别的东西会在这条采样线上留下
     * 颜色，所以「暗了」只可能是阴影。参照色取同一行离球 90px 处的台呢，于是这条检查
     * 量的不是绝对亮度（那会随主题变），而是<b>物体有没有在自己的落影里</b>
     */
    private static void checkBallShadow(CheckHarness checks, GameRenderer renderer, Table table) {
        double bx = Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5;
        double by = Layout.PLAYFIELD_TOP + Layout.playfieldHeight() * 0.5;
        BufferedImage image = CheckImages.render(renderer, table, new GameStateView(
                new BallView[]{new BallView(1, bx, by, true)}, AimView.NONE, 0.0, false, ""));

        double reference = lumaAt(image, bx - 90, by);
        int radius = (int) Math.ceil(Ball.RADIUS);

        int dark = 0;
        double nearest = 0;
        double farthest = 0;
        for (int distance = 1; distance <= SHADOW_PROBE_ROWS; distance = distance + 1) {
            double left = lumaAt(image, bx, by + radius + distance);
            if (left < reference - SHADOW_MIN_DROP) {
                dark = dark + 1;
            }
            if (distance == 1) {
                nearest = left;
            }
            if (distance == SHADOW_PROBE_ROWS) {
                farthest = left;
            }
        }
        checks.check("球的正下方有一段连续的接触阴影（不是一条边）",
                dark >= SHADOW_MIN_PIXELS,
                String.format("球心下方 %d 行里只有 %d 行暗于台呢 %.2f（阈值 %d 行、压暗 %.1f）",
                        SHADOW_PROBE_ROWS, dark, reference, SHADOW_MIN_PIXELS, SHADOW_MIN_DROP));
        checks.check("接触阴影是渐隐的（贴球处比远处暗）",
                nearest < farthest - 2.0,
                String.format("离球 1px 处 %.2f，%dpx 处 %.2f", nearest, SHADOW_PROBE_ROWS, farthest));
    }

    /**
     * 袋口内壁必须是<b>连续</b>的明暗，不能出现悬空的描边弧
     *
     * <p>判法是沿半径逐像素走一圈：真实的洞壁明暗是渐变的（每像素变化 1–3 个亮度），
     * 而任何描边都会在它的两条边界上留下一次十几到二十个亮度的跳变。只看「最亮与最暗
     * 差多少」是不够的——那种写法同样会被一条渐变的亮带骗过去
     */
    private static void checkPocketInterior(CheckHarness checks, Table table) {
        BufferedImage image = TableTexture.bake(table).image();
        double worst = 0;
        String where = "";
        int probes = 0;

        for (Pocket pocket : table.pockets()) {
            double rMax = pocket.mouthRadius() - 2.0;
            for (int ai = 0; ai < 72; ai = ai + 1) {
                double ang = ai * Math.PI / 36.0;
                double previous = Double.NaN;
                for (double rad = 2.0; rad <= rMax; rad = rad + (1.0)) {
                    double left = lumaAtLogical(image,
                            pocket.center().x + Math.cos(ang) * rad,
                            pocket.center().y + Math.sin(ang) * rad);
                    if (Double.isNaN(left)) {
                        previous = Double.NaN;   // 走出位图（角袋被外框切掉的弦），这一段不判
                        continue;
                    }
                    if (!Double.isNaN(previous)) {
                        double step = Math.abs(left - previous);
                        probes = probes + 1;
                        if (step > worst) {
                            worst = step;
                            where = String.format("袋 %d、%.0f°、半径 %.0f",
                                    pocket.index(), Math.toDegrees(ang), rad);
                        }
                    }
                    previous = left;
                }
            }
        }

        // 防「检查退化」：采样点若因为坐标算错而全军覆没，上面那个 worst 会停在 0 而全绿
        checks.check("袋口内壁采样点足够（检查没有退化）", probes > 1000, "只采到 " + probes + " 个点");
        checks.check("袋口内壁是连续的明暗（没有悬空的描边弧）",
                worst < POCKET_MAX_STEP,
                String.format("半径方向最大跳变 %.1f 个亮度（阈值 %.1f），出现在 %s",
                        worst, POCKET_MAX_STEP, where));
    }

    /**
     * 库边必须在袋口处<b>接上</b>，中间不许露木框
     *
     * <p>这条检查的由来是一个算得出来的缝：角袋袋心在 {@code (52.929, 52.929)}、洞口半径 36，
     * 沿 {@code y = 50} 只伸到 {@code x ≈ 88.8}；而库边段从
     * {@code PLAYFIELD_LEFT + CUSHION_GAP_HALF_CORNER = 97} 才开始。中间那 9px 是木框
     * 中袋同病，左右各约 3px
     *
     * <p>判据用<b>色相</b>而不是颜色值：木料是暖色（R &gt; G），台呢、库边与袋口都是
     * 冷色或中性（G ≥ R）。这条判据不随主题色变，只随「这像素属于哪种材料」变
     *
     * <p>袋口自己那一圈皮革环是木色的，所以走进 {@code 洞口半径 + 3} 以内就不再判——
     * 那里本来就该是皮革。反过来也有一条防退化断言：采样数太少说明「全被袋口吃掉了」，
     * 那时这条检查什么都没测
     */
    private static void checkCushionMeetsPocket(CheckHarness checks, Table table) {
        BufferedImage image = TableTexture.bake(table).image();
        int probes = 0;
        int wood = 0;
        String firstWood = "";

        for (RailSegment rail : table.rails()) {
            Vec2 segmentStart = rail.a();
            Vec2 segmentEnd = rail.b();
            Vec2 outward = rail.inwardNormal().times(-1.0);
            for (int end = 0; end < 2; end = end + 1) {
                Vec2 position = segmentStart;
                Vec2 otherEndpoint = segmentEnd;
                if (end != 0) {
                    position = segmentEnd;
                    otherEndpoint = segmentStart;
                }
                double length = Math.hypot(position.x - otherEndpoint.x, position.y - otherEndpoint.y);
                if (length == 0.0) {
                    continue;
                }
                double ux = (position.x - otherEndpoint.x) / length;   // 从段内指向段外，也就是朝袋口
                double uy = (position.y - otherEndpoint.y) / length;

                for (double out = 1.0; out <= 12.0; out = out + (1.0)) {
                    for (double depth = 0.0; depth < Layout.CUSHION_BAND - 2.0; depth = depth + (4.0)) {
                        double lx = position.x + ux * out + outward.x * depth;
                        double ly = position.y + uy * out + outward.y * depth;
                        if (coveredByPocket(table, lx, ly)) {
                            continue;
                        }
                        double left = lumaAtLogical(image, lx, ly);
                        if (Double.isNaN(left)) {
                            continue;
                        }
                        probes = probes + 1;
                        if (isWoodColor(image, lx, ly)) {
                            wood = wood + 1;
                            if (firstWood.isEmpty()) {
                                firstWood = String.format("(%.0f, %.0f)", lx, ly);
                            }
                        }
                    }
                }
            }
        }

        checks.check("库边端头的采样点足够（检查没有退化）", probes >= 60, "只采到 " + probes + " 个点");
        checks.check("库边在袋口处接上了（端头到袋口之间不露木框）",
                wood == 0, "有 " + wood + " / " + probes + " 个像素是木色，首个在 " + firstWood);
    }

    /**
     * 库边必须在台呢上留下<b>有方向</b>的落影——这是「高度」的来源
     *
     * <p>光在左上，所以上库与左库把影投到台面内侧，下库与右库的影投到台外。四条库里
     * 只有两条会在台面上留影，于是同一批球台下，<b>贴着上库的一条台呢必须比贴着下库的
     * 那条暗</b>
     *
     * <p>加这条之前，四条库边内侧的台呢是一样暗的——那是一圈对称的暗角（vignette），
     * 不是落影。它让人说不上来哪里不对，但库边就是一条画上去的绿带，没有厚度
     * 对称的暗角过不了这条检查：它给出的差值恰好是 0
     */
    private static void checkCushionCastShadow(CheckHarness checks, Table table) {
        BufferedImage image = TableTexture.bake(table).image();
        double width = Layout.playfieldWidth();
        double hgt = Layout.playfieldHeight();

        // 取中段，避开两个中袋与四个角袋在台面上占的那一片
        double sx0 = Layout.PLAYFIELD_LEFT + width * 0.30;
        double sx1 = Layout.PLAYFIELD_LEFT + width * 0.48;
        double sy0 = Layout.PLAYFIELD_TOP + hgt * 0.30;
        double sy1 = Layout.PLAYFIELD_TOP + hgt * 0.48;
        double band = 6.0;

        double top = meanLumaLogical(image, sx0, Layout.PLAYFIELD_TOP + 1,
                sx1, Layout.PLAYFIELD_TOP + 1 + band);
        double bottom = meanLumaLogical(image, sx0, Layout.PLAYFIELD_BOTTOM - 1 - band,
                sx1, Layout.PLAYFIELD_BOTTOM - 1);
        double left = meanLumaLogical(image, Layout.PLAYFIELD_LEFT + 1, sy0,
                Layout.PLAYFIELD_LEFT + 1 + band, sy1);
        double right = meanLumaLogical(image, Layout.PLAYFIELD_RIGHT - 1 - band, sy0,
                Layout.PLAYFIELD_RIGHT - 1, sy1);

        checks.check("上库把影投到了台呢上（上侧比下侧暗）",
                top < bottom - CUSHION_CAST_MIN_DROP,
                String.format("上侧 %.2f，下侧 %.2f（要求至少差 %.1f）", top, bottom,
                        CUSHION_CAST_MIN_DROP));
        checks.check("左库把影投到了台呢上（左侧比右侧暗）",
                left < right - CUSHION_CAST_MIN_DROP,
                String.format("左侧 %.2f，右侧 %.2f（要求至少差 %.1f）", left, right,
                        CUSHION_CAST_MIN_DROP));
    }

    // ---- 采样小工具 ----

    /** 窗口位图上一点的亮度，越界返回 0 */
    private static double lumaAt(BufferedImage image, double x, double y) {
        int pixelX = (int) Math.round(x);
        int pixelY = (int) Math.round(y);
        if (pixelX < 0 || pixelY < 0 || pixelX >= image.getWidth() || pixelY >= image.getHeight()) {
            return 0;
        }
        return lumaOf(image.getRGB(pixelX, pixelY));
    }

    /** 逻辑坐标 → 球台底图上的亮度（底图原点在外框左上角，所以减掉 outerLeft/Top）；越界返回 NaN */
    private static double lumaAtLogical(BufferedImage image, double lx, double ly) {
        int pixelX = (int) Math.round(lx - Layout.outerLeft());
        int pixelY = (int) Math.round(ly - Layout.outerTop());
        if (pixelX < 0 || pixelY < 0 || pixelX >= image.getWidth() || pixelY >= image.getHeight()) {
            return Double.NaN;
        }
        return lumaOf(image.getRGB(pixelX, pixelY));
    }

    /** 这块像素是不是木料。木料偏暖（红多于绿），台呢/库边/袋口都不满足这条 */
    private static boolean isWoodColor(BufferedImage image, double lx, double ly) {
        int pixelX = (int) Math.round(lx - Layout.outerLeft());
        int pixelY = (int) Math.round(ly - Layout.outerTop());
        if (pixelX < 0 || pixelY < 0 || pixelX >= image.getWidth() || pixelY >= image.getHeight()) {
            return false;
        }
        int argb = image.getRGB(pixelX, pixelY);
        return ((argb >> 16) & 0xFF) > ((argb >> 8) & 0xFF) + 6;
    }

    /** 逻辑坐标处的 alpha（0 = 完全透明）。越界算 0：位图之外本来就是没有东西 */
    private static int alphaAtLogical(BufferedImage image, double lx, double ly) {
        int pixelX = (int) Math.round(lx - Layout.outerLeft());
        int pixelY = (int) Math.round(ly - Layout.outerTop());
        if (pixelX < 0 || pixelY < 0 || pixelX >= image.getWidth() || pixelY >= image.getHeight()) {
            return 0;
        }
        return image.getRGB(pixelX, pixelY) >>> 24;
    }

    /** 这个逻辑坐标是否落在某个袋口的皮革环（洞口半径 + 环宽）以内 */
    private static boolean coveredByPocket(Table table, double lx, double ly) {
        for (Pocket pocket : table.pockets()) {
            double deltaX = lx - pocket.center().x;
            double deltaY = ly - pocket.center().y;
            double rim = pocket.mouthRadius() + Layout.POCKET_RIM_WIDTH;
            if (deltaX * deltaX + deltaY * deltaY <= rim * rim) {
                return true;
            }
        }
        return false;
    }

    /** 把逻辑坐标换成贴图坐标后计算区域平均亮度 */
    private static double meanLumaLogical(BufferedImage image, double lx0, double ly0,
                                          double lx1, double ly1) {
        int deltaX = (int) Math.round(Layout.outerLeft());
        int deltaY = (int) Math.round(Layout.outerTop());
        double sum = 0;
        int count = 0;
        for (int y = (int) Math.round(ly0) - deltaY; y < (int) Math.round(ly1) - deltaY; y = y + 1) {
            for (int x = (int) Math.round(lx0) - deltaX; x < (int) Math.round(lx1) - deltaX; x = x + 1) {
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                    continue;
                }
                sum = sum + (lumaOf(image.getRGB(x, y)));
                count = count + 1;
            }
        }
        if (count == 0) {
            return 0;
        }
        return sum / count;
    }

    /** 按颜色通道的亮度权重计算像素亮度 */
    private static double lumaOf(int argb) {
        return 0.299 * ((argb >> 16) & 0xFF)
                + 0.587 * ((argb >> 8) & 0xFF)
                + 0.114 * (argb & 0xFF);
    }

    /** 一块区域的颜色数、平均亮度与亮度标准差。「是不是平涂」就靠这三个数 */
    private static RegionStats statsLogical(BufferedImage image, double lx0, double ly0,
                                            double lx1, double ly1) {
        int deltaX = (int) Math.round(Layout.outerLeft());
        int deltaY = (int) Math.round(Layout.outerTop());
        int x0 = (int) Math.round(lx0) - deltaX;
        int y0 = (int) Math.round(ly0) - deltaY;
        int x1 = (int) Math.round(lx1) - deltaX;
        int y1 = (int) Math.round(ly1) - deltaY;

        Set<Integer> colors = new HashSet<>();
        double sum = 0;
        double sumSq = 0;
        int count = 0;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y = y + 1) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x = x + 1) {
                int argb = image.getRGB(x, y);
                colors.add(argb);
                double luma = 0.299 * ((argb >> 16) & 0xFF)
                        + 0.587 * ((argb >> 8) & 0xFF)
                        + 0.114 * (argb & 0xFF);
                sum = sum + (luma);
                sumSq = sumSq + (luma * luma);
                count = count + 1;
            }
        }
        RegionStats strength = new RegionStats();
        strength.distinct = colors.size();
        strength.pixels = count;
        if (count == 0) {
            strength.meanLuma = 0;
        } else {
            strength.meanLuma = sum / count;
        }
        double variance = 0;
        if (count != 0) {
            variance = sumSq / count - strength.meanLuma * strength.meanLuma;
        }
        if (variance <= 0) {
            strength.lumaSd = 0;
        } else {
            strength.lumaSd = Math.sqrt(variance);
        }
        return strength;
    }

    /** 保存采样区域的颜色数和亮度统计，供材质检查比较 */
    private static final class RegionStats {
        int distinct;
        int pixels;
        double meanLuma;
        double lumaSd;
    }

    /**
     * 空快照也必须能整帧画出来
     *
     * <p>{@link GameStateView#empty()} 是构造期与无头检查里的合法初始值，画它时
     * 没有球、没有瞄准线、状态行是空串——几条「没有内容就跳过」的分支会全部走到
     * 这些分支最容易被写成 NPE 或除零，而它们在真实开局的第一帧就会跑到
     */
    private static void checkEmptyViewRenders(CheckHarness checks, GameRenderer renderer, Table table) {
        BufferedImage image = null;
        try {
            image = CheckImages.render(renderer, table, GameStateView.empty());
        } catch (Throwable error) {
            checks.check("空快照能整帧渲染（不抛异常）", false, error.toString());
            return;
        }
        int nonBackground = CheckImages.nonBackgroundCount(image);
        double coverage = nonBackground / (double) (image.getWidth() * image.getHeight());
        checks.check("空快照能整帧渲染，且球台与 HUD 照画",
                coverage >= MIN_COVERAGE,
                String.format("非背景像素占比 %.1f%%", coverage * 100));

        // 已落袋的球必须被剔掉，而不是「留在原地不动」。判法是让两张本该长得一模一样的
        // 图互相比：一张一颗球都没有，一张有 16 颗球但全部标成已落袋。若绘制时漏了
        // onTable 的守卫，后者会把 16 颗球照画上去，两张图立刻不同
        int pottedDiff = CheckImages.diffCount(image,
                CheckImages.render(renderer, table, CheckImages.rackViewAllOffTable()));
        checks.check("已落袋的球不会被画出来（空快照与全落袋快照画出同一张图）",
                pottedDiff == 0, "两张图有 " + pottedDiff + " 个像素不同");
    }

}

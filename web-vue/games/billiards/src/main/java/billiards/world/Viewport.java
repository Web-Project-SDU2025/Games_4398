package billiards.world;

/**
 * 逻辑画布（{@link Layout#WINDOW_W}×{@link Layout#WINDOW_H}）画到实际窗口上的映射：
 * <b>等比缩放 + 居中留边</b>
 *
 * <h2>为什么窗口能缩放而代码里一处坐标都不用改</h2>
 * 整个工程从头到尾只用逻辑坐标说话：模拟在逻辑坐标里跑，渲染器按逻辑坐标画，鼠标位置
 * 换算成逻辑坐标再交给模拟。窗口尺寸与逻辑尺寸之间只差<b>一个仿射变换</b>，于是支持缩放
 * 要改的只有两处——画的时候施加一次变换（{@code GamePanel}），收鼠标的时候施加一次逆变换
 * （{@code InputController}）。其余几百处坐标一个字都不用动
 *
 * <p>这正是把 {@code toLogical/toScreen} 从一开始就留在 {@link Layout} 里、而不是在各
 * 调用点随手乘一个比例所换来的：<b>「单一真源」不是在文档里写一句，而是让支持缩放这件事
 * 有一个唯一可以落脚的地方</b>
 *
 * <h2>为什么必须等比、必须留边</h2>
 * 分别缩放 x 与 y 会把球压成椭圆、把圆袋压成扁的，一眼假。所以取长宽两个方向所需缩放里
 * 的<b>较小者</b>，多出来的那一边用背景色留边（letterbox）。窗口边拖边看，台面始终是
 * 880×440 的比例
 *
 * <h2>为什么维度是纯数值</h2>
 * 这个类住在 {@code world} 包，而 {@code world} <b>刻意不引入 {@code java.awt}</b>——
 * 所以这里不出现 {@code Rectangle}/{@code Point}，只有三个 double。好处是这个映射本身
 * 也能被无头自检直接断言，不必先造一个 {@code Graphics2D}
 *
 * @param scale   逻辑坐标 → 屏幕坐标的缩放倍数，恒 &gt; 0
 * @param offsetX 逻辑坐标原点在窗口里的 x（居中留边），单位是屏幕像素
 * @param offsetY 同上，y
 */
public final class Viewport {
    private final double scale;
    private final double offsetX;
    private final double offsetY;

    /** 一比一且不留边的映射，画布与逻辑尺寸一致或尚未布局时使用 */
    public static final Viewport IDENTITY = new Viewport(1.0, 0.0, 0.0);

    /** 保存构造参数，字段只读以便安全传递状态 */
    public Viewport(double scale, double offsetX, double offsetY) {

        if (!(scale > 0.0)) {
            throw new IllegalArgumentException("缩放必须为正: " + scale);
        }
            this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** 返回保存的缩放倍数 */
    public double scale() {
        return scale;
    }

    /** 返回保存的水平留边 */
    public double offsetX() {
        return offsetX;
    }

    /** 返回保存的竖直留边 */
    public double offsetY() {
        return offsetY;
    }

    /**
     * 按窗口（画布组件）的实际尺寸算出一个映射
     *
     * <p>留边量取整：缩放为 1 时，任何整数逻辑坐标都会落在整数屏幕像素上，于是
     * <b>默认尺寸下的画面与 M4 之前逐像素相同</b>——这一条让「支持缩放」不至于顺手
     * 把原来那份已经验收过的画面改掉。缩放不为整数时左右各差半像素，看不出来
     *
     * <p>尺寸为 0 是<b>合法输入</b>而不是异常：组件在第一次布局完成之前就是 0×0，
     * 而那时可能已经有人来取映射了（比如一帧空 paint）。退回 1:1 而不是让调用方
     * 拿到一个除以零的 NaN，是这里唯一需要的防御
     */
    public static Viewport fit(int width, int height) {
        if (width <= 0 || height <= 0) {
            return IDENTITY;
        }
        double scale = Math.min(width / (double) Layout.WINDOW_W,
                height / (double) Layout.WINDOW_H);
        double offsetX = Math.round((width - Layout.WINDOW_W * scale) * 0.5);
        double offsetY = Math.round((height - Layout.WINDOW_H * scale) * 0.5);
        return new Viewport(scale, offsetX, offsetY);
    }

    /** 屏幕坐标（组件坐标）→ 逻辑坐标。收鼠标走这条 */
    public Vec2 toLogical(double screenX, double screenY) {
        return new Vec2((screenX - offsetX) / scale, (screenY - offsetY) / scale);
    }

    /**
     * 逻辑坐标 → 屏幕坐标。渲染并不用它（那条路直接把变换压进 {@code Graphics2D}），
     * 它存在是为了让自检能断言两个方向互为逆映射
     */
    public Vec2 toScreen(Vec2 logical) {
        return new Vec2(offsetX + logical.x * scale, offsetY + logical.y * scale);
    }

    /** 是否恰好是 1:1 且不留边。自检拿它钉住「默认尺寸下画面没变」这条 */
    public boolean isIdentity() {
        return scale == 1.0 && offsetX == 0.0 && offsetY == 0.0;
    }
}

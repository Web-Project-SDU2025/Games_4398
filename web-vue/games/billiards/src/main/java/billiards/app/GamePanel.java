package billiards.app;

import billiards.audio.SoundSink;
import billiards.render.GameRenderer;
import billiards.render.Theme;
import billiards.world.Layout;
import billiards.world.Viewport;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * 球台画布
 *
 * <p>它在整个线程模型里的位置很窄：{@link #tickFixed()} 由 {@link GameLoop} 的模拟线程
 * 调用（转手给 {@link MatchSession}），{@code paintComponent} 由 Swing 的 EDT 调用
 * （只从快照读）。<b>这个类自己不持有任何会变的状态</b>——上一版它直接拿着
 * {@code List<Ball>}，那等于让 EDT 和模拟线程共享一堆可变对象；现在两者之间只隔一个
 * {@code volatile} 快照字段，谁改什么在类型上就写死了
 *
 * <p>由此得到一条能写进报告的结论：<b>并发安全不是靠「小心地加锁」，而是靠让共享可变
 * 状态根本不存在。</b>读快照的方法 {@link MatchSession#view()} 永远返回一个构造完成的
 * 不可变对象，所以 EDT 侧再怎么读都不会读到中间态
 *
 * <h2>缩放画布为什么只改这一个方法</h2>
 * 渲染器全部按逻辑坐标（{@link Layout#WINDOW_W}×{@link Layout#WINDOW_H}）画，与实际窗口
 * 尺寸无关；这里在调渲染器之前把缩放与留边压进 {@code Graphics2D} 的变换里，画出来的就
 * 正好铺满窗口。<b>整个工程里知道窗口真实大小的只有两处——这里，和 {@code InputController}
 * 收鼠标的那一处（它用同一个 {@link Viewport#fit} 做逆变换）。</b>两边都是从同一个纯函数
 * 现算，所以画在哪、点在哪在构造上就不可能错位
 */
public final class GamePanel extends JPanel {

    private final MatchSession session;
    private final GameRenderer renderer = new GameRenderer();

    /** 静音画布。自检与无头路径用这个——和 {@link MatchSession} 的默认出口是同一条约定 */
    public GamePanel() {
        this(SoundSink.SILENT);
    }

    /** @param sink 声音出口，非 {@code null}；由 {@link MainFrame} 注入 */
    public GamePanel(SoundSink sink) {
        session = new MatchSession(sink);
        renderer.prepare(session.table());
        setPreferredSize(new Dimension(Layout.WINDOW_W, Layout.WINDOW_H));
        setBackground(Theme.WINDOW_BG);
        setDoubleBuffered(true);

        // 输入适配器只认组件，不认游戏——它把鼠标键盘翻译成指令，谁消费由模拟侧决定
        session.input().attach(this);
    }

    /** 返回当前画布管理的对局会话 */
    public MatchSession session() {
        return session;
    }

    /**
     * 当前窗口尺寸对应的映射
     *
     * <p>它与 {@code InputController} 收鼠标时用的那个值<b>不共享实例</b>，而是各自调一次
     * {@link Viewport#fit}——两个方向因此永远出自同一个纯函数。共享实例反而要处理「谁在
     * 什么时候更新它」，那才是会漏的地方
     */
    public Viewport viewport() {
        return Viewport.fit(getWidth(), getHeight());
    }

    /** 推进一个固定步长。只由模拟线程调用 */
    public void tickFixed() {
        session.tickFixed();
    }

    /** 实时循环只发布一份完整画面，参数是这一帧需要执行的固定物理步数 */
    public void advanceFrame(int steps) {
        for (int index = 0; index < steps; index = index + 1) {
            session.advanceFixed();
        }
        if (steps > 0) {
            session.publishFrame();
        }
    }

    /** 按窗口尺寸缩放画布并绘制只读快照，不修改模拟状态 */
    @Override
    protected void paintComponent(Graphics graphics) {
        // 先让父类按背景色铺满整块组件：多出来的部分（留边）就是这层背景色
        super.paintComponent(graphics);
        Graphics2D graphicsValue = (Graphics2D) graphics.create();
        try {
            Viewport viewport = viewport();
            graphicsValue.translate(viewport.offsetX(), viewport.offsetY());
            graphicsValue.scale(viewport.scale(), viewport.scale());
            // 整个绘制路径只读这一个快照。渲染器会被传进 table——那是构造后不变的几何
            renderer.draw(graphicsValue, session.table(), session.view());
        } finally {
            graphicsValue.dispose();
        }
    }
}

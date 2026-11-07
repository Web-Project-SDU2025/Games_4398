package billiards.app;

import billiards.audio.Sound;
import billiards.audio.SoundSink;
import billiards.world.Layout;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 主窗口
 *
 * <p>窗口<b>可以随意缩放</b>，台面按 {@link billiards.world.Viewport} 等比缩放并居中留边，
 * 所以从窗口拖到全屏都不会变形，鼠标也不会错位——逻辑坐标与屏幕坐标的换算收在
 * {@code Viewport} 一处，各渲染器里一个比例都没乘
 *
 * <p>默认尺寸取「屏幕可用区域能放下的最大等比尺寸」（约 92%），而不是逻辑尺寸 1000×640：
 * 后者在现在的显示器上只占中间一小块，看着难受。<b>上限与下限都只是默认值</b>——用户拖
 * 成什么样就是什么样，程序不保存窗口尺寸，避免产生额外的用户配置文件
 */
public final class MainFrame extends JFrame {

    /** 游戏主窗口标题 */
    public static final String TITLE = "美式八球 - 面向对象技术课程实验";

    /** 默认窗口占屏幕可用区域的比例，窗口与屏幕边缘和任务栏之间保留空隙 */
    private static final double DEFAULT_SCREEN_FRACTION = 0.92;

    /** 窗口最小尺寸。再小台面就成一枚邮票了，而且 HUD 的文字会糊成一团 */
    private static final Dimension MIN_SIZE = new Dimension(640, 440);

    private final GamePanel panel;
    private final GameLoop loop;
    private final Thread loopThread;
    private boolean loopStarted;
    private boolean stopped;

    /**
     * 声音出口。持有它是为了在关窗时能 {@link SoundSink#close()}——
     * <b>谁开的谁关</b>，{@link MatchSession} 只是使用者，不负责它的生死
     */
    private final SoundSink sound;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public MainFrame() {
        this(false);
    }

    /** 浏览器模式使用原始画面尺寸和平台标题栏，桌面模式保留可缩放窗口 */
    public MainFrame(boolean browserMode) {
        super(TITLE);

        // 打开声音。拿不到设备时返回的是 SILENT，不是异常——游戏照常跑，只是没声音
        // 这一步不会阻塞：真正的输出线是在音频线程上开的（见 MixerSink）
        sound = Sound.open();
        panel = new GamePanel(sound);
        setContentPane(panel);
        setResizable(!browserMode);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (browserMode) {
            setUndecorated(true);
        }

        loop = new GameLoop(panel);
        loopThread = new Thread(loop, "billiards-sim-loop");
        loopThread.setDaemon(true);

        addWindowListener(new GameWindowListener());

        pack();
        if (!browserMode) {
            setMinimumSize(MIN_SIZE);
            setSize(defaultWindowSize());
        }
        setLocationRelativeTo(null);
    }

    /** 默认窗口尺寸：屏幕可用区域内能放下的最大等比尺寸 */
    private static Dimension defaultWindowSize() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        double fit = Math.min(usable.width * DEFAULT_SCREEN_FRACTION / Layout.WINDOW_W,
                usable.height * DEFAULT_SCREEN_FRACTION / Layout.WINDOW_H);
        double scale = Math.max(1.0, fit);
        return new Dimension((int) Math.round(Layout.WINDOW_W * scale),
                (int) Math.round(Layout.WINDOW_H * scale));
    }

    /** 返回窗口中的游戏画布 */
    public GamePanel panel() {
        return panel;
    }

    /** 显示之后再启动模拟线程，避免窗口还没画出来物理就跑完了 */
    public void startLoop() {
        if (!loopStarted && !stopped) {
            loopStarted = true;
            loopThread.start();
        }
    }

    /** 窗口关闭或网页退出时释放资源，重复关闭不会重复等待音频线程 */
    @Override
    public void dispose() {
        stopTasks();
        super.dispose();
    }

    /** 同时结束模拟与音频任务，避免关闭窗口后浏览器继续消耗计算资源 */
    private void stopTasks() {
        if (stopped) {
            return;
        }
        stopped = true;
        loop.shutdown();
        loopThread.interrupt();
        sound.close();
    }

    /** 关闭窗口时停止模拟并释放音频设备 */
    private final class GameWindowListener extends WindowAdapter {

        /** 处理窗口关闭事件，停止本窗口启动的后台任务 */
        @Override
        public void windowClosing(WindowEvent event) {
            stopTasks();
        }
    }
}

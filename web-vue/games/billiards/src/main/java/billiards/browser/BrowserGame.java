package billiards.browser;

import billiards.app.MainFrame;
import billiards.render.RenderAssets;
import javax.swing.SwingUtilities;

/** 管理网页内的台球窗口，重复启动复用同一对局，关闭时释放模拟与声音任务 */
public final class BrowserGame {
    private static MainFrame window;

    /** 网页通过静态方法启动游戏，不需要创建适配器对象 */
    private BrowserGame() {
    }

    /** 打开或恢复台球窗口，目录参数由平台协议提供，本游戏从类路径读取音效 */
    public static void open(String directory, boolean editorMode) throws Exception {
        if (editorMode) {
            throw new IllegalArgumentException("美式八球没有关卡编辑器");
        }
        System.setProperty(RenderAssets.BROWSER_PROPERTY, "true");
        runOnEventThread(new OpenWindow());
    }

    /** 关闭当前窗口，主窗口的 dispose 方法负责停止其后台任务 */
    public static void close() throws Exception {
        runOnEventThread(new CloseWindow());
    }

    /** 已在界面线程时直接执行，否则等待界面任务完成后再向网页报告成功 */
    private static void runOnEventThread(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeAndWait(task);
        }
    }

    /** 保存窗口创建任务，让全部 Swing 操作始终在同一界面线程执行 */
    private static final class OpenWindow implements Runnable {
        /** 首次创建无边框窗口，后续只恢复已有窗口，避免重复启动模拟循环 */
        @Override
        public void run() {
            if (window == null || !window.isDisplayable()) {
                window = new MainFrame(true);
            }
            window.setVisible(true);
            window.panel().paintImmediately(0, 0, window.panel().getWidth(), window.panel().getHeight());
            window.toFront();
            window.panel().requestFocusInWindow();
            window.startLoop();
        }
    }

    /** 保存窗口关闭任务，与平台关闭按钮共用清理流程 */
    private static final class CloseWindow implements Runnable {
        /** 关闭后清空窗口引用，下次启动建立新对局 */
        @Override
        public void run() {
            if (window != null) {
                window.dispose();
                window = null;
            }
        }
    }
}

package billiards;

import billiards.app.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口
 *
 * <p>只在 EDT 上建窗口与启动模拟线程，别的什么都不做——把接线留给
 * {@code app} 包，把逻辑留给各自的包，{@code Main} 保持成一行的可读入口
 */
public final class Main {

    /** 工具类只提供静态方法，不需要创建对象 */
    private Main() {
    }

    /** 解析启动参数并运行程序入口，参数由命令行传入 */
    public static void main(String[] args) {
        // 用系统外观，Windows 下的中文标签与按钮才正常；失败则退回默认（不影响自绘部分）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 外观不是关键路径，取不到就用默认
        }

        SwingUtilities.invokeLater(new GameStarter());
    }

    /** 在 Swing 界面线程中创建窗口并启动模拟 */
    private static final class GameStarter implements Runnable {

        /** 创建主窗口并启动模拟循环 */
        @Override
        public void run() {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.startLoop();
        }
    }
}

package billiards.app;

import billiards.world.PhysicsConfig;
import billiards.render.GameStateView;

/**
 * 模拟主循环，跑在自己的守护线程上
 *
 * <p>结构是经典的「固定步累加器」：墙钟流逝多少毫秒，就补多少次固定步长的
 * {@code update}。墙钟只用来决定<b>补几步</b>，模拟本身永远以精确的
 * {@link PhysicsConfig#FIXED_STEP_MS} 前进——这就是确定性的来源。换成
 * {@code javax.swing.Timer} 或直接拿帧间 dt 积分，同一杆在不同机器上会跑出不同结果
 *
 * <p>线程规矩：<b>只有本线程修改模拟状态</b>；Swing 的 EDT 只在 {@code repaint()} 里读
 * 反过来（在 EDT 上跑物理）会撕裂动画，两边同时改球列表则会产生不确定竞态
 */
public final class GameLoop implements Runnable {

    /** 单帧最多追赶的墙钟时间。防止机器休眠/断点后「死亡螺旋」式补步 */
    private static final double MAX_FRAME_MS = 250.0;

    private final GamePanel panel;
    private volatile boolean running = true;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public GameLoop(GamePanel panel) {
        this.panel = panel;
    }

    /** 通知模拟循环停止，窗口关闭后不再推进对局 */
    public void shutdown() {
        running = false;
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    @Override
    public void run() {
        final long frameNanos = (long) (1_000_000_000.0 / PhysicsConfig.TARGET_FPS);
        final FixedStepAccumulator accumulator = new FixedStepAccumulator();

        long lastNanos = System.nanoTime();
        GameStateView lastPainted = null;

        while (running) {
            long frameStart = System.nanoTime();
            double elapsedMs = (frameStart - lastNanos) / 1_000_000.0;
            lastNanos = frameStart;
            if (elapsedMs > MAX_FRAME_MS) {
                elapsedMs = MAX_FRAME_MS;
            }

            int steps = accumulator.advance(elapsedMs);
            panel.advanceFrame(steps);

            GameStateView current = panel.session().view();
            if (current != lastPainted) {
                panel.repaint();
                lastPainted = current;
            }

            long remaining = frameNanos - (System.nanoTime() - frameStart);
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

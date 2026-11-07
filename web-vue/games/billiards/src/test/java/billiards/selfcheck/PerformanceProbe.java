package billiards.selfcheck;

import billiards.app.MatchSession;
import billiards.world.Layout;
import billiards.world.Vec2;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** 测量静止瞄准时的会话更新成本，只在开发者手动运行时执行 */
public final class PerformanceProbe {
    /** 正式测量前的预热次数，让即时编译器先完成常用路径的编译 */
    private static final int WARMUP_TICKS = 10000;
    /** 每轮更新次数，次数较多可减少计时误差 */
    private static final int MEASURE_TICKS = 30000;
    /** 测量轮数，最终取中位数以减少后台任务的影响 */
    private static final int ROUNDS = 5;
    private static volatile double observedPosition;

    /** 工具类只提供命令行入口，不需要创建对象 */
    private PerformanceProbe() {
    }

    /** 在默认球局静止瞄准时测量每步耗时和当前线程分配字节数 */
    public static void main(String[] args) {
        MatchSession session = new MatchSession();
        session.resetMatch();
        session.engine().placeCueBall(new Vec2(190, Layout.playfieldCenterY()));
        advance(session, WARMUP_TICKS);
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().getId();
        double[] times = new double[ROUNDS];
        double[] allocations = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round = round + 1) {
            long allocatedBefore = threads.getThreadAllocatedBytes(threadId);
            long started = System.nanoTime();
            advance(session, MEASURE_TICKS);
            times[round] = (System.nanoTime() - started) / (double) MEASURE_TICKS;
            long allocatedAfter = threads.getThreadAllocatedBytes(threadId);
            allocations[round] = (allocatedAfter - allocatedBefore) / (double) MEASURE_TICKS;
        }
        Arrays.sort(times);
        Arrays.sort(allocations);
        int middle = ROUNDS / 2;
        System.out.printf("Idle aiming: %.0f ns/tick, %.0f bytes/tick%n",
                times[middle], allocations[middle]);
    }

    /** 连续推进指定步数，并读取输出，防止测试代码被当成无用计算 */
    private static void advance(MatchSession session, int ticks) {
        for (int index = 0; index < ticks; index = index + 1) {
            session.tickFixed();
            observedPosition = session.view().cueX();
        }
    }
}

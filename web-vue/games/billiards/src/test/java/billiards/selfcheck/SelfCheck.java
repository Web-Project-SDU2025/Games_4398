package billiards.selfcheck;

/**
 * 自检入口
 *
 * <p>用 {@code java -Djava.awt.headless=true -cp build\classes billiards.selfcheck.SelfCheck} 运行，
 * 或直接 {@code run.bat test}。全通过时 stdout 以 {@code PASS: <n> 项检查通过} 结尾、退出码 0；
 * 任一失败则退出码 1 并把失败清单打到 stderr
 *
 * <p>自检跑的是<b>生产代码本身</b>——同一份 {@code PhysicsEngine}、同一个
 * {@code FoulDetector}，不是为测试另写一套。因此自检绿了，游戏里跑的确实是这段逻辑
 *
 * <p>整份自检<b>不碰声卡</b>：A 组（声音）能跑，是因为素材加载、合流、限幅都是纯函数，
 * 真出口只有 GUI 会开。见 {@link AudioChecks}
 */
public final class SelfCheck {

    /** 工具类只提供静态方法，不需要创建对象 */
    private SelfCheck() {
    }

    /** 解析启动参数并运行程序入口，参数由命令行传入 */
    public static void main(String[] args) {
        System.out.println("MyBilliards 自检");
        System.out.println("  Java  : " + System.getProperty("java.version")
                + "  (" + System.getProperty("java.vendor") + ")");
        System.out.println("  OS    : " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch"));
        System.out.println("  编码  : " + System.getProperty("file.encoding"));

        CheckHarness geometry = new CheckHarness("L  台面与摆球几何");
        GeometryChecks.run(geometry);

        CheckHarness physics = new CheckHarness("P  物理引擎");
        PhysicsChecks.run(physics);

        CheckHarness rendering = new CheckHarness("R  无头渲染");
        RenderingChecks.run(rendering);

        CheckHarness checks = new CheckHarness("U  交互与状态快照");
        UIChecks.run(checks);

        CheckHarness rules = new CheckHarness("S  规则引擎与判罚");
        RulesChecks.run(rules);

        CheckHarness audio = new CheckHarness("A  声音（素材 · 适配 · 混音）");
        AudioChecks.run(audio);

        // 用非短路的 & 而不是 &&：即使前面有失败也把后面全跑完，
        // 一次运行就能看到所有问题，而不是修一个冒一个
        boolean allPassed = geometry.report() & physics.report() & rendering.report()
                & checks.report() & rules.report() & audio.report();

        int passed = geometry.passed() + physics.passed() + rendering.passed()
                + checks.passed() + rules.passed() + audio.passed();
        int total = geometry.total() + physics.total() + rendering.total()
                + checks.total() + rules.total() + audio.total();

        // 可选的调试产物，默认什么都不做。两个开关各管一件事：
        //   -Dbilliards.filmstrip=<目录>  一杆满力开球导成逐帧 PNG（看物理）
        //   -Dbilliards.sound=<目录>      两种音色与一杆真实开球导成 wav（用耳朵判音色）
        // 放在判定之后、PASS 之前，是为了让它们落在各组检查都跑完之后，
        // 导出失败时仍保留前面各组的验证输出
        ShotFilmstrip.dumpIfRequested();
        SoundAudition.dumpIfRequested();

        System.out.println();
        if (allPassed) {
            System.out.println("PASS: " + passed + " 项检查通过");
            System.exit(0);
        } else {
            System.err.println("FAIL: " + (total - passed) + " / " + total + " 项检查未通过");
            System.exit(1);
        }
    }
}

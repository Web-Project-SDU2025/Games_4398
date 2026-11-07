package billiards.selfcheck;

/** 按固定顺序执行输入、对局和显示检查，后续布局检查使用真实收集的状态文字 */
public final class UIChecks {

    /** 工具类只提供检查入口，不需要创建对象 */
    private UIChecks() {
    }

    /** 执行全部界面检查，参数用于收集每项验证结果 */
    public static void run(CheckHarness checks) {
        UIFixtures.OBSERVED_STATUS.clear();
        UIInputChecks.checkShotPower(checks);
        UIInputChecks.checkSpeedMapping(checks);
        UIInputChecks.checkAimGeometry(checks);
        UIInputChecks.checkCueGeometry(checks);
        UIInputChecks.checkViewShape(checks);
        UISessionChecks.checkMatchShot(checks);
        UISessionChecks.checkMatchFlow(checks);
        UISessionChecks.checkSnapshotIsolation(checks);
        UIFrameChecks.checkHudLayout(checks);
        UIFrameChecks.checkStateReachesScreen(checks);
    }
}

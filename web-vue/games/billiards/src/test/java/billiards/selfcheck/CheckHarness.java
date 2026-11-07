package billiards.selfcheck;

import java.util.ArrayList;
import java.util.List;

/**
 * 自检骨架：计通过与失败，并在收尾时给出可被脚本判读的一行结论
 *
 * <p>契约（与课程第一个实验保持一致的习惯）：
 * <ul>
 *   <li>每条通过 → stdout 打 {@code [ok]   名称}</li>
 *   <li>每条失败 → stderr 打 {@code [FAIL] 名称: 期望/实际}</li>
 *   <li>全通过 → stdout 打 {@code PASS: <n> 项检查通过}，进程退出码 0</li>
 *   <li>有失败 → stderr 汇总每条失败，进程退出码 1</li>
 * </ul>
 * 因此 {@code exit code == 0 且 stdout 出现 PASS} 就是「全绿」，脚本只需判这一条
 *
 * <p>断言失败<b>不抛异常</b>：一次跑完能列出所有问题，比第一次失败就中断有用得多——
 * 调物理参数时经常是同一处改动同时影响好几项检查
 */
public final class CheckHarness {

    private final String group;
    private final List<String> failures = new ArrayList<>();
    private int passed = 0;
    private int total = 0;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public CheckHarness(String group) {
        this.group = group;
        System.out.println();
        System.out.println("== " + group + " ==");
    }

    /** 返回当前对象所属的球组或检查分组 */
    public String group() {
        return group;
    }

    /** 记录条件的验证结果，失败时同时保留名称和诊断说明 */
    public void check(String name, boolean ok) {
        check(name, ok, null);
    }

    /** 记录条件的验证结果，失败时同时保留名称和诊断说明 */
    public void check(String name, boolean ok, String detail) {
        total = total + 1;
        if (ok) {
            passed = passed + 1;
            System.out.println("  [ok]   " + name);
        } else {
            String line = name;
            if (detail != null) {
                line = name + " —— " + detail;
            }
            failures.add(line);
            System.err.println("  [FAIL] " + line);
        }
    }

    /** 浮点近似相等。{@code tol} 用绝对容差 */
    public void checkNear(String name, double expected, double actual, double tol) {
        double diff = Math.abs(expected - actual);
        check(name, diff <= tol,
                String.format("期望 %.6f ± %.6f，实际 %.6f（相差 %.3e）", expected, tol, actual, diff));
    }

    /**
     * 位级相等。用于确定性断言——「两次跑出同一个 double」不能靠容差比较，
     * 容差相等并不排除两条轨迹只是碰巧接近
     */
    public void checkBitsEqual(String name, double expected, double actual) {
        long quadraticTerm = Double.doubleToLongBits(expected);
        long linearTerm = Double.doubleToLongBits(actual);
        check(name, quadraticTerm == linearTerm,
                String.format("位模式不同：期望 0x%s，实际 0x%s（值 %.17g / %.17g）",
                        Long.toHexString(quadraticTerm), Long.toHexString(linearTerm), expected, actual));
    }

    /** 检查传入对象是否存在，并将结果记入报告 */
    public void checkNotNull(String name, Object value) {
        check(name, value != null, "值为 null");
    }

    /** 返回当前已通过的检查数量 */
    public int passed() {
        return passed;
    }

    /** 返回当前已执行的检查总数 */
    public int total() {
        return total;
    }

    /** 返回当前失败检查的数量 */
    public int failed() {
        return failures.size();
    }

    /** 返回当前收集的失败检查说明 */
    public List<String> failures() {
        return failures;
    }

    /** 打印本组结论。返回是否全通过 */
    public boolean report() {
        if (failures.isEmpty()) {
            System.out.println("  全部 " + total + " 项通过");
            return true;
        }
        System.err.println("  " + failures.size() + " / " + total + " 项失败");
        for (String fraction : failures) {
            System.err.println("    - " + fraction);
        }
        return false;
    }
}

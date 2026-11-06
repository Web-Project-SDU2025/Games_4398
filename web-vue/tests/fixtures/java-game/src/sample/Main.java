package sample;

/** 通用导入检查的最小 Java 游戏入口，只输出收到的参数 */
public final class Main {
    /** 示例入口以静态方法提供，调用方直接执行 main */
    private Main() {
    }

    /** 输出完整参数，验证普通 main 游戏保持自身参数顺序 */
    public static void main(String[] arguments) {
        for (String argument : arguments) {
            System.out.println(argument);
        }
    }
}

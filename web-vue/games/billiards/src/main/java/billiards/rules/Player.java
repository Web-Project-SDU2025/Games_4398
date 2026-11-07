package billiards.rules;

/**
 * 对局双方
 *
 * <p>用枚举而不是 {@code int} 0/1：规则代码里到处是「本杆是谁打的」「下一手是谁」
 * 「对手的组是什么」，用整数下标时这些全长得一样，读代码时要不停回头看变量含义
 * 枚举把「谁是当前手」变成类型问题，写错了编译不过
 *
 * <p>只有两个常量，所以 {@link #opponent()} 是全覆盖的，不需要 default 分支——
 * 换手逻辑永远不会漏掉某个分支
 */
public enum Player {

    ONE("玩家 1"),
    TWO("玩家 2");

    private final String labelZh;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    Player(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 界面上显示的名字。全中文界面，见方案里「界面语言」那条约定 */
    public String labelZh() {
        return labelZh;
    }

    /** 对手。两人对局，所以这是全覆盖的 */
    public Player opponent() {
        if (this == ONE) {
            return TWO;
        }
        return ONE;
    }
}

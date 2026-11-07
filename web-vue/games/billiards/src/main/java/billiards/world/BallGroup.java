package billiards.world;

/**
 * 球的分组
 *
 * <p>把「这颗球属于谁」这件事收敛成一个枚举，而不是散落各处的 {@code id >= 9 && id <= 15}
 * 之类的算术判断——规则引擎里每一处「是不是本方球」「是不是 8 号」都走这里，
 * 加球型只需要改 {@link BallCatalog} 一张表
 */
public enum BallGroup {

    /** 主球（白色，id 0） */
    CUE("主球"),

    /** 全色球 1–7 */
    SOLID("全色"),

    /** 花色球 9–15 */
    STRIPE("花色"),

    /** 8 号球，决胜球 */
    EIGHT("8号");

    private final String labelZh;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    BallGroup(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 返回该枚举值对应的中文名称 */
    public String labelZh() {
        return labelZh;
    }

    /** 是否为目标球（1–15）。开球的「四球碰库」只数目标球，主球不算 */
    public boolean isObjectBall() {
        return this != CUE;
    }

    /** 返回当前球组是否表示八号球 */
    public boolean isEight() {
        return this == EIGHT;
    }

    /** 全色↔花色互换；主球与 8 号没有对组，返回 {@code null} */
    public BallGroup oppositeGroup() {
        if (this == SOLID) {
            return STRIPE;
        }
        if (this == STRIPE) {
            return SOLID;
        }
        return null;
    }
}

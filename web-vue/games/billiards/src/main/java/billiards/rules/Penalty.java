package billiards.rules;

/**
 * 犯规的处罚方式
 *
 * <p>表里八种犯规有七种罚自由球，但**开球犯规的自由球被限制在开球线后**，而 8 号相关的
 * 两种直接结束比赛。若把「罚什么」写在检测逻辑里，那三种例外会散成三处 {@code if}；
 * 把它做成犯规自身的属性之后，「检测」与「处罚」彻底分开——检测只回答「犯没犯」，
 * 处罚由这张表查出来，加一种处罚不影响任何检测代码
 */
public enum Penalty {

    /** 普通自由球：台面任意合法位置 */
    BALL_IN_HAND("自由球"),

    /**
     * 自由球，但**必须放在开球线后**
     *
     * <p>仅用于开球犯规。开球没打好还能把主球摆到球堆边上接着打，等于把开球的失误
     * 变成一次进攻机会——这条限制就是堵这个
     */
    BALL_IN_HAND_BEHIND_HEAD_STRING("自由球（须置于开球线后）"),

    /** 直接判负。8 号提前落袋、8 号与犯规同杆落袋 */
    LOSE_GAME("直接判负");

    private final String labelZh;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    Penalty(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 返回该枚举值对应的中文名称 */
    public String labelZh() {
        return labelZh;
    }

    /** 是否把自由球限制在开球线后 */
    public boolean isBehindHeadString() {
        return this == BALL_IN_HAND_BEHIND_HEAD_STRING;
    }

    /** 是否直接结束比赛 */
    public boolean endsMatch() {
        return this == LOSE_GAME;
    }
}

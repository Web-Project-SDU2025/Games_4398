package billiards.rules;

/**
 * 规则开关与规则侧常量
 *
 * <p>与 {@link billiards.world.PhysicsConfig} 的分界：{@code PhysicsConfig} 管的是
 * 「球怎么动」（长度、时间、摩擦），这里管的是「什么叫合规」。两者混在一起的话，
 * 调物理手感时会在规则常量堆里翻找
 */
public final class RulesConfig {

        /**
     * 打 8 号是否要求「报袋」（指定袋口）
     *
     * <p><b>默认关闭</b>，而且是有意的：报袋要求玩家先点一个袋口，那是 M6 的 UI 工作量；
     * 更重要的是它让 8 号球的判定多出一个「报的袋和进的袋是不是同一个」的维度，而演示时
     * 观众看不见这层意图，只会觉得「明明进了却判负」。大纲给的规则里也没有这一条
     *
     * <p>留成常量而不是删掉，是因为它是真实规则的一部分，写进报告里能说明「我们知道有
     * 这条，是主动取舍掉的」，而不是没想到
     */
    public static final boolean RULES_REQUIRE_CALL_8 = false;

    /**
     * 开球合规所需「至少几颗目标球碰到库边」
     *
     * <p>WPA 规则：开球后要么有目标球落袋，要么至少四颗目标球碰库，否则开球犯规
     * 只数目标球——主球碰库不算，它几乎一定会碰
     */
    public static final int BREAK_MIN_BALLS_TO_RAIL = 4;

/** 工具类只提供静态方法，不需要创建对象 */
    private RulesConfig() {
    }

    // ---- 规则开关 ----

    // ---- 开球 ----

}

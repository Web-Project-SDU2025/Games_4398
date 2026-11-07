package billiards.rules;

/**
 * 正式对局的阶段
 *
 * <p>M4 的练习模式把「瞄准/出杆/停稳」压在一个 {@code Phase} 枚举里；正式对局在同样的
 * 骨架上多出四个状态，每一个都对应一件**玩家必须看见**的事——这正是它值得独立成类型的
 * 原因：
 * <ul>
 *   <li>{@link #BREAK_SETUP}：开球前主球可以在开球区内拖动，所以「还没开球」和「瞄准中」
 *       是两种不同的可操作状态，可放置的区域也不同</li>
 *   <li>{@link #RESOLVING}：判定要**看得见**。犯规、定组、胜负在一瞬间算完然后直接切到
 *       下一手，玩家只会看到球突然不听话了；把判定做成一个持续
 *       {@link billiards.world.Layout#VERDICT_BANNER_MS} 毫秒的真实阶段，演示时才有
 *       「裁判在判」这个过程</li>
 *   <li>{@link #BALL_IN_HAND}：自由球是**另一种摆放**，不是瞄准。限制在开球线后时更要有
 *       明显的视觉区分，否则玩家会奇怪为什么球放不下去</li>
 *   <li>{@link #GAME_OVER}：终局。它必须是一个终止状态，而不是靠 {@code outcome != null}
 *       在各个方法里反复判断</li>
 * </ul>
 *
 * <p>状态的迁移全部发生在 {@link RulesEngine#tickFixed()} 里，且每一步都有自检钉着
 * （S1 走完一整套 开球布置 → 瞄准 → 出杆 → 判定 → 下一手）
 */
public enum GamePhase {

    /** 比赛尚未开始。构造后的初始值，只存在于 {@link RulesEngine} 的构造函数与之之间 */
    MATCH_START("比赛未开始"),

    /** 开球布置：主球可在开球线后拖动，尚未击球 */
    BREAK_SETUP("摆放主球（开球线后）"),

    /** 可瞄准、可拉杆 */
    AIMING("瞄准中"),

    /** 球在滚动，输入被忽略 */
    SHOT_IN_FLIGHT("球在滚动…"),

    /** 本杆判定结果正在显示。横幅走完才进入下一手 */
    RESOLVING("判定中…"),

    /** 自由球：把主球放到合法位置（可能被限制在开球线后） */
    BALL_IN_HAND("自由球：放置主球"),

    /** 终局。判定结果里带着 {@link MatchOutcome} */
    GAME_OVER("比赛结束");

    private final String labelZh;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    GamePhase(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 返回该枚举值对应的中文名称 */
    public String labelZh() {
        return labelZh;
    }

    /** 是否可以移动主球（开球布置与自由球）。两处的合法性判据不同，见 {@link PlacementValidator} */
    public boolean allowsCuePlacement() {
        return this == BREAK_SETUP || this == BALL_IN_HAND;
    }

    /** 是否可以出杆 */
    public boolean allowsShot() {
        return this == AIMING;
    }
}

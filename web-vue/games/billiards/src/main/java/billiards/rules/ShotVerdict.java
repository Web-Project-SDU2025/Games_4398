package billiards.rules;

import billiards.world.BallGroup;

import java.util.List;

/**
 * 一杆的判决书：犯没犯规、罚什么、谁打下一杆、是不是打完了
 *
 * <p>它是一杆结束后<b>所有下游都只读的那一个对象</b>——HUD 读它出横幅、{@link RulesEngine}
 * 读它决定下一阶段、自检读它做断言。做成只读对象 有两个直接好处：判定结果不可能被
 * 后续代码改掉（改了就说不清是哪一版判的），以及「一杆只判一次」在结构上成立
 *
 * <p>{@code messageZh} 不是字段而是方法：文案完全由其余字段决定，存成字段就多了一处
 * 可能与它们不一致的副本。写成一方法，改判罚逻辑时文案自动跟着变
 */
public final class ShotVerdict {
    private final Player shooter;
    private final List<FoulType> fouls;
    private final BallGroup assignedGroup;
    private final Player nextPlayer;
    private final boolean continues;
    private final MatchOutcome outcome;

    /** 保存构造参数，字段只读以便安全传递状态 */
    public ShotVerdict(Player shooter, List<FoulType> fouls, BallGroup assignedGroup, Player nextPlayer, boolean continues, MatchOutcome outcome) {

        fouls = List.copyOf(fouls);
            this.shooter = shooter;
        this.fouls = fouls;
        this.assignedGroup = assignedGroup;
        this.nextPlayer = nextPlayer;
        this.continues = continues;
        this.outcome = outcome;
    }

    /** 返回保存的击球方 */
    public Player shooter() {
        return shooter;
    }

    /** 返回保存的犯规列表 */
    public List<FoulType> fouls() {
        return fouls;
    }

    /** 返回保存的本杆确定的组属 */
    public BallGroup assignedGroup() {
        return assignedGroup;
    }

    /** 返回保存的下一位击球者 */
    public Player nextPlayer() {
        return nextPlayer;
    }

    /** 返回保存的续杆状态 */
    public boolean continues() {
        return continues;
    }

    /** 返回保存的终局结果 */
    public MatchOutcome outcome() {
        return outcome;
    }

    // 查询

    /** 返回本杆判决是否包含犯规 */
    public boolean isFoul() {
        return !fouls.isEmpty();
    }

    /** 返回本杆结果是否已经结束比赛 */
    public boolean gameOver() {
        return outcome != null;
    }

    /** 本杆是否刚刚定下组属。{@code assignedGroup()} 为 {@code null} 时表示没定组 */
    public boolean assignedGroupNow() {
        return assignedGroup != null;
    }

    /**
     * 最重的一条犯规：先看有没有直接终局的，再依次按表里的顺序取第一条
     *
     * <p>「重」不是按顺序拍的——{@link Penalty#LOSE_GAME} 天然重于自由球，所以在两种处罚
     * 内部保持声明顺序即可。一次犯规同时触发多条时（比如主球落袋 + 飞球），这一条决定
     * 玩家看到的是哪句话；没有犯规时返回 {@code null}
     */
    public FoulType primaryFoul() {
        FoulType best = null;
        for (FoulType foul : fouls) {
            if (best == null) {
                best = foul;
                continue;
            }
            if (foul.penalty().endsMatch() && !best.penalty().endsMatch()) {
                best = foul;
            }
        }
        return best;
    }

    /** 下一手是否获得自由球。终局时没有「下一手」，返回 {@code false} */
    public boolean ballInHand() {
        if (gameOver()) {
            return false;
        }
        return isFoul() && !continues;
    }

    /** 自由球是否被限制在开球线后 */
    public boolean ballInHandBehindHeadString() {
        FoulType foul = primaryFoul();
        return ballInHand() && foul != null && foul.penalty().isBehindHeadString();
    }

    // 文案

    /**
     * 给 HUD 的一行字
     *
     * <p>放在这里而不是渲染器里：文案要说的是「判了什么」这件规则事实，渲染器只该负责
     * 把它画出来。规则改了文案不改，玩家会看到与判定不符的提示——那是很难查的一类 bug
     */
    public String messageZh() {
        if (gameOver()) {
            return outcome.winner().labelZh() + " 获胜 · " + outcome.reasonZh();
        }

        StringBuilder text = new StringBuilder();
        if (isFoul()) {
            text.append("犯规：").append(foulListZh());
            if (continues) {
                // 同时出现犯规和续杆时明确显示冲突，便于定位异常判决
                text.append("（仍由 ").append(shooter.labelZh()).append(" 击球）");
            } else {
                text.append(" · ").append(nextPlayer.labelZh());
                if (ballInHandBehindHeadString()) {
                    text.append(" 获自由球，须置于开球线后");
                } else {
                    text.append(" 获自由球");
                }
            }
            return text.toString();
        }

        if (continues) {
            text.append(shooter.labelZh());
            if (assignedGroupNow()) {
                text.append(" 打进一球，本组为 ").append(assignedGroup.labelZh());
            } else {
                text.append(" 继续击球");
            }
            return text.toString();
        }

        return "换手： " + nextPlayer.labelZh() + " 击球";
    }

    /** 把犯规名称按记录顺序连接成中文说明 */
    private String foulListZh() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < fouls.size(); index = index + 1) {
            if (index > 0) {
                text.append('、');
            }
            text.append(fouls.get(index).labelZh());
        }
        return text.toString();
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        return "ShotVerdict{" + shooter + " → " + nextPlayer
                + ", 犯规=" + fouls
                + ", 定组=" + assignedGroup
                + ", 续杆=" + continues
                + ", 终局=" + outcome + "}";
    }
}

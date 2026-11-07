package billiards.rules;

/**
 * 终局结果：谁赢了，以及为什么
 *
 * <p>{@code reasonZh} 不是给日志看的，是给<b>玩家</b>看的：8 号球判负有好几条完全不同的
 * 路径（提前落袋、跟犯规同杆落袋、主球一起落袋），只显示「你输了」等于让玩家去猜
 * 判定与文案在同一次计算里产生，就不会出现「规则改了文案没改」
 */
public final class MatchOutcome {
    private final Player winner;
    private final Player loser;
    private final String reasonZh;

    /** 保存构造参数，字段只读以便安全传递状态 */
    public MatchOutcome(Player winner, Player loser, String reasonZh) {

        if (winner == null || loser == null) {
            throw new IllegalArgumentException("胜负双方都不能为空");
        }
        if (winner == loser) {
            throw new IllegalArgumentException("不能和自己比赛: " + winner);
        }
            this.winner = winner;
        this.loser = loser;
        this.reasonZh = reasonZh;
    }

    /** 返回保存的胜方 */
    public Player winner() {
        return winner;
    }

    /** 返回保存的败方 */
    public Player loser() {
        return loser;
    }

    /** 返回保存的结果说明 */
    public String reasonZh() {
        return reasonZh;
    }

    /** 由胜方造出结果，败方自动是对手 */
    public static MatchOutcome wonBy(Player winner, String reasonZh) {
        return new MatchOutcome(winner, winner.opponent(), reasonZh);
    }

    /** 由败方造出结果，胜方自动是对手 */
    public static MatchOutcome lostBy(Player loser, String reasonZh) {
        return new MatchOutcome(loser.opponent(), loser, reasonZh);
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        return winner.labelZh() + " 获胜（" + reasonZh + "）";
    }
}

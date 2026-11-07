package billiards.render;

import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.world.BallGroup;
import java.util.Arrays;

/**
 * 对局层面的只读快照：谁在打、双方各是什么组、哪些球已经落袋、这一帧要不要盖浮层
 *
 * <h2>为什么单独一个类型，而不是塞进 {@link GameStateView}</h2>
 * 两者变动的<b>频率</b>差了两个数量级。{@link BallView} 每帧都不同（球一直在动），
 * 而「谁打哪个组」一局只变一两次、「谁刚打进一颗」一杆变一次。塞进同一个对象并不会
 * 更省事，反而会让「球的位置」这条每帧都要走的热路径顺手牵上一堆一局只算一次的东西
 *
 * <p>但两者又必须<b>同时</b>发布——它们描述的是同一帧。所以 {@code GameStateView}
 * 持有本对象（可缺省，见 {@link MatchView#NONE}），模拟线程一帧只写一个 {@code volatile}
 * 字段，EDT 读到的 HUD 与球位永远出自同一帧
 *
 * <h2>字段为什么是平的</h2>
 * 与 {@link AimView} 同一个理由：渲染包不该去认识 {@code RulesEngine}。这里存的是
 * <b>已经算好的结论</b>（「玩家 1 的托盘里有 3 号和 5 号」），不是「去问引擎」的凭据
 * 类型上放 {@link GamePhase}/{@link Player}/{@link BallGroup}/{@link MatchOutcome}
 * 是允许的——它们都在 {@code rules} 与 {@code world} 里，而依赖箭头是
 * {@code render ← rules ← world}，方向没被掉头
 *
 * <p>{@code app} 包（{@code MatchSession}）负责把引擎的当前状态抄进来；抄的成本是十几行
 * 赋值，换来的是「渲染层永远不可能反向驱动规则」
 */
public final class MatchView {

    /**
     * 一行文字/一个徽章的<b>语气</b>
     *
     * <p>它取代了「把颜色写进快照」的做法：快照不认识 {@code java.awt.Color}（那会把
     * 图形库拖进跨线程共享的数据里），只说明「这是一句好话/坏话/结果」。挑哪种绿、哪种红
     * 是 {@link Theme} 与 {@link HudRenderer} 的事
     */
    public enum Tone {
        /** 中性陈述（轮到谁、比赛未开始） */
        NEUTRAL,
        /** 好事：合法进球、定组、续杆 */
        LEGAL,
        /** 坏事：犯规 */
        FOUL,
        /** 结果：胜负已定 */
        WIN
    }

    /**
     * 「这一帧没有对局信息」。构造期与无头夹具用它充当合法但没有内容的初值
     *
     * <p>它的阶段取 {@link GamePhase#AIMING} 而不是「更中性」的 {@code MATCH_START}，
     * 这个选择是有代价意识的反直觉：{@code MATCH_START} 与 {@code GAME_OVER} 是仅有的两个
     * <b>会招来整屏浮层</b>的阶段，把中性值取成它们中的任何一个，等于让每一份无头夹具帧
     * 都被压暗的浮层盖住——而自检里所有「画与不画两帧必须不同」的像素比对都在那些帧上跑，
     * 底色一层压下来，差值全被吃掉，测了等于没测
     *
     * <p>{@code AIMING} 是唯一一个「什么都不额外画」的进行中阶段，所以它才是真正的中性值
     * 生产路径不经过这里：{@link billiards.app.MatchSession} 每一帧都构造真实的 {@code MatchView}
     */
    public static final MatchView NONE = new MatchView(
            GamePhase.AIMING, Player.ONE, null, null,
            new int[0], new int[0], true,
            Tone.NEUTRAL, null, "", Tone.NEUTRAL,
            false, false, false, Double.NaN, Double.NaN, new String[0]);

    private final GamePhase phase;
    private final Player current;
    private final BallGroup groupOne;
    private final BallGroup groupTwo;
    private final int[] pottedOne;
    private final int[] pottedTwo;
    private final boolean eightOnTable;
    private final Tone statusTone;
    private final MatchOutcome outcome;
    private final String banner;
    private final Tone bannerTone;
    private final boolean placing;
    private final boolean placementRestricted;
    private final boolean placementValid;
    private final double placementX;
    private final double placementY;
    private final String[] debugLines;

    /** 保存阶段、双方球组、落袋托盘与提示状态，并复制数组以隔离后续修改 */
    public MatchView(GamePhase phase, Player current, BallGroup groupOne, BallGroup groupTwo,
                     int[] pottedOne, int[] pottedTwo, boolean eightOnTable,
                     Tone statusTone, MatchOutcome outcome, String banner, Tone bannerTone,
                     boolean placing, boolean placementRestricted, boolean placementValid,
                     double placementX, double placementY, String[] debugLines) {
        if (phase == null) {
            this.phase = GamePhase.MATCH_START;
        } else {
            this.phase = phase;
        }
        if (current == null) {
            this.current = Player.ONE;
        } else {
            this.current = current;
        }
        this.groupOne = groupOne;
        this.groupTwo = groupTwo;
        this.pottedOne = pottedOne.clone();
        this.pottedTwo = pottedTwo.clone();
        this.eightOnTable = eightOnTable;
        if (statusTone == null) {
            this.statusTone = Tone.NEUTRAL;
        } else {
            this.statusTone = statusTone;
        }
        this.outcome = outcome;
        if (banner == null) {
            this.banner = "";
        } else {
            this.banner = banner;
        }
        if (bannerTone == null) {
            this.bannerTone = Tone.NEUTRAL;
        } else {
            this.bannerTone = bannerTone;
        }
        this.placing = placing;
        this.placementRestricted = placementRestricted;
        this.placementValid = placementValid;
        this.placementX = placementX;
        this.placementY = placementY;
        this.debugLines = debugLines.clone();
    }

    /** 返回当前对局阶段 */
    public GamePhase phase() {
        return phase;
    }

    /** 返回当前击球方 */
    public Player current() {
        return current;
    }

    /** 根据玩家返回对应组属，未定组时返回 null */
    public BallGroup groupOf(Player player) {
        if (player == Player.ONE) {
            return groupOne;
        }
        return groupTwo;
    }

    /** 某位玩家已落袋的球号个数 */
    public int pottedCount(Player player) {
        return potted(player).length;
    }

    /** 某位玩家已落袋的第 {@code index} 颗球号。越界抛 {@link IndexOutOfBoundsException} */
    public int potted(Player player, int index) {
        return potted(player)[index];
    }

    /** 根据玩家返回其落袋球快照数据 */
    private int[] potted(Player player) {
        if (player == Player.ONE) {
            return pottedOne;
        }
        return pottedTwo;
    }

    /** 8 号是否还在台上。不在台上时 HUD 的中央徽章画成暗色划掉 */
    public boolean eightOnTable() {
        return eightOnTable;
    }

    /** 返回状态文字的语气，实际颜色由主题决定 */
    public Tone statusTone() {
        return statusTone;
    }

    /** 返回终局结果，比赛未结束时返回 null */
    public MatchOutcome outcome() {
        return outcome;
    }

    /** 本帧是否有判定横幅要盖 */
    public boolean hasBanner() {
        return !banner.isEmpty();
    }

    /** 返回判定横幅文字 */
    public String banner() {
        return banner;
    }

    /** 根据当前阶段和判决返回横幅语气，颜色由绘制器选择 */
    public Tone bannerTone() {
        return bannerTone;
    }

    /** 是否正在拖放主球 */
    public boolean placing() {
        return placing;
    }

    /** 待放位置的虚影是否已经算出来了（光标有效且在拖放阶段） */
    public boolean hasPlacement() {
        return placing && !Double.isNaN(placementX);
    }

    /**
     * 放置是否被限制在开球线后
     *
     * <p>它得进快照，不该由渲染层去猜：判据是「开球布置」或「开球犯规后的自由球」，
     * 后者是引擎里一个不对外可见的布尔值，从阶段和球位都推不出来
     */
    public boolean placementRestricted() {
        return placementRestricted;
    }

    /** 返回待放主球的位置是否通过规则校验 */
    public boolean placementValid() {
        return placementValid;
    }

    /** 返回待放主球的水平逻辑坐标 */
    public double placementX() {
        return placementX;
    }

    /** 返回待放主球的竖直逻辑坐标 */
    public double placementY() {
        return placementY;
    }

    /** 调试浮层是否开着。开着时 {@link #debugLines()} 才有内容 */
    public boolean debug() {
        return debugLines.length > 0;
    }

    /** 调试浮层的行数 */
    public int debugLineCount() {
        return debugLines.length;
    }

    /** 按下标返回已生成的调试文字 */
    public String debugLine(int index) {
        return debugLines[index];
    }

    /** 比较影响画面的字段，未变化的阶段和托盘不需要触发重绘 */
    public boolean sameDisplay(MatchView other) {
        return phase == other.phase && current == other.current
                && groupOne == other.groupOne && groupTwo == other.groupTwo
                && Arrays.equals(pottedOne, other.pottedOne) && Arrays.equals(pottedTwo, other.pottedTwo)
                && eightOnTable == other.eightOnTable && statusTone == other.statusTone
                && outcome == other.outcome && banner.equals(other.banner) && bannerTone == other.bannerTone
                && placing == other.placing && placementRestricted == other.placementRestricted
                && placementValid == other.placementValid
                && Double.compare(placementX, other.placementX) == 0
                && Double.compare(placementY, other.placementY) == 0
                && Arrays.equals(debugLines, other.debugLines);
    }
}

package billiards.rules;

import billiards.world.BallCatalog;
import billiards.world.BallGroup;

import java.util.Collection;

/**
 * 定组：开放台面上「先落袋的那颗球」决定击球方从此打全色还是花色
 *
 * <h2>为什么是「先落袋」而不是「落袋的颗数多者」</h2>
 * 一杆同时打进一颗全色和一颗花色，规则必须给一个答案，而且这个答案<b>不能依赖物理细节</b>
 * 「哪颗先落袋」是玩家看得见的顺序（球是一颗颗掉进去的），「哪组多」在 1:1 时还要再看
 * 谁的号码小，纯属人为。所以取前者——它也是 WPA 的算法
 *
 * <p>落袋顺序由 {@link ShotRecord#pocketedInOrder()} 提供，它按物理引擎报告事件的真实
 * 先后排列。同一子步内两颗球同时落袋时顺序由球列表下标决定（物理引擎的迭代顺序固定），
 * 所以结果仍然是确定性的、可复现的
 *
 * <h2>定组的三个前提</h2>
 * <ol>
 *   <li>台面是开放的（还没人定组）；</li>
 *   <li>不是开球那一杆——开球进球只让开球方继续，不定组；</li>
 *   <li>本杆<b>无犯规</b>。犯规的一杆即使进了球也不定组，否则「故意犯规顺手定组」会
 *       变成一种策略，而犯规本来就该是纯亏的</li>
 * </ol>
 * 三个前提都不在这里判断——它们是 {@link FoulDetector} 的上下文，本类只回答
 * 「按这个落袋顺序，该定哪一组」
 */
public final class GroupAssigner {

    /** 工具类只提供静态方法，不需要创建对象 */
    private GroupAssigner() {
    }

    /**
     * 按落袋顺序给出应定的组；没有可定的球（含只落了 8 号、只落了主球、什么都没进）返回
     * {@code null}
     *
     * <p>跳过主球与 8 号而不是报错：开放台面上 8 号落袋本来就是另一条规则（判负）管的事，
     * 走到这里的落袋清单里出现 8 号说明那一杆已经被胜负判定接管了，不该在这里二次判
     */
    public static BallGroup assignFrom(Collection<Integer> pocketedInOrder) {
        for (int ballId : pocketedInOrder) {
            BallGroup ballGroup = BallCatalog.groupOf(ballId);
            if (ballGroup == BallGroup.SOLID || ballGroup == BallGroup.STRIPE) {
                return ballGroup;
            }
        }
        return null;
    }

    /** 给定在台球号集合，该组还剩几颗 */
    public static int countOnTable(BallGroup group, Collection<Integer> idsOnTable) {
        int count = 0;
        for (int ballId : idsOnTable) {
            if (BallCatalog.groupOf(ballId) == group) {
                count = count + 1;
            }
        }
        return count;
    }

    /** 给定在台球号集合，该组是否已清空 */
    public static boolean isCleared(BallGroup group, Collection<Integer> idsOnTable) {
        return group != null && countOnTable(group, idsOnTable) == 0;
    }
}

package billiards.rules;

import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.List;

/**
 * 主球放置校验：几何合法 <b>且</b> 规则允许
 *
 * <h2>为什么在这薄薄一层，而不是把这些条件塞进 Table</h2>
 * 两条判据的归属完全不同：
 * <ul>
 *   <li><b>几何</b>——在台面内、不压别的球、不陷在袋口里——是
 *       {@link Table#canPlaceBall} 的事，一个纯几何问题，与台球规则无关；</li>
 *   <li><b>规则</b>——开球与开球犯规的自由球必须放在开球线后——是规则的事，
 *       {@code world} 包不该知道「开球线之后」意味着什么</li>
 * </ul>
 * 分开之后几何只有一份实现（{@code Table}），规则侧只做一次叠加。放在一起的话，
 * 「开球线后」这个条件会渗进 `world`，而那正是包依赖里要守住的边界
 *
 * <h2>一个容易漏的点</h2>
 * 放置校验用的是一个<b>忽略主球</b>的几何判定（{@code ignoreBallId = 0}）。主球此刻不在
 * 台上（自由球时它已经落袋了），但它可能还带着旧坐标；不忽略它的话，往它自己原来的
 * 位置放球会被判成「和主球重叠」，而那个位置往往是玩家最想放的地方
 */
public final class PlacementValidator {

    /** 工具类只提供静态方法，不需要创建对象 */
    private PlacementValidator() {
    }

    /** 台面任意合法位置。自由球与开球布置之外的调试用 */
    public static boolean isValidAnywhere(Table table, List<Ball> balls, Vec2 pos) {
        return table.canPlaceBall(balls, pos, BallCatalog.CUE_ID);
    }

    /**
     * 放置是否合法
     *
     * @param behindHeadStringOnly 是否限制在开球线后（开球、以及开球犯规后的自由球）
     */
    public static boolean isValid(Table table, List<Ball> balls, Vec2 pos, boolean behindHeadStringOnly) {
        if (!table.canPlaceBall(balls, pos, BallCatalog.CUE_ID)) {
            return false;
        }
        return !behindHeadStringOnly || table.isInBreakZone(pos);
    }
}

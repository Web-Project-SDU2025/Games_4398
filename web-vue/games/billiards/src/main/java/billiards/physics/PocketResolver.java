package billiards.physics;

import billiards.world.Ball;
import billiards.world.Layout;
import billiards.world.Pocket;
import billiards.world.ShotEventListener;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.List;

/**
 * 判定入袋并处理异常离开台面的球
 *
 * <p>只在<b>库边解算之后</b>调用：擦着袋口边缘过去的球会先被库边推回台内，
 * 只有真正进了袋口圆心的球才会离场。这个顺序就是「擦库不掉袋」的机制，
 * 不是靠额外判断实现的
 *
 * <p>袋心在台外、捕获半径小于「球半径 + 袋心外偏」，这两条一起保证平行滚过边袋的球
 * 永远不满足捕获条件（详见 {@code Pocket} 的注释）
 *
 * <p>「飞出台面」在 2D 引擎里正常不可达——它是子步位移不变量被破坏时的症状
 * 留着它既是安全网，也对应了大纲里的「跳球」犯规
 */
public final class PocketResolver {

    /** 工具类只提供静态方法，不需要创建对象 */
    private PocketResolver() {
    }

    /** 根据球列表和球台几何处理当前接触，监听者接收对应事件 */
    public static void resolve(List<Ball> balls, Table table, List<ShotEventListener> listeners) {
        for (Ball ball : balls) {
            if (!ball.isOnTable()) {
                continue;
            }

            Pocket pocket = findCapturingPocket(ball.position(), table);
            if (pocket != null) {
                // 落袋瞬间的速度要在 setOnTable(false) 之前读——那之后球就不再被推进，
                // 速度会停在最后一步的值上，不再是「掉进去时的速度」
                double speed = ball.speed();
                ball.setOnTable(false);
                if (listeners != null) {
                    for (ShotEventListener listener : listeners) {
                        listener.onBallPocketed(ball.id(), pocket.index(), speed);
                    }
                }
                continue;
            }

            if (isBeyondFrame(ball.position())) {
                ball.setOnTable(false);
                if (listeners != null) {
                    for (ShotEventListener listener : listeners) {
                        listener.onBallOffTable(ball.id());
                    }
                }
            }
        }
    }

    /** 球心落在哪个袋口的捕获圆内；不在任何袋口返回 {@code null} */
    public static Pocket findCapturingPocket(Vec2 ballCenter, Table table) {
        for (Pocket pocket : table.pockets()) {
            if (pocket.captures(ballCenter)) {
                return pocket;
            }
        }
        return null;
    }

    /** 球心是否已经越出木质外框——正常玩法下不可达 */
    private static boolean isBeyondFrame(Vec2 position) {
        return position.x < Layout.outerLeft() || position.x > Layout.outerRight()
                || position.y < Layout.outerTop() || position.y > Layout.outerBottom();
    }
}

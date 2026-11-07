package billiards.render;

import billiards.world.Vec2;

/**
 * 瞄准辅助的只读快照：一条虚线、一颗虚影球、两条分离线
 *
 * <p>{@link #NONE} 表示「这一帧什么都不画」（球在滚动、或主球不在台上）。用一个空对象
 * 而不是 {@code null}，是为了让渲染器不必到处判空——空对象本身就是「没有瞄准辅助」这个
 * 状态的合法表示
 *
 * <h2>为什么字段是平的，不直接放 {@code AimCalculator.Preview}</h2>
 * 包依赖是单向的：{@code render} 依赖 {@code world}/{@code rules}，而 {@code input}
 * 在这条链的<b>下游</b>。让渲染包持有 {@code input} 的类型，等于把依赖方向掉个头——
 * 今天只是少写几行赋值，明天就会有人顺手在渲染里调用 {@code input} 的别的东西
 * 所以由 {@code app} 层（它本来就认识所有人）把 {@link billiards.input.AimCalculator.Preview}
 * 的字段逐个抄进这里。抄的成本是六行，换来的是依赖图不会有例外
 *
 * <p>{@code Vec2} 与 {@code Ball} 一样都在最底层的 {@code world} 包，所以这里的字段
 * 用 {@code Vec2} 完全没有问题
 *
 * <h2>终点为什么不是「画到台边为止」</h2>
 * 而是<b>主球球心在首次接触时停下的位置</b>：撞球时是两球心距 2R 处，撞库时是离库边线 R 处
 * 这个区别在斜射时很明显——直接拿射线与库边线的交点当终点，中线会画进球里约
 * {@code R/sinθ} 那么深。计算在 {@code AimCalculator} 里
 */
public final class AimView {

    /** 「这一帧不画瞄准辅助」 */
    public static final AimView NONE = new AimView(null, null, null, -1, null, null);

    private final Vec2 dir;
    private final Vec2 lineEnd;
    private final Vec2 ghost;
    private final int targetId;
    private final Vec2 targetSeparation;
    private final Vec2 cueSeparation;

    /**
     * @param dir              瞄准方向（单位向量）；{@code null} = 本帧不画
     * @param lineEnd          虚线终点
     * @param ghost            虚影球球心；{@code null} = 这一杆撞不到任何东西
     * @param targetId         被撞球号；{@code -1} = 撞库或没撞到
     * @param targetSeparation 目标球离开方向；没撞到球时为 {@code null}
     * @param cueSeparation    主球离开方向；没撞到球时为 {@code null}
     */
    public AimView(Vec2 direction, Vec2 lineEnd, Vec2 ghost, int targetId,
                   Vec2 targetSeparation, Vec2 cueSeparation) {
        this.dir = direction;
        this.lineEnd = lineEnd;
        this.ghost = ghost;
        this.targetId = targetId;
        this.targetSeparation = targetSeparation;
        this.cueSeparation = cueSeparation;
    }

    /** 返回预览中的单位瞄准方向 */
    public Vec2 dir() {
        return dir;
    }

    /** 返回瞄准线的末端坐标 */
    public Vec2 lineEnd() {
        return lineEnd;
    }

    /** 返回虚影球心，没有接触位置时返回 null */
    public Vec2 ghost() {
        return ghost;
    }

    /** 返回首次接触的目标球号，没有目标球时返回负值 */
    public int targetId() {
        return targetId;
    }

    /** 返回目标球预计离开方向 */
    public Vec2 targetSeparation() {
        return targetSeparation;
    }

    /** 返回主球预计离开方向 */
    public Vec2 cueSeparation() {
        return cueSeparation;
    }

    /** 本帧是否需要画瞄准辅助 */
    public boolean isPresent() {
        return dir != null;
    }

    /** 本帧是否有虚影球（也就是这一杆会撞上东西） */
    public boolean hasGhost() {
        return ghost != null;
    }

    /** 虚影球处撞到的是不是球（而非库边） */
    public boolean hitBall() {
        return targetId >= 0;
    }
}

package billiards.input;

/**
 * EDT 投递给模拟线程的一条<b>状态变更</b>指令
 *
 * <h2>为什么鼠标移动不在这里</h2>
 * 输入分两类，走两条路：
 * <ul>
 *   <li><b>只影响显示的连续量</b>（光标位置 → 瞄准方向与拉杆距离）：EDT 直接写一个
 *       {@code volatile} 引用，模拟线程随读随用。鼠标一秒移动上百次，排队反而会积压，
 *       而瞄准方向晚一帧无所谓</li>
 *   <li><b>改变模拟状态的离散事件</b>（按下、松开、取消、重摆）：走这条队列，
 *       由模拟线程在每帧开头统一收走。这类事件一旦乱序或丢一个，就会卡在「一直拉着杆」
 *       或者「球没打出去」，所以必须串行化在唯一的那个线程上</li>
 * </ul>
 * 于是<b>模拟状态永远只有模拟线程碰得到</b>——不是靠加锁去管理共享，而是让它压根不共享
 *
 * <h2>为什么没有时间戳</h2>
 * 这里曾经带一个 {@code whenMs}，因为力度是「按住时长」——那必须知道事件真正发生的时刻，
 * 否则一次卡顿就会把排队的按下-松开算成即时点击。力度改成<b>拉距</b>之后，这一杆有多重
 * 只取决于松手时光标离起点多远，与时间无关：{@code PRESS} 记下起点、{@code RELEASE}
 * 报出终点，两者之间的时间完全不参与计算
 *
 * <p>顺带的结果是模拟线程不再读任何墙钟，一杆的演化成为纯粹由初速决定的确定性过程
 *
 * @param kind 指令类型
 * @param x    世界坐标 x（{@link Kind#PRESS} 与 {@link Kind#RELEASE} 用得上，其余为 NaN）
 * @param y    世界坐标 y（同上）
 */
public final class InputCommand {
    private final Kind kind;
    private final double x;
    private final double y;

    /** 保存构造参数，字段只读以便安全传递状态 */
    public InputCommand(Kind kind, double x, double y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
    }

    /** 返回保存的指令或声音类型 */
    public Kind kind() {
        return kind;
    }

    /** 返回保存的水平坐标 */
    public double x() {
        return x;
    }

    /** 返回保存的竖直坐标 */
    public double y() {
        return y;
    }

    /** 指令类型 */
    public enum Kind {
        /** 鼠标按下：记下拉杆起点、锁定瞄准方向 */
        PRESS,
        /** 鼠标松开：按拉距决定力度并击球（拉距不足死区则取消） */
        RELEASE,
        /** 取消这一杆（Esc 或右键）：松开拉杆，不击球 */
        CANCEL,
        /** 重摆一局 */
        RESET,
        /** 切换调试浮层（D） */
        DEBUG,
        /** 切换静音（M） */
        MUTE
    }

    /** 根据鼠标按下坐标创建输入指令，供模拟线程处理 */
    public static InputCommand press(double x, double y) {
        return new InputCommand(Kind.PRESS, x, y);
    }

    /**
     * 松开鼠标。带上坐标而不是只报「松了」，是因为力度要在松手那一刻按光标的<b>最终</b>
     * 位置定下来：光标的中间轨迹不需要逐条投递（那是 {@code volatile} 那条路的事），
     * 但终点必须在指令里，否则模拟线程会拿一个可能旧一帧的光标去算力度
     */
    public static InputCommand release(double x, double y) {
        return new InputCommand(Kind.RELEASE, x, y);
    }

    /** 无坐标的指令（取消、重摆、切浮层、静音） */
    public static InputCommand of(Kind kind) {
        return new InputCommand(kind, Double.NaN, Double.NaN);
    }
}

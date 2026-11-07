package billiards.world;

/**
 * 物理引擎的观察者接口——本工程最重要的一处解耦缝
 *
 * <p>物理引擎完全不知道台球规则是什么：它只负责把球推来推去，每发生一次有意义的
 * 交互就喊一声。规则引擎（{@code ShotRecord}）订阅它来收集判罚证据，
 * {@code audio.ShotSounds} 订阅它来发声。因此依赖方向永远是
 * {@code physics → ShotEventListener ← rules}，两者互相不认识
 *
 * <p>接口刻意放在最底层的 {@code world} 包，就是为了让 {@code physics} 与 {@code rules}
 * 都能引用它而不必互相引用——若把它放进 {@code rules}，物理就得反过来依赖规则了
 *
 * <p>所有回调都在模拟线程（{@code GameLoop}）内同步调用，实现方不应在这里做重活，
 * 更不应反向修改 {@code Ball} 的状态——那会破坏确定性。发声这条尤其要守：
 * {@code ShotSounds} 在回调里<b>只把 {@code Hit} 塞进队列</b>，混音与写声卡都在音频线程，
 * 于是「挂不挂声音」对球的运动没有任何影响，这一点有断言钉着
 */
public interface ShotEventListener {

    /**
     * 两颗球发生碰撞。{@code aId} 与 {@code bId} 是球号，可能是 0（主球）
     *
     * @param normalSpeed 碰撞<b>前</b>沿法向的相对接近速度（px/s），恒为正
     *                    它是「这一下有多重」的唯一客观度量：规则层用不上，
     *                    但声音要按它决定音量与音色，否则所有碰撞只能是一个响度
     */
    void onBallBallContact(int aId, int bId, Vec2 contactPoint, double normalSpeed);

    /**
     * 球碰到库边。{@code railIndex} 是 {@link Table#rails()} 里的下标
     *
     * @param normalSpeed 碰撞前沿库边内法向的速度分量绝对值（px/s），恒为正
     */
    void onCushionContact(int ballId, int railIndex, double normalSpeed);

    /**
     * 球落袋。落袋后该球的 {@code onTable} 已置为 false
     *
     * @param speed 落袋瞬间的球速（px/s）
     */
    void onBallPocketed(int ballId, int pocketIndex, double speed);

    /** 通知球离开台面，二维引擎使用它处理异常越界 */
    void onBallOffTable(int ballId);
}

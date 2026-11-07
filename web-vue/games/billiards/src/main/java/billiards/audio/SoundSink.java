package billiards.audio;

/**
 * 声音的出口
 *
 * <p>它是一层<b>故意做薄的接口</b>，为的是两件事：
 *
 * <ol>
 *   <li><b>模拟侧不认识声卡。</b> {@code app.MatchSession} 只持有这个类型，
 *       于是自检构造 {@code MatchSession} 时默认拿到的是 {@link #SILENT}——
 *       在<b>构造上</b>不可能去打开音频设备。自检要在没有声卡的机器上跑，
 *       这不是「小心一点」能保证的</li>
 *   <li><b>发声这件事本身可测。</b> 自检可以用一个只记账不发声的实现替换掉真出口，
 *       断言「这一杆该响几下、每下多重」，全程不需要音频硬件</li>
 * </ol>
 *
 * <p>{@link #hit} 的实现<b>不许抛异常</b>、也不许阻塞：它是在模拟线程上被调用的
 * 声音出问题是小事，把模拟拖住是大事
 */
public interface SoundSink {

    /** 静音出口，自检或音频设备不可用时使用 */
    SoundSink SILENT = new SilentSoundSink();

/**
     * 一次打击事件。实现方应当立刻返回——真正的混音与播放交给别的线程
     *
     * @param hit 事件值，不可变
     */
    void hit(Hit hit);

    /**
     * 是否真的能发出声。默认 {@code false}，因为「没接上设备」是本接口的常态而非异常
     *
     * <p>存在的理由是给调试浮层一句实话：玩家按了 M 却听不见、也分不清是静音了还是
     * 这台机器压根没声卡时，浮层上要能看出区别
     */
    default boolean isLive() {
        return false;
    }

    /** 释放资源。默认什么都不做，给静音实现和测试替身用 */
    default void close() {
    }

    /** 不播放声音的实现，供自检和无音频设备时使用 */
    final class SilentSoundSink implements SoundSink {

        /** 静音出口收到声音事件时不做处理 */
        @Override
        public void hit(Hit hit) {
        }
    }
}

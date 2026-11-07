package billiards.audio;

/**
 * 取一个能用的声音出口。全局唯一需要 {@code javax.sound} 的入口在这里收口
 *
 * <p>本类存在的唯一理由是那条工程纪律：<b>声音永远不许把游戏弄坏</b>
 * 没有声卡、驱动被占用、远程桌面里没有音频设备、容器里什么都没有——这些都只该
 * 导致「这次没声音」，而不是「游戏起不来」。所以这里不抛异常，只降级
 *
 * <p>{@link #open()} 不会阻塞等待设备：真正的 {@code SourceDataLine} 是在
 * {@link MixerSink} 自己的线程上开的，开不起来它就静默退出。于是启动速度与
 * 有没有声卡无关——这一点在演示机上很重要
 */
public final class Sound {

    /** 工具类只提供静态方法，不需要创建对象 */
    private Sound() {
    }

    /**
     * 拿一个声音出口。拿不到就返回 {@link SoundSink#SILENT}，调用方不需要处理失败
     *
     * <p>返回的对象可能<b>暂时</b>还没开好线（{@link MixerSink#isLive()} 为 false），
     * 随后自己会好；也可能永远好不了。两种情况对调用方都是同一件事：往里塞事件就行
     */
    public static SoundSink open() {
        try {
            return new MixerSink();
        } catch (Exception | LinkageError error) {
            // 构造本身都失败（例如线程都起不来）——退回静音
            // 刻意不把异常抛给调用方：为了一门课程演示，静音永远好过崩溃
            return SoundSink.SILENT;
        }
    }
}

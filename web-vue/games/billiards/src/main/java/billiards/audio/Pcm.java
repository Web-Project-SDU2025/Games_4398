package billiards.audio;

/**
 * 16 位小端 PCM 的格式契约：一个 {@code double} 样本怎么变成两个字节
 *
 * <p>这个类是 M8.2 里从 {@code WaveSynth} 摘出来的。摘出来的理由不是整洁，是那一步
 * <b>把代码合成换成了采样</b>——合成器整个删掉了，但格式约定必须原样留下：实时混音
 * （{@link MixerSink}）、总线量化（{@link Bus}）、离线导出（{@code selfcheck.SoundAudition}）
 * 都只经由这里落地成字节
 *
 * <p><b>本类刻意不含任何 {@code javax.sound} 的引用</b>，同 {@code world.Viewport} 不含
 * {@code java.awt}：纯函数才能无头测，声卡只出现在 {@link MixerSink} 里
 *
 * <p>三个方法都是 {@code public}，因为自检要直接对着它们写断言而不是隔着一次发声去猜
 * 尤其 {@link #pcm16} 与 {@link #writePcm16} 被实时那条路共用——<b>正因为共用，自检钉住的
 * 小端字节序才真的覆盖了出声的那条路</b>，而不是覆盖一个只有自检会走的副本
 */
public final class Pcm {

    /** 工具类只提供静态方法，不需要创建对象 */
    private Pcm() {
    }

    /** 整段波形 → {@code samples.length * 2} 字节的小端 PCM */
    public static byte[] toPcm16(double[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int index = 0; index < samples.length; index = index + 1) {
            writePcm16(out, index, pcm16(samples[index]));
        }
        return out;
    }

    /**
     * 单个样本 → 16 位有符号整数（−32768..32767）。<b>限幅在这里</b>，调用方不必先自己钳
     *
     * <p>{@code NaN} 走的是比较全为假那条路，于是落到 {@code Math.round(NaN) == 0}——
     * 坏值降级为静音而不是噪声，这一点由自检钉住
     */
    public static int pcm16(double value) {
        if (value > 1.0) {
            value = 1.0;
        } else if (value < -1.0) {
            value = -1.0;
        }
        return (int) Math.round(value * 32767.0);
    }

    /**
     * 把 16 位样本按<b>小端</b>写进 {@code out} 的第 {@code index} 个样本位
     * （即字节 {@code 2*index} 与 {@code 2*index+1}）
     *
     * <p>字节序单独抽出来，是因为它是个格式契约：散落的 {@code (byte)(q & 0xFF)} 只要有一处
     * 写成大端，得到的不是报错而是白噪声——那种错查起来最费劲
     */
    public static void writePcm16(byte[] out, int index, int sample) {
        out[index * 2] = (byte) (sample & 0xFF);
        out[index * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
}

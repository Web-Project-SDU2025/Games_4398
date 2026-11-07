package billiards.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.InputStream;

/**
 * 两段撞击采样的仓库：把 wav 读成 {@code double[]}，并按工程约定收拾干净
 *
 * <h2>为什么这里读文件不算破例</h2>
 *
 * <p>M8.2 之前，本工程的音效是<b>代码合成</b>的，理由是「零素材」。那一版做出来之后，
 * 用户戴上耳机判了「闭着眼睛以为在敲锣打鼓」，并带来了课程示例里的真实录音
 * 于是这条约束在音频上被<b>明确放弃</b>了——图形仍然是程序化绘制，只有这两个声音改成了采样
 * 取舍的经过记在 {@code docs/ANALYSIS.md}，来源见同目录的 {@code PROVENANCE.txt}
 *
 * <h2>收拾什么，为什么</h2>
 *
 * <p>素材是原始录音，直接播会在两处露馅：
 *
 * <ul>
 *   <li><b>头部静音</b>——球碰球前面有 57ms、落袋前面有 100ms 的空白。不移掉，
 *       声音就比画面晚半拍，落袋尤其明显（球都进袋一百毫秒了才「咚」）</li>
 *   <li><b>峰值差 10dB</b>——球碰球峰值只有满量程的 31%，落袋是 98%。不归一，
 *       两种声音的相对响度就成了录音师当时的选择，而不是我们能调的参数</li>
 * </ul>
 *
 * <p>所以：双声道合成单声道（两声道本来就相同）、按峰值比例找到真正的起音点、
 * 掐掉首尾静音、峰值归一到 1.0，最后在两端各加一小段淡入淡出——<b>「首末样本恰好为 0」
 * 因此是被构造出来的，而不是碰巧</b>。相对响度交给 {@link AudioConfig} 里那两个
 * {@code *_GAIN_*}，那才是调音的地方
 *
 * <p>素材文件本身<b>一个字节都没改</b>，上面这些全在加载时做：原始录音仍然可核对，
 * 参数也全都写在 {@link AudioConfig} 里、看得见改得动
 *
 * <p><b>本类不含任何 {@code javax.sound} 的「设备」引用</b>——只用到解码，不开线
 * 所以自检在完全没有声卡的机器上照样能加载并断言这两段波形
 */
public final class SampleBank {

    /** 资源路径。ASCII 文件名，避免任何编码问题 */
    private static final String DIR = "/billiards/audio/";

    /** 按 {@link Hit.Kind#ordinal()} 索引。没加载成功时是空数组 */
    private final double[][] samples;
    private final boolean loaded;

        /** 加载失败时返回的只读空波形，避免下游处理 null */
    private static final double[] EMPTY = new double[0];

/** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    private SampleBank(double[][] samples, boolean loaded) {
        this.samples = samples;
        this.loaded = loaded;
    }

    /**
     * 加载两段采样。**失败不抛异常**，只返回一个「没加载」的仓库——理由同
     * {@link Sound#open()}：声音永远不许把游戏弄坏。资源缺失时游戏照常跑，只是没声音，
     * 而这件事由自检里那条「两段采样都能从 classpath 读出来」负责喊出来
     */
    public static SampleBank load() {
        double[][] loadedSamples = new double[Hit.Kind.values().length][];
        try {
            loadedSamples[Hit.Kind.BALL.ordinal()] = read(DIR + "ball.wav");
            loadedSamples[Hit.Kind.POCKET.ordinal()] = read(DIR + "pocket.wav");
        } catch (Exception | LinkageError error) {
            return new SampleBank(new double[Hit.Kind.values().length][], false);
        }
        return new SampleBank(loadedSamples, true);
    }

    /** 供自检注入合成波形用：不碰文件，于是混音与增益规则可以脱离素材单独断言 */
    public static SampleBank of(double[] ball, double[] pocket) {
        double[][] suppliedSamples = new double[Hit.Kind.values().length][];
        suppliedSamples[Hit.Kind.BALL.ordinal()] = ball;
        suppliedSamples[Hit.Kind.POCKET.ordinal()] = pocket;
        return new SampleBank(suppliedSamples, true);
    }

    /** 两段采样是否都读进来了 */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 某种音色的波形。没加载成功时返回空数组——调用方（{@link Bus}）据此<b>不发声</b>，
     * 而不是拿到 {@code null} 再崩
     */
    public double[] pcm(Hit.Kind kind) {
        double[] waveform = samples[kind.ordinal()];
        if (waveform == null) {
            return EMPTY;
        }
        return waveform;
    }

    /** 某种音色的帧数。诊断与自检用 */
    public int frames(Hit.Kind kind) {
        return pcm(kind).length;
    }

    // 读与收拾

    /**
     * 读一个 wav，返回收拾好的单声道波形（峰值 1.0，首末样本为 0）
     *
     * <p>解码与声道下混都交给 {@code AudioSystem} 自己的转换器，而不是手搓 wav 头解析：
     * PCM 线性转换是 JDK 里成熟的一块，自己写只会多出一堆「这个 chunk 没见过」的坑
     */
    private static double[] read(String resource) throws Exception {
        try (InputStream raw = SampleBank.class.getResourceAsStream(resource)) {
            if (raw == null) {
                throw new IllegalStateException("资源不存在：" + resource);
            }
            AudioFormat target = new AudioFormat(AudioConfig.SAMPLE_RATE,
                    AudioConfig.BITS_PER_SAMPLE, AudioConfig.CHANNELS, true, false);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(target,
                    AudioSystem.getAudioInputStream(raw))) {
                return prepare(decode16(in.readAllBytes()));
            }
        }
    }

    /** 小端 16 位字节流 → [-1, 1] 的 double。字节序与 {@link Pcm} 是同一条契约 */
    private static double[] decode16(byte[] bytes) {
        int count = bytes.length / 2;
        double[] samples = new double[count];
        for (int sampleIndex = 0; sampleIndex < count; sampleIndex = sampleIndex + 1) {
            int lowByte = bytes[2 * sampleIndex] & 0xFF;
            int highByte = bytes[2 * sampleIndex + 1];
            samples[sampleIndex] = (short) ((highByte << 8) | lowByte) / 32768.0;
        }
        return samples;
    }

    /** 掐掉首尾静音、峰值归一、两端淡入淡出 */
    private static double[] prepare(double[] samples) {
        if (samples.length == 0) {
            return samples;
        }
        double peak = 0.0;
        for (double value : samples) {
            peak = Math.max(peak, Math.abs(value));
        }
        if (peak <= 0.0) {
            return new double[0];
        }

        // 阈值取峰值的固定比例而不是绝对电平：这样换一段录音也不用重调参数
        // 头尾各用一个阈值，理由见 AudioConfig 里那两条常量
        double headThr = peak * AudioConfig.TRIM_HEAD_FRACTION;
        int head = 0;
        while (head < samples.length && Math.abs(samples[head]) <= headThr) {
            head = head + 1;
        }
        double tailThr = peak * AudioConfig.TRIM_TAIL_FRACTION;
        int tail = samples.length - 1;
        while (tail > head && Math.abs(samples[tail]) <= tailThr) {
            tail = tail - 1;
        }
        if (head >= tail) {
            return new double[0];
        }

        // 往前留一点点，别把起音前沿本身切掉
        int lead = (int) Math.round(AudioConfig.TRIM_LEAD_S * AudioConfig.SAMPLE_RATE);
        head = Math.max(0, head - lead);
        int count = tail - head + 1;

        double[] out = new double[count];
        for (int sampleIndex = 0; sampleIndex < count; sampleIndex = sampleIndex + 1) {
            out[sampleIndex] = samples[head + sampleIndex] / peak;
        }
        fadeEdges(out);
        return out;
    }

    /**
     * 两端各加一段余弦淡入淡出
     *
     * <p>归一之后波形在两端仍有几百分之一的幅度，直接起播或截断都是一步阶跃，
     * 那声「咔哒」在安静的机器上听得见。淡完两端就<b>恰好</b>是 0
     */
    private static void fadeEdges(double[] samples) {
        int fraction = Math.min((int) Math.round(AudioConfig.EDGE_FADE_S * AudioConfig.SAMPLE_RATE),
                samples.length / 2);
        for (int fadeIndex = 0; fadeIndex < fraction; fadeIndex = fadeIndex + 1) {
            double weight = 0.5 - 0.5 * Math.cos(Math.PI * fadeIndex / fraction);
            samples[fadeIndex] = samples[fadeIndex] * weight;
            int tailIndex = samples.length - 1 - fadeIndex;
            samples[tailIndex] = samples[tailIndex] * weight;
        }
        samples[0] = 0.0;
        samples[samples.length - 1] = 0.0;
    }
}

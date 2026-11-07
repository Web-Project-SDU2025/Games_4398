package billiards.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * 总线：把若干正在发声的 voice 叠成一个缓冲块，软限幅、量化成 PCM
 *
 * <p>它<b>不认识声卡，也不认识时间</b>——只知道「现在有哪些 voice、各自播到哪了」
 * 这条分界让同一段混音代码能跑在两个地方：{@link MixerSink} 的音频线程（把块写进
 * {@code SourceDataLine}），以及 {@code selfcheck.SoundAudition} 的离线导出（把块写进 wav）
 * 于是试听导出听到的<b>就是</b>游戏里那条路径，而不是照着它另抄的一个近似实现
 *
 * <p>没有 {@code javax.sound} 的引用，理由同 {@link Pcm}：纯逻辑才能在没有音频设备的
 * 机器上跑，也才能被自检直接断言（{@link #gainFor}、{@link #limit} 与
 * {@link MixerSink#loudestPerKind} 是同一约定下的另外几处）
 *
 * <h2>力度只调音量</h2>
 *
 * <p>M8.2 换成采样之后，同一次撞击只有一条波形，力度<b>只能调音量</b>——合成那版
 * 「越重越脆」的做法（频带随力度上移、衰减随力度变短）没有了。这是明确接受的代价，
 * 理由写在 {@link AudioConfig} 里。于是本类只剩一件事要做：把采样按 {@link #gainFor}
 * 缩放后叠起来
 *
 * <p>缩放是逐样本乘法、增益是连续量，所以<b>响度随力度连续变化</b>，没有被量化成台阶
 */
public final class Bus {

    /** 一个正在发声的音色实例 */
    private static final class Voice {
        final double[] samples;
        /** 该 voice 起播时的增益，{@link #renderInto} 逐样本乘上去 */
        final double gain;
        /** 剩余能量，用来决定并发满了该抢谁。峰值 × 响度，够用 */
        final double energy;
        int pos;

        /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
        Voice(double[] samples, double gain, double energy) {
            this.samples = samples;
            this.gain = gain;
            this.energy = energy;
        }

        /** 返回这段音频是否已播放完毕 */
        boolean done() {
            return pos >= samples.length;
        }
    }

    private final List<Voice> voices = new ArrayList<>(AudioConfig.MAX_VOICES);
    private final double[] mix = new double[AudioConfig.BUFFER_FRAMES];

    private final SampleBank bank;

    private long droppedByCap;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public Bus(SampleBank bank) {
        this.bank = bank;
    }

    /** 起一个音色。并发数满了就抢掉最弱的那个 */
    public void add(Hit hit) {
        double[] samples = bank.pcm(hit.kind());
        if (samples.length == 0) {
            return;
        }
        double gain = gainFor(hit.kind(), hit.strength());
        Voice voice = new Voice(samples, gain, energyOf(samples, gain));

        if (voices.size() < AudioConfig.MAX_VOICES) {
            voices.add(voice);
            return;
        }
        droppedByCap = droppedByCap + 1;
        int weakest = 0;
        double weakestEnergy = Double.MAX_VALUE;
        for (int index = 0; index < voices.size(); index = index + 1) {
            double energy = voices.get(index).energy;
            if (energy < weakestEnergy) {
                weakestEnergy = energy;
                weakest = index;
            }
        }
        if (weakestEnergy < voice.energy) {
            voices.set(weakest, voice);
        }
    }

    /**
     * 力度 → 增益：在 {@link AudioConfig} 那对 {@code *_GAIN_LO/HI} 之间线性插值
     *
     * <p>是<b>公开的</b>，因为自检要直接对着这条曲线写断言——「越重越响」这件事
     * 现在完全落在这里，隔着一次混音去猜它没有意义
     *
     * <p>插值而不是查表：增益是连续的，所以轻碰与重击之间没有台阶
     */
    public static double gainFor(Hit.Kind kind, double strength) {
        double minimum;
        double maximum;
        if (kind == Hit.Kind.POCKET) {
            minimum = AudioConfig.POCKET_GAIN_LO;
            maximum = AudioConfig.POCKET_GAIN_HI;
        } else {
            minimum = AudioConfig.BALL_GAIN_LO;
            maximum = AudioConfig.BALL_GAIN_HI;
        }
        double normalizedStrength = strength;
        if (normalizedStrength < 0.0) {
            normalizedStrength = 0.0;
        }
        if (normalizedStrength > 1.0) {
            normalizedStrength = 1.0;
        }
        return minimum + (maximum - minimum) * normalizedStrength;
    }

    /**
     * 渲染下一个缓冲块（{@link AudioConfig#BUFFER_FRAMES} 帧）到 {@code out}，
     * 并把所有 voice 往前推一格
     *
     * <p>缓冲由调用方提供而不是每次新分配：实时那条路每秒要走四十多个块，
     * 让它每块新建一个 2KB 数组是白给的垃圾
     */
    public void renderInto(byte[] out) {
        java.util.Arrays.fill(mix, 0.0);
        for (Voice voice : voices) {
            int count = Math.min(mix.length, voice.samples.length - voice.pos);
            for (int index = 0; index < count; index = index + 1) {
                mix[index] = mix[index] + voice.samples[voice.pos + index] * voice.gain;
            }
        }

        for (int indexValue = 0; indexValue < mix.length; indexValue = indexValue + 1) {
            // 量化与字节序都走 Pcm：那里的自检断言因此覆盖的正是这条出声路径
            Pcm.writePcm16(out, indexValue, Pcm.pcm16(limit(mix[indexValue] * AudioConfig.BUS_GAIN)));
        }

        // 从后向前移除播完的音色，避免删除后跳过相邻对象
        for (int voiceIndex = voices.size() - 1; voiceIndex >= 0; voiceIndex = voiceIndex - 1) {
            Voice voice = voices.get(voiceIndex);
            voice.pos = voice.pos + mix.length;
            if (voice.done()) {
                voices.remove(voiceIndex);
            }
        }
    }

    /** 当前有没有 voice 在响。导出时用它决定还要不要继续渲染 */
    public boolean isEmpty() {
        return voices.isEmpty();
    }

    /** 因并发上限被丢掉的事件数。诊断用，正常玩应该一直是 0 */
    public long droppedByCap() {
        return droppedByCap;
    }

    /** 总线软限幅 */
    public static double limit(double sample) {
        double amplitude = Math.abs(sample);
        if (amplitude <= AudioConfig.LIMIT_KNEE) {
            return sample;
        }
        double over = (amplitude - AudioConfig.LIMIT_KNEE) / (1.0 - AudioConfig.LIMIT_KNEE);
        double shaped = AudioConfig.LIMIT_KNEE + (1.0 - AudioConfig.LIMIT_KNEE) * Math.tanh(over);
        if (sample < 0.0) {
            return -shaped;
        }
        return shaped;
    }

    /** 根据采样峰值和增益估算响度，供并发满时选择替换对象 */
    private static double energyOf(double[] samples, double gain) {
        double peak = 0.0;
        for (double value : samples) {
            peak = Math.max(peak, Math.abs(value));
        }
        return peak * gain;
    }
}

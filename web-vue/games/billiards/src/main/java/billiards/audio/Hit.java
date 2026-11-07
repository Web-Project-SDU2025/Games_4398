package billiards.audio;

/**
 * 一次待发声的打击事件：<b>什么声音</b> ＋ <b>多重</b>
 *
 * <p>它是模拟线程与音频线程之间唯一传递的东西——一个不可变值，没有引用、没有回调、
 * 没有可被后续修改的状态。音频因此可以整条从模拟里摘掉（换成 {@link SoundSink#SILENT}）
 * 而不改变任何球的运动，这一点由自检里那条「挂不挂 sink，开球末位置按位相同」钉住
 *
 * <p>{@code strength} 归一化到 0–1，是<b>物理量到音量的映射结果</b>，不是物理量本身
 * 映射发生在 {@link ShotSounds} 里，理由与力度映射留在 {@code input.ShotPower}
 * 完全一样：底层只认物理单位，界面/听觉的标度留在各自的包
 */
public final class Hit {
    private final Kind kind;
    private final double strength;

    /** 保存构造参数，字段只读以便安全传递状态 */
    public Hit(Kind kind, double strength) {

        strength = clamp01(strength);
            this.kind = kind;
        this.strength = strength;
    }

    /** 返回保存的指令或声音类型 */
    public Kind kind() {
        return kind;
    }

    /** 返回保存的声音力度 */
    public double strength() {
        return strength;
    }

    /**
     * 音色表。顺序即 {@link SampleBank} 里波形表的顺序
     *
     * <p><b>只有两种</b>，而且这是用户的决定，不是没做完：原话是「就只做球与球碰，
     * 球进袋的声音就行了，其他的声音不做」。M8 那一版还有库边与出杆两种合成音色，
     * M8.2 换成素材时一并去掉了——库边因此是静音的（{@link ShotSounds#onCushionContact}），
     * 出杆也不再发声（{@code MatchSession.fire} 里那句已删）
     *
     * <p>台面上球撞库边的次数远多于球撞球，去掉它之后整局会安静不少。这是想要的：
     * 课程示例里的那两个声音就是这个分量，多出来的反而盖住它们
     */
    public enum Kind {
        /** 球碰球——台球最标志性的那声「嗒」 */
        BALL,
        /** 落袋——球掉进去，加袋底弹两下 */
        POCKET
    }

    /** 静音（力度低于可闻下限）。调用方据此跳过，省掉一次混音 */
    public boolean isSilent() {
        return strength <= 0.0;
    }

    /** 将传入数值限制在零到一之间，供力度或音量换算使用 */
    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}

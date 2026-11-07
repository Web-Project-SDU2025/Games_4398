package billiards.selfcheck;

import billiards.app.MatchSession;
import billiards.audio.AudioConfig;
import billiards.audio.Bus;
import billiards.audio.Hit;
import billiards.audio.MixerSink;
import billiards.audio.Pcm;
import billiards.audio.SampleBank;
import billiards.audio.ShotSounds;
import billiards.audio.SoundSink;
import billiards.render.MatchView;
import billiards.rules.GamePhase;
import billiards.rules.Player;
import billiards.rules.RulesEngine;
import billiards.world.Ball;
import billiards.world.BallGroup;
import billiards.world.Vec2;

import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 声音检查（A 组）
 *
 * <h2>这一组为什么一行声卡代码都没有</h2>
 * 自检要在没有音频设备的机器上跑（容器、远程桌面、CI），所以它<b>不许</b>去开输出线——
 * 「小心一点」保证不了这件事，得让它在构造上做不到：{@link Pcm} 里没有 {@code javax.sound}，
 * {@link SampleBank} 只用它的解码器、不开线，{@link MixerSink} 的合流与限幅被抽成了纯函数，
 * 而真出口（{@code Sound.open()}）只有 GUI 会调。于是整组断言跑完，机器上什么都没响
 *
 * <h2>M8.2：这一组跟着音源换了，但三条主张没换</h2>
 * 换素材之后，凡是<b>量合成器</b>的断言都失去了对象（波形矩阵、变奏互异、过零率亮度排序），
 * 它们连同合成器一起删了。剩下的三类性质与音源无关，只是换了问法：
 * <ul>
 *   <li><b>素材本身</b>——两段都读得进来、长度合理、有界、首尾归零、头部静音已去
 *       第一条尤其重要：资源不进 classpath 是个<b>只会在演示机上现形</b>的缺陷，
 *       而它的症状是沉默——正是本项目花最大力气让它不可能发生的那一类</li>
 *   <li><b>物理量到音量的映射</b>——哪类事件发哪个声、撞多快算多响。换成采样之后
 *       「多响」完全落在 {@link Bus#gainFor} 上，所以这条现在比 M8 更该钉</li>
 *   <li><b>声音不影响球的运动</b>——这是 M8 唯一的<b>结构性</b>风险：声音一旦挂上物理
 *       回调，它就有机会改变物理。用「挂与不挂，一杆的末位置按位相同」把它堵死</li>
 * </ul>
 *
 * <p><b>这一组仍然不管「像不像」。</b>M8 的教训还在：31 项全绿而声音是敲锣打鼓
 * 音色好不好听只能靠耳朵，交付物里的 wav 导出就是为这件事准备的
 */
public final class AudioChecks {

        /** 参与增益断言的四档力度。0 与 1 是端点，中间两档代表「轻推」与「重击」 */
    private static final double[] STRENGTHS = {0.0, 0.25, 0.5, 0.75, 1.0};

    /** 撞击速度夹具（px/s）。取几档跨越 {@link AudioConfig#SILENT_SPEED} 到满力 */
    private static final double[] SPEEDS = {10.0, 44.0, 200.0, 900.0, 2400.0, 4000.0};

    /** 检查使用的水平向右单位方向 */
    private static final Vec2 RIGHT = new Vec2(1.0, 0.0);

    /**
     * 一杆正面直撞的夹具：主球在左、1 号球在右、另有一颗 9 号球偏在下侧
     *
     * <p>直撞不斜切，是为了让「有没有碰到、碰了几下」不依赖子步检测的相位——同样的局面
     * 每次跑出的接触次数都一样，合流与确定性两条断言才有确定的东西可比
     * 那颗 9 号球是故意放的：主球撞开 1 号之后会把它也带上，于是监听器收到的事件不止一条，
     * 「挂上声音之后物理一个比特都没变」才不至于因为只有一次接触而显得太容易满足
     */
    private static final List<RulesEngine.BallPlacement> STRAIGHT_SHOT = List.of(
            new RulesEngine.BallPlacement(0, 250.0, 320.0),
            new RulesEngine.BallPlacement(1, 600.0, 320.0),
            new RulesEngine.BallPlacement(9, 640.0, 400.0));

    /** 一杆推进的步数上限：物理自己会在硬超时处收手，这个上限只是防死循环 */
    private static final int MAX_TICKS = 20000;

    /** 判定横幅走完的步数上限 */
    private static final int MAX_BANNER_TICKS = 1000;

/** 工具类只提供静态方法，不需要创建对象 */
    private AudioChecks() {
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    public static void run(CheckHarness checks) {
        checkSamples(checks);
        checkGain(checks);
        checkPcm(checks);
        checkAdapter(checks);
        checkMixerRules(checks);
        checkSessionIntegration(checks);
        checkDeterminism(checks);
    }

    // 素材

    /** 两段采样本身：读得进来、长度合理、有界、首尾归零、头部静音已去 */
    private static void checkSamples(CheckHarness checks) {
        SampleBank bank = SampleBank.load();
        checks.check("素材：两段 wav 都能从 classpath 读进来（打包漏了资源的话，这一条是唯一的警报）",
                bank.isLoaded(), "SampleBank.load() 报未加载");

        String empty = null;
        String outOfRange = null;
        String headZero = null;
        String tailZero = null;
        for (Hit.Kind kind : Hit.Kind.values()) {
            double[] samples = bank.pcm(kind);
            double minimum = 0.05 * AudioConfig.SAMPLE_RATE;
            double maximum = 2.0 * AudioConfig.SAMPLE_RATE;
            if (samples.length < minimum || samples.length > maximum) {
                empty = String.format("%s：%d 帧（%.3f 秒），预期 %.2f–%.2f 秒",
                        kind, samples.length, samples.length / AudioConfig.SAMPLE_RATE, 0.05, 2.0);
            }
            for (int index = 0; index < samples.length; index = index + 1) {
                if (!(Math.abs(samples[index]) <= 1.0)) {
                    outOfRange = String.format("%s：第 %d 个样本为 %.6f", kind, index, samples[index]);
                    break;
                }
            }
            if (samples.length > 0 && samples[0] != 0.0) {
                headZero = String.format("%s：首样本为 %.6g", kind, samples[0]);
            }
            if (samples.length > 0 && samples[samples.length - 1] != 0.0) {
                tailZero = String.format("%s：末样本为 %.6g", kind, samples[samples.length - 1]);
            }
        }
        checks.check("素材：两段长度都在合理量级（读丢了或读重了会在这里现形）",
                empty == null, empty);
        checks.check("素材：样本全部有界（|x| ≤ 1）", outOfRange == null, outOfRange);
        checks.check("素材：首样本恰好为 0（起播不产生阶跃）", headZero == null, headZero);
        checks.check("素材：末样本恰好为 0（收尾不产生截断咔哒）", tailZero == null, tailZero);

        // 峰值归一：加载时除以各自峰值，所以两种声音的相对响度由 AudioConfig 的增益决定，
        // 而不是由两段录音当初录得多响决定（原始素材差着 10dB）
        String notNormalized = null;
        for (Hit.Kind kind : Hit.Kind.values()) {
            double peak = peakOf(bank.pcm(kind));
            if (!(peak >= 0.99 && peak <= 1.0)) {
                notNormalized = String.format("%s：峰值 %.6f", kind, peak);
            }
        }
        checks.check("素材：两段都峰值归一（相对响度归增益管，不归录音电平管）",
                notNormalized == null, notNormalized);

        // 头部静音已去：素材前面有 57ms / 100ms 的空白，不去掉声音就比画面晚半拍
        // 判据是「起音落在开头几毫秒内」——这正是 trim 做没做的直接后果
        //
        // 取 5% 而不是更高的比例，是因为落袋的起音不是一步到位：它花约 3ms 从 1% 爬到
        // 满幅，那是这段录音本身的形状，不是缺陷。5% 已经远超素材的底噪（归一后约 1%），
        // 所以它既能认出「声音真的开始了」，又不会因为起音形状不同而误判
        String lateOnset = null;
        for (Hit.Kind kind : Hit.Kind.values()) {
            double[] samplesValue = bank.pcm(kind);
            int onset = 0;
            while (onset < samplesValue.length && Math.abs(samplesValue[onset]) < 0.05) {
                onset = onset + 1;
            }
            double ms = 1000.0 * onset / AudioConfig.SAMPLE_RATE;
            if (ms > 6.0) {
                lateOnset = String.format("%s：起音在第 %.1f ms（超过 6ms 说明头部静音没掐掉）",
                        kind, ms);
            }
        }
        checks.check("素材：头部静音已掐掉，起音落在开头 6ms 内（否则落袋比画面晚一百毫秒）",
                lateOnset == null, lateOnset);

        // 换素材之后「导出的 wav 可复现」这件事仍然要成立：加载是确定的
        SampleBank again = SampleBank.load();
        boolean stable = again.isLoaded();
        for (Hit.Kind kind : Hit.Kind.values()) {
            stable = stable & (sameWave(bank.pcm(kind), again.pcm(kind)));
        }
        checks.check("素材：两次加载得到逐位相同的波形（导出的 wav 仍然可复现）", stable,
                "两次加载结果不同");

        // 两种音色必须是两段<b>不同</b>的波形——load() 里把两个文件接错是很容易犯的错，
        // 而它的症状是「球进袋听起来像球碰球」，光看代码看不出来
        checks.check("素材：两种音色确实是两段不同的波形（两个文件没有接反或复制粘贴）",
                !sameWave(bank.pcm(Hit.Kind.BALL), bank.pcm(Hit.Kind.POCKET)),
                "球碰球与落袋的波形完全相同");
    }

    // 力度 → 音量

    /**
     * 力度只调音量，而这件事完全落在 {@link Bus#gainFor} 上
     *
     * <p>所以这里既断言那条曲线本身（单调、满力至少是最轻的两倍），也断言它<b>真的接到了
     * 输出上</b>：用真实素材过一遍 {@link Bus}，把渲染出来的 PCM 解回来量峰值。只测
     * {@code gainFor} 是不够的——增益算对了但忘了乘上去，曲线照样单调
     */
    private static void checkGain(CheckHarness checks) {
        String notMonotone = null;
        String notLouder = null;
        for (Hit.Kind kind : Hit.Kind.values()) {
            double previous = -1.0;
            for (double strength : STRENGTHS) {
                double gain = Bus.gainFor(kind, strength);
                if (gain < previous - 1e-12 && notMonotone == null) {
                    notMonotone = String.format("%s：力度 %.2f 的增益 %.4f 低于上一档的 %.4f",
                            kind, strength, gain, previous);
                }
                previous = gain;
            }
            double ratio = Bus.gainFor(kind, 1.0) / Bus.gainFor(kind, 0.0);
            if (ratio < 2.0 && notLouder == null) {
                notLouder = String.format("%s：满力只有最轻的 %.2f 倍", kind, ratio);
            }
        }
        checks.check("力度：两种音色的增益都随力度单调不减", notMonotone == null, notMonotone);
        checks.check("力度：满力一击的增益至少是最轻一碰的两倍", notLouder == null, notLouder);

        // 端到端：真的过一遍总线，量输出
        SampleBank bank = SampleBank.load();
        String notScaled = null;
        for (Hit.Kind kind : Hit.Kind.values()) {
            double quiet = renderedPeak(bank, new Hit(kind, 0.0));
            double loud = renderedPeak(bank, new Hit(kind, 1.0));
            if (!(quiet > 0.0) || !(loud > quiet * 2.0)) {
                notScaled = String.format("%s：轻 %.5f → 满力 %.5f", kind, quiet, loud);
            }
        }
        checks.check("力度：同一段素材，满力渲出来的峰值确实远高于最轻（增益真的乘上去了）",
                notScaled == null, notScaled);

        // 素材缺失时总线必须静音，而不是崩：这是「声音永远不许把游戏弄坏」的落地处
        Bus dead = new Bus(SampleBank.of(new double[0], new double[0]));
        dead.add(new Hit(Hit.Kind.BALL, 1.0));
        byte[] silence = new byte[AudioConfig.BUFFER_FRAMES * 2];
        dead.renderInto(silence);
        boolean allZero = true;
        for (byte linearTerm : silence) {
            allZero = allZero & (linearTerm == 0);
        }
        checks.check("力度：素材没加载进来时总线输出全零（没声音，但不崩）", allZero,
                "空仓库却输出了非零样本");
    }

    // PCM 编码

    /** 量化与字节序。这两条管线那头的东西——错了听到的不是报错，是白噪声 */
    private static void checkPcm(CheckHarness checks) {
        boolean ends = Pcm.pcm16(0.0) == 0
                && Pcm.pcm16(1.0) == 32767
                && Pcm.pcm16(-1.0) == -32767;
        checks.check("PCM：0 / +1 / −1 量化到 0 / 32767 / −32767", ends,
                String.format("实际 %d / %d / %d", Pcm.pcm16(0.0),
                        Pcm.pcm16(1.0), Pcm.pcm16(-1.0)));

        boolean clamped = Pcm.pcm16(2.0) == 32767
                && Pcm.pcm16(-2.0) == -32767
                && Pcm.pcm16(1e9) == 32767
                && Pcm.pcm16(-1e9) == -32767;
        checks.check("PCM：越界样本被夹到端点（回绕会变成反相的爆音）", clamped,
                String.format("实际 %.1f → %d，%.1f → %d",
                        2.0, Pcm.pcm16(2.0), -2.0, Pcm.pcm16(-2.0)));

        // +1 → 0x7FFF 与 −1 → 0x8001：大端会得到 FF 7F 与 80 01，正好是这两组反过来
        byte[] bytes = Pcm.toPcm16(new double[]{1.0, -1.0, 0.0});
        boolean little = bytes.length == 6
                && bytes[0] == (byte) 0xFF && bytes[1] == 0x7F
                && bytes[2] == 0x01 && bytes[3] == (byte) 0x80
                && bytes[4] == 0x00 && bytes[5] == 0x00;
        checks.check("PCM：16 位小端字节序（低字节在前）", little,
                String.format("实际 %02X %02X | %02X %02X | %02X %02X",
                        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]));
    }

    // 适配器

    /** 记账用的出口：只记下收到了什么，不出声、不碰设备 */
    private static final class Recorder implements SoundSink {
        final List<Hit> hits = new ArrayList<>();

        /** 接收声音事件并交给当前出口，实际播放由音频线程处理 */
        @Override
        public void hit(Hit hit) {
            hits.add(hit);
        }

        /** 统计指定音色被测试出口接收的事件数 */
        int count(Hit.Kind kind) {
            int count = 0;
            for (Hit hit : hits) {
                if (hit.kind() == kind) {
                    count = count + 1;
                }
            }
            return count;
        }
    }

    /** 检查适配：球碰球 / 落袋 报成两种音色，将验证结果记入传入的检查报告 */
    private static void checkAdapter(CheckHarness checks) {
        // 四类物理事件各报一次，看哪些落成声音
        Recorder recorder = new Recorder();
        ShotSounds sounds = new ShotSounds(recorder);
        sounds.onBallBallContact(1, 2, new Vec2(0, 0), 900.0);
        sounds.onCushionContact(1, 3, 700.0);
        sounds.onBallPocketed(5, 0, 900.0);
        sounds.onBallOffTable(7);

        boolean mapped = recorder.hits.size() == 2
                && recorder.count(Hit.Kind.BALL) == 1
                && recorder.count(Hit.Kind.POCKET) == 1;
        checks.check("适配：球碰球 / 落袋 报成两种音色", mapped, "实际收到 " + recorder.hits);

        // 当前音效只包含球球碰撞与入袋，碰库和异常出台面不播放声音
        // 两条合一条断言，因为在这一层它们要证的同一件事是「事件订阅了却不出声」
        checks.check("适配：碰库与飞出台面都不发声（一个用户不要，一个只是兜底分支）",
                recorder.count(Hit.Kind.BALL) + recorder.count(Hit.Kind.POCKET) == 2,
                "实际收到 " + recorder.hits.size() + " 条：" + recorder.hits);

        // 撞击速度 → 力度：单调不减，且恒在 [0,1]
        String outOfRange = null;
        String notMonotone = null;
        double previous = -1.0;
        for (double speed : SPEEDS) {
            double strength = ShotSounds.strengthFor(speed);
            if (!(strength >= 0.0 && strength <= 1.0) && outOfRange == null) {
                outOfRange = String.format("速度 %.1f → 力度 %.6f", speed, strength);
            }
            if (strength < previous - 1e-12 && notMonotone == null) {
                notMonotone = String.format("速度 %.1f 的力度 %.6f 低于上一档的 %.6f",
                        speed, strength, previous);
            }
            previous = strength;
        }
        boolean endpoints = ShotSounds.strengthFor(0.0) == 0.0
                && ShotSounds.strengthFor(AudioConfig.SILENT_SPEED) == 0.0
                && ShotSounds.strengthFor(AudioConfig.FULL_SPEED) == 1.0
                && ShotSounds.strengthFor(AudioConfig.FULL_SPEED * 10.0) == 1.0;
        checks.check("适配：撞击越快力度越大、且恒在 0–1 之间",
                outOfRange == null && notMonotone == null && endpoints,
                describeStrengthFailure(outOfRange, notMonotone));

        checks.check("适配：NaN 撞击速度读成「不出声」，而不是 NaN 力度",
                ShotSounds.strengthFor(Double.NaN) == 0.0
                        && new Hit(Hit.Kind.BALL, Double.NaN).isSilent(),
                String.format("strengthFor(NaN) = %.6f", ShotSounds.strengthFor(Double.NaN)));

        // 静音阈值以下的接触一声不发：球停稳前后有大量速度趋近于零的接触判定
        Recorder quiet = new Recorder();
        ShotSounds quietSounds = new ShotSounds(quiet);
        quietSounds.onBallBallContact(1, 2, new Vec2(0, 0), AudioConfig.SILENT_SPEED - 1.0);
        quietSounds.onCushionContact(1, 0, 0.0);
        quietSounds.onBallPocketed(3, 0, 1.0);
        checks.check("适配：低于静音阈值的接触一条都不发（否则停稳时是一片沙沙声）",
                quiet.hits.isEmpty(), "实际收到 " + quiet.hits.size() + " 条");
    }

    /** 组合力度曲线检查中发现的第一个错误信息 */
    private static String describeStrengthFailure(String outOfRange, String notMonotone) {
        if (outOfRange != null) {
            return outOfRange;
        }
        if (notMonotone != null) {
            return notMonotone;
        }
        return "";
    }

    // 混音与限幅（纯逻辑部分）

    /** 检查合流：一批 12 下球碰球只留最响的一下（开球是一声砰，不是一串鞭炮），将验证结果记入传入的检查报告 */
    private static void checkMixerRules(CheckHarness checks) {
        // 合流：开球时十几下接触挤在几个固定步内，一次涌入只该出一声
        Hit[] batch = new Hit[12];
        for (int index = 0; index < batch.length; index = index + 1) {
            batch[index] = new Hit(Hit.Kind.BALL, 0.05 * (index + 1));
        }
        Hit[] best = MixerSink.loudestPerKind(batch);
        double loudest = 0.05 * batch.length;
        boolean oneBall = best[Hit.Kind.BALL.ordinal()] != null
                && Math.abs(best[Hit.Kind.BALL.ordinal()].strength() - loudest) < 1e-12;
        checks.check("合流：一批 12 下球碰球只留最响的一下（开球是一声砰，不是一串鞭炮）",
                oneBall, "留下的是 " + best[Hit.Kind.BALL.ordinal()]);

        Hit[] mixed = MixerSink.loudestPerKind(new Hit[]{
                new Hit(Hit.Kind.POCKET, 0.4),
                new Hit(Hit.Kind.BALL, 0.2), new Hit(Hit.Kind.BALL, 0.7),
        });
        boolean separate = mixed[Hit.Kind.POCKET.ordinal()].strength() == 0.4
                && mixed[Hit.Kind.BALL.ordinal()].strength() == 0.7;
        checks.check("合流：不同音色各留各的，互不吞并（落袋不会被同时的撞击吞掉）", separate,
                "实际 " + java.util.Arrays.toString(mixed));

        boolean emptyNull = true;
        for (Hit hit : MixerSink.loudestPerKind(new Hit[0])) {
            emptyNull = emptyNull & (hit == null);
        }
        checks.check("合流：空批不产生任何声音", emptyNull, "空批里出现了非 null 项");

        // 软限幅：拐点以下逐位恒等（安静时不被染色），全域有界（绝不回绕）
        double[] quiet = {-0.7, -0.5, -0.3, -0.05, 0.0, 0.05, 0.3, 0.5, 0.7};
        String colored = null;
        for (double x : quiet) {
            if (Bus.limit(x) != x && colored == null) {
                colored = String.format("limit(%.4f) = %.10f", x, Bus.limit(x));
            }
        }
        checks.check("限幅：拐点以下逐位恒等（安静时一点都不被染色）", colored == null, colored);

        double[] loud = {-1e9, -50.0, -3.0, -1.0, -0.71, 0.71, 1.0, 3.0, 50.0, 1e9};
        String unbounded = null;
        for (double x : loud) {
            double y = Bus.limit(x);
            if (!(Math.abs(y) <= 1.0) && unbounded == null) {
                unbounded = String.format("limit(%g) = %g", x, y);
            }
        }
        checks.check("限幅：全域有界（|limit| ≤ 1，任何叠加都不会回绕成爆音）",
                unbounded == null, unbounded);

        // 坏值降级成静音而不是爆音：NaN 一路走到量化会变成 0
        checks.check("限幅：NaN 样本最终量化成 0（坏值降级为静音，不是噪声）",
                Pcm.pcm16(Bus.limit(Double.NaN)) == 0,
                "实际 " + Pcm.pcm16(Bus.limit(Double.NaN)));
    }

    // 会话接线

    /**
     * 会话层：默认不出声、M 键真的静音、调试浮层报出状态
     *
     * <p>这里走的是<b>真实的一局</b>——同一个 {@code MatchSession}、同一个物理引擎，
     * 只是把出口换成一个只记账的实现。所以它同时证明了「挂上真出口能跑完一杆」
     */
    private static void checkSessionIntegration(CheckHarness checks) {
        checks.check("会话：静音出口本身就是「没设备」的答案（不自称在响）",
                !SoundSink.SILENT.isLive(), "SILENT.isLive() 为真");

        Recorder recorder = new Recorder();
        MatchSession session = new MatchSession(recorder);
        JPanel canvas = new JPanel();
        session.input().attach(canvas);

        // 一杆直撞：主球正面撞 1 号球，必然产生球碰球，多半还会碰库
        shootOnce(session);
        boolean audible = recorder.count(Hit.Kind.BALL) > 0;
        checks.check("会话：一杆直撞真的报出了球碰球（否则下面的静音断言是空过）",
                audible, "收到 " + recorder.hits.size() + " 条事件：" + recorder.hits);

        // 按 M 静音，再打同样一杆
        int before = recorder.hits.size();
        pressKey(canvas, KeyEvent.VK_M);
        session.tickFixed();
        shootOnce(session);
        checks.check("会话：按 M 之后一杆打出去一声都不响",
                recorder.hits.size() == before,
                "又收到 " + (recorder.hits.size() - before) + " 条：" + recorder.hits.subList(before, recorder.hits.size()));

        // 再按一次恢复
        pressKey(canvas, KeyEvent.VK_M);
        session.tickFixed();
        int beforeUndo = recorder.hits.size();
        shootOnce(session);
        checks.check("会话：再按一次 M 恢复出声", recorder.hits.size() > beforeUndo,
                "静音解除后一条都没收到");

        // 调试浮层里要能看出「是静音了还是这台机器没声卡」。到这一步为止按了两次 M，
        // 会话是出声状态；再按一次才静音
        pressKey(canvas, KeyEvent.VK_D);
        session.tickFixed();
        String onLine = findDebugLine(session, "声音");
        pressKey(canvas, KeyEvent.VK_M);
        session.tickFixed();
        String offLine = findDebugLine(session, "声音");
        // 「关」这个字是判据：它只出现在静音那一行（另一行写的是「声音 开」）
        checks.check("会话：调试浮层报出声音开关与设备有无（听不见时能分清是哪一种）",
                onLine != null && onLine.contains("开") && !onLine.contains("关")
                        && offLine != null && offLine.contains("关"),
                "出声时「" + onLine + "」，静音后「" + offLine + "」");
    }

    /** 声音挂上物理回调之后，球的运动必须一个比特都不变 */
    private static void checkDeterminism(CheckHarness checks) {
        RulesEngine bare = new RulesEngine();
        RulesEngine heard = new RulesEngine();
        Recorder recorder = new Recorder();
        heard.addListener(new ShotSounds(recorder));

        bare.arrange(STRAIGHT_SHOT, Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        heard.arrange(STRAIGHT_SHOT, Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);

        bare.takeShot(RIGHT, 1400.0);
        heard.takeShot(RIGHT, 1400.0);

        int ticks = 0;
        while (bare.phase() == GamePhase.SHOT_IN_FLIGHT && ticks < MAX_TICKS) {
            bare.tickFixed();
            heard.tickFixed();
            ticks = ticks + 1;
        }
        while (heard.phase() == GamePhase.RESOLVING && ticks < MAX_TICKS) {
            heard.tickFixed();
            ticks = ticks + 1;
        }

        String moved = null;
        for (int index = 0; index < bare.balls().size(); index = index + 1) {
            Ball ball = bare.balls().get(index);
            Ball ballValue = heard.balls().get(index);
            if (Double.doubleToLongBits(ball.position().x) != Double.doubleToLongBits(ballValue.position().x)
                    || Double.doubleToLongBits(ball.position().y)
                    != Double.doubleToLongBits(ballValue.position().y)
                    || ball.isOnTable() != ballValue.isOnTable()) {
                if (moved == null) {
                    moved = String.format("球 %d：不挂 %s，挂了 %s", ball.id(),
                            ball.position(), ballValue.position());
                }
            }
        }

        checks.check("确定性：挂上声音监听器的那一台确实收到了事件（否则下面一条是空过）",
                !recorder.hits.isEmpty(), "一条事件都没收到");
        checks.check("确定性：挂不挂声音，同一杆的末局面按位相同（声音改不了球的运动）",
                moved == null, moved);
    }

    // 工具

    /**
     * 摆一杆直撞并推进到判定横幅走完
     *
     * <p>循环条件读的是<b>引擎</b>的阶段而不是快照里的：摆局与出杆是绕过会话直接调引擎的，
     * 而快照要到下一次 {@code tickFixed} 才会反映出来——照快照判，循环会在第一次判断时
     * 就看到「还没开打」，一步都不推进（这一条正是被自检自己抓出来的）
     */
    private static void shootOnce(MatchSession session) {
        session.engine().arrange(STRAIGHT_SHOT, Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        session.engine().takeShot(RIGHT, 1400.0);

        int ticks = 0;
        while (session.engine().phase() == GamePhase.SHOT_IN_FLIGHT && ticks < MAX_TICKS) {
            session.tickFixed();
            ticks = ticks + 1;
        }
        ticks = 0;
        while (session.engine().phase() == GamePhase.RESOLVING && ticks < MAX_BANNER_TICKS) {
            session.tickFixed();
            ticks = ticks + 1;
        }
    }

    /** 走真实的键绑定按一次键，返回是否真的绑上了 */
    private static boolean pressKey(JPanel canvas, int keyCode) {
        Object name = canvas.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW)
                .get(javax.swing.KeyStroke.getKeyStroke(keyCode, 0));
        if (name == null) {
            return false;
        }
        javax.swing.Action action = canvas.getActionMap().get(name);
        if (action == null) {
            return false;
        }
        action.actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, "selfcheck"));
        return true;
    }

    /** 调试浮层里含 {@code needle} 的那一行；浮层没开或没有这一行时返回 {@code null} */
    private static String findDebugLine(MatchSession session, String needle) {
        MatchView match = session.view().match();
        for (int index = 0; index < match.debugLineCount(); index = index + 1) {
            if (match.debugLine(index).contains(needle)) {
                return match.debugLine(index);
            }
        }
        return null;
    }

    /** 逐样本比较两段波形，返回二进制数值是否完全相同 */
    private static boolean sameWave(double[] firstSamples, double[] secondSamples) {
        if (firstSamples.length != secondSamples.length) {
            return false;
        }
        for (int index = 0; index < firstSamples.length; index = index + 1) {
            if (Double.doubleToLongBits(firstSamples[index]) != Double.doubleToLongBits(secondSamples[index])) {
                return false;
            }
        }
        return true;
    }

    /** 返回波形中样本绝对值的最大值 */
    private static double peakOf(double[] samples) {
        double peak = 0.0;
        for (double value : samples) {
            peak = Math.max(peak, Math.abs(value));
        }
        return peak;
    }

    /** 把一个 {@link Hit} 过一遍真实的总线，返回渲染出来那一段 PCM 的峰值（0–1） */
    private static double renderedPeak(SampleBank bank, Hit hit) {
        Bus bus = new Bus(bank);
        bus.add(hit);
        byte[] block = new byte[AudioConfig.BUFFER_FRAMES * 2];
        bus.renderInto(block);
        double peak = 0.0;
        for (int index = 0; index < AudioConfig.BUFFER_FRAMES; index = index + 1) {
            int minimum = block[2 * index] & 0xFF;
            int maximum = block[2 * index + 1];
            peak = Math.max(peak, Math.abs((short) ((maximum << 8) | minimum)) / 32768.0);
        }
        return peak;
    }
}

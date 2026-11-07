package billiards.selfcheck;

import billiards.audio.AudioConfig;
import billiards.audio.Bus;
import billiards.audio.Hit;
import billiards.audio.MixerSink;
import billiards.audio.SampleBank;
import billiards.audio.ShotSounds;
import billiards.audio.SoundSink;
import billiards.physics.PhysicsEngine;
import billiards.rules.RackBuilder;
import billiards.world.Ball;
import billiards.world.PhysicsConfig;
import billiards.world.Table;
import billiards.world.Vec2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把音效导出成 wav，好让人用耳朵判一次
 *
 * <h2>为什么需要一个「用耳朵判」的出口</h2>
 * 自检能钉住波形有界、首尾归零、越重越响这些<b>性质</b>，但钉不住「像不像台球」
 * 音色是审美判断，只能由人来听。而人在游戏里听有两个麻烦：得先在窗口里凑出那一杆，
 * 而且没法回看、没法两版对比。所以导成文件——可以反复听、可以并排放着 A/B
 *
 * <p>M8 初版正是这么被推翻的：自检 31 项全绿，而用户戴上耳机一听是「敲锣打鼓」
 * 所以这个出口不是可选步骤，是验收的一部分
 *
 * <h2>导出的不是「近似」，是这条路径本身</h2>
 * 混音走的是 {@link Bus}，也就是 {@link MixerSink} 实时出声时用的那一个；合流走的是
 * {@link MixerSink#loudestPerKind}；素材走的是 {@link SampleBank}，与游戏加载的是同一次
 * 收拾（去头部静音、峰值归一）。所以这里听到的就是游戏里的声音，而不是照着它
 * 另抄的一个近似实现——否则试听就失去意义了
 *
 * <h2>真正的那个文件是 break.wav</h2>
 * 逐档音色那两条是给调音用的（听得出「重击是不是真的更响」）。而 <b>{@code break.wav}
 * 是用真实物理引擎打一杆满力开球、把引擎报出来的每一次接触按真实时刻混出来的</b>：
 * 十几下接触挤在几个固定步内，正好检验合流与限幅会不会让它糊成一片
 * 要判断「游戏听起来对不对」，听这一个就够
 *
 * <p>默认什么都不做；设了 {@code -Dbilliards.sound=&lt;目录&gt;} 才导出，与
 * {@code -Dbilliards.filmstrip} 同一约定，自检的默认输出保持干净
 */
public final class SoundAudition {

    /** 逐档试听用的力度。取两端与中间，够听出趋势 */
    private static final double[] DEMO_STRENGTHS = {0.15, 0.45, 1.0};

    /**
     * 同一种音色连击之间的间隔（毫秒）
     *
     * <p>要大于素材本身的长度，否则三下叠在一起，反而听不出「一档比一档响」
     * 落袋素材收拾完约 0.5 秒，所以这里给到 0.7 秒
     */
    private static final double DEMO_GAP_MS = 700.0;

    /** 导出开球时最多模拟多久（毫秒）。开球几秒内就停稳了 */
    private static final double BREAK_TAIL_MS = 4000.0;

    /** 工具类只提供静态方法，不需要创建对象 */
    private SoundAudition() {
    }

    /** 读取命令行导出设置，只有提供输出位置时才生成检查文件 */
    public static void dumpIfRequested() {
        String dirPath = System.getProperty("billiards.sound");
        if (dirPath == null || dirPath.isBlank()) {
            return;
        }
        File direction = new File(dirPath);
        if (!direction.isDirectory() && !direction.mkdirs()) {
            System.err.println("  [warn] 试听目录建不出来: " + direction.getAbsolutePath());
            return;
        }

        SampleBank bank = SampleBank.load();
        if (!bank.isLoaded()) {
            System.err.println("  [warn] 两段素材没读进来，试听导出中止（先跑一次 .\\build.ps1）");
            return;
        }

        System.out.println("  [wav]  试听导出 → " + direction.getAbsolutePath());
        dumpTimbres(direction, bank);
        dumpBreak(direction, bank);
    }

    // 逐档音色

    /** 两种音色各导一条，每条里力度从轻到重各响一下——「越重越响」要能听出来 */
    private static void dumpTimbres(File direction, SampleBank bank) {
        for (Hit.Kind kind : Hit.Kind.values()) {
            Offline off = new Offline(bank);
            double ms = 0.0;
            for (double strength : DEMO_STRENGTHS) {
                off.schedule(framesOfMs(ms), new Hit(kind, strength));
                ms = ms + (DEMO_GAP_MS);
            }
            off.renderTo(framesOfMs(ms));
            // 名字取小写是给耳朵以外的人看的：文件名在命令行里打起来方便
            write(direction, "tone-" + kind.name().toLowerCase(Locale.ROOT) + ".wav", off.finish());
        }
    }

    // 一杆真实开球

    /**
     * 用真实物理引擎打一杆满力开球，把引擎报出来的每次接触按<b>真实时刻</b>混成一条 wav
     *
     * <p>关键是「按真实时刻」：事件是边跑物理边收到的，这里记下每个事件发生在第几个固定步，
     * 再把整条时间轴离线渲染出来。于是听到的节奏就是游戏里的节奏——包括十几下接触挤在一起
     * 的密度，以及合流把它们并成一声之后还剩多少
     */
    private static void dumpBreak(File direction, SampleBank bank) {
        List<Ball> balls = RackBuilder.buildRack();
        Ball cue = RackBuilder.cueBall(balls);
        if (cue == null) {
            System.err.println("  [warn] 找不到主球，开球试听导出中止");
            return;
        }

        // 这里曾经先排一记「杆声」。M8.2 去掉了出杆音色，于是开球这条波形里
        // 第一声就是主球撞上球堆的那一下——和游戏里一致
        Offline off = new Offline(bank);

        OfflineBreakListener listener = new OfflineBreakListener(off);
        PhysicsEngine engine = new PhysicsEngine(new Table(), balls);
        engine.addListener(new ShotSounds(listener));

        cue.setVelocity(new Vec2(PhysicsConfig.MAX_SHOT_SPEED, 0.0));

        int maxSteps = (int) (BREAK_TAIL_MS / PhysicsConfig.FIXED_STEP_MS);
        while (listener.step() < maxSteps) {
            engine.step();
            listener.advanceStep();
            if (engine.allAtRest()) {
                break;
            }
        }

        off.renderTo(stepAt(listener.step()));
        write(direction, "break.wav", off.finish());

        System.out.printf("         开球：%d 次接触，抢掉 %d 个 voice，球台上还剩 %d 颗%n",
                off.scheduled(), off.droppedByCap(), engine.ballsOnTable());
    }

    /** 记录物理步数，并把碰撞事件放到对应的离线时间点 */
    private static final class OfflineBreakListener implements SoundSink {

        private final Offline offline;
        private int currentStep;

        /** 保存离线混音器并从第一步开始计数 */
        private OfflineBreakListener(Offline offline) {
            this.offline = offline;
            this.currentStep = 0;
        }

        /** 将碰撞事件安排到它发生的模拟时间 */
        @Override
        public void hit(Hit hit) {
            offline.schedule(stepAt(currentStep), hit);
        }

        /** 返回当前物理步数 */
        private int step() {
            return currentStep;
        }

        /** 物理引擎完成一个固定步后推进离线事件时间 */
        private void advanceStep() {
            currentStep = currentStep + 1;
        }
    }

    // 离线渲染

    /**
     * 把「第几帧该响哪一下」排好队，按块喂给 {@link Bus}，攒成 PCM
     *
     * <p><b>按块对齐是刻意的</b>：实时路径就是每写一个缓冲块之前把队列抽干一次
     * （{@code MixerSink.acceptPending}），所以同一块内到达的事件是一起被看见、一起被归并的
     * 离线这里照搬这条粒度，导出的开球在合流上就与游戏里那一杆是同一种行为——
     * 而不是「按样本摆放精确时刻」那种听起来更准、实际却与实时路径不符的做法
     */
    private static final class Offline {

        /** 保存待播放事件及其音频帧位置，离线导出按这个时间安排播放 */
        private static final class Cue {
            private final long frame;
            private final Hit hit;

            /** 保存构造参数，字段只读以便安全传递状态 */
            private Cue(long frame, Hit hit) {
                this.frame = frame;
                this.hit = hit;
            }

            /** 返回保存的音频帧位置 */
            public long frame() {
                return frame;
            }

            /** 返回保存的声音事件 */
            public Hit hit() {
                return hit;
            }

        }

        private final Bus bus;
        private final List<Cue> cues = new ArrayList<>();
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private final byte[] block =
                new byte[AudioConfig.BUFFER_FRAMES * (AudioConfig.BITS_PER_SAMPLE / 8)];

        /** 已渲染的帧数与下一个待喂的排期下标 */
        private long rendered;
        private int next;

        /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
        Offline(SampleBank bank) {
            this.bus = new Bus(bank);
        }

        /** 排一次发声。{@code frame} 须随调用递增——本类不排序，只按序吞 */
        void schedule(long frame, Hit hit) {
            cues.add(new Cue(frame, hit));
        }

        /** 排期总数。开球那条用它报「引擎一共报了几次接触」 */
        int scheduled() {
            return cues.size();
        }

        /** 因并发上限被抢掉的 voice 数。诊断用，正常应该很小 */
        long droppedByCap() {
            return bus.droppedByCap();
        }

        /** 渲染到第 {@code frames} 帧为止。末尾不足一整块的部分留给 {@link #finish()} */
        void renderTo(long frames) {
            while (rendered + AudioConfig.BUFFER_FRAMES <= frames) {
                feed(rendered + AudioConfig.BUFFER_FRAMES);
                bus.renderInto(block);
                pcm.write(block, 0, block.length);
                rendered = rendered + (AudioConfig.BUFFER_FRAMES);
            }
        }

        /** 把剩余排期全部喂掉，再把总线渲到没有 voice 在响为止 */
        byte[] finish() {
            feed(Long.MAX_VALUE);
            while (!bus.isEmpty()) {
                bus.renderInto(block);
                pcm.write(block, 0, block.length);
            }
            return pcm.toByteArray();
        }

        /** 收进发生在本块结束之前的事件；批内同种音色只留最响的一下（同实时路径） */
        private void feed(long blockEnd) {
            List<Hit> batch = new ArrayList<>();
            while (next < cues.size() && cues.get(next).frame() < blockEnd) {
                batch.add(cues.get(next).hit());
                next = next + 1;
            }
            if (batch.isEmpty()) {
                return;
            }
            Hit[] best = MixerSink.loudestPerKind(batch.toArray(new Hit[0]));
            for (Hit hit : best) {
                if (hit != null) {
                    bus.add(hit);
                }
            }
        }
    }

    // wav 容器

    /** 写一个单声道 16 位 wav。头部就 44 字节，直接拼出来，不为这点事引第三方库 */
    private static void write(File direction, String name, byte[] pcm) {
        File file = new File(direction, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(header(pcm.length));
            out.write(pcm);
        } catch (IOException error) {
            System.err.println("  [warn] 试听写入失败 " + name + ": " + error);
            return;
        }
        System.out.printf("  [wav]  %-20s %6.2f 秒%n",
                name, pcm.length / 2.0 / AudioConfig.SAMPLE_RATE);
    }

    /** 根据音频字节数生成标准 WAV 文件头 */
    private static byte[] header(int pcmBytes) {
        ByteArrayOutputStream height = new ByteArrayOutputStream(44);
        ascii(height, "RIFF");
        le32(height, 36 + pcmBytes);                             // RIFF 块长度 = 36 + 数据长度
        ascii(height, "WAVE");
        ascii(height, "fmt ");
        le32(height, 16);                                        // fmt 块长度
        le16(height, 1);                                         // 1 = PCM
        le16(height, AudioConfig.CHANNELS);
        le32(height, (int) AudioConfig.SAMPLE_RATE);
        le32(height, (int) AudioConfig.SAMPLE_RATE * AudioConfig.CHANNELS
                * (AudioConfig.BITS_PER_SAMPLE / 8));       // 字节率
        le16(height, AudioConfig.CHANNELS * (AudioConfig.BITS_PER_SAMPLE / 8));  // 块对齐
        le16(height, AudioConfig.BITS_PER_SAMPLE);
        ascii(height, "data");
        le32(height, pcmBytes);
        return height.toByteArray();
    }

    /** 把指定文本逐字节写入音频文件头 */
    private static void ascii(ByteArrayOutputStream out, String strength) {
        for (int index = 0; index < strength.length(); index = index + 1) {
            out.write(strength.charAt(index));
        }
    }

    /** 按低字节在前的顺序写入三十二位整数 */
    private static void le32(ByteArrayOutputStream out, int value) {
        le16(out, value & 0xFFFF);
        le16(out, (value >>> 16) & 0xFFFF);
    }

    /** 按低字节在前的顺序写入十六位整数 */
    private static void le16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    /** 把模拟毫秒数换算成音频帧数，结果至少保留一帧 */
    private static long framesOfMs(double ms) {
        return Math.max(Math.round(ms / 1000.0 * AudioConfig.SAMPLE_RATE), 1L);
    }

    /** 第 {@code step} 个固定步对应的帧号。按步号一次算出来，避免逐块累加带来的漂移 */
    private static long stepAt(int step) {
        return framesOfMs(step * PhysicsConfig.FIXED_STEP_MS);
    }
}

package billiards.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 真正的发声器：一条输出线 ＋ 一条音频线程 ＋ 一道合流闸门
 *
 * <h2>为什么不是「把声音包成 Clip 让声卡自己播」</h2>
 *
 * <p>M8.2 用采样之后，最容易的写法变成了 {@code Clip}：每个 wav 开一个，要响就
 * {@code setFramePosition(0); start()}。它有两个硬伤——<b>音量只能整段设一次</b>，
 * 而「响度跟撞击力度走」正是这里最要紧的功能；同一个 {@code Clip} 重入还会截断自己，
 * 开球那一下十几颗球几乎同时接触，恰恰是它发作的场合。自己混音则不受并发数限制，
 * 增益可以逐 voice 连续给，还能在总线上做软限幅（见 {@link Bus}）
 *
 * <p>（M8 那一版这条还多一个理由：当时声音是合成的，Clip 得预先烘成若干档力度，
 * 于是力度被量化成台阶。换成采样后这条不再适用，但上面两条仍然成立。）
 *
 * <h2>线程</h2>
 *
 * <p>模拟线程只往 {@link #queue} 里塞一个不可变值就走；本类的线程负责取、合流、混音、
 * 写线。{@code SourceDataLine.write} 会阻塞到缓冲区被消费掉，这就是天然的节拍器——
 * 不需要自己睡一个精确的时长
 *
 * <h2>合流：一次涌入只出一声</h2>
 *
 * <p>每次取事件时会把当时队列里的<b>整批</b>一起取走，同种音色只保留最响的那一下
 * 于是开球的十几下接触混成一声「砰」而不是一串机关枪，主循环追赶时一次性送达的
 * 一整杆事件也不会炸开。代价是同一批里较晚的、更轻的接触会被吞掉——但它们本来
 * 也就淹没在别人声音里
 *
 * <p>批内归并之外还有一道<b>时间窗</b>（{@link AudioConfig#COALESCE_MS}）：批是按缓冲块
 * 切的，同一阵接触如果跨了两个块，归并就管不着了。两道闸门管的是同一件事的两个尺度
 *
 * <h2>拿不到设备不是错误</h2>
 *
 * <p>线是在本类自己的线程上打开的。失败就把 {@link #isLive()} 置为 false 并静默退出，
 * 绝不向调用方抛异常——声音出问题是小事，起不来是大事。所以构造它永远不会失败，
 * 调用方不需要 try
 */
public final class MixerSink implements SoundSink {

    /** 没有待播音色时的等待间隔，单位为毫秒，避免浏览器持续混合静音采样 */
    private static final int IDLE_WAIT_MS = 12;

    private final ConcurrentLinkedQueue<Hit> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedByCoalesce = new AtomicLong();

    /** 只有音频线程碰它，所以它不需要任何同步 */
    private final Bus bus;

    /** 两段采样。构造时读一次，之后只读。加载失败时是空仓库，总线据此静音 */
    private final SampleBank bank;

    private final Thread thread;
    private volatile boolean running = true;
    private volatile boolean live;

    /** 上次真正发出某种音色的时刻（纳秒）。合流门用它。只有音频线程读写 */
    private final long[] lastFiredNanos = new long[Hit.Kind.values().length];

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public MixerSink() {
        bank = SampleBank.load();
        bus = new Bus(bank);
        thread = new Thread(new AudioWorker(), "billiards-audio");
        // 守护线程：窗口一关，进程就该走，不该被音频线程拖住
        thread.setDaemon(true);
        thread.start();
    }

    /** 运行声音播放循环的具名线程任务 */
    private final class AudioWorker implements Runnable {

        /** 在线程中读取声音队列并写入音频设备 */
        @Override
        public void run() {
            runAudioLoop();
        }
    }

    /** 两段采样有没有读进来。与 {@link #isLive()} 分开报：好把「没素材」和「没设备」分开 */
    public boolean samplesLoaded() {
        return bank.isLoaded();
    }

    /** 接收声音事件并交给当前出口，实际播放由音频线程处理 */
    @Override
    public void hit(Hit hit) {
        if (hit == null || hit.isSilent()) {
            return;
        }
        queue.offer(hit);
    }

    /** 声卡是否真的开起来了。真的开着才为 true；没设备时为 false 且一切照常运行 */
    @Override
    public boolean isLive() {
        return live;
    }

    /** 因并发上限被丢掉的事件数。诊断用，正常玩应该一直是 0 */
    public long droppedByCap() {
        return bus.droppedByCap();
    }

    /** 因合流窗口被并掉的事件数。开球那一下必然不为 0，那是设计意图 */
    public long droppedByCoalesce() {
        return droppedByCoalesce.get();
    }

    /** 停止后台声音任务并释放资源，等待线程结束时保留中断状态 */
    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try {
            thread.join(500);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    // 音频线程

    /** 打开音频设备并持续混音，直到窗口关闭或设备不可用 */
    private void runAudioLoop() {
        SourceDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(AudioConfig.SAMPLE_RATE,
                    AudioConfig.BITS_PER_SAMPLE, AudioConfig.CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, AudioConfig.BUFFER_FRAMES * 4 * (AudioConfig.BITS_PER_SAMPLE / 8));
            line.start();
        } catch (Exception | LinkageError error) {
            // 没有声卡、没有权限、没有 provider——都不是错误，只是没声音
            live = false;
            return;
        }
        live = true;

        byte[] block = new byte[AudioConfig.BUFFER_FRAMES * (AudioConfig.BITS_PER_SAMPLE / 8)];
        try {
            while (running) {
                acceptPending();
                if (bus.isEmpty()) {
                    try {
                        Thread.sleep(IDLE_WAIT_MS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                bus.renderInto(block);
                line.write(block, 0, block.length);
            }
        } finally {
            live = false;
            line.stop();
            line.close();
        }
    }

    /**
     * 把队列里积压的事件一次性收进来：批内同种音色只留最响的一下，再过一道时间窗
     *
     * <p>「整批取走再按种类归并」而不是「一个一个处理」，是因为批内的间隔在
     * 时间上并无意义——它们是主循环一次补齐积压后同时送达的
     */
    private void acceptPending() {
        Hit[] batch = drainQueue();
        if (batch.length == 0) {
            return;
        }
        Hit[] best = loudestPerKind(batch);

        long now = System.nanoTime();
        long windowNanos = (long) (AudioConfig.COALESCE_MS * 1_000_000.0);
        for (int kindIndex = 0; kindIndex < best.length; kindIndex = kindIndex + 1) {
            if (best[kindIndex] == null) {
                continue;
            }
            if (now - lastFiredNanos[kindIndex] < windowNanos) {
                droppedByCoalesce.incrementAndGet();
                continue;
            }
            lastFiredNanos[kindIndex] = now;
            bus.add(best[kindIndex]);
        }
    }

    /** 把队列抽干。没有事件时返回空数组 */
    private Hit[] drainQueue() {
        List<Hit> batch = new ArrayList<>();
        Hit hit;
        while ((hit = queue.poll()) != null) {
            batch.add(hit);
        }
        return batch.toArray(new Hit[0]);
    }

    /** 一批事件里，每种音色只留<b>最响</b>的那一下；没有的音色该位为 {@code null} */
    public static Hit[] loudestPerKind(Hit[] batch) {
        Hit[] best = new Hit[Hit.Kind.values().length];
        for (Hit hit : batch) {
            int sampleIndex = hit.kind().ordinal();
            if (best[sampleIndex] == null || hit.strength() > best[sampleIndex].strength()) {
                best[sampleIndex] = hit;
            }
        }
        return best;
    }
}

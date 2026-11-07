package billiards.audio;

import billiards.world.ShotEventListener;
import billiards.world.Vec2;

/**
 * 把物理引擎喊出来的事件翻译成声音，转手交给 {@link SoundSink}
 *
 * <p>它就是那个「渲染器订阅 {@link ShotEventListener} 来播声音」的实现——接口当初长在
 * {@code world} 包、还带着撞击强度参数，正是为了这个。物理不认识声音，声音也不认识物理
 *
 * <h2>本类在模拟线程上被调用，所以它只做一件事：入队</h2>
 *
 * <p>这里<b>不碰素材、不混音、不碰声卡</b>。{@link ShotEventListener} 的约定是
 * 「实现方不应在这里做重活」——放在 200Hz 的模拟回调里，等于让声音直接决定球的运动节奏
 * 折算力度是几次乘除，可以在这里做；{@link SoundSink} 的实现负责把其余的挪到别的线程
 */
public final class ShotSounds implements ShotEventListener {

    private final SoundSink sink;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public ShotSounds(SoundSink sink) {
        this.sink = sink;
    }

    /** 处理球球接触事件，参数提供球号、接触点和碰撞前速度 */
    @Override
    public void onBallBallContact(int aId, int bId, Vec2 contactPoint, double normalSpeed) {
        emit(Hit.Kind.BALL, normalSpeed);
    }

    /**
     * <b>刻意不发声。</b>库边是 M8 有的、M8.2 去掉的音色之一——用户的要求是
     * 「就只做球与球碰，球进袋的声音就行了，其他的声音不做」
     *
     * <p>事件照旧订阅、照旧被调用，只是不再翻译成声音：{@link ShotEventListener} 是物理层
     * 的接口，它该报什么与谁在听没有关系（{@code rules.ShotRecord} 还在用它记犯规）
     * 少一个订阅者就改接口，等于让最上层的需求往物理层里渗
     */
    @Override
    public void onCushionContact(int ballId, int railIndex, double normalSpeed) {
        // 不发声，见上
    }

    /** 处理落袋事件，参数提供球号、袋号和落袋前速度 */
    @Override
    public void onBallPocketed(int ballId, int pocketIndex, double speed) {
        emit(Hit.Kind.POCKET, speed);
    }

    /**
     * 够响才转手。<b>阈值以下的接触在这里就被丢掉，不建对象、不入队</b>
     *
     * <p>理由是频率：球停稳前后会持续产生大量速度趋近于零的接触判定，它们是所有事件里
     * 最多的一类。交给下游去丢，等于为每一次「本来就不该出声」的接触在模拟线程上白建一个
     * {@link Hit}——而这一步正是在 200Hz 的物理回调里跑的。{@link Hit#isSilent()} 本来就是
     * 为这件事准备的
     */
    private void emit(Hit.Kind kind, double speed) {
        double strength = strengthFor(speed);
        if (strength > 0.0) {
            sink.hit(new Hit(kind, strength));
        }
    }

    /** 处理飞出台面的球号，记录或忽略该事件由所属模块决定 */
    @Override
    public void onBallOffTable(int ballId) {
        // 飞出台面事件只用于异常位置处理，因此不播放正常击球音效
    }

    /** 撞击速度 → 0–1 的力度 */
    public static double strengthFor(double speed) {
        double span = AudioConfig.FULL_SPEED - AudioConfig.SILENT_SPEED;
        double timeFraction = (speed - AudioConfig.SILENT_SPEED) / span;
        // 写成 !(t > 0) 而不是 t <= 0：NaN 在两条比较里都是假，会一路渗到 Hit 才被钳掉
        // 这一层是「物理量 → 音量」的最后一道关口，坏值该在这里就变成「不出声」
        // （与 ShotPower.powerFor 同一写法、同一理由）
        if (!(timeFraction > 0.0)) {
            return 0.0;
        }
        if (timeFraction >= 1.0) {
            return 1.0;
        }
        return Math.pow(timeFraction, AudioConfig.STRENGTH_CURVE);
    }
}

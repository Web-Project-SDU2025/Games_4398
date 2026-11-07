package billiards.render;

import billiards.world.Ball;
import billiards.world.Layout;
import billiards.world.Vec2;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * 画球杆。杆头随着拉杆往后退，退了多少就是力度
 *
 * <h2>为什么球杆比力度条重要</h2>
 * 力度条是<b>读数</b>——它告诉你数字；球杆的位置是<b>手感</b>——它让你不用看读数也知道
 * 这一杆有多重。真实球台上没有力度条，人靠的就是「杆往后拉了多少」。所以这里宁可让球杆
 * 后拉的幅度大一点（满力 90px，是球半径的八倍多），也不让它只是微微一动
 *
 * <h2>几何为什么是 {@code public static}</h2>
 * {@link #tipCentre} 是「力度 → 杆头位置」这条换算的全部内容，也是本类唯一会算错的地方
 * （渐变画歪了没人看得出来，杆头位置算错则一眼假）。做成静态纯函数之后，自检可以直接断言
 * 「满力时杆头恰好退到 {@code R + 间隙 + 90}」，不必去数像素
 *
 * <p>杆身是一段四边形而不是一条粗线：真实的杆有锥度（杆头细、杆尾粗），用
 * {@code BasicStroke} 画出来的是等宽带，近看就是一根棍子
 */
public final class CueRenderer {

    /** 皮头（杆尖那截蓝色）长度（px） */
    private static final double TIP_LEN = 6.0;

    /** 白箍（先角）长度（px） */
    private static final double FERRULE_LEN = 12.0;

    /** 后把（杆尾那截深色）长度（px） */
    private static final double BUTT_LEN = 96.0;

    /** 杆头端半宽（px） */
    private static final double HALF_W_TIP = 1.5;

    /** 杆尾端半宽（px） */
    private static final double HALF_W_BUTT = 3.5;

    /** 后把接箍（黄铜环）的长度（px）。真实球杆的接牙就在这里 */
    private static final double JOINT_LEN = 5.0;

    // ---- 三段短色块的受光/背光色。都写成具名常量，不走 Theme.brighten：----
    // brighten 是「朝白色插值」，对已经很暗的后把（#2B1D14）会把棕调洗成灰调

    /** 皮头（蓝）的暗侧与亮侧 */
    private static final Color TIP_DARK = new Color(0x18, 0x3E, 0x6C);
    /** 球杆对应部位的受光颜色，保持与全场照明方向一致 */
    private static final Color TIP_LIGHT = new Color(0x55, 0x9B, 0xE0);

    /** 先角（白箍）的暗侧与亮侧。偏象牙白，纯白在深色杆身旁会显得刺眼 */
    private static final Color FERRULE_DARK = new Color(0xC9, 0xC7, 0xBE);
    /** 球杆对应部位的受光颜色，保持与全场照明方向一致 */
    private static final Color FERRULE_LIGHT = new Color(0xFA, 0xF8, 0xF2);

    /** 后把的暗侧与亮侧，棕色亮面用于表现木质受光 */
    private static final Color BUTT_DARK = new Color(0x1A, 0x11, 0x0B);
    /** 球杆对应部位的受光颜色，保持与全场照明方向一致 */
    private static final Color BUTT_LIGHT = new Color(0x74, 0x4B, 0x2C);

    /** 后把接箍的黄铜环 */
    private static final Color JOINT_DARK = new Color(0x6B, 0x52, 0x2A);
    /** 球杆对应部位的受光颜色，保持与全场照明方向一致 */
    private static final Color JOINT_LIGHT = new Color(0xD3, 0xB0, 0x66);

    /**
     * 落影的两趟偏移（px）与不透明度。光在左上，杆悬在台面上方，影朝右下偏
     *
     * <p>两趟叠出软边：远的那趟更偏、更宽、更淡，近的那趟贴着实、收得紧。一趟画不出
     * 「悬空」——单趟要么太实（像画了一根黑杆）要么太淡（等于没画）
     *
     * <p>偏移是<b>屏幕空间</b>的固定向量，不随杆的方向旋转：方向光投下的影子本来就与
     * 物体朝向无关。杆转到哪个角度，影都朝右下
     */
    private static final double SHADOW_FAR_DX = 6.5;
    /** 球杆投影的竖直偏移，单位为逻辑像素 */
    private static final double SHADOW_FAR_DY = 9.0;
    /** 球杆投影相对杆宽的扩散倍数 */
    private static final double SHADOW_FAR_SPREAD = 1.9;
    /** 球杆投影的不透明度，范围为零到二百五十五 */
    private static final int SHADOW_FAR_ALPHA = 26;

    /** 球杆投影的水平偏移，单位为逻辑像素 */
    private static final double SHADOW_NEAR_DX = 3.5;
    /** 球杆投影的竖直偏移，单位为逻辑像素 */
    private static final double SHADOW_NEAR_DY = 5.0;
    /** 球杆投影相对杆宽的扩散倍数 */
    private static final double SHADOW_NEAR_SPREAD = 1.35;
    /** 球杆投影的不透明度，范围为零到二百五十五 */
    private static final int SHADOW_NEAR_ALPHA = 52;

    /**
     * 杆头（皮头中心）的位置
     *
     * <p>球杆在瞄准方向的<b>反面</b>，与球面隔着 {@link Layout#CUE_TIP_GAP_PX} 的静态间隙；
     * 拉杆时这个间隙再撑开 {@code power × CUE_PULL_BACK_PX}。力度为 0 时球杆停在球边待命，
     * 那是「可以打了」的姿态
     */
    public static Vec2 tipCentre(Vec2 ballCentre, Vec2 direction, double power) {
        double pull = Math.max(0.0, Math.min(power, 1.0));
        double gap = Ball.RADIUS + Layout.CUE_TIP_GAP_PX + pull * Layout.CUE_PULL_BACK_PX;
        return ballCentre.minus(direction.times(gap));
    }

    /**
     * @param aim  本帧的瞄准快照；没有瞄准辅助（球在飞、主球不在台上）时不画杆
     * @param cueX 主球球心 x
     * @param cueY 主球球心 y
     */
    public void draw(Graphics2D graphics, AimView aim, double cueX, double cueY, double power) {
        if (!aim.isPresent() || aim.dir() == null) {
            return;
        }

        Vec2 direction = aim.dir();
        Vec2 tip = tipCentre(new Vec2(cueX, cueY), direction, power);
        Vec2 back = direction.times(-1.0);          // 从杆头指向杆尾
        Vec2 normal = back.perpendicular();   // 杆的宽度方向

        double ferruleEnd = TIP_LEN + FERRULE_LEN;
        double buttStart = Layout.CUE_LENGTH_PX - BUTT_LEN;

        Object oldAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 影在最下面。没有它，杆与台呢之间没有任何联系，杆读起来是「印在台面上的一道
        // 色块」而不是「悬在台面上方的一根木头」——这是「球杆做得太糙」里最要紧的一条
        paintDropShadow(graphics, tip, back, normal);

        // 杆身：<b>横跨杆宽</b>的三段渐变（暗—亮—暗），让它看起来是一根圆柱
        //
        // 沿杆长做渐变（杆头亮、杆尾暗）看着更自然，实际却等于没有：杆有 300px，而主球
        // 贴着左库开球时杆尾本来就在窗口外，屏幕上能看见的只有靠杆头的那一百多像素——
        // GradientPaint 在渐变起点之外会一直沿用起点色，于是整根可见的杆是**一个纯色**
        // 横跨杆宽的渐变和杆有多长无关，露出哪一段都是圆的，这才是「看起来像根杆」的正解
        graphics.setPaint(acrossWidth(tip, back, normal, ferruleEnd, buttStart, HALF_W_BUTT,
                Theme.CUE_SHAFT_DARK, Theme.CUE_SHAFT_LIGHT));
        quad(graphics, tip, back, normal, ferruleEnd, buttStart, HALF_W_TIP + 0.4, HALF_W_BUTT);

        // 三段短色块。杆身有锥度，所以半宽按长度插值，接缝处才对得上。三段本来都是平涂，
        // 深色的后把占了可见杆身的一半，一整块死色正是「糙」的来源——现在三段各自也走
        // 横跨杆宽的渐变，与杆身同一个光
        graphics.setPaint(acrossWidth(tip, back, normal, 0.0, TIP_LEN, HALF_W_TIP + 0.1,
                TIP_DARK, TIP_LIGHT));
        quad(graphics, tip, back, normal, 0.0, TIP_LEN, HALF_W_TIP, HALF_W_TIP + 0.2);

        graphics.setPaint(acrossWidth(tip, back, normal, TIP_LEN, ferruleEnd, HALF_W_TIP + 0.3,
                FERRULE_DARK, FERRULE_LIGHT));
        quad(graphics, tip, back, normal, TIP_LEN, ferruleEnd, HALF_W_TIP + 0.2, HALF_W_TIP + 0.4);

        graphics.setPaint(acrossWidth(tip, back, normal, buttStart, Layout.CUE_LENGTH_PX,
                HALF_W_BUTT + 0.2, BUTT_DARK, BUTT_LIGHT));
        quad(graphics, tip, back, normal, buttStart, Layout.CUE_LENGTH_PX,
                HALF_W_BUTT, HALF_W_BUTT + 0.4);

        // 两道接缝环。杆只有三十几个像素宽，一条真正的「分界线」是它读成一支球杆而不是
        // 一根渐变色棒的关键：先角断在哪儿、后把从哪儿开始，全靠这两道环交代
        graphics.setColor(new Color(0x6E, 0x60, 0x3C, 210));
        quad(graphics, tip, back, normal, ferruleEnd - 0.9, ferruleEnd + 0.9,
                HALF_W_TIP + 0.5, HALF_W_TIP + 0.55);

        graphics.setPaint(acrossWidth(tip, back, normal, buttStart - JOINT_LEN * 0.5,
                buttStart + JOINT_LEN * 0.5, HALF_W_BUTT + 0.1, JOINT_DARK, JOINT_LIGHT));
        quad(graphics, tip, back, normal, buttStart - JOINT_LEN * 0.5, buttStart + JOINT_LEN * 0.5,
                HALF_W_BUTT + 0.05, HALF_W_BUTT + 0.1);

        if (oldAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    /** 杆在台呢上的落影：两趟，远而宽的一趟打底，近而紧的一趟收边 */
    private static void paintDropShadow(Graphics2D graphics, Vec2 tip, Vec2 back, Vec2 normal) {
        java.awt.geom.AffineTransform old = graphics.getTransform();

        graphics.setColor(new Color(0, 0, 0, SHADOW_FAR_ALPHA));
        graphics.translate(SHADOW_FAR_DX, SHADOW_FAR_DY);
        quad(graphics, tip, back, normal, 0.0, Layout.CUE_LENGTH_PX,
                HALF_W_TIP * SHADOW_FAR_SPREAD, HALF_W_BUTT * SHADOW_FAR_SPREAD);
        graphics.setTransform(old);

        graphics.setColor(new Color(0, 0, 0, SHADOW_NEAR_ALPHA));
        graphics.translate(SHADOW_NEAR_DX, SHADOW_NEAR_DY);
        quad(graphics, tip, back, normal, 0.0, Layout.CUE_LENGTH_PX,
                HALF_W_TIP * SHADOW_NEAR_SPREAD, HALF_W_BUTT * SHADOW_NEAR_SPREAD);
        graphics.setTransform(old);
    }

    /**
     * 某一段的「横跨杆宽」三段渐变（暗—亮—暗）。渐变轴垂直于杆轴，所以杆转到任何角度
     * 都看得到同一根圆柱；轴心取这一段的中点，半宽取两端的平均值——有锥度的一段，
     * 亮带才不会偏出一边
     *
     * <p>把这段换算收在一处是有原因的：它是「球杆像不像一根圆柱」的全部内容，
     * 五个面（杆身 + 三段短色块 + 接箍）任何一处写错，那一段就会变成一块平板
     */
    private static LinearGradientPaint acrossWidth(Vec2 tip, Vec2 back, Vec2 normal,
                                                   double s0, double s1, double halfW,
                                                   Color dark, Color light) {
        Vec2 mid = tip.plus(back.times((s0 + s1) * 0.5));
        Vec2 across = normal.times(halfW);
        return new LinearGradientPaint(
                (float) (mid.x - across.x), (float) (mid.y - across.y),
                (float) (mid.x + across.x), (float) (mid.y + across.y),
                new float[]{0.0f, 0.35f, 1.0f},
                new Color[]{dark, light, dark});
    }

    /** 沿杆轴从 {@code s0} 到 {@code s1}、半宽从 {@code w0} 变到 {@code w1} 的一段，填成四边形 */
    private static void quad(Graphics2D graphics, Vec2 tip, Vec2 back, Vec2 normal,
                             double s0, double s1, double w0, double w1) {
        Vec2 center = tip.plus(back.times(s0));
        Vec2 centerValue = tip.plus(back.times(s1));
        Vec2 centerValueForN0 = normal.times(w0);
        Vec2 centerValueForN1 = normal.times(w1);

        Path2D.Double profileValue = new Path2D.Double();
        profileValue.moveTo(center.x + centerValueForN0.x, center.y + centerValueForN0.y);
        profileValue.lineTo(centerValue.x + centerValueForN1.x, centerValue.y + centerValueForN1.y);
        profileValue.lineTo(centerValue.x - centerValueForN1.x, centerValue.y - centerValueForN1.y);
        profileValue.lineTo(center.x - centerValueForN0.x, center.y - centerValueForN0.y);
        profileValue.closePath();
        graphics.fill(profileValue);
    }
}

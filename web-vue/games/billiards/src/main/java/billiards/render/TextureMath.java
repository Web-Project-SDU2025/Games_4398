package billiards.render;

/** 生成可重复的纹理噪声并计算颜色，不负责台面几何与绘制顺序 */
final class TextureMath {

    /** 每层纹理的频率倍数，增大会增加细密纹路 */
    private static final double FREQUENCY_SCALE = 2.03;

    /** 后一层纹理的权重比例，降低可减少高频噪点 */
    private static final double AMPLITUDE_SCALE = 0.5;

    /** 不同层采用不同种子，避免重复同一组纹路 */
    private static final int LAYER_SEED_STEP = 101;

    /** 工具类只提供静态计算，不需要创建对象 */
    private TextureMath() {
    }

    /** 整数散列 → [0,1)。纯函数：同一个 {@code (x, y, seed)} 永远同一个值 */
    static double hash01(int x, int y, int seed) {
        // 整数散列只由坐标与种子决定，相同台面不会每帧产生新噪点
        int hash = x * 0x1F1F1F1F ^ y * 0x27D4EB2D ^ seed * 0x165667B1;
        hash = (hash ^ (hash >>> 15)) * 0x2545F491;
        hash = hash ^ (hash >>> 13);
        hash = hash * 0x27D4EB2D;
        hash = hash ^ (hash >>> 16);
        return (hash >>> 8) / 16777216.0;
    }

    /**
     * 值噪声：在整数格点上取散列值，格内做平滑插值。返回 [0,1)
     *
     * <p>插值用 smoothstep（不是线性），否则每个格点边界上会留下一条可见的
     * 「折痕」——线性插值的导数在格点处不连续，眼睛对这种网格非常敏感
     */
    static double valueNoise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);
        double v00 = hash01(xi, yi, seed);
        double v10 = hash01(xi + 1, yi, seed);
        double v01 = hash01(xi, yi + 1, seed);
        double v11 = hash01(xi + 1, yi + 1, seed);
        double upperValue = v00 + (v10 - v00) * sx;
        double lowerValue = v01 + (v11 - v01) * sx;
        return upperValue + (lowerValue - upperValue) * sy;
    }

    /** 几个倍频叠起来的噪声（fBm），返回 [0,1)。倍频多一层，细节多一倍、幅度减半 */
    static double fbm(double x, double y, int octaves, int seed) {
        double sum = 0;
        double amplitude = 0.5;
        double totalWeight = 0;
        for (int index = 0; index < octaves; index = index + 1) {
            sum = sum + valueNoise(x, y, seed + index * LAYER_SEED_STEP) * amplitude;
            totalWeight = totalWeight + amplitude;
            x = x * FREQUENCY_SCALE;
            y = y * FREQUENCY_SCALE;
            amplitude = amplitude * AMPLITUDE_SCALE;
        }
        return sum / totalWeight;
    }

    /** 在等距采样的剖面表上下取一段，段内用 smoothstep 过渡 */
    static double sampleProfile(double[] profile, double segmentFraction) {
        double timeFraction = clamp(segmentFraction, 0.0, 1.0) * (profile.length - 1);
        int index = (int) Math.floor(timeFraction);
        if (index >= profile.length - 1) {
            return profile[profile.length - 1];
        }
        double fraction = timeFraction - index;
        return profile[index] + (profile[index + 1] - profile[index]) * smoothstep(fraction);
    }

    /** 用平滑曲线过渡采样值，减少纹理格点边界的折痕 */
    static double smoothstep(double timeFraction) {
        return timeFraction * timeFraction * (3.0 - 2.0 * timeFraction);
    }

    /** 将数值限制在给定上下限内并返回结果 */
    static double clamp(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }

    /** 三个 double 通道打包成不透明的 ARGB */
    static int pack(double red, double green, double blue) {
        int redChannel = (int) clamp(Math.round(red), 0, 255);
        int greenChannel = (int) clamp(Math.round(green), 0, 255);
        int blueChannel = (int) clamp(Math.round(blue), 0, 255);
        return 0xFF000000 | (redChannel << 16) | (greenChannel << 8) | blueChannel;
    }
}

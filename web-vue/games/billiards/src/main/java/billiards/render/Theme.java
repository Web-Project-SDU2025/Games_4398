package billiards.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 配色与字体
 *
 * <p>全部颜色集中在这里，渲染器只管「画什么形状」不管「用什么颜色」——改主题不用翻
 * 六个渲染器。中文界面需要能显示汉字的字体族，{@link #uiFont} 会挑一个存在的，
 * 挑不到就退回逻辑字体 {@code SansSerif}（JRE 自带 CJK 回退，不会画成方框）
 */
public final class Theme {

    /** 窗口底色（球台以外的区域） */
    public static final Color WINDOW_BG = new Color(0x10, 0x12, 0x15);

    /** 台呢绿 */
    public static final Color CLOTH = new Color(0x1E, 0x7A, 0x44);

    /** 台呢中心略亮、边缘略暗的晕影用色 */
    public static final Color CLOTH_LIGHT = new Color(0x27, 0x8E, 0x51);

    /**
     * 库边底色。库边裹的就是同一块台呢布，所以它只比台呢压暗一点点，不是另一种绿
     *
     * <p>原来这里是 {@code 0x145C33}（亮度 65.8），比台呢的 88.3 暗了四分之一——那是把
     * 库边当成了另一种材质。后果不是「暗一点」这么轻：库边顶面（实测 53.3）反而比紧挨着
     * 它的台呢（74.5）<b>更暗</b>，于是亮度次序整个反了，眼睛读到的是一道凹槽而不是一条
     * 抬起的棱——这正是「库边的高度没做出来」的直接成因。库边比旁边暗没问题，但「暗」
     * 必须来自它<b>自己投下的影</b>，而不是来自底色
     */
    public static final Color CUSHION = new Color(0x1C, 0x75, 0x41);

    /** 库边顶面高光 */
    public static final Color CUSHION_EDGE = new Color(0x2A, 0x93, 0x57);

    /** 木质外框 */
    public static final Color FRAME_WOOD = new Color(0x5A, 0x36, 0x1E);

    /** 外框内圈亮边，做出倒角感 */
    public static final Color FRAME_HIGHLIGHT = new Color(0x8A, 0x5A, 0x33);

    /** 袋口 */
    public static final Color POCKET = new Color(0x06, 0x07, 0x08);

    /** 开球线与置球点 */
    public static final Color TABLE_MARK = new Color(0xFF, 0xFF, 0xFF, 0x55);

    /** 状态区背景色 */
    public static final Color HUD_BG = new Color(0x18, 0x1B, 0x20);

    /** 状态区面板底色 */
    public static final Color HUD_PANEL = new Color(0x23, 0x27, 0x2E);

    /** 当前击球者的信息区高亮底色 */
    public static final Color HUD_PANEL_ACTIVE = new Color(0x2E, 0x3A, 0x48);

    /** 状态区边框颜色 */
    public static final Color HUD_BORDER = new Color(0x3A, 0x40, 0x4A);

    /** 主要文字颜色 */
    public static final Color TEXT = new Color(0xE8, 0xEA, 0xED);

    /** 次要文字颜色 */
    public static final Color TEXT_DIM = new Color(0x9A, 0xA0, 0xA6);

    /** 当前操作的强调颜色 */
    public static final Color ACCENT = new Color(0x4F, 0xC3, 0xF7);

    /** 合法击球结果颜色 */
    public static final Color LEGAL = new Color(0x66, 0xBB, 0x6A);

    /** 犯规结果颜色 */
    public static final Color FOUL = new Color(0xE5, 0x39, 0x35);

    /** 胜负和限制提示颜色 */
    public static final Color WARN = new Color(0xFF, 0xB3, 0x00);

    /** 瞄准主线颜色和不透明度 */
    public static final Color AIM_LINE = new Color(0xFF, 0xFF, 0xFF, 0xAA);

    /** 虚影球填充色和不透明度 */
    public static final Color AIM_GHOST = new Color(0xFF, 0xFF, 0xFF, 0x3C);

    /** 目标球分离方向提示颜色 */
    public static final Color AIM_TARGET_LINE = new Color(0x4F, 0xC3, 0xF7, 0xCC);

    /** 主球分离方向提示颜色 */
    public static final Color AIM_CUE_LINE = new Color(0xFF, 0xFF, 0xFF, 0x66);

    /** 合法摆球位置的提示颜色 */
    public static final Color PLACEMENT_OK = new Color(0x66, 0xBB, 0x6A, 0x99);

    /** 非法摆球位置的提示颜色 */
    public static final Color PLACEMENT_BAD = new Color(0xE5, 0x39, 0x35, 0x99);

    /** 球杆杆身的受光颜色 */
    public static final Color CUE_SHAFT_LIGHT = new Color(0xE8, 0xC9, 0x8A);

    /** 球杆杆身的背光颜色 */
    public static final Color CUE_SHAFT_DARK = new Color(0xA8, 0x7C, 0x3C);

    /** 球杆后把底色 */
    public static final Color CUE_BUTT = new Color(0x2B, 0x1D, 0x14);

    /** 球杆皮头底色 */
    public static final Color CUE_TIP = new Color(0x2E, 0x6F, 0xB8);

    /** 低力度区间颜色 */
    public static final Color POWER_LOW = new Color(0x66, 0xBB, 0x6A);

    /** 中力度区间颜色 */
    public static final Color POWER_MID = new Color(0xFF, 0xB3, 0x00);

    /** 高力度区间颜色 */
    public static final Color POWER_HIGH = new Color(0xE5, 0x39, 0x35);

    /** 界面使用的字体族，启动时根据已安装字体选择 */
    private static final String UI_FAMILY = pickUiFamily();

    /** 工具类只提供静态方法，不需要创建对象 */
    private Theme() {
    }

    /** 按优先顺序选择已安装中文字体，没有合适字体时使用逻辑字体 */
    private static String pickUiFamily() {
        // 浏览器使用 Java 逻辑字体，避免枚举全部字体时触发额外下载和初始化
        if (RenderAssets.browserMode()) {
            return Font.SANS_SERIF;
        }
        String[] preferred = {"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "PingFang SC", "Noto Sans CJK SC"};
        try {
            Set<String> available = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String name : preferred) {
                if (available.contains(name)) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // 无头环境或字体子系统异常：退回逻辑字体，JRE 会做 CJK 回退
        }
        return Font.SANS_SERIF;
    }

    /** 根据字重和字号创建界面字体，字体族由模块统一选择 */
    public static Font uiFont(int style, float size) {
        return new Font(UI_FAMILY, style, Math.round(size)).deriveFont(size);
    }

    // ---- 小工具 ----

    /** 保留颜色通道并设置指定不透明度，返回新的颜色对象 */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** 往白色方向插值，{@code amount ∈ [0,1]} */
    public static Color brighten(Color color, float amount) {
        return new Color(
                Math.min(255, Math.round(color.getRed() + (255 - color.getRed()) * amount)),
                Math.min(255, Math.round(color.getGreen() + (255 - color.getGreen()) * amount)),
                Math.min(255, Math.round(color.getBlue() + (255 - color.getBlue()) * amount)));
    }

    /** 往黑色方向插值，{@code amount ∈ [0,1]} */
    public static Color darken(Color color, float amount) {
        return new Color(
                Math.max(0, Math.round(color.getRed() * (1.0f - amount))),
                Math.max(0, Math.round(color.getGreen() * (1.0f - amount))),
                Math.max(0, Math.round(color.getBlue() * (1.0f - amount))));
    }
}

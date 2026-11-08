package pvz.editor;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.UIManager;

/** 维护编辑器的系统外观和字体，不参与关卡读写或窗口交互 */
final class EditorAppearance {
    /** 编辑器公共控件的字号 */
    private static final int FONT_SIZE = 12;
    /** 本机优先使用的中文界面字体，浏览器改用 Java 逻辑字体 */
    private static final String PREFERRED_FONT = "Microsoft YaHei UI";

    /** 外观通过静态方法设置，不需要保存实例 */
    private EditorAppearance() {
    }

    /** 创建控件前设置系统外观，加载失败时保留默认外观并输出原因 */
    static void apply() {
        try {
            String appearance = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(appearance);
            applyFont();
        } catch (Exception failure) {
            System.out.println("套用系统外观失败，改用默认外观：" + failure.getMessage());
        }
    }

    /** 浏览器按需加载逻辑字形，本机安装首选字体时统一替换控件默认字体 */
    private static void applyFont() {
        String family = PREFERRED_FONT;
        if (Boolean.getBoolean("pvz.browser")) {
            family = Font.DIALOG;
        } else if (!isFontInstalled(family)) {
            return;
        }
        Font font = new Font(family, Font.PLAIN, FONT_SIZE);
        List<Object> keys = new ArrayList<Object>(UIManager.getLookAndFeelDefaults().keySet());
        for (Object key : keys) {
            if (UIManager.get(key) instanceof Font) {
                UIManager.put(key, font);
            }
        }
    }

    /** 检查本机是否安装指定字体，未安装时保留系统已有字体 */
    private static boolean isFontInstalled(String name) {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] installed = environment.getAvailableFontFamilyNames();
        for (String family : installed) {
            if (family.equals(name)) {
                return true;
            }
        }
        return false;
    }
}

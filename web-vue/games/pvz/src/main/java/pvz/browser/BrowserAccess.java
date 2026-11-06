package pvz.browser;

/** 网页角色决定浏览器内编辑器入口，本地 Java 游戏沿用原有编辑功能 */
public final class BrowserAccess {
    /** 网页读取后端身份后传入编辑器开关 */
    private static final String EDITOR_ACCESS_PROPERTY = "platform.editor.allowed";

    /** 此类只提供共享入口检查 */
    private BrowserAccess() {
    }

    /** 本地游戏允许编辑，浏览器游戏按当前账户的编辑器开关处理 */
    public static boolean canEdit() {
        if (!Boolean.getBoolean("pvz.browser")) {
            return true;
        }
        return Boolean.getBoolean(EDITOR_ACCESS_PROPERTY);
    }
}

package billiards.rules;

/** 保存本局成就名称，判定条件由成就记录器维护 */
public enum Achievement {
    OPENING_POT("开门红"),
    DOUBLE_POT("一杆双收"),
    TRIPLE_POT("一杆三收"),
    THREE_SHOT_RUN("连下三城"),
    BANK_POT("翻袋好球"),
    KICK_POT("勾球进袋");

    private final String title;

    /** 保存展示名称，参数为界面中显示的中文标题 */
    Achievement(String title) {
        this.title = title;
    }

    /** 返回成就的中文标题 */
    public String title() {
        return title;
    }
}

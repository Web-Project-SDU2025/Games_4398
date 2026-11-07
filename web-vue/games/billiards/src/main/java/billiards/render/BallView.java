package billiards.render;

/** 保存一颗球的只读显示状态，界面线程无需访问可变的物理对象 */
public final class BallView {
    private final int id;
    private final double x;
    private final double y;
    private final boolean onTable;

    /** 保存球号、球心坐标和在台状态，不携带额外的球面装饰标记 */
    public BallView(int ballId, double x, double y, boolean onTable) {
        this.id = ballId;
        this.x = x;
        this.y = y;
        this.onTable = onTable;
    }

    /** 返回球号，供绘制器选择图案 */
    public int id() {
        return id;
    }

    /** 返回球心的逻辑水平坐标 */
    public double x() {
        return x;
    }

    /** 返回球心的逻辑竖直坐标 */
    public double y() {
        return y;
    }

    /** 返回球是否仍在台上，落袋球不参与绘制 */
    public boolean onTable() {
        return onTable;
    }
}

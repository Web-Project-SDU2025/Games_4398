package pvz.editor;

import java.awt.Point;

/**
 * 约定编辑器如何处理僵尸拖动
 *
 * 拖动可以从僵尸列表开始，在出怪网格结束
 * 两个面板都把鼠标动作交给主窗口，由它记录拖动中的僵尸及其位置
 */
public interface EditorDragController {
    /**
     * 通知主窗口开始拖动僵尸
     *
     * 参数：kind 是拖的僵尸品种；count 是拖的只数；
     *       fromRow、fromWave 是拖动起点所在的格子，从僵尸列表拖出来时传 -1；
     *       screenPoint 是鼠标在屏幕上的位置
     */
    void beginDrag(String kind, int count, int fromRow, int fromWave, Point screenPoint);

    /**
     * 报告拖动期间鼠标的新位置
     *
     * 参数：screenPoint 是鼠标在屏幕上的位置
     */
    void updateDrag(Point screenPoint);

    /**
     * 报告用户松开鼠标时的位置
     *
     * 参数：screenPoint 是鼠标在屏幕上的位置
     */
    void finishDrag(Point screenPoint);

    /**
     * 判断当前是否正在拖动僵尸
     *
     * 返回：正在拖动时为 true，面板据此决定是否高亮鼠标下方的格子
     */
    boolean isDragging();

    /**
     * 查询僵尸列表里当前选中的是哪一种
     *
     * 返回：僵尸品种名；实现保证不为 null（初始就是僵尸列表的第一项）
     */
    String selectedKind();

    /**
     * 报告用户在僵尸列表里选中了某一种僵尸
     *
     * 参数：kind 是僵尸品种名
     */
    void selectKind(String kind);

    /** 网格内容变化后，通知主窗口刷新画面和底部统计 */
    void designChanged();

    /** 用户选中另一个格子后，通知主窗口更新随机行复选框 */
    void selectionChanged();
}

package billiards.render;

import billiards.world.Table;

import java.awt.Graphics2D;

/**
 * 画球台。整层的外观见 {@link TableTexture}，这里只负责「什么时候烘、烘哪一张」
 *
 * <h2>为什么缓存放在这里而不是 {@code GameRenderer}</h2>
 * {@link GameRenderer} 在构造期就能烘（它自己 {@code new Table()} 就行），但那样
 * {@code draw(g, table, view)} 收进来的 {@code table} 就只对球有约束、对台面没有，
 * 成了一个「传了但没人用」的参数——正是这类参数会让人以为改了它有用
 * 放在这里，烘的就是调用方真正传进来的那张台子的几何
 *
 * <h2>为什么比对面而不是比内容</h2>
 * 正常情况下全程只有一张 {@code Table}（{@link billiards.rules.RulesEngine} 里那张），
 * 于是只烘一次。调用方传入另一张<b>几何不同</b>的台子时，需要重新烘焙，不能拿旧图
 * 去画新台面——那会画出「看得见的库边」和「算得出的库边」对不上的画面，而这正是
 * 这个工程一路在防的那种错位。比对引用是最省事又不会错判的判据
 *
 * <h2>浏览器启动</h2>
 * 构建工具提前生成木纹和绒面，浏览器在窗口显示前读取 PNG，避免首帧执行逐像素计算
 * 桌面自检仍使用原绘制器，检查生成图像与物理几何一致
 */
public final class TableRenderer {

    private TableTexture texture;
    private Table bakedFrom;

    /** 根据传入的模型或只读快照绘制当前图层 */
    public void draw(Graphics2D graphics, Table table) {
        prepare(table);
        texture.draw(graphics);
    }

    /** 在窗口出现前读取静态底图，避免首次绘制时才开始准备大图 */
    public void prepare(Table table) {
        if (texture == null || bakedFrom != table) {
            if (RenderAssets.browserMode()) {
                texture = TableTexture.loadPrepared();
            } else {
                texture = TableTexture.bake(table);
            }
            bakedFrom = table;
        }
    }
}

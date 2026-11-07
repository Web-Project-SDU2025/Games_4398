package billiards.world;

/**
 * 全部屏幕/桌面几何与 UI 时间常量
 *
 * <p>这是坐标的<b>单一真源</b>：{@code InputController} 把鼠标坐标转成世界坐标、
 * {@code TableRenderer} 把世界坐标画到屏幕，两边都读这里，因此不可能出现
 * 「画在这、点在那」的错位
 *
 * <p>逻辑画布固定 {@link #WINDOW_W}×{@link #WINDOW_H}，<b>与实际窗口尺寸无关</b>：
 * 窗口可以随意缩放，靠 {@link Viewport} 做一次等比缩放 + 居中留边把两者对上。整个工程
 * 里只有两处需要知道窗口的真实大小——画的时候施加变换、收鼠标的时候施加逆变换
 */
public final class Layout {

    // ---- 逻辑画布 ----

    /**
     * 逻辑画布尺寸。它<b>不是</b>窗口尺寸——窗口多大由 {@link Viewport} 缩放到它上面
     *
     * <p>高度从 M1–M5 的 640 涨到 700 是 M6 唯一动过的一处画布数字：HUD 要塞进
     * 双方落袋托盘、组徽、当前手高亮、一行判定文案、力度条与「重新开始」按钮，
     * 而 640 只给 HUD 留了 94px（内衬之后 70px）——一行判定文案就撑满了，托盘无处可放
     * <b>台面几何一个数都没动</b>（{@code PLAYFIELD_TOP} 仍是 60，外框下沿仍是 540），
     * 多出来的 60px 全部落在 HUD 那一侧，所以球的物理、袋口位置、瞄准几何全都不受影响
     */
    public static final int WINDOW_W = 1000;
    public static final int WINDOW_H = 700;

    // ---- 台面 ----

    /** 台呢（可滚球区域）边界。880×440，正好 2:1 */
    public static final double PLAYFIELD_LEFT = 60.0;
    public static final double PLAYFIELD_TOP = 60.0;
    public static final double PLAYFIELD_RIGHT = 940.0;
    public static final double PLAYFIELD_BOTTOM = 500.0;

    /** 库边带宽（台呢外沿到外框内沿） */
    public static final double CUSHION_BAND = 18.0;

    /**
     * 木质外框带宽（库边外沿再往外）
     *
     * <p><b>它由角袋定，不是随手挑的。</b>角袋的外圈半径是
     * {@code POCKET_MOUTH_RADIUS_CORNER + POCKET_RIM_WIDTH = 39}，而袋心离台面角点只有
     * {@code POCKET_CENTER_OFFSET/√2 ≈ 7.07}。外框边线若比
     * {@code 39 − 7.07 = 31.93} 更贴近袋心，袋口就会捅出木头外面——原来取 22（边线离袋心
     * 32.93）时，四个角袋各自向外捅出 <b>6.07px</b>，被贴图边界切出一道笔直的弦，看上去
     * 像台面被削了一角。反解：
     * <pre>
     *     PLAYFIELD_LEFT − CUSHION_BAND − FRAME_BAND ≥ 角袋袋心 x − 角袋外圈半径
     *     60 − 18 − FRAME_BAND ≥ 52.929 − 39
     *     FRAME_BAND ≥ 28.071
     * </pre>
     * 取 30 而不是压线的 28.071：圆角弧（见 {@link #cornerRadius()}）于是在皮革环外还留出
     * 约 1.9px 木头，袋口是被木框<b>包住</b>的，而不是正好齐平
     *
     * <p>上界的余量很窄：外框下沿 {@code 500 + 18 + 30 = 548}，而 {@code HUD_TOP = 546}——
     * 台面底边比 HUD 区上沿低 2px。这没关系，HUD 底板本身还内缩 {@code HUD_INSET = 12}
     * 才开始画，实际留白（548 → 558）仍有 10px。<b>但别再往上加了</b>
     */
    public static final double FRAME_BAND = 30.0;

    /** 球半径。直径 22px，沿长边可排 40 个球 */
    public static final double BALL_RADIUS = 11.0;

    // ---- 袋口 ----

    /** 画出来的袋口半径。角袋画得比中袋大——真实球台也是这样 */
    public static final double POCKET_MOUTH_RADIUS_CORNER = 36.0;
    public static final double POCKET_MOUTH_RADIUS_SIDE = 24.0;

    /**
     * 洞口外那圈皮革（金属）边的宽度，画面上是 {@code r .. r + 3} 那一环
     *
     * <p>它本来只是 {@code TableTexture} 里的一个字面量 {@code 3.0}，之所以提到这里，
     * 是因为<b>木框的宽度要用它</b>：外框边线必须让开 {@code 洞口 + 皮革环}这个外圈，
     * 见 {@link #FRAME_BAND}。画法与几何共用同一个数，就不会出现「皮革环悄悄变厚、
     * 于是又被边界切掉一角」这种改一处忘一处的事
     */
    public static final double POCKET_RIM_WIDTH = 3.0;

    /**
     * 捕获判定半径。<b>角袋与中袋不同</b>，这不是随手调的数值，而是由一条必须成立的
     * 等式反解出来的，改动前请先读 {@link #CUSHION_GAP_HALF_CORNER} 的推导
     *
     * <p>中袋 19：必须小于 {@code BALL_RADIUS + POCKET_CENTER_OFFSET = 21}，否则贴库
     * 平行滚动的球会被中袋吸进去——「擦库不得捕获」这条闭环测试钉的就是它
     *
     * <p>角袋 36：角袋没有这条保护（也不该有，贴库滚进角袋就该掉），但需要足够大才能
     * 兼顾「封住漏缝」与「可进球窗口」这两个互相拉扯的要求
     */
    public static final double POCKET_CATCH_RADIUS_CORNER = 36.0;
    public static final double POCKET_CATCH_RADIUS_SIDE = 19.0;

    /** 袋心向台外偏移量（角袋沿 45° 对角线，边袋沿库边法向） */
    public static final double POCKET_CENTER_OFFSET = 10.0;

    /** 库边在角袋处的缺口半宽 */
    public static final double CUSHION_GAP_HALF_CORNER = 37.0;

    /** 库边在边袋处的缺口半宽 */
    public static final double CUSHION_GAP_HALF_SIDE = 25.0;

    /*
     * ============================ 袋口几何的推导 ============================
     *
     * 上面四个数（两个缺口半宽、两个捕获半径）不能独立乱调，它们被两条互相拉扯的
     * 要求同时钉住。记：
     *     gc   = CUSHION_GAP_HALF_CORNER
     *     rc   = POCKET_CATCH_RADIUS_CORNER
     *     o    = POCKET_CENTER_OFFSET / √2     角袋袋心到台面角点的对角偏移分量
     *     cc   = PLAYFIELD_LEFT - o            角袋袋心的 x（= 52.929）
     *     库边覆盖起点 = PLAYFIELD_LEFT + gc - BALL_RADIUS
     *        （库边的「端帽」按一个球半径向袋口方向伸出，见 CushionResolver）
     *
     * 要求一【封住漏缝】：球心在 y = PLAYFIELD_TOP 这条线上若既不被任何库边覆盖、
     * 又不在任何捕获圆内，它就会一路掉出台面。捕获圆在这条线上的右边界是
     *     cc + √(rc² - o²)
     * 因为袋心在 y = TOP - o，比这条线高 o。于是必须
     *     PLAYFIELD_LEFT + gc - R  ≤  cc + √(rc² - o²)          … (1)
     * 要求二【可进球窗口】：球心沿对角线进角时必须能在被端帽推回之前穿过 y = TOP + R
     * 该线的通过点 x 相对理想值（≈70.9，即对角线在 y = TOP + R 处的 x）每偏 1° 约
     * 移动 4.2px，所以窗口宽度直接决定容差角度。窗口右界就是 (1) 的左式，故
     *     gc 越小、rc 越大 → 窗口越宽
     * 两条要求方向相反：gc 小或 rc 大会把 (1) 推向不成立，gc 大又会缩窗口
     *
     * 当前取值 gc = 37、rc = 36 代入：
     *     覆盖起点 = 60 + 37 - 11 = 86.00
     *     捕获右界 = 52.929 + √(36² - 7.071²) = 52.929 + 35.30 = 88.23   ✓ 余量 2.23px
     *     容差     = (86.00 - 70.9) / 4.2 ≈ ±3.6°   （真实球台约 ±3.5°，量级一致）
     *
     * 中袋同理，但袋心就在 y = TOP - POCKET_CENTER_OFFSET，故
     *     未被覆盖带 = (midX - gs + R, midX + gs - R) = (486, 514)
     *     捕获带     = (midX ± √(rs² - offset²))    = (483.84, 516.16)   ✓ 前者被后者包住
     *
     * 改动任一个数，GeometryChecks 里的「袋口密封扫描」会立刻炸出来——它沿四条库边线
     * 逐像素采样，断言每个采样点要么被库边覆盖、要么在某捕获圆内。这条检查是上面这段
     * 推导的可执行版本，比注释可靠
     * =======================================================================
     */

    /** 袋口数量 */
    public static final int POCKET_COUNT = 6;

    // ---- 关键线位 ----

    /** 开球线：长边的 1/4 处。主球开球时球心须在其左侧（且留出半径） */
    public static final double HEAD_STRING_X = PLAYFIELD_LEFT + playfieldWidth() * 0.25;

    /** 置球点：长边的 3/4 处、台面中线。摆球顶球与 8 号重置都用它 */
    public static final double FOOT_SPOT_X = PLAYFIELD_LEFT + playfieldWidth() * 0.75;
    public static final double FOOT_SPOT_Y = PLAYFIELD_TOP + playfieldHeight() * 0.5;

    // ---- HUD ----

    /*
     * ================== HUD 的版面是从上沿一路推下来的 ==================
     *
     * 面板 y ∈ [HUD_TOP + HUD_INSET, HUD_BOTTOM - HUD_INSET] = [558, 688]，高 130
     * 面板里再留 HUD_PAD 的内衬，得到一个高 110 的内容带：
     *
     *     568 ┌──────────── A 行 42：双方托盘卡 + 中央 8 号徽章 ────────────┐
     *     610 └──────────────────────────────────────────────────────────┘
     *     616 ┌─ B 行 24：阶段药丸 + 一行判定文案 ────────────────────────┐
     *     640 └──────────────────────────────────────────────────────────┘
     *     646 ┌─ C 行 32：重新开始按钮 + 力度条 ──────────────────────────┐
     *     678 └──────────────────────────────────────────────────────────┘
     *
     * 42 + 6 + 24 + 6 + 32 = 110，严丝合缝。下面那些 hudRowXxx() 就是这张图的可执行版本：
     * 渲染器只读它们，一个数字都不自己拼，改这里版面整体跟着动，不会出现「改了行高、
     * 力度条还留在原地」
     *
     * 为什么 A 行最高：托盘里的球是 22px 的球体位图（BallSpriteCache 的烘焙尺寸），
     * 42px 才放得下球再加一点呼吸。B 行只放文字，24px 够。C 行按力度条的 22px 加 5px 上下留白
     * ====================================================================
     */

    public static final double HUD_TOP = 546.0;
    public static final double HUD_BOTTOM = WINDOW_H;

    /** HUD 底板相对窗口边缘的内缩 */
    public static final double HUD_INSET = 12.0;

    /** 底板内衬（内容带相对底板的留白） */
    public static final double HUD_PAD = 10.0;

    /** 三行之间的竖直间隙 */
    public static final double HUD_ROW_GAP = 6.0;

    /** A 行高度：双方托盘卡与中央 8 号徽章 */
    public static final double HUD_ROW_A_H = 42.0;

    /** B 行高度：阶段药丸 + 判定文案 */
    public static final double HUD_ROW_B_H = 24.0;

    /** C 行高度：重新开始按钮 + 力度条 */
    public static final double HUD_ROW_C_H = 32.0;

    /**
     * 玩家卡宽度。两张卡对称贴住内容带两侧，中间留给 8 号徽章
     *
     * <p>340 不是拍的：一张卡要放得下「名字（15px 粗体）+ 组徽（最宽是『花色 9–15』）
     * + 7 个托盘槽位（24px 间距）」，算下来正好 342px 减去两侧内衬。这个预算由
     * {@code HudRenderer.cardSlack} 量着，UIChecks 里有一条断言钉住——改字号或改槽距
     * 而忘了改卡宽，那条会先炸，而不是等到某台机器上托盘压出卡片边框才发现
     */
    public static final double HUD_CARD_W = 340.0;

    /** 托盘里一颗球的直径与相邻球心间距。直径必须等于 {@code BallSpriteCache} 的烘焙直径 */
    public static final double HUD_TRAY_BALL_D = 2.0 * BALL_RADIUS;
    public static final double HUD_TRAY_PITCH = 24.0;

    /** 托盘槽位数：全色与花色各 7 颗。固定成 7 而不是「剩几颗画几颗」——位置固定才知道少了谁 */
    public static final int HUD_TRAY_SLOTS = 7;

    /** 「重新开始」按钮 */
    public static final double RESTART_BTN_W = 114.0;

    /**
     * 力度条的位置与尺寸
     *
     * <p>放在 {@link Layout} 而不是渲染器里，是为了让自检能断言「力度条整条落在窗口内」——
     * 这种「画到窗口外面去了」的错误在肉眼下只表现为「力度条不见了」，很难查
     * 注意这里是<b>数值</b>而不是 {@code Rectangle2D}：{@code world} 包刻意不引入
     * {@code java.awt}，好让模型层与图形库完全无关
     */
    public static final double POWER_BAR_W = 308.0;
    public static final double POWER_BAR_H = 22.0;

    // ---- 瞄准与拉杆 ----

    /*
     * ===================== 一个手势，两个互相独立的量 =====================
     *
     * 拉杆要同时定下两件事——**往哪打**与**打多重**。它们必须能一起调：瞄完了才发现力度
     * 不合适、或者力度定好了才发现瞄歪一点，都只能推倒重来，那这个操作就没法练
     *
     * 两个量挂在**同一只手**上，所以要让它们互相独立，得让它们各自对应手的一个**正交**
     * 自由度：
     *     方向 ← 光标相对**主球**的方位（手绕着球转）
     *     力度 ← 手离**按下点**的距离（手往远处伸）
     * 于是绕球转手腕只改方向、往后拖只改力度；两件事同时做也互不干扰
     *
     * 力度为什么不是「按住时长」：读数一直在动。玩家看到力度条涨到想要的位置再松手，
     * 等手真的松开，条又涨了一截——误差正比于各人的反应时间，而且**松手之后无法修正**
     * 改成拉距之后力度是一个**位置**：拉到哪就是多少，手停住读数就停住，松手之前可以
     * 来回微调。附带的好处是模拟线程不必再读墙钟
     *
     * 力度为什么取「离按下点的**距离**」而不是「沿拉杆轴的**投影**」：投影要求拉杆轴
     * 先定死，而拉杆轴就是瞄准方向——方向要能实时改，轴就在实时转，投影会跟着轴一起
     * 变，「转手腕」于是漏出轴向分量被算成力度，越转越重，两个量又绑回一起了。取距离
     * 则与方向完全无关：手拖了多远就是多重，与拖向哪边无关
     *
     * 代价是「斜着拖也会加力」——但那本来就该加力（手确实往外伸了）。实测绕按下点等
     * 半径摆动时力度**逐位不变**（UIChecks 里那条断言钉的就是它），所以两个量在手感上
     * 是干净分开的
     * ====================================================================
     */

    /** 拉满力度所需的手部位移（px）——从按下点到光标的距离 */
    public static final double PULL_MAX_PX = 140.0;

    /**
     * 拉杆死区（px）。手离按下点不足这个距离就松手＝取消这一杆，不击球
     *
     * <p>它一物两用。<b>其一</b>是防误触：点一下鼠标不该打出一杆。<b>其二</b>是吸收
     * 「按下瞬间手带的那一点位移」——鼠标按下的同时总会有几像素的抖动，那点抖动不该
     * 被读成一记轻推
     *
     * <p>力度取的是手部位移的<b>大小</b>，任何方向的移动都会算进来（见上面那段推导），
     * 所以死区是挡在「按一下」与「打一杆」之间唯一的东西，别调得太小
     */
    public static final double PULL_DEAD_ZONE_PX = 20.0;

    /**
     * 光标离球心近于这个距离时，瞄准方向不再更新（保持上一次的方向）
     *
     * <p>取 2.5R：光标压在球心上时「球 → 光标」这个向量由浮点噪声决定，跟着它转会让
     * 瞄准线疯狂乱晃。<b>保持上一次方向</b>而不是跳回默认方向，是因为玩家的手只是从球上
     * 划过去，方向不该突然翻掉
     */
    public static final double AIM_MIN_RADIUS_PX = 2.5 * BALL_RADIUS;

    /** 球杆长度（px） */
    public static final double CUE_LENGTH_PX = 300.0;

    /** 球杆头与球面之间的静态间隙（px） */
    public static final double CUE_TIP_GAP_PX = 6.0;

    /**
     * 满力时球杆额外后拉的距离（px）
     *
     * <p>它让「拉了多少」不看力度条也一眼可见——力度条是读数，球杆的位置才是手感
     */
    public static final double CUE_PULL_BACK_PX = 90.0;

    /**
     * 瞄准线一路撞不到任何东西时，仍然画这么长（px）
     *
     * <p>「撞不到」不是理论情形：库边在袋口处是断开的，贴着袋口斜着往外瞄，射线就从缺口
     * 穿出台面。这时没有虚影球可画，但整条瞄准线不该凭空消失（玩家会以为程序卡了）
     * 1100 &gt; 台面对角线 {@code √(880² + 440²) ≈ 984}，所以不管从台面哪个角落射出、
     * 朝哪个方向，这条线都长到能一路画到窗口外，看起来就是「射出去了」
     */
    public static final double AIM_ESCAPE_LENGTH_PX = 1100.0;

    // ---- 浮层 ----

    /*
     * 开场提示与终局面板共用一块居中的板子：两处都是「盖住台面说一件事」，用同一个尺寸
     * 才不会在两次出现之间跳动。横幅（判定中）另算，它是横贯台面的一条，不是一块板
     */

    /** 开场/终局浮层板的尺寸 */
    public static final double OVERLAY_PANEL_W = 560.0;
    public static final double OVERLAY_PANEL_H = 300.0;

    /** 浮层板的圆角 */
    public static final double OVERLAY_PANEL_ARC = 16.0;

    /** 判定横幅：高度，以及允许的最大宽度（超了就把字缩小，不裁字） */
    public static final double BANNER_H = 56.0;
    public static final double BANNER_MAX_W = 820.0;

    /** 调试浮层文本块的左上角。放在台面左上角内侧，不压住球堆（球堆在置球点 x=720 一带） */
    public static final double DEBUG_PANEL_X = 74.0;
    public static final double DEBUG_PANEL_Y = 74.0;

    // ---- UI 时间 ----

    /** 判定横幅（犯规/胜负提示）至少显示多久，让裁判过程在演示中看得见 */
    public static final double VERDICT_BANNER_MS = 1400.0;

    /** 工具类只提供静态方法，不需要创建对象 */
    private Layout() {
    }

    /** 力度条右沿贴住内容带右沿，竖直居中于 C 行 */
    public static double powerBarX() {
        return hudContentRight() - POWER_BAR_W;
    }

    /** 按控件行高返回力度条的居中竖直位置 */
    public static double powerBarY() {
        return hudRowCTop() + (HUD_ROW_C_H - POWER_BAR_H) * 0.5;
    }

    /** HUD 内容带（三行共同的可写区域） */
    public static double hudContentLeft() {
        return HUD_INSET + HUD_PAD;
    }

    /** 返回状态区扣除外边距和内边距后的右边界 */
    public static double hudContentRight() {
        return WINDOW_W - HUD_INSET - HUD_PAD;
    }

    /** 返回玩家信息行的顶部位置 */
    public static double hudRowATop() {
        return HUD_TOP + HUD_INSET + HUD_PAD;
    }

    /** 根据上一行高度和行距返回状态文字行顶部 */
    public static double hudRowBTop() {
        return hudRowATop() + HUD_ROW_A_H + HUD_ROW_GAP;
    }

    /** 根据上一行高度和行距返回操作行顶部 */
    public static double hudRowCTop() {
        return hudRowBTop() + HUD_ROW_B_H + HUD_ROW_GAP;
    }

    /** 内容带下沿。自检断言它不越出底板内沿 */
    public static double hudContentBottom() {
        return hudRowCTop() + HUD_ROW_C_H;
    }

    /** 玩家 1 的卡（左） */
    public static double hudCardOneX() {
        return hudContentLeft();
    }

    /** 玩家 2 的卡（右） */
    public static double hudCardTwoX() {
        return hudContentRight() - HUD_CARD_W;
    }

    /** 「重新开始」按钮的外框 */
    public static double restartBtnX() {
        return hudContentLeft();
    }

    /** 返回重开按钮所在操作行的顶部位置 */
    public static double restartBtnY() {
        return hudRowCTop();
    }

    /** 返回居中浮层的水平起点 */
    public static double overlayPanelX() {
        return (WINDOW_W - OVERLAY_PANEL_W) * 0.5;
    }

    /** 返回居中浮层的竖直起点 */
    public static double overlayPanelY() {
        return (WINDOW_H - OVERLAY_PANEL_H) * 0.5;
    }

    // ---- 派生 ----

    /** 返回可滚球台面的宽度 */
    public static double playfieldWidth() {
        return PLAYFIELD_RIGHT - PLAYFIELD_LEFT;
    }

    /** 返回可滚球台面的高度 */
    public static double playfieldHeight() {
        return PLAYFIELD_BOTTOM - PLAYFIELD_TOP;
    }

    /** 返回台面中心线的竖直位置 */
    public static double playfieldCenterY() {
        return PLAYFIELD_TOP + playfieldHeight() * 0.5;
    }

    /** 外框（含木边）的矩形边界 */
    public static double outerLeft() {
        return PLAYFIELD_LEFT - CUSHION_BAND - FRAME_BAND;
    }

    /** 返回包含库边和木框的最上侧边界 */
    public static double outerTop() {
        return PLAYFIELD_TOP - CUSHION_BAND - FRAME_BAND;
    }

    /** 返回包含库边和木框的最右侧边界 */
    public static double outerRight() {
        return PLAYFIELD_RIGHT + CUSHION_BAND + FRAME_BAND;
    }

    /** 返回包含库边和木框的最下侧边界 */
    public static double outerBottom() {
        return PLAYFIELD_BOTTOM + CUSHION_BAND + FRAME_BAND;
    }

    // ---- 木框的外轮廓（四角不是尖角） ----

    /** 角袋袋心 x（沿对角线往台外偏 {@code POCKET_CENTER_OFFSET/√2}） */
    public static double cornerPocketX() {
        return PLAYFIELD_LEFT - POCKET_CENTER_OFFSET / Math.sqrt(2.0);
    }

    /** 角袋袋心 y。与 {@link #cornerPocketX()} 对称 */
    public static double cornerPocketY() {
        return PLAYFIELD_TOP - POCKET_CENTER_OFFSET / Math.sqrt(2.0);
    }

    /** 角袋外圈半径：洞口 + 那圈皮革边。木框的外边线就是被它顶住的 */
    public static double cornerPocketOuterRadius() {
        return POCKET_MOUTH_RADIUS_CORNER + POCKET_RIM_WIDTH;
    }

    /**
     * 木框四角的圆角半径：<b>弧心正是角袋袋心</b>，所以这条弧与相邻两条外框边线相切，
     * 过渡处没有折角，而皮革环外沿（{@link #cornerPocketOuterRadius()}）恰好落在弧内
     *
     * <p>真实球台的俯视图本来就不是一个方正矩形——角上那块让给了袋口，外轮廓是沿着
     * 袋口收进去的。这个圆角就是那件事
     */
    public static double cornerRadius() {
        return cornerPocketX() - outerLeft();
    }

    /** 返回目标球摆放点的逻辑坐标 */
    public static Vec2 footSpot() {
        return new Vec2(FOOT_SPOT_X, FOOT_SPOT_Y);
    }

    // 坐标换算见 Viewport：屏幕坐标与这里这批逻辑坐标之间的换算要按窗口尺寸来算，
    // 所以它需要一个参数（缩放与留边），不是无参的静态换算

    /** 逻辑画布的宽高比。窗口缩放取等比，为的就是保住它不变 */
    public static double aspectRatio() {
        return WINDOW_W / (double) WINDOW_H;
    }
}

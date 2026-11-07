package billiards.rules;

import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.ShotEventListener;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一杆球的<b>证据</b>：把物理引擎喊出来的事件记成一张判罚用得上的清单
 *
 * <h2>为什么要有这一层</h2>
 * 判罚需要的事实全都<b>发生在过程中</b>而不是末尾：「主球第一颗碰到的是谁」「碰库发生在
 * 首次触球之前还是之后」「哪几颗球碰过库」「落袋的先后顺序」。这些量在球全停下来之后
 * 从台面状态里<b>根本读不出来</b>——球停在哪不告诉你它路上碰过谁。所以必须一边滚一边记
 *
 * <p>它是 {@link ShotEventListener} 的实现，由 {@link RulesEngine} 注册进
 * {@link billiards.physics.PhysicsEngine}。物理不认识规则、规则不认识物理，两边只通过
 * {@code world} 包里这个接口说话——这就是方案里那条「本工程最重要的一处解耦缝」
 *
 * <h2>为什么是可变的一个对象反复用，而不是每杆新建</h2>
 * 每杆新建当然更干净，但录制器要被<b>物理引擎长期持有</b>（注册一次，之后每杆都在）
 * 于是要么每杆重新注册/注销，要么就地清空。选了后者：{@link #reset()} 一次，开销为零，
 * 也不存在「注销漏了导致上一杆的监听器还在收事件」这类问题
 *
 * <h2>记录的量与它们各自服务哪条判罚</h2>
 * <ul>
 *   <li>{@code firstContactBallId} → 先碰错球（{@code WRONG_BALL_FIRST}）、没碰到球
 *       （{@code MISS_NO_CONTACT}）</li>
 *   <li>{@code anyRailAfterContact} → 碰球后无球碰库（{@code NO_RAIL_AFTER_CONTACT}）</li>
 *   <li>{@code objectBallsTouchingRail} → 开球四球碰库（{@code ILLEGAL_BREAK}）</li>
 *   <li>{@code pocketedInOrder} → 8 号落袋、开放台面定组（「先落袋的那颗」）</li>
 *   <li>{@code cueBallPocketed} → 主球落袋</li>
 *   <li>{@code ballsOffTable} → 飞球</li>
 * </ul>
 */
public final class ShotRecord implements ShotEventListener {

    /**
     * 首次触球：与主球发生碰撞的第一颗目标球。<b>主球与主球不会碰</b>，所以凡涉及 0 号的
     * 球球碰撞就是「主球碰到了谁」。取值 {@code -1} 表示本杆还没碰到过任何球
     */
    private int firstContactBallId = -1;

    /** 本杆是否已发生首次触球。用单独一个布尔而不是判 {@code firstContactBallId >= 0}， 是为了让「碰到了但球号非法」这类异常也能被看见 */
    private boolean firstContactOccurred;

    /** 落袋顺序。开放台面定组要看「先落袋的那颗」，所以顺序是必需的，不能只留一个集合 */
    private final List<Integer> pocketedInOrder = new ArrayList<>(4);

    /** 主球是否落袋。单独存一份是因为落袋清单里也可能有它，判罚时要问很多次 */
    private boolean cueBallPocketed;

    /** 碰过库的目标球号（去重）。开球「四球碰库」数的是<b>球</b>不是次数——同一颗球来回弹三下仍只算一颗 */
    private final Set<Integer> objectBallsTouchingRail = new LinkedHashSet<>();

    /** 主球是否碰过库。不计入四球碰库，但「碰球后有球碰库」算它 */
    private boolean cueBallTouchedRail;

    /** 首次触球之后是否有任何球碰过库 */
    private boolean anyRailAfterContact;

    /** 飞出台面的球号（去重） */
    private final Set<Integer> ballsOffTable = new LinkedHashSet<>();

    /** 本杆球球碰撞总次数。诊断用，判罚不看它 */
    private int contactCount;

    /** 只在首次触球前记录主球碰库，避免把碰球后的反弹误判为勾球 */
    private boolean cueRailBeforeFirstContact;

    /** 目标球落袋时已发生过碰库的球号，避免事后事件改变翻袋证据 */
    private final Set<Integer> bankedPots = new LinkedHashSet<>();

    /** 清空以迎接下一杆。由 {@link RulesEngine} 在出杆前调用 */
    public void reset() {
        firstContactBallId = -1;
        firstContactOccurred = false;
        pocketedInOrder.clear();
        cueBallPocketed = false;
        objectBallsTouchingRail.clear();
        cueBallTouchedRail = false;
        anyRailAfterContact = false;
        ballsOffTable.clear();
        contactCount = 0;
        cueRailBeforeFirstContact = false;
        bankedPots.clear();
    }

    // ShotEventListener

    /** 处理球球接触事件，参数提供球号、接触点和碰撞前速度 */
    @Override
    public void onBallBallContact(int aId, int bId, Vec2 contactPoint, double normalSpeed) {
        contactCount = contactCount + 1;
        if (firstContactOccurred) {
            return;
        }
        // 只有「涉及主球」的那次才算首次触球。子步内的松弛迭代会把同一对球反复报几次
        // （位置修正每轮都可能重新判定重叠），所以这里必须幂等：首次一旦记下就不再改
        if (aId == BallCatalog.CUE_ID) {
            markFirstContact(bId);
        } else if (bId == BallCatalog.CUE_ID) {
            markFirstContact(aId);
        }
    }

    /** 保存主球首次接触的目标球，主球自身不作为目标 */
    private void markFirstContact(int ballId) {
        if (ballId == BallCatalog.CUE_ID) {
            return;
        }
        firstContactBallId = ballId;
        firstContactOccurred = true;
    }

    /**
     * {@code normalSpeed} 不参与判罚：八条犯规问的都是「碰没碰过库」，
     * 不问「碰得多重」。它存在是为了让订阅同一个接口的 {@code audio.ShotSounds}
     * 能把库边声按力度分档——这也是这个接口当初就长在 {@code world} 的原因
     */
    @Override
    public void onCushionContact(int ballId, int railIndex, double normalSpeed) {
        if (ballId == BallCatalog.CUE_ID) {
            cueBallTouchedRail = true;
            if (!firstContactOccurred) {
                cueRailBeforeFirstContact = true;
            }
        } else {
            objectBallsTouchingRail.add(ballId);
        }
        if (firstContactOccurred) {
            anyRailAfterContact = true;
        }
    }

    /** 处理落袋事件，参数提供球号、袋号和落袋前速度 */
    @Override
    public void onBallPocketed(int ballId, int pocketIndex, double speed) {
        pocketedInOrder.add(ballId);
        if (objectBallsTouchingRail.contains(ballId)) {
            bankedPots.add(ballId);
        }
        if (ballId == BallCatalog.CUE_ID) {
            cueBallPocketed = true;
        }
    }

    /** 处理飞出台面的球号，记录或忽略该事件由所属模块决定 */
    @Override
    public void onBallOffTable(int ballId) {
        ballsOffTable.add(ballId);
    }

    // 查询

    /** 返回主球是否在首次碰到目标球之前碰过库边 */
    public boolean cueRailBeforeFirstContact() {
        return cueRailBeforeFirstContact;
    }

    /** 返回指定目标球是否在落袋前碰过库边 */
    public boolean bankedBeforePocket(int ballId) {
        return bankedPots.contains(ballId);
    }

    /** 返回本杆是否已经发生首次触球 */
    public boolean firstContactOccurred() {
        return firstContactOccurred;
    }

    /** 返回主球首次接触的目标球号 */
    public int firstContactBallId() {
        return firstContactBallId;
    }

    /** 首次触球那颗球的组属；从没碰到球时返回 {@code null} */
    public BallGroup firstContactGroup() {
        if (!firstContactOccurred) {
            return null;
        }
        return BallCatalog.groupOf(firstContactBallId);
    }

    /** 落袋顺序（只读，含主球） */
    public List<Integer> pocketedInOrder() {
        return Collections.unmodifiableList(pocketedInOrder);
    }

    /** 目标球落袋清单（按顺序，不含主球）。开放台面定组看的就是它 */
    public List<Integer> objectBallsPocketedInOrder() {
        List<Integer> out = new ArrayList<>(pocketedInOrder.size());
        for (int ballId : pocketedInOrder) {
            if (ballId != BallCatalog.CUE_ID) {
                out.add(ballId);
            }
        }
        return out;
    }

    /** 返回指定球号是否出现在本杆落袋记录中 */
    public boolean pocketed(int ballId) {
        return pocketedInOrder.contains(ballId);
    }

    /** 返回本杆是否有任意球落袋 */
    public boolean anyBallPocketed() {
        return !pocketedInOrder.isEmpty();
    }

    /** 返回主球是否在本杆落袋 */
    public boolean cueBallPocketed() {
        return cueBallPocketed;
    }

    /** 返回首次触球之后是否有球碰库 */
    public boolean anyRailAfterContact() {
        return anyRailAfterContact;
    }

    /** 返回本杆主球是否碰过库边 */
    public boolean cueBallTouchedRail() {
        return cueBallTouchedRail;
    }

    /** 碰过库的目标球号（只读） */
    public Set<Integer> objectBallsTouchingRail() {
        return Collections.unmodifiableSet(objectBallsTouchingRail);
    }

    /** 碰过库的目标球<b>颗数</b>。开球四球碰库判据 */
    public int objectBallsTouchingRailCount() {
        return objectBallsTouchingRail.size();
    }

    /** 飞出台面的球号（只读） */
    public Set<Integer> ballsOffTable() {
        return Collections.unmodifiableSet(ballsOffTable);
    }

    /** 返回本杆累计记录的球球接触次数 */
    public int contactCount() {
        return contactCount;
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        String firstContactText = "无";
        if (firstContactOccurred) {
            firstContactText = String.valueOf(firstContactBallId);
        }
        return "ShotRecord{首触=" + firstContactText
                + ", 落袋=" + pocketedInOrder
                + ", 碰库目标球=" + objectBallsTouchingRail
                + ", 触球后碰库=" + anyRailAfterContact
                + ", 主球落袋=" + cueBallPocketed
                + ", 飞球=" + ballsOffTable + "}";
    }
}

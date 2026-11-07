package billiards.input;

import billiards.world.Layout;
import billiards.world.Vec2;
import billiards.world.Viewport;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 鼠标与键盘的适配器：把 Swing 事件翻译成 {@link InputCommand} 与光标位置
 *
 * <p>它<b>不认识游戏</b>——不知道什么叫球、什么叫撞库，只知道「按下了」「移动到这」
 * 因此模拟侧改玩法（M5 换成完整规则引擎）完全不用动这里，只要换一个消费指令的人
 * 这就是把输入单独成包的意义：它是人与模拟之间唯一的翻译层
 *
 * <h2>两类输入为什么分两条路</h2>
 * 见 {@link InputCommand} 的说明。简单说：光标用 {@code volatile} 引用直接发布
 * （读到一个旧值最多让瞄准线晚一帧），按键与点击走队列交给模拟线程
 * （它们改状态，不能有第二个线程碰）
 *
 * <h2>坐标</h2>
 * {@code MouseEvent.getX()/getY()} 是<b>组件</b>坐标，而游戏里的一切都在逻辑坐标里说
 * （逻辑画布固定 {@link Layout#WINDOW_W}×{@link Layout#WINDOW_H}，窗口可以任意缩放）
 * 两者之间隔着一个 {@link Viewport}，所以这里每次事件都要按组件的当前尺寸求一次映射
 *
 * <p>为什么不把映射缓存起来：它必须与 {@code GamePanel} 画的时候用的是同一个值，而画布
 * 尺寸随时可能变（用户正在拖窗口边缘）。每帧各自从同一个纯函数
 * {@link Viewport#fit} 算一遍，两边<b>在构造上</b>就不可能对不上；缓存则要同步、
 * 要处理失效，一旦漏了就变成「画在这、点在那」——那是本项目最不想要的一类 bug
 * 一次事件两次除法，代价可以忽略
 */
public final class InputController extends MouseAdapter {

    private final Queue<InputCommand> pending = new ConcurrentLinkedQueue<>();

    /**
     * 接上鼠标键盘的那个画布。留着它是为了随时能问到当前的窗口尺寸——
     * {@link #updateCursor} 靠它把组件坐标换算回逻辑坐标
     */
    private JComponent component;

    /**
     * 光标位置。用 {@code volatile} 引用发布一个不可变的 {@code Vec2}——
     * 引用赋值本身是原子的，所以模拟线程要么读到旧位置、要么读到新位置，绝不会读到
     * 「x 是新值、y 是旧值」的撕裂值。（直接把 x、y 两个 double 各自 volatile 就做不到
     * 这一点：两个独立的写之间没有原子性。）
     */
    private volatile Vec2 cursor = new Vec2(0.0, 0.0);

    /** 光标是否已经有过真实移动。没有时瞄准方向应由模拟侧取默认值，而不是指向左上角 */
    private volatile boolean cursorValid = false;

    /** 把监听器装到画布上 */
    public void attach(JComponent component) {
        this.component = component;
        component.addMouseListener(this);
        component.addMouseMotionListener(this);

        // 用 InputMap 而不是 KeyListener：WHEN_IN_FOCUSED_WINDOW 让快捷键只要窗口有焦点
        // 就生效，不必先把焦点抢到画布上——「按 R 没反应」通常是焦点问题，这里从设计上避开
        bindKey(component, KeyEvent.VK_R, "billiards.reset", InputCommand.Kind.RESET);
        bindKey(component, KeyEvent.VK_ESCAPE, "billiards.cancel", InputCommand.Kind.CANCEL);
        bindKey(component, KeyEvent.VK_D, "billiards.debug", InputCommand.Kind.DEBUG);
        bindKey(component, KeyEvent.VK_M, "billiards.mute", InputCommand.Kind.MUTE);
    }

    /** 将键码绑定到具名动作，让窗口有焦点时即可接收快捷键 */
    private void bindKey(JComponent component, int keyCode, String name, InputCommand.Kind kind) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0), name);
        component.getActionMap().put(name, new InputKeyAction(kind));
    }

    /** 把键盘动作转换成输入队列中的指令 */
    private final class InputKeyAction extends AbstractAction {

        private final InputCommand.Kind kind;

        /** 保存该快捷键要发送的指令类型 */
        private InputKeyAction(InputCommand.Kind kind) {
            this.kind = kind;
        }

        /** 将键盘操作放入队列，供模拟线程处理 */
        @Override
        public void actionPerformed(ActionEvent event) {
            pending.add(InputCommand.of(kind));
        }
    }

    // 模拟线程侧

    /** 当前光标（世界坐标）。未移动过时是 {@code (0, 0)}，配合 {@link #isCursorValid()} 使用 */
    public Vec2 cursor() {
        return cursor;
    }

    /** 返回光标是否已产生有效位置 */
    public boolean isCursorValid() {
        return cursorValid;
    }

    /** 取出队列中的下一条输入指令，队列为空时返回 null */
    public InputCommand poll() {
        return pending.poll();
    }

    /** 重开时丢弃待处理指令，防止旧的按下指令在新局中触发拉杆 */
    public void clearPending() {
        pending.clear();
    }

    // EDT 侧

    /** 读取移动事件中的坐标，转换为游戏逻辑坐标 */
    @Override
    public void mouseMoved(MouseEvent event) {
        updateCursor(event);
    }

    /** 更新拖动中的光标坐标，让瞄准与力度持续跟随鼠标 */
    @Override
    public void mouseDragged(MouseEvent event) {
        // 拖动时鼠标位置的更新走这里而不是 mouseMoved。不处理的话，拉杆期间光标就不动了——
        // 而拉杆正是靠拖动光标的距离来定力度的，整条力度反馈都会失效
        updateCursor(event);
    }

    /** 按组件尺寸换算鼠标坐标，输入和绘制使用同一套缩放规则 */
    private void updateCursor(MouseEvent event) {
        Viewport viewport = Viewport.IDENTITY;
        if (component != null) {
            viewport = Viewport.fit(component.getWidth(), component.getHeight());
        }
        cursor = viewport.toLogical(event.getX(), event.getY());
        cursorValid = true;
    }

    /** 更新光标并发送按下指令，右键直接发送取消指令 */
    @Override
    public void mousePressed(MouseEvent event) {
        updateCursor(event);
        // 右键在按下时就把拉杆取消掉，不等松开——玩家按右键就是「不打了」
        // 用到 getButton 而非 isPopupTrigger：后者在 Windows 上要等到放开才为真
        if (event.getButton() == MouseEvent.BUTTON3) {
            pending.add(InputCommand.of(InputCommand.Kind.CANCEL));
            return;
        }
        pending.add(InputCommand.press(cursor.x, cursor.y));
    }

    /** 更新光标并发送松手指令，右键取消已在按下时处理 */
    @Override
    public void mouseReleased(MouseEvent event) {
        updateCursor(event);
        if (event.getButton() == MouseEvent.BUTTON3) {
            return; // 右键在按下时已处理，松开不再发第二条取消
        }
        pending.add(InputCommand.release(cursor.x, cursor.y));
    }
}

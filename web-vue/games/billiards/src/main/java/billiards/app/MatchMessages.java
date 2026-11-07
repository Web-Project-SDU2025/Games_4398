package billiards.app;

import billiards.render.MatchView;
import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.rules.RulesEngine;
import billiards.rules.ShotVerdict;
import billiards.world.BallGroup;

/** 根据对局结果生成状态文案与提示语气，输入操作仍由会话处理 */
final class MatchMessages {
    private final RulesEngine engine;

    /** 保存规则引擎，用它读取当前阶段与最近一杆结果 */
    MatchMessages(RulesEngine engine) {
        this.engine = engine;
    }

    /**
     * HUD 上那一行字
     *
     * <p>长度是有预算的（B 行就那么宽），所以这里刻意<b>不</b>把整份判决书塞进瞄准阶段的
     * 提示里：完整文案只在 {@link GamePhase#RESOLVING} 那 1.4 秒里显示，其余时候换成一句
     * 短的。规则本身给了这个便利——{@link ShotVerdict#messageZh()} 是给横幅用的长句，
     * 而 {@code primaryFoul().labelZh()} 是给一行提示用的短语
     */
    String statusText(GamePhase phase) {
        if (phase == GamePhase.MATCH_START) {
            return "点击开始对局";
        }
        if (phase == GamePhase.BREAK_SETUP) {
            return "拖动主球到开球线后，松开落位";
        }
        if (phase == GamePhase.BALL_IN_HAND) {
            if (engine.cuePlacementRestricted()) {
                return "自由球：拖动主球到开球线后，松开落位";
            }
            return "自由球：拖动主球到台面任意合法位置，松开落位";
        }
        if (phase == GamePhase.AIMING) {
            return aimStatusText();
        }
        if (phase == GamePhase.SHOT_IN_FLIGHT) {
            return "球在滚动…";
        }
        if (phase == GamePhase.RESOLVING) {
            return verdictText();
        }
        return "本局结束 · 按 R 或点击再来一局";
    }

    /** 生成当前击球方、目标组属和上一杆犯规提示 */
    String aimStatusText() {
        Player current = engine.currentPlayer();
        BallGroup group = engine.groupOf(current);
        StringBuilder text = new StringBuilder();
        text.append(current.labelZh()).append(" 击球 · 目标 ");
        if (group == null) {
            text.append("未定（先进球者定组）");
        } else {
            text.append(group.labelZh());
        }
        ShotVerdict last = engine.lastVerdict();
        if (last != null && last.isFoul() && last.primaryFoul() != null) {
            text.append(" · 上杆犯规：").append(last.primaryFoul().labelZh());
        }
        return text.toString();
    }

    /** 读取最近一杆判决，尚无判决时显示等待状态 */
    String verdictText() {
        ShotVerdict verdict = engine.lastVerdict();
        if (verdict != null) {
            return verdict.messageZh();
        }
        MatchOutcome outcome = engine.outcome();
        if (outcome != null) {
            return outcome.winner().labelZh() + " 获胜 · " + outcome.reasonZh();
        }
        return "判定中…";
    }

    /**
     * 状态文案的语气
     *
     * <p>「上杆犯规」这句话要在瞄准阶段一直留着，所以它也得跟着变红——语气不能只看当前
     * 阶段，还要看这句话里说的是什么
     */
    MatchView.Tone statusTone(GamePhase phase) {
        if (phase == GamePhase.GAME_OVER) {
            return MatchView.Tone.WIN;
        }
        if (phase == GamePhase.RESOLVING) {
            return bannerTone(phase);
        }
        if (phase == GamePhase.AIMING) {
            ShotVerdict last = engine.lastVerdict();
            if (last != null && last.isFoul()) {
                return MatchView.Tone.FOUL;
            }
        }
        return MatchView.Tone.NEUTRAL;
    }

    // 判定横幅

    /** 横幅只在 {@link GamePhase#RESOLVING} 期间出现——它就是那个阶段的可视化 */
    String bannerText(GamePhase phase) {
        if (phase == GamePhase.RESOLVING) {
            return verdictText();
        }
        return "";
    }

    /** 根据当前阶段和判决返回横幅语气，颜色由绘制器选择 */
    MatchView.Tone bannerTone(GamePhase phase) {
        if (phase != GamePhase.RESOLVING) {
            return MatchView.Tone.NEUTRAL;
        }
        ShotVerdict verdict = engine.lastVerdict();
        if (verdict == null) {
            return MatchView.Tone.NEUTRAL;
        }
        if (verdict.gameOver()) {
            return MatchView.Tone.WIN;
        }
        if (verdict.isFoul()) {
            return MatchView.Tone.FOUL;
        }
        return MatchView.Tone.LEGAL;
    }

}

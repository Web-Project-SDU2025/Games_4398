package billiards.rules;

import billiards.world.BallCatalog;

/** 保存犯规名称和处罚，根据出杆前局面与本杆记录判断犯规 */
public enum FoulType {
    MISS_NO_CONTACT("未击中任何球", Penalty.BALL_IN_HAND),
    WRONG_BALL_FIRST("先碰到的不是本方目标球", Penalty.BALL_IN_HAND),
    CUE_BALL_POCKETED("主球落袋", Penalty.BALL_IN_HAND),
    NO_RAIL_AFTER_CONTACT("碰球后无球碰库", Penalty.BALL_IN_HAND),
    BALL_OFF_TABLE("球飞出台面", Penalty.BALL_IN_HAND),
    ILLEGAL_BREAK("开球未达标（无球落袋且不足四球碰库）", Penalty.BALL_IN_HAND_BEHIND_HEAD_STRING),
    EIGHT_POCKETED_EARLY("8 号球提前落袋", Penalty.LOSE_GAME),
    EIGHT_WITH_FOUL("8 号球与犯规同杆落袋", Penalty.LOSE_GAME);

    private final String labelZh;
    private final Penalty penalty;

    /** 保存玩家看到的名称和对应处罚 */
    FoulType(String labelZh, Penalty penalty) {
        this.labelZh = labelZh;
        this.penalty = penalty;
    }

    /** 根据出杆前局面和本杆事件，返回此项犯规是否成立 */
    public boolean detect(ShotContext context, ShotRecord shotRecord) {
        if (this == MISS_NO_CONTACT) {
            return !shotRecord.firstContactOccurred();
        }
        if (this == WRONG_BALL_FIRST) {
            return detectsWrongFirstBall(context, shotRecord);
        }
        if (this == CUE_BALL_POCKETED) {
            return shotRecord.cueBallPocketed();
        }
        if (this == NO_RAIL_AFTER_CONTACT) {
            return detectsMissingRail(context, shotRecord);
        }
        if (this == BALL_OFF_TABLE) {
            return !shotRecord.ballsOffTable().isEmpty();
        }
        if (this == ILLEGAL_BREAK) {
            return detectsIllegalBreak(context, shotRecord);
        }
        if (this == EIGHT_POCKETED_EARLY) {
            return detectsEarlyEight(context, shotRecord);
        }
        return detectsEightWithFoul(context, shotRecord);
    }

    /** 普通击球须先碰合法目标，开球使用独立的达标规则 */
    private boolean detectsWrongFirstBall(ShotContext context, ShotRecord shotRecord) {
        if (context.breakShot() || !shotRecord.firstContactOccurred()) {
            return false;
        }
        return !context.isLegalFirstContact(shotRecord);
    }

    /** 检查普通击球触球后是否既没有碰库也没有落袋 */
    private boolean detectsMissingRail(ShotContext context, ShotRecord shotRecord) {
        if (context.breakShot() || !shotRecord.firstContactOccurred()) {
            return false;
        }
        return !shotRecord.anyRailAfterContact() && !shotRecord.anyBallPocketed();
    }

    /** 开球没有目标球落袋时，检查碰库数量是否达到配置要求 */
    private boolean detectsIllegalBreak(ShotContext context, ShotRecord shotRecord) {
        if (!context.breakShot()) {
            return false;
        }
        if (!shotRecord.objectBallsPocketedInOrder().isEmpty()) {
            return false;
        }
        return shotRecord.objectBallsTouchingRailCount() < RulesConfig.BREAK_MIN_BALLS_TO_RAIL;
    }

    /** 用本杆结束后的组内剩余球判断提前落八号，允许同杆清组并进八号 */
    private boolean detectsEarlyEight(ShotContext context, ShotRecord shotRecord) {
        if (context.breakShot() || !shotRecord.pocketed(BallCatalog.EIGHT_ID)) {
            return false;
        }
        return !context.groupClearedAfter(shotRecord);
    }

    /** 八号落袋时检查其他犯规，跳过自身以免递归调用 */
    private boolean detectsEightWithFoul(ShotContext context, ShotRecord shotRecord) {
        if (context.breakShot() || !shotRecord.pocketed(BallCatalog.EIGHT_ID)) {
            return false;
        }
        // 提前落八号已有独立败因，其他犯规仍须逐项检查
        for (FoulType otherFoul : values()) {
            if (otherFoul == EIGHT_WITH_FOUL || otherFoul == EIGHT_POCKETED_EARLY) {
                continue;
            }
            if (otherFoul.detect(context, shotRecord)) {
                return true;
            }
        }
        return false;
    }

    /** 返回玩家看到的犯规名称 */
    public String labelZh() {
        return labelZh;
    }

    /** 返回此项犯规的处罚 */
    public Penalty penalty() {
        return penalty;
    }

    /** 返回此项处罚是否结束比赛 */
    public boolean endsMatch() {
        return penalty.endsMatch();
    }

    /** 返回此项是否属于八号球胜负判定 */
    public boolean isEightBallVerdict() {
        return this == EIGHT_POCKETED_EARLY || this == EIGHT_WITH_FOUL;
    }
}

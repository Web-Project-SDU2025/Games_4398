package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.util.BilliardsRewards;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 台球判定工具类
 *
 * 按服务器保存的球组重新判定单杆得分
 * 客户端只提交击球事件，不提交奖励金额或名称
 */
public final class BilliardsJudge {
    /** 每盘默认十六颗球，目标球落袋后不会在后续杆重复计分 */
    public static final int BALL_COUNT = 16;

    /** 工具类禁止实例化 */
    private BilliardsJudge() {
    }

    /**
     * 台球局面状态类
     * 保存可序列化的局面，整数球组：0未定，1全色，2花色
     */
    public static final class State {
        /** 每个球是否还在台上 */
        public boolean[] active = new boolean[BALL_COUNT];

        /** 两个玩家的球组 */
        public int[] groups = new int[2];

        /** 当前轮到哪个玩家 */
        public int current;

        /** 是否在开球阶段 */
        public boolean breaking = true;

        /** 连击中的玩家，-1表示无人连击 */
        public int runPlayer = -1;

        /** 当前连击杆数 */
        public int runShots;

        /** 已领取的奖励位掩码 */
        public int earned;

        /** 本盘尚未入账的奖励编号，结束时由奖励服务合并结算 */
        public List<String> pendingRewards = new ArrayList<>();

        /** 本盘是否已结束 */
        public boolean ended;

        /** 获胜玩家编号，玩家一为 0，玩家二为 1，未结束时为 -1 */
        public int winner = -1;

        /** 是否为人机对战 */
        public boolean aiMatch;

        /** 开球方，服务器登记本盘时随机确定 */
        public int starter;

        /**
         * 构造初始局面
         * 新盘开始全部球在台上
         */
        public State() {
            java.util.Arrays.fill(active, true);
        }
    }

    /**
     * 应用击球结果
     * 返回本杆首次解锁的服务器奖励编号，其他结果更新传入的局面
     */
    public static List<String> apply(State state, JsonNode shot) {
        if (state.ended || shot.path("player").asInt(-1) != state.current) {
            throw new IllegalArgumentException("台球轮次不正确");
        }
        List<Integer> pots = ids(shot.path("pots"));
        List<Integer> rails = ids(shot.path("rails"));
        List<Integer> banks = ids(shot.path("bankPots"));
        List<Integer> off = ids(shot.path("off"));
        for (int id : off) {
            if (!state.active[id]) {
                throw new IllegalArgumentException("已落袋球不能再次飞出台面");
            }
        }
        int first = shot.path("first").asInt(-1);
        if (first < -1 || first == 0 || first >= BALL_COUNT
                || (first >= 0 && !state.active[first])) {
            throw new IllegalArgumentException("首次触球记录不正确");
        }
        for (int id : rails) {
            if (!state.active[id]) {
                throw new IllegalArgumentException("已落袋球不能再次碰库");
            }
        }
        for (int id : banks) {
            if (id == 0 || !pots.contains(id) || !rails.contains(id)) {
                throw new IllegalArgumentException("翻袋记录与碰库落袋记录不一致");
            }
        }
        int player = state.current;
        int group = state.groups[player];
        int remainingBefore = remaining(state, group);
        boolean foul = first < 0 || pots.contains(0) || !off.isEmpty();
        if (!state.breaking && first >= 0) {
            boolean legal = first != 8;
            if (group != 0) {
                legal = groupOf(first) == group;
                if (remainingBefore == 0) {
                    legal = first == 8;
                }
            }
            if (!legal || (!shot.path("railAfter").asBoolean() && pots.isEmpty())) {
                foul = true;
            }
        }
        int objectPots = 0;
        for (int id : pots) {
            if (!state.active[id] || off.contains(id)) {
                throw new IllegalArgumentException("落袋记录包含重复计分或冲突球号");
            }
            state.active[id] = false;
            if (id != 0) {
                objectPots = objectPots + 1;
            }
        }
        int railCount = rails.size();
        if (rails.contains(0)) {
            railCount = railCount - 1;
        }
        if (state.breaking && objectPots == 0 && railCount < 4) {
            foul = true;
        }
        if (!state.breaking && group == 0 && !foul) {
            for (int id : pots) {
                if (groupOf(id) > 0) {
                    group = groupOf(id);
                    state.groups[player] = group;
                    state.groups[1 - player] = 3 - group;
                    break;
                }
            }
        }
        int ownPots = 0;
        boolean bank = false;
        for (int id : pots) {
            if (id != 0 && id != 8 && (state.breaking || groupOf(id) == group)) {
                ownPots = ownPots + 1;
                bank = bank || banks.contains(id);
            }
        }
        int winner = -1;
        if (pots.contains(8) && !state.breaking) {
            winner = player;
            if (group == 0 || remaining(state, group) > 0 || foul) {
                winner = 1 - player;
                foul = true;
            }
            state.ended = true;
            state.winner = winner;
        }
        List<String> result = new ArrayList<>();
        if (!foul && ownPots > 0) {
            if (state.runPlayer != player) {
                state.runShots = 0;
            }
            state.runPlayer = player;
            state.runShots = state.runShots + 1;
            if (player == 0) {
                if (state.breaking) {
                    unlock(state, "opening", result);
                } else {
                    if (ownPots >= 3) {
                        unlock(state, "triple", result);
                    } else if (ownPots == 2) {
                        unlock(state, "double", result);
                    }
                    if (bank) {
                        unlock(state, "bank", result);
                    }
                    if (shot.path("cueRailBefore").asBoolean() && rails.contains(0)) {
                        unlock(state, "kick", result);
                    }
                }
                if (state.runShots >= 3) {
                    unlock(state, "run", result);
                }
            }
        } else {
            state.runPlayer = -1;
            state.runShots = 0;
        }
        if (winner == 0 && state.aiMatch) {
            unlock(state, "win", result);
        }
        boolean continues = !foul && (ownPots > 0 || (state.breaking && pots.contains(8)));
        if (!continues) {
            state.current = 1 - player;
            state.runPlayer = -1;
            state.runShots = 0;
        }
        state.active[0] = true;
        if (state.breaking && pots.contains(8)) {
            state.active[8] = true;
        }
        state.breaking = false;
        return result;
    }

    /**
     * 解析球号列表
     * 严格读取去重球号列表，异常类型和越界值直接拒绝
     */
    private static List<Integer> ids(JsonNode source) {
        if (!source.isArray() || source.size() > BALL_COUNT) {
            throw new IllegalArgumentException("台球事件应为球号数组");
        }
        List<Integer> result = new ArrayList<>();
        for (JsonNode value : source) {
            int id = value.asInt(-1);
            if (!value.isIntegralNumber() || id < 0 || id >= BALL_COUNT || result.contains(id)) {
                throw new IllegalArgumentException("台球事件球号无效或重复");
            }
            result.add(id);
        }
        return result;
    }

    /**
     * 获取球组
     * 返回目标球组，主球和八号不属于任何玩家的普通目标组
     */
    private static int groupOf(int id) {
        if (id >= 1 && id <= 7) {
            return 1;
        }
        if (id >= 9 && id <= 15) {
            return 2;
        }
        return 0;
    }

    /**
     * 统计剩余球数
     * 统计指定球组仍在台上的球数，未定组时返回零
     */
    private static int remaining(State state, int group) {
        int count = 0;
        for (int id = 1; id < BALL_COUNT; id = id + 1) {
            if (group > 0 && state.active[id] && groupOf(id) == group) {
                count = count + 1;
            }
        }
        return count;
    }

    /**
     * 解锁奖励
     * 按固定奖励顺序记录是否已领取，同项成就重复出现时不再入账
     */
    private static void unlock(State state, String id, List<String> result) {
        int index = BilliardsRewards.IDS.indexOf(id);
        int bit = 1;
        for (int position = 0; position < index; position = position + 1) {
            bit = bit * 2;
        }
        if ((state.earned & bit) == 0) {
            state.earned = state.earned | bit;
            result.add(id);
        }
    }
}

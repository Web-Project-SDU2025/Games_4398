package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 游戏会话实体类
 *
 * 保存一局游戏的完整信息：身份、时长、结果和奖励
 * 记录和奖励共用同一个唯一会话编号，防止重复发放
 */
@Entity
@Table(name = "game_play")
@Getter
@Setter
public class GamePlay {
    /** 会话ID，主键，格式为 userId:runId */
    @Id
    private String playId;

    /** 用户ID */
    private Integer userId;

    /** 游戏ID */
    private Integer gameId;

    /** 关联的游戏记录ID */
    private Integer recordId;

    /** 挑战标识，用于验证客户端身份 */
    private String challenge;

    /** 关卡编号 */
    private Integer levelNumber;

    /** 关卡名称 */
    private String levelName;

    /** 关卡文件哈希值，用于验证关卡是否被篡改 */
    private String levelHash;

    /** 游戏模式：official正式模式、trial试玩模式 */
    private String mode;

    /** 结果状态：playing游戏中、completed完成、abandoned放弃 */
    private String outcome;

    /** 最短通关时间（秒），用于防作弊 */
    private Integer minimumSeconds;

    /** 应发奖励金额 */
    private Long rewardAmount;

    /** 实际已发奖励金额 */
    private Long awardedAmount;

    /** 客户端上报的总时长（毫秒） */
    private Long reportedMilliseconds;

    /** 服务器计算的活跃时长（毫秒） */
    private Long activeMilliseconds;

    /** 开始时间 */
    private Instant startedAt;

    /** 最后更新时间 */
    private Instant updatedAt;

    /** 结束时间 */
    private Instant endedAt;
}

package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 台球局实体类
 *
 * 保存一盘台球的判定状态和最近请求
 * 即使服务器重启也能识别重复结算，防止刷币
 */
@Entity
@Table(name = "billiards_round")
@Getter
@Setter
public class BilliardsRound {
    /** 局ID，主键，格式为 playId:roundId */
    @Id
    private String roundId;

    /** 关联的游戏会话ID */
    private String playId;

    /** 当前击球序号，0表示开局 */
    @Column(name = "shot_sequence")
    private Integer sequence;

    /** 台球判定状态的JSON表示 */
    @Column(length = 12000)
    private String stateJson;

    /** 最近一次请求的哈希值，用于去重 */
    private String requestHash;

    /** 最近一次发放的奖励金额 */
    private Long lastReward;

    /** 最近一次发放的奖励名称 */
    private String lastTitles;

    /** 最后更新时间 */
    private Instant updatedAt;
}

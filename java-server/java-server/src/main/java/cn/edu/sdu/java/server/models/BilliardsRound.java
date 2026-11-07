package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存一盘台球的判定状态和最近请求，重启后仍可识别重复结算 */
@Entity
@Table(name = "billiards_round")
@Getter
@Setter
public class BilliardsRound {
    @Id
    private String roundId;
    private String playId;
    @Column(name = "shot_sequence")
    private Integer sequence;
    @Column(length = 12000)
    private String stateJson;
    private String requestHash;
    private Long lastReward;
    private String lastTitles;
    private Instant updatedAt;
}

package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存一局游戏的身份、时长和结果，记录与奖励共用唯一会话编号 */
@Entity
@Table(name = "game_play")
@Getter
@Setter
public class GamePlay {
    @Id
    private String playId;
    private Integer userId;
    private Integer gameId;
    private Integer recordId;
    private String challenge;
    private Integer levelNumber;
    private String levelName;
    private String levelHash;
    private String mode;
    private String outcome;
    private Integer minimumSeconds;
    private Long rewardAmount;
    private Long awardedAmount;
    private Long reportedMilliseconds;
    private Long activeMilliseconds;
    private Instant startedAt;
    private Instant updatedAt;
    private Instant endedAt;
}

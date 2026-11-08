package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 游戏记录实体类
 *
 * 保存用户一次游玩的分数、时长和时间
 * 关联用户和游戏两个实体
 */
@Getter
@Setter
@Entity
@Table(name = "game_record")
public class GameRecord {
    /** 记录ID，主键，自动递增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordId;

    /** 关联的用户 */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /** 关联的游戏 */
    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    /** 得分 */
    private Integer score;

    /** 游玩时长，单位是秒 */
    private Integer durationSeconds;

    /** 游玩时间 */
    private LocalDateTime playedAt;
}

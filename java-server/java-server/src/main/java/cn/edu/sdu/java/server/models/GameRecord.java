package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/*
 * GameRecord 保存用户一次游玩的分数、时长和时间。
 * user 和 game 分别关联游戏用户和游戏。
 */
@Getter
@Setter
@Entity
@Table(name = "game_record")
public class GameRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    private Integer score;
    private Integer durationSeconds;
    private LocalDateTime playedAt;
}

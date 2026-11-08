package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 游戏信息模型
 *
 * 保存游戏的基本信息：编号、名称、分类、介绍和入口路径
 * gameId是主键，游戏库和游戏记录都通过它来关联游戏
 * levelReward字段是管理员设置的通关奖励，空值时用默认配置
 */
@Getter
@Setter
@Entity
@Table(name = "game")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer gameId;
    private String num;
    private String name;
    private String category;
    private String description;
    private String gamePath;
    /** 管理员设置的每次正式通关奖励，空值时使用默认配置 */
    private Long levelReward;
}

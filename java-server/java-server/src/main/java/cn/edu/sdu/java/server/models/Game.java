package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/*
 * Game 保存游戏编号、名称、分类、介绍和入口路径。
 * gameId 是 game 表的主键，游戏库和游戏记录通过它关联游戏。
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
}

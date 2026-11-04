package cn.edu.sdu.java.server.models;


import jakarta.persistence.*;

/*
 * UserType 用户类型表实体类，保存管理员和游戏用户角色
 * Integer id user_type 表 主键 id
 * name 保存角色名称 ROLE_ADMIN 或 ROLE_GAME_USER
 */
import lombok.Getter;
import lombok.Setter;
/** 保存账户角色名称，认证权限使用数据库中的当前角色 */
@Getter
@Setter
@Entity
@Table(name = "user_type")
public class UserType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 20)
    private String name;
}

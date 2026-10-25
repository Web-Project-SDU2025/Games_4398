package cn.edu.sdu.java.server.models;


import jakarta.persistence.*;

/*
 * UserType用户类型表实体类 两种类型 管理员和游戏用户 对应 枚举类型EUserType
 * Integer id user_type 表 主键 id
 * EUserType nam 角色名称 ROLE_ADMIN, ROLE_GAME_USER
 */
import lombok.Getter;
import lombok.Setter;
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

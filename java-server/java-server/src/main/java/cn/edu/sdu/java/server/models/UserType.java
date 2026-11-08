package cn.edu.sdu.java.server.models;


import jakarta.persistence.*;

/**
 * 用户类型实体类
 *
 * 保存账户角色名称，比如管理员或普通用户
 * 认证权限使用数据库中的当前角色
 */
@Getter
@Setter
@Entity
@Table(name = "user_type")
public class UserType {
    /** 角色ID，主键，自动递增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 角色名称，比如 ROLE_ADMIN 或 ROLE_GAME_USER */
    @Column(length = 20)
    private String name;
}

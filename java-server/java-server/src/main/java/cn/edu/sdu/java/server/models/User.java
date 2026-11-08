package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;
import cn.edu.sdu.java.server.util.FieldLimits;


/*
 * User用户表实体类，保存账号、个人资料、游戏库和收藏
 * Integer userId 用户表 user 主键 user_id
 * UserType userType 关联到用户类型对象
 * String userName 登录账号
 * String password 保存密码哈希，登录时由编码器验证输入
 *
 */
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体类
 *
 * 统一保存账户信息、个人资料和游戏关联
 * 包含登录账号、密码哈希、游戏库和收藏列表
 */
@Getter
@Setter
@Entity
@Table(	name = "user",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "userName"),
        })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userId;

    @ManyToOne()
    @JoinColumn(name = "userTypeId")
    private UserType userType;

    @NotBlank
    @Size(max = FieldLimits.USERNAME)
    private String userName;


    @NotBlank
    @Size(max = FieldLimits.PASSWORD)
    private String password;

    @Size(max = FieldLimits.NAME)
    private String name;
    @Size(max = FieldLimits.GENDER)
    private String gender;
    @Size(max = FieldLimits.DATE)
    private String birthday;
    @Email
    @Size(max = FieldLimits.EMAIL)
    private String email;
    @Size(max = FieldLimits.CONTACT)
    private String phone;
    @Size(max = FieldLimits.CONTACT)
    private String address;
    @Size(max = FieldLimits.INTRODUCTION)
    private String introduce;
    @Size(max = FieldLimits.SIGNATURE)
    private String signature;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "longblob")
    private byte[] photo;

    @ManyToMany
    @JoinTable(name = "user_game", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "game_id"))
    private List<Game> games = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "game_favorite", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "game_id"))
    private List<Game> favorites = new ArrayList<>();

    private Integer loginCount;
    @Size(max = FieldLimits.DATE_TIME)
    private String lastLoginTime;
    @Size(max = FieldLimits.DATE_TIME)
    private String  createTime;
    private Integer creatorId;
}

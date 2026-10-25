package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;


/*
 * User用户表实体类，保存账号、个人资料、游戏库和收藏。
 * Integer userId 用户表 user 主键 user_id
 * UserType userType 关联到用户类型对象
 * String userName 登录账号
 * String password 用户密码的哈希值，不能还原成原密码
 *
 */
import lombok.Getter;
import lombok.Setter;
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
    @Size(max = 20)
    private String userName;


    @NotBlank
    @Size(max = 60)
    private String password;

    @Size(max = 50)
    private String name;
    @Size(max = 2)
    private String gender;
    @Size(max = 10)
    private String birthday;
    @Email
    @Size(max = 60)
    private String email;
    @Size(max = 20)
    private String phone;
    @Size(max = 20)
    private String address;
    @Size(max = 1000)
    private String introduce;
    @Size(max = 200)
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
    @Size(max = 20)
    private String lastLoginTime;
    @Size(max = 20)
    private String  createTime;
    private Integer creatorId;
}

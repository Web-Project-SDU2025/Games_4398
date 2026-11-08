package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT响应类
 *
 * JWT数据返回对象，包含客户登录的信息
 */
@Setter
@Getter
public class JwtResponse {
    /** JWT令牌字符串 */
    private String token;

    /** JWT类型 */
    private String type = "Bearer";

    /** 用户ID */
    private Integer id;

    /** 用户登录名 */
    private String username;

    /** 用户昵称 */
    private String perName;

    /** 用户角色：ROLE_ADMIN或ROLE_GAME_USER */
    private String role;

    /** 服务器名称 */
    private String serverName;

    /**
     * 构造函数
     * 保存登录成功后的身份字段，昵称为空时由前端使用账号显示
     */
    public JwtResponse(String token, Integer id, String username, String perName,String role) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.perName = perName;
        this.role = role;
    }
}

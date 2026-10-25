package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

/**
 * JwtResponse JWT数据返回对象 包含客户登录的信息
 * String token token字符串
 * String type JWT 类型
 * Integer id 用户的ID user_id
 * String username 用户的登录名
 * String role 用户角色 ROLE_ADMIN, ROLE_GAME_USER
 */
@Setter
@Getter
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private Integer id;
    private String username;
    private String perName;
    private String role;
    private String serverName;

    public JwtResponse(String token, Integer id, String username, String perName,String role) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.perName = perName;
        this.role = role;
    }
}

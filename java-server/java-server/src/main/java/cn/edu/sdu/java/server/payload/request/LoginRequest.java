package cn.edu.sdu.java.server.payload.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/*
 * LoginRequest 登录请求数据类
 * String username 用户名
 * String password 密码
 */
@Getter
@Setter
public class LoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private Integer validateCodeId;
    private String validateCode;

}

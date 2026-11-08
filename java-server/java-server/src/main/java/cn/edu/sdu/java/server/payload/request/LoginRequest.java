package cn.edu.sdu.java.server.payload.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求类
 *
 * 封装用户登录所需的信息
 * 包含用户名、密码和验证码
 */
@Getter
@Setter
public class LoginRequest {
    /** 用户名，必填 */
    @NotBlank
    private String username;

    /** 密码，必填 */
    @NotBlank
    private String password;

    /** 验证码ID */
    private Integer validateCodeId;

    /** 验证码 */
    private String validateCode;
}

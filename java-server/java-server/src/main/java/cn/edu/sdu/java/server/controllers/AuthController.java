package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * 认证控制器
 *
 * 提供登录、注册和验证码相关接口
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 依赖注入
     * 保存认证业务服务，登录和注册结果由服务统一生成
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口
     * 验证用户名、密码和验证码，成功后返回JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.authenticateUser(loginRequest);
    }
    /**
     * 获取图形验证码
     * 返回验证码ID和图片数据
     */
    @PostMapping("/getValidateCode")
    public DataResponse getValidateCode(@Valid @RequestBody DataRequest dataRequest) {
        return authService.getValidateCode(dataRequest);
    }

    /**
     * 校验验证码
     * 注册页面用这个接口检查用户输入的验证码是否正确
     */
    @PostMapping("/testValidateInfo")
    public DataResponse testValidateInfo(@Valid @RequestBody DataRequest dataRequest) {
        return authService.testValidateInfo(dataRequest);
    }
    /**
     * 用户注册接口
     * 创建新的游戏用户账户
     */
    @PostMapping("/registerUser")
    public DataResponse registerUser(@Valid @RequestBody DataRequest dataRequest) {
        return authService.registerUser(dataRequest);
    }
}

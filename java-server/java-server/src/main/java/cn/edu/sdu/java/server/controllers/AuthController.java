package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 *  AuthController 实现 登录和注册Web服务
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     *  用户登录
     * @param loginRequest   username 登录名  password 密码
     * @return   JwtResponse 用户信息， 该信息再后续的web请求时作为请求头的一部分，用于框架的请求服务权限验证
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.authenticateUser(loginRequest);
    }
    /** 管理员登录时额外验证角色，普通用户不能通过更换页面取得管理员权限 */
    @PostMapping("/adminLogin")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.authenticateAdmin(loginRequest);
    }
    /**
     * 获取一张新的图形验证码。
     * @param dataRequest 请求参数，当前接口不需要业务参数
     * @return 包含验证码编号和图片数据的响应
     */
    @PostMapping("/getValidateCode")
    public DataResponse getValidateCode(@Valid @RequestBody DataRequest dataRequest) {
        return authService.getValidateCode(dataRequest);
    }

    /**
     * 单独检查验证码是否正确。
     * @param dataRequest data 中包含 validateCodeId 和 validateCode
     * @return 验证成功或失败的响应
     */
    @PostMapping("/testValidateInfo")
    public DataResponse testValidateInfo(@Valid @RequestBody DataRequest dataRequest) {
        return authService.testValidateInfo(dataRequest);
    }
    /**
     * 注册一个游戏用户。
     * @param dataRequest data 中包含账号、密码、昵称、邮箱和验证码
     * @return 注册成功或失败的响应
     */
    @PostMapping("/registerUser")
    public DataResponse registerUser(@Valid @RequestBody DataRequest dataRequest) {
        return authService.registerUser(dataRequest);
    }
}

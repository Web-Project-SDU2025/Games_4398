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
    @PostMapping("/getValidateCode")
    public DataResponse getValidateCode(@Valid @RequestBody DataRequest dataRequest) {
        return authService.getValidateCode(dataRequest);
    }

    @PostMapping("/testValidateInfo")
    public DataResponse testValidateInfo(@Valid @RequestBody DataRequest dataRequest) {
        return authService.testValidateInfo(dataRequest);
    }
    @PostMapping("/registerUser")
    public DataResponse registerUser(@Valid @RequestBody DataRequest dataRequest) {
        return authService.registerUser(dataRequest);
    }
}

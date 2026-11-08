package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.payload.response.JwtResponse;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Date;

/**
 * 认证服务
 *
 * 处理验证码生成、登录验证和用户注册
 * 角色和密码都由后端校验，前端不能绕过
 */
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GameUserService gameUserService;

    /**
     * 依赖注入
     * 保存认证所需的各种依赖，登录成功后统一签发JWT令牌
     */
    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager,
                       JwtService jwtService, GameUserService gameUserService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.gameUserService = gameUserService;
    }

    /**
     * 用户登录
     * 检查验证码和账号密码，验证通过后生成JWT令牌
     */
    public ResponseEntity<?> authenticateUser(LoginRequest loginRequest) {
        boolean valid = LoginControlUtil.getInstance().checkAndRemoveCode(
                loginRequest.getValidateCodeId(), loginRequest.getValidateCode());
        if (!valid) {
            return ResponseEntity.badRequest().body(CommonMethod.getReturnMessageError("验证码不正确或已失效，请刷新"));
        }
        UsernamePasswordAuthenticationToken credentials = new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(), loginRequest.getPassword());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(credentials);
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonMethod.getReturnMessageError("账号或密码不正确"));
        }
        UserDetailsImpl details = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(details.getId()).orElseThrow();
        recordSuccessfulLogin(user);
        return ResponseEntity.ok(createSession(user));
    }

    /**
     * 记录成功登录
     * 更新最后登录时间和登录次数
     */
    public void recordSuccessfulLogin(User user) {
        user.setLastLoginTime(DateTimeTool.parseDateTime(new Date()));
        Integer count = user.getLoginCount();
        if (count == null) {
            count = 0;
        }
        user.setLoginCount(count + 1);
        userRepository.save(user);
    }

    /**
     * 创建登录会话
     * 根据数据库账户信息生成JWT令牌和基本资料
     * 本地测试切号和正常登录共用这个格式
     */
    public JwtResponse createSession(User user) {
        UserDetailsImpl details = UserDetailsImpl.build(user);
        String token = jwtService.generateToken(details);
        return new JwtResponse(token, user.getUserId(), user.getUserName(),
                user.getName(), user.getUserType().getName());
    }
    /**
     * 生成图形验证码
     * 返回验证码ID和图片数据，前端用这个ID来校验用户输入
     */
    public DataResponse getValidateCode(DataRequest request) {
        return CommonMethod.getReturnData(LoginControlUtil.getInstance().getValidateCodeDataMap());
    }

    /**
     * 校验验证码
     * 注册页面用这个接口实时验证用户输入的验证码是否正确
     */
    public DataResponse testValidateInfo(DataRequest request) {
        Integer codeId = request.getInteger("validateCodeId");
        String code = request.getString("validateCode");
        if (codeId == null || code == null || code.isBlank()) {
            return CommonMethod.getReturnMessageError("验证码不能为空");
        }
        String expected = LoginControlUtil.getInstance().getValidateCode(codeId);
        if (!code.equals(expected)) {
            return CommonMethod.getReturnMessageError("验证码不正确");
        }
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 用户注册
     * 注册时在同一条记录里保存账号和资料
     * 注册角色固定为游戏用户，管理员需要从账户管理里设置
     */
    public DataResponse registerUser(DataRequest request) {
        boolean valid = LoginControlUtil.getInstance().checkAndRemoveCode(
                request.getInteger("validateCodeId"), request.getString("validateCode"));
        if (!valid) {
            return CommonMethod.getReturnMessageError("验证码不正确或已失效，请刷新");
        }
        request.add("role", "ROLE_GAME_USER");
        return gameUserService.createAccount(request);
    }
}

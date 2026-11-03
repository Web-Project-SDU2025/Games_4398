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

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GameUserService gameUserService;

    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager,
                       JwtService jwtService, GameUserService gameUserService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.gameUserService = gameUserService;
    }

    /**
     * 检查验证码和账号密码，登录成功后生成 JWT 登录令牌。
     * @param loginRequest 包含账号、密码和验证码
     * @return 登录结果，成功时包含 JWT 和账户基本信息
     */
    public ResponseEntity<?> authenticateUser(LoginRequest loginRequest) {
        return authenticate(loginRequest, false);
    }

    /** 管理员入口仍验证密码和验证码，只有数据库中的管理员可以进入 */
    public ResponseEntity<?> authenticateAdmin(LoginRequest loginRequest) {
        return authenticate(loginRequest, true);
    }

    /** 共用登录检查，管理员入口额外检查数据库角色，不提升普通账户权限 */
    private ResponseEntity<?> authenticate(LoginRequest loginRequest, boolean adminOnly) {
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
        if (adminOnly && !"ROLE_ADMIN".equals(user.getUserType().getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonMethod.getReturnMessageError("此入口只允许管理员账号登录"));
        }
        user.setLastLoginTime(DateTimeTool.parseDateTime(new Date()));
        Integer count = user.getLoginCount();
        if (count == null) {
            count = 0;
        }
        user.setLoginCount(count + 1);
        userRepository.save(user);
        return ResponseEntity.ok(createSession(user));
    }

    /** 根据数据库账户签发登录信息，本地检查切号与普通登录共用同一格式 */
    public JwtResponse createSession(User user) {
        UserDetailsImpl details = UserDetailsImpl.build(user);
        String token = jwtService.generateToken(details);
        return new JwtResponse(token, user.getUserId(), user.getUserName(),
                user.getName(), user.getUserType().getName());
    }
    /**
     * 生成一张新的图形验证码并保存验证码内容。
     * @param request 当前接口不需要业务参数
     * @return 验证码编号和图片数据
     */
    public DataResponse getValidateCode(DataRequest request) {
        return CommonMethod.getReturnData(LoginControlUtil.getInstance().getValidateCodeDataMap());
    }

    /**
     * 检查请求中的验证码是否正确。
     * @param request data 中包含 validateCodeId 和 validateCode
     * @return 验证成功或失败的响应
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

    /*
     * 注册游戏用户，在同一条用户记录中保存账号和资料。
     * 公开注册只创建游戏用户，管理员不能通过注册页面自行获得。
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

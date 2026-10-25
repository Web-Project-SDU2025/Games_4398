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
        user.setLastLoginTime(DateTimeTool.parseDateTime(new Date()));
        Integer count = user.getLoginCount();
        if (count == null) {
            count = 0;
        }
        user.setLoginCount(count + 1);
        userRepository.save(user);
        String token = jwtService.generateToken(details);
        String role = user.getUserType().getName();
        return ResponseEntity.ok(new JwtResponse(token, details.getId(), details.getUsername(),
                details.getPerName(), role));
    }

    public DataResponse getValidateCode(DataRequest request) {
        return CommonMethod.getReturnData(LoginControlUtil.getInstance().getValidateCodeDataMap());
    }

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

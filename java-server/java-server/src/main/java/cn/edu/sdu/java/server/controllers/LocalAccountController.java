package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.AuthService;
import cn.edu.sdu.java.server.util.CommonMethod;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 已登录用户在本机检查时可免密切号；演示模式供课程在线检查开放，未开启时接口不提供该能力 */
@RestController
@RequestMapping("/api/localAccount")
public class LocalAccountController {
    private final UserRepository users;
    private final AuthService auth;
    private final boolean enabled;
    private final boolean demoMode;

    /** 从配置读取检查开关和演示开关，两个开关默认关闭 */
    public LocalAccountController(UserRepository users, AuthService auth,
            @Value("${app.local-account-switch:false}") boolean enabled,
            @Value("${app.demo-mode:false}") boolean demoMode) {
        this.users = users;
        this.auth = auth;
        this.enabled = enabled;
        this.demoMode = demoMode;
    }

    /** 演示模式对任意来源开放；本机检查开关仍要求本机直连，转发头由客户端输入独立保存 */
    public boolean available(HttpServletRequest request) {
        if (demoMode) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        String address = request.getRemoteAddr();
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }

    /** 返回检查模式状态和账号名称，不返回密码、联系方式或其他资料 */
    @PostMapping("/list")
    public DataResponse list(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> accounts = new ArrayList<>();
        boolean allowed = available(request);
        if (allowed) {
            for (User user : users.findAll()) {
                Map<String, Object> account = new HashMap<>();
                account.put("username", user.getUserName());
                account.put("name", user.getName());
                account.put("role", user.getUserType().getName());
                accounts.add(account);
            }
        }
        result.put("enabled", allowed);
        result.put("accounts", accounts);
        return CommonMethod.getReturnData(result);
    }

    /** 本地检查切号时重新签发凭证，权限始终取自目标账户的数据库角色 */
    @PostMapping("/switch")
    public DataResponse switchAccount(@RequestBody DataRequest data, HttpServletRequest request) {
        if (!available(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本地切号功能未开启");
        }
        Optional<User> found = users.findByUserName(data.getString("username"));
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("账号不存在或已经删除");
        }
        User user = found.get();
        auth.recordSuccessfulLogin(user);
        return CommonMethod.getReturnData(auth.createSession(user));
    }
}

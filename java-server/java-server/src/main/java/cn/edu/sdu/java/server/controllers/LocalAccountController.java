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
import java.util.Enumeration;

/**
 * 本地账户控制器
 *
 * 已登录用户在本机检查时可免密切号
 * 演示模式开启时对任意来源开放，两个开关都关闭后接口不提供该能力
 */
@RestController
@RequestMapping("/api/localAccount")
public class LocalAccountController {
    /** 常见代理转发标记，代理请求不能借用后端的本机连接免密切号 */
    private static final List<String> PROXY_HEADERS = List.of("forwarded", "x-real-ip");
    private final UserRepository users;
    private final AuthService auth;
    private final boolean enabled;
    private final boolean demoMode;

    /**
     * 构造函数
     * 从配置读取检查开关和演示开关，本机检查默认可用，演示模式默认开启
     */
    public LocalAccountController(UserRepository users, AuthService auth,
            @Value("${app.local-account-switch:true}") boolean enabled,
            @Value("${app.demo-mode:true}") boolean demoMode) {
        this.users = users;
        this.auth = auth;
        this.enabled = enabled;
        this.demoMode = demoMode;
    }

    /**
     * 检查本地访问
     * 演示模式对任意来源开放；本机检查按实际连接来源判断，带代理标记的请求不提供免密切号
     */
    public boolean available(HttpServletRequest request) {
        if (demoMode) {
            return true;
        }
        if (!enabled || hasProxyHeaders(request)) {
            return false;
        }
        String address = request.getRemoteAddr();
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }

    /** 只检查代理标记是否存在，不信任客户端填写的转发地址 */
    private boolean hasProxyHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return false;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String normalized = name.toLowerCase(java.util.Locale.ROOT);
            if (PROXY_HEADERS.contains(normalized) || normalized.startsWith("x-forwarded-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询账户列表
     * 返回检查模式状态和账号名称，不返回密码、联系方式或其他资料
     */
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

    /**
     * 切换账户
     * 本地检查切号时重新签发凭证，权限始终取自目标账户的数据库角色
     */
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

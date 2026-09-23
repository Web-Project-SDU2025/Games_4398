# 拦截器

此目录用于存放 Spring MVC 拦截器。

## 什么是拦截器

拦截器用于在请求到达 Controller 之前或之后执行特定逻辑，例如权限验证、日志记录、请求预处理等。

## 拦截器 vs 过滤器

| 特性 | 拦截器（Interceptor） | 过滤器（Filter） |
|------|---------------------|----------------|
| 规范 | Spring MVC | Servlet |
| 执行时机 | DispatcherServlet 之后 | DispatcherServlet 之前 |
| 访问 Spring 容器 | 可以 | 不可以 |
| 拦截范围 | 只拦截 Controller 请求 | 拦截所有请求 |

## 目录结构

```
interceptor/
├── AuthInterceptor.java         # 认证拦截器
├── RateLimitInterceptor.java    # 限流拦截器
└── README.md
```

## 拦截器接口方法

```java
public interface HandlerInterceptor {
    
    /**
     * 预处理
     * 在 Controller 方法执行前调用
     * @return true 继续执行，false 中断请求
     */
    boolean preHandle(HttpServletRequest request, 
                     HttpServletResponse response, 
                     Object handler);
    
    /**
     * 后处理
     * 在 Controller 方法执行后、视图渲染前调用
     */
    void postHandle(HttpServletRequest request, 
                   HttpServletResponse response, 
                   Object handler, 
                   ModelAndView modelAndView);
    
    /**
     * 完成处理
     * 在整个请求完成后调用（包括视图渲染）
     */
    void afterCompletion(HttpServletRequest request, 
                        HttpServletResponse response, 
                        Object handler, 
                        Exception ex);
}
```

## 示例

### AuthInterceptor.java - 认证拦截器

```java
package com.game.system.interceptor;

import com.game.system.exception.UnauthorizedException;
import com.game.system.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 认证拦截器
 * 验证用户是否登录（通过 JWT Token）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 预处理
     * 验证请求中的 Token 是否有效
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 处理器
     * @return true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        
        // 获取请求头中的 Authorization
        String authorization = request.getHeader("Authorization");
        
        // 检查是否存在 Authorization
        if (authorization == null || authorization.isEmpty()) {
            throw new UnauthorizedException("缺少认证令牌");
        }
        
        // 检查格式是否为 "Bearer xxx"
        if (!authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("认证令牌格式错误");
        }
        
        // 提取 Token
        String token = authorization.substring(7);
        
        // 验证 Token 是否有效
        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("认证令牌无效或已过期");
        }
        
        // 从 Token 中提取用户 ID
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        // 将用户 ID 存入请求属性，后续可以使用
        request.setAttribute("userId", userId);
        
        // 验证通过，继续执行
        return true;
    }
    
    /**
     * 后处理
     * 在 Controller 方法执行后调用
     */
    @Override
    public void postHandle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler, 
                          org.springframework.web.servlet.ModelAndView modelAndView) {
        // 可以在这里记录响应信息
    }
    
    /**
     * 完成处理
     * 在整个请求完成后调用
     */
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        // 清理资源
    }
}
```

### RateLimitInterceptor.java - 限流拦截器

```java
package com.game.system.interceptor;

import com.game.system.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流拦截器
 * 防止同一用户短时间内发送大量请求
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    /**
     * 存储每个 IP 的请求时间戳
     * Key: IP 地址
     * Value: 上次请求的时间戳（毫秒）
     */
    private final ConcurrentHashMap<String, Long> requestTimeMap = new ConcurrentHashMap<>();
    
    /**
     * 请求间隔限制（毫秒）
     * 同一 IP 两次请求之间必须间隔至少这么长时间
     */
    private static final long REQUEST_INTERVAL = 1000;
    
    /**
     * 清理过期记录的时间间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL = 60000;
    
    /**
     * 上次清理的时间戳
     */
    private long lastCleanupTime = System.currentTimeMillis();
    
    /**
     * 预处理
     * 检查请求频率是否超过限制
     */
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        
        // 获取客户端 IP 地址
        String clientIp = getClientIp(request);
        
        // 获取当前时间
        long currentTime = System.currentTimeMillis();
        
        // 获取该 IP 上次请求的时间
        Long lastRequestTime = requestTimeMap.get(clientIp);
        
        // 如果存在上次请求时间
        if (lastRequestTime != null) {
            // 计算时间间隔
            long timeSinceLastRequest = currentTime - lastRequestTime;
            
            // 如果间隔太短，拒绝请求
            if (timeSinceLastRequest < REQUEST_INTERVAL) {
                long waitTime = REQUEST_INTERVAL - timeSinceLastRequest;
                throw new BusinessException("请求过于频繁，请 " + waitTime + " 毫秒后重试");
            }
        }
        
        // 更新该 IP 的请求时间
        requestTimeMap.put(clientIp, currentTime);
        
        // 定期清理过期记录
        cleanupExpiredRecords(currentTime);
        
        // 允许请求继续
        return true;
    }
    
    /**
     * 获取客户端 IP 地址
     * 考虑代理和负载均衡的情况
     * @param request HTTP 请求
     * @return IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 尝试从 X-Forwarded-For 获取（nginx 代理）
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 尝试从 X-Real-IP 获取
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 直接获取远程地址
            ip = request.getRemoteAddr();
        }
        
        // 如果有多个 IP（经过多层代理），取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * 清理过期的请求记录
     * 避免 Map 无限增长
     * @param currentTime 当前时间
     */
    private void cleanupExpiredRecords(long currentTime) {
        // 如果距离上次清理时间超过清理间隔
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            // 遍历所有记录
            requestTimeMap.forEach((ip, time) -> {
                // 如果记录超过 10 分钟没有更新，删除它
                if (currentTime - time > TimeUnit.MINUTES.toMillis(10)) {
                    requestTimeMap.remove(ip);
                }
            });
            
            // 更新清理时间
            lastCleanupTime = currentTime;
        }
    }
}
```

## 注册拦截器

创建配置类注册拦截器：

```java
package com.game.system.config;

import com.game.system.interceptor.AuthInterceptor;
import com.game.system.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    /**
     * 添加拦截器
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册限流拦截器
        // 拦截所有请求
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**");
        
        // 注册认证拦截器
        // 拦截所有 API 请求
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 排除登录和注册接口
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
    }
}
```

## 拦截器执行顺序

多个拦截器的执行顺序：

```
请求 → 拦截器1.preHandle → 拦截器2.preHandle → Controller
     → 拦截器2.postHandle → 拦截器1.postHandle → 视图渲染
     → 拦截器2.afterCompletion → 拦截器1.afterCompletion → 响应
```

**注意**：
- preHandle 按注册顺序执行
- postHandle 和 afterCompletion 按注册顺序的逆序执行
- 如果某个 preHandle 返回 false，后续拦截器和 Controller 都不会执行

## 在 Controller 中获取拦截器设置的属性

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @GetMapping("/profile")
    public ApiResponse<User> getUserProfile(HttpServletRequest request) {
        // 获取拦截器设置的用户 ID
        Long userId = (Long) request.getAttribute("userId");
        
        // 根据用户 ID 查询用户信息
        User user = userService.getUserById(userId);
        
        return ApiResponse.success(user);
    }
}
```

## 常见应用场景

1. **身份认证**：验证用户是否登录
2. **权限检查**：验证用户是否有访问权限
3. **日志记录**：记录请求和响应信息
4. **性能监控**：统计请求处理时间
5. **限流控制**：防止恶意请求
6. **跨域处理**：设置 CORS 响应头
7. **请求预处理**：修改请求参数
8. **响应后处理**：修改响应内容

## 最佳实践

1. **保持简单**：拦截器逻辑应该简单快速
2. **避免阻塞**：不要在拦截器中执行耗时操作
3. **正确排除路径**：避免拦截不需要的请求
4. **异常处理**：正确处理异常，避免影响其他请求
5. **资源清理**：在 afterCompletion 中清理资源

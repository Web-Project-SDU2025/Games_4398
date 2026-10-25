# 数据传输对象（DTO）

此目录用于存放数据传输对象（Data Transfer Object）。

## DTO 的作用

1. **解耦实体和接口**：避免直接暴露数据库实体
2. **精简数据传输**：只传输需要的字段
3. **数据转换**：处理前后端数据格式差异
4. **安全性**：隐藏敏感字段（如密码）

## 目录结构

```
dto/
├── request/       # 请求数据对象
│   ├── UserLoginRequest.java
│   ├── UserRegisterRequest.java
│   ├── UserUpdateRequest.java
│   └── ...
├── response/      # 响应数据对象
│   ├── UserInfoResponse.java
│   ├── UserListResponse.java
│   └── ...
└── README.md
```

## 示例

### 请求对象示例

```java
/**
 * 用户登录请求对象
 */
public class UserLoginRequest {
    
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    // 验证码（可选）
    private String captcha;
    
    /**
     * 获取用户名
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * 设置用户名
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * 获取密码
     * @return 密码
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * 设置密码
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * 获取验证码
     * @return 验证码
     */
    public String getCaptcha() {
        return captcha;
    }
    
    /**
     * 设置验证码
     * @param captcha 验证码
     */
    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
```

### 响应对象示例

```java
/**
 * 用户信息响应对象
 * 注意：不包含敏感信息（如密码）
 */
public class UserInfoResponse {
    
    // 用户ID
    private Long id;
    
    // 用户名
    private String username;
    
    // 邮箱
    private String email;
    
    // 手机号
    private String phone;
    
    // 头像URL
    private String avatar;
    
    // 角色名称
    private String roleName;
    
    // 状态
    private Integer status;
    
    // 创建时间
    private String createTime;
    
    /**
     * 从实体对象转换为响应对象
     * @param user 用户实体
     * @return 用户响应对象
     */
    public static UserInfoResponse fromEntity(User user) {
        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setStatus(user.getStatus());
        
        // 格式化创建时间
        if (user.getCreateTime() != null) {
            response.setCreateTime(user.getCreateTime().toString());
        }
        
        // 获取角色名称
        if (user.getRole() != null) {
            response.setRoleName(user.getRole().getName());
        }
        
        return response;
    }
    
    // getter 和 setter 方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    // 其他 getter 和 setter 方法省略...
}
```

## 使用场景

### 1. Controller 中使用

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public DataResponse login(@Valid @RequestBody UserLoginRequest request) {
        // 调用服务层登录
        User user = userService.login(request.getUsername(), request.getPassword());
        
        // 转换为响应对象
        UserInfoResponse response = UserInfoResponse.fromEntity(user);
        
        return DataResponse.success(response);
    }
    
    /**
     * 获取用户信息
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public DataResponse getUserInfo(@PathVariable Long id) {
        User user = userService.getUserById(id);
        UserInfoResponse response = UserInfoResponse.fromEntity(user);
        return DataResponse.success(response);
    }
}
```

## 命名规范

1. **请求对象**：业务名 + Request（如 UserLoginRequest）
2. **响应对象**：业务名 + Response（如 UserInfoResponse）
3. **查询对象**：业务名 + Query（如 UserSearchQuery）
4. **DTO对象**：业务名 + DTO（如 UserDTO）

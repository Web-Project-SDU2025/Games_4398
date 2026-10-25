# 枚举类

此目录用于存放枚举类型定义。

## 什么是枚举

枚举用于定义一组固定的常量值，例如状态、类型、角色等。

## 目录结构

```
enums/
├── UserStatus.java          # 用户状态枚举
├── RoleType.java           # 角色类型枚举
├── MenuType.java           # 菜单类型枚举
├── GameStatus.java         # 游戏状态枚举
└── README.md
```

## 示例

### UserStatus.java - 用户状态枚举

```java
package com.game.system.enums;

/**
 * 用户状态枚举
 */
public enum UserStatus {
    
    /**
     * 禁用状态
     */
    DISABLED(0, "禁用"),
    
    /**
     * 启用状态
     */
    ENABLED(1, "启用");
    
    /**
     * 状态码
     */
    private final Integer code;
    
    /**
     * 状态描述
     */
    private final String description;
    
    /**
     * 构造函数
     * @param code 状态码
     * @param description 状态描述
     */
    UserStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 获取状态码
     * @return 状态码
     */
    public Integer getCode() {
        return code;
    }
    
    /**
     * 获取状态描述
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据状态码获取枚举
     * @param code 状态码
     * @return 枚举对象，找不到返回 null
     */
    public static UserStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        
        for (UserStatus status : UserStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        
        return null;
    }
    
    /**
     * 检查状态码是否有效
     * @param code 状态码
     * @return 是否有效
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
```

### RoleType.java - 角色类型枚举

```java
package com.game.system.enums;

/**
 * 角色类型枚举
 */
public enum RoleType {
    
    /**
     * 超级管理员
     */
    ADMIN("ROLE_ADMIN", "超级管理员"),
    
    /**
     * 普通用户
     */
    USER("ROLE_USER", "普通用户"),
    
    /**
     * 游戏管理员
     */
    GAME_ADMIN("ROLE_GAME_ADMIN", "游戏管理员");
    
    /**
     * 角色编码
     */
    private final String code;
    
    /**
     * 角色名称
     */
    private final String name;
    
    /**
     * 构造函数
     * @param code 角色编码
     * @param name 角色名称
     */
    RoleType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    /**
     * 获取角色编码
     * @return 角色编码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取角色名称
     * @return 角色名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 根据角色编码获取枚举
     * @param code 角色编码
     * @return 枚举对象，找不到返回 null
     */
    public static RoleType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        
        for (RoleType roleType : RoleType.values()) {
            if (roleType.getCode().equals(code)) {
                return roleType;
            }
        }
        
        return null;
    }
    
    /**
     * 检查角色编码是否有效
     * @param code 角色编码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
```

### MenuType.java - 菜单类型枚举

```java
package com.game.system.enums;

/**
 * 菜单类型枚举
 */
public enum MenuType {
    
    /**
     * 菜单
     */
    MENU(1, "菜单"),
    
    /**
     * 按钮
     */
    BUTTON(2, "按钮");
    
    /**
     * 类型码
     */
    private final Integer code;
    
    /**
     * 类型描述
     */
    private final String description;
    
    /**
     * 构造函数
     * @param code 类型码
     * @param description 类型描述
     */
    MenuType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 获取类型码
     * @return 类型码
     */
    public Integer getCode() {
        return code;
    }
    
    /**
     * 获取类型描述
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据类型码获取枚举
     * @param code 类型码
     * @return 枚举对象，找不到返回 null
     */
    public static MenuType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        
        for (MenuType menuType : MenuType.values()) {
            if (menuType.getCode().equals(code)) {
                return menuType;
            }
        }
        
        return null;
    }
    
    /**
     * 检查类型码是否有效
     * @param code 类型码
     * @return 是否有效
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
```

### GameStatus.java - 游戏状态枚举

```java
package com.game.system.enums;

/**
 * 游戏状态枚举
 */
public enum GameStatus {
    
    /**
     * 下架
     */
    OFFLINE(0, "下架"),
    
    /**
     * 上架
     */
    ONLINE(1, "上架");
    
    /**
     * 状态码
     */
    private final Integer code;
    
    /**
     * 状态描述
     */
    private final String description;
    
    /**
     * 构造函数
     * @param code 状态码
     * @param description 状态描述
     */
    GameStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 获取状态码
     * @return 状态码
     */
    public Integer getCode() {
        return code;
    }
    
    /**
     * 获取状态描述
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据状态码获取枚举
     * @param code 状态码
     * @return 枚举对象，找不到返回 null
     */
    public static GameStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        
        for (GameStatus status : GameStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        
        return null;
    }
    
    /**
     * 检查状态码是否有效
     * @param code 状态码
     * @return 是否有效
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
```

## 使用示例

### 在实体类中使用

```java
@Entity
@Table(name = "user")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    
    /**
     * 用户状态
     * 存储为整数，但可以转换为枚举
     */
    private Integer status;
    
    /**
     * 获取用户状态枚举
     * @return 用户状态枚举
     */
    public UserStatus getUserStatusEnum() {
        return UserStatus.fromCode(this.status);
    }
    
    /**
     * 设置用户状态枚举
     * @param userStatus 用户状态枚举
     */
    public void setUserStatusEnum(UserStatus userStatus) {
        this.status = userStatus.getCode();
    }
    
    /**
     * 检查用户是否启用
     * @return 是否启用
     */
    public boolean isEnabled() {
        return UserStatus.ENABLED.equals(getUserStatusEnum());
    }
}
```

### 在 Service 中使用

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public void enableUser(Long userId) {
        // 查找用户
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("用户", "id", userId));
        
        // 设置为启用状态
        user.setUserStatusEnum(UserStatus.ENABLED);
        
        // 保存
        userRepository.save(user);
    }
    
    @Override
    public void disableUser(Long userId) {
        // 查找用户
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("用户", "id", userId));
        
        // 设置为禁用状态
        user.setUserStatusEnum(UserStatus.DISABLED);
        
        // 保存
        userRepository.save(user);
    }
    
    @Override
    public List<User> getEnabledUsers() {
        // 查询所有启用的用户
        return userRepository.findByStatus(UserStatus.ENABLED.getCode());
    }
}
```

### 在 Controller 中验证

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @PostMapping("/update-status")
    public ApiResponse<Void> updateUserStatus(
        @RequestParam Long userId,
        @RequestParam Integer status
    ) {
        // 验证状态码是否有效
        if (!UserStatus.isValid(status)) {
            throw new ValidationException("status", "无效的状态码");
        }
        
        // 更新用户状态
        userService.updateUserStatus(userId, status);
        
        return ApiResponse.success();
    }
}
```

## 枚举的优势

1. **类型安全**：编译时检查，避免使用无效值
2. **可读性强**：使用有意义的名称而不是魔法数字
3. **易于维护**：集中管理所有可能的值
4. **自动补全**：IDE 可以提供代码提示
5. **防止错误**：不能创建枚举外的值

## 最佳实践

1. **提供转换方法**：fromCode() 方法用于从数据库值转换
2. **提供验证方法**：isValid() 方法用于验证输入
3. **添加描述字段**：便于显示和理解
4. **使用有意义的名称**：ENABLED 比 STATUS_1 更清晰
5. **避免在枚举中添加业务逻辑**：保持枚举简单

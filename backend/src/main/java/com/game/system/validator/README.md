# 自定义验证器

此目录用于存放自定义验证器，配合 Spring Validation 使用。

## 什么是自定义验证器

Spring Validation 提供了基础验证注解（如 @NotNull、@Size），自定义验证器用于实现特定的业务验证规则。

## 目录结构

```
validator/
├── UsernameValidator.java       # 用户名验证器
├── PhoneValidator.java         # 手机号验证器
├── annotation/                 # 验证注解
│   ├── ValidUsername.java
│   └── ValidPhone.java
└── README.md
```

## 创建步骤

1. 定义验证注解
2. 实现验证器类
3. 在需要验证的字段上使用注解

## 示例

### ValidUsername.java - 用户名验证注解

```java
package com.game.system.validator.annotation;

import com.game.system.validator.UsernameValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户名验证注解
 * 验证用户名格式是否符合规则
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = UsernameValidator.class)
public @interface ValidUsername {
    
    /**
     * 验证失败时的错误信息
     */
    String message() default "用户名格式不正确，应为4-20位字母、数字或下划线";
    
    /**
     * 验证组
     */
    Class<?>[] groups() default {};
    
    /**
     * 负载
     */
    Class<? extends Payload>[] payload() default {};
}
```

### UsernameValidator.java - 用户名验证器

```java
package com.game.system.validator;

import com.game.system.validator.annotation.ValidUsername;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 用户名验证器
 * 验证用户名格式：4-20位字母、数字或下划线
 */
public class UsernameValidator implements ConstraintValidator<ValidUsername, String> {
    
    /**
     * 用户名正则表达式
     * 4-20位字母、数字或下划线
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
    
    /**
     * 初始化方法
     * @param constraintAnnotation 注解实例
     */
    @Override
    public void initialize(ValidUsername constraintAnnotation) {
        // 可以在这里获取注解的参数
    }
    
    /**
     * 验证方法
     * @param value 要验证的值
     * @param context 验证上下文
     * @return true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 如果值为 null，交给 @NotNull 处理
        if (value == null) {
            return true;
        }
        
        // 检查是否为空字符串
        if (value.isEmpty()) {
            return false;
        }
        
        // 使用正则表达式验证格式
        return USERNAME_PATTERN.matcher(value).matches();
    }
}
```

### ValidPhone.java - 手机号验证注解

```java
package com.game.system.validator.annotation;

import com.game.system.validator.PhoneValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手机号验证注解
 * 验证手机号格式是否正确
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = PhoneValidator.class)
public @interface ValidPhone {
    
    /**
     * 验证失败时的错误信息
     */
    String message() default "手机号格式不正确";
    
    /**
     * 是否允许为空
     */
    boolean nullable() default false;
    
    /**
     * 验证组
     */
    Class<?>[] groups() default {};
    
    /**
     * 负载
     */
    Class<? extends Payload>[] payload() default {};
}
```

### PhoneValidator.java - 手机号验证器

```java
package com.game.system.validator;

import com.game.system.validator.annotation.ValidPhone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 手机号验证器
 * 验证中国大陆手机号格式
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
    
    /**
     * 手机号正则表达式
     * 中国大陆手机号：1开头，第二位是3-9，后面9位数字
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    
    /**
     * 是否允许为空
     */
    private boolean nullable;
    
    /**
     * 初始化方法
     * @param constraintAnnotation 注解实例
     */
    @Override
    public void initialize(ValidPhone constraintAnnotation) {
        // 获取注解参数
        this.nullable = constraintAnnotation.nullable();
    }
    
    /**
     * 验证方法
     * @param value 要验证的值
     * @param context 验证上下文
     * @return true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 如果值为 null
        if (value == null) {
            // 根据 nullable 参数决定是否通过
            return nullable;
        }
        
        // 检查是否为空字符串
        if (value.isEmpty()) {
            return nullable;
        }
        
        // 使用正则表达式验证格式
        return PHONE_PATTERN.matcher(value).matches();
    }
}
```

### ValidEnum.java - 枚举验证注解

```java
package com.game.system.validator.annotation;

import com.game.system.validator.EnumValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举验证注解
 * 验证值是否在指定的枚举范围内
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = EnumValidator.class)
public @interface ValidEnum {
    
    /**
     * 枚举类
     */
    Class<? extends Enum<?>> enumClass();
    
    /**
     * 验证失败时的错误信息
     */
    String message() default "无效的枚举值";
    
    /**
     * 验证组
     */
    Class<?>[] groups() default {};
    
    /**
     * 负载
     */
    Class<? extends Payload>[] payload() default {};
}
```

### EnumValidator.java - 枚举验证器

```java
package com.game.system.validator;

import com.game.system.validator.annotation.ValidEnum;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * 枚举验证器
 * 验证值是否在指定的枚举范围内
 */
public class EnumValidator implements ConstraintValidator<ValidEnum, Integer> {
    
    /**
     * 枚举类
     */
    private Class<? extends Enum<?>> enumClass;
    
    /**
     * 初始化方法
     * @param constraintAnnotation 注解实例
     */
    @Override
    public void initialize(ValidEnum constraintAnnotation) {
        // 获取枚举类
        this.enumClass = constraintAnnotation.enumClass();
    }
    
    /**
     * 验证方法
     * @param value 要验证的值
     * @param context 验证上下文
     * @return true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // 如果值为 null，交给 @NotNull 处理
        if (value == null) {
            return true;
        }
        
        // 获取所有枚举常量
        Enum<?>[] enumConstants = enumClass.getEnumConstants();
        
        // 检查值是否在枚举范围内
        for (Enum<?> enumConstant : enumConstants) {
            // 调用枚举的 ordinal() 方法获取序号
            if (enumConstant.ordinal() == value) {
                return true;
            }
        }
        
        return false;
    }
}
```

## 在 DTO 中使用验证注解

```java
package com.game.system.dto.request;

import com.game.system.validator.annotation.ValidPhone;
import com.game.system.validator.annotation.ValidUsername;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 用户创建请求 DTO
 */
public class UserCreateRequest {
    
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @ValidUsername
    private String username;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度应为6-20位")
    private String password;
    
    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 手机号
     */
    @ValidPhone(nullable = true)
    private String phone;
    
    /**
     * 角色 ID
     */
    @NotNull(message = "角色不能为空")
    private Long roleId;
    
    // Getter 和 Setter 方法
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
```

## 在 Controller 中启用验证

```java
package com.game.system.controller;

import com.game.system.dto.request.UserCreateRequest;
import com.game.system.payload.response.ApiResponse;
import com.game.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建用户
     * @param request 用户创建请求
     * @return 创建结果
     */
    @PostMapping("/create")
    public ApiResponse<Void> createUser(@Valid @RequestBody UserCreateRequest request) {
        // @Valid 注解会触发验证
        // 如果验证失败，会抛出 MethodArgumentNotValidException
        
        userService.createUser(request);
        return ApiResponse.success();
    }
}
```

## 处理验证异常

在全局异常处理器中处理验证异常：

```java
package com.game.system.config;

import com.game.system.payload.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理参数验证异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
        MethodArgumentNotValidException exception
    ) {
        // 获取所有验证错误
        Map<String, String> errors = new HashMap<>();
        
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // 字段名
            String fieldName = error.getField();
            // 错误信息
            String errorMessage = error.getDefaultMessage();
            
            errors.put(fieldName, errorMessage);
        }
        
        // 创建响应
        ApiResponse<Object> response = ApiResponse.fail(400, "参数验证失败");
        response.setData(errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
```

## 常用内置验证注解

```java
// 空值检查
@NotNull          // 不能为 null
@NotEmpty         // 不能为 null 或空（集合、字符串）
@NotBlank         // 不能为 null 或空白（字符串）

// 数值检查
@Min(value)       // 最小值
@Max(value)       // 最大值
@DecimalMin       // 最小值（支持小数）
@DecimalMax       // 最大值（支持小数）
@Positive         // 正数
@PositiveOrZero   // 正数或零
@Negative         // 负数
@NegativeOrZero   // 负数或零

// 大小检查
@Size(min, max)   // 长度范围（字符串、集合、数组）

// 格式检查
@Email            // 邮箱格式
@Pattern(regexp)  // 正则表达式匹配

// 时间检查
@Past             // 过去的时间
@PastOrPresent    // 过去或现在的时间
@Future           // 将来的时间
@FutureOrPresent  // 将来或现在的时间
```

## 最佳实践

1. **错误信息清晰**：提供明确的错误提示
2. **复用验证器**：相同的验证逻辑只写一次
3. **组合使用**：可以在一个字段上使用多个验证注解
4. **性能考虑**：避免在验证器中执行耗时操作
5. **国际化支持**：错误信息可以使用 MessageSource

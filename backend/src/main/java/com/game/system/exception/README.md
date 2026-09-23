# 后端异常处理

此目录用于存放自定义异常类。

## 目录结构

```
exception/
├── BaseException.java            # 基础异常类
├── BusinessException.java        # 业务异常
├── ValidationException.java      # 验证异常
├── NotFoundException.java        # 资源不存在异常
├── UnauthorizedException.java    # 未授权异常
└── README.md
```

## 异常层次结构

```
RuntimeException
    └── BaseException (自定义基础异常)
        ├── BusinessException (业务异常)
        ├── ValidationException (验证异常)
        ├── NotFoundException (资源不存在)
        └── UnauthorizedException (未授权)
```

## 示例

### BaseException.java - 基础异常类

```java
package com.game.system.exception;

/**
 * 自定义基础异常类
 * 所有自定义异常的父类
 */
public class BaseException extends RuntimeException {
    
    /**
     * 错误码
     */
    private Integer code;
    
    /**
     * 错误信息
     */
    private String message;
    
    /**
     * 构造函数
     * @param code 错误码
     * @param message 错误信息
     */
    public BaseException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 构造函数
     * @param message 错误信息
     */
    public BaseException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
    }
    
    /**
     * 获取错误码
     * @return 错误码
     */
    public Integer getCode() {
        return code;
    }
    
    /**
     * 设置错误码
     * @param code 错误码
     */
    public void setCode(Integer code) {
        this.code = code;
    }
    
    /**
     * 获取错误信息
     * @return 错误信息
     */
    @Override
    public String getMessage() {
        return message;
    }
    
    /**
     * 设置错误信息
     * @param message 错误信息
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
```

### BusinessException.java - 业务异常

```java
package com.game.system.exception;

/**
 * 业务异常
 * 用于处理业务逻辑中的错误
 */
public class BusinessException extends BaseException {
    
    /**
     * 构造函数
     * @param message 错误信息
     */
    public BusinessException(String message) {
        super(400, message);
    }
    
    /**
     * 构造函数
     * @param code 错误码
     * @param message 错误信息
     */
    public BusinessException(Integer code, String message) {
        super(code, message);
    }
}
```

### ValidationException.java - 验证异常

```java
package com.game.system.exception;

/**
 * 验证异常
 * 用于处理参数验证失败的情况
 */
public class ValidationException extends BaseException {
    
    /**
     * 构造函数
     * @param message 错误信息
     */
    public ValidationException(String message) {
        super(400, message);
    }
    
    /**
     * 构造函数
     * @param fieldName 字段名
     * @param message 错误信息
     */
    public ValidationException(String fieldName, String message) {
        super(400, fieldName + ": " + message);
    }
}
```

### NotFoundException.java - 资源不存在异常

```java
package com.game.system.exception;

/**
 * 资源不存在异常
 * 用于处理查找资源失败的情况
 */
public class NotFoundException extends BaseException {
    
    /**
     * 构造函数
     * @param message 错误信息
     */
    public NotFoundException(String message) {
        super(404, message);
    }
    
    /**
     * 构造函数
     * @param resourceName 资源名称
     * @param fieldName 字段名
     * @param fieldValue 字段值
     */
    public NotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(404, String.format("%s 不存在：%s = %s", resourceName, fieldName, fieldValue));
    }
}
```

### UnauthorizedException.java - 未授权异常

```java
package com.game.system.exception;

/**
 * 未授权异常
 * 用于处理未登录或权限不足的情况
 */
public class UnauthorizedException extends BaseException {
    
    /**
     * 构造函数
     * @param message 错误信息
     */
    public UnauthorizedException(String message) {
        super(401, message);
    }
    
    /**
     * 无参构造函数
     */
    public UnauthorizedException() {
        super(401, "未授权，请先登录");
    }
}
```

## 全局异常处理器

在 `config` 包中创建全局异常处理器：

```java
package com.game.system.config;

import com.game.system.exception.BaseException;
import com.game.system.exception.BusinessException;
import com.game.system.exception.NotFoundException;
import com.game.system.exception.UnauthorizedException;
import com.game.system.exception.ValidationException;
import com.game.system.payload.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理所有异常，返回标准的响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理自定义基础异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseException(BaseException exception) {
        // 创建失败响应
        ApiResponse<Object> response = ApiResponse.fail(
            exception.getCode(),
            exception.getMessage()
        );
        
        // 根据错误码设置 HTTP 状态码
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (exception.getCode() == 400) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getCode() == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (exception.getCode() == 404) {
            status = HttpStatus.NOT_FOUND;
        }
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * 处理业务异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        ApiResponse<Object> response = ApiResponse.fail(
            exception.getCode(),
            exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理验证异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(ValidationException exception) {
        ApiResponse<Object> response = ApiResponse.fail(
            exception.getCode(),
            exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理资源不存在异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException exception) {
        ApiResponse<Object> response = ApiResponse.fail(
            exception.getCode(),
            exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 处理未授权异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException exception) {
        ApiResponse<Object> response = ApiResponse.fail(
            exception.getCode(),
            exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * 处理所有未捕获的异常
     * @param exception 异常对象
     * @return 响应结果
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        // 打印异常堆栈
        exception.printStackTrace();
        
        // 返回通用错误信息
        ApiResponse<Object> response = ApiResponse.fail(
            500,
            "服务器内部错误：" + exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

## 使用示例

在 Service 或 Controller 中使用：

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public User getUserById(Long id) {
        // 查找用户
        User user = userRepository.findById(id).orElse(null);
        
        // 如果用户不存在，抛出异常
        if (user == null) {
            throw new NotFoundException("用户", "id", id);
        }
        
        return user;
    }
    
    @Override
    public void deleteUser(Long id) {
        // 检查用户是否存在
        User user = getUserById(id);
        
        // 检查是否可以删除
        if (user.getRoleId() == 1) {
            throw new BusinessException("不能删除管理员账号");
        }
        
        // 删除用户
        userRepository.deleteById(id);
    }
    
    @Override
    public User createUser(UserCreateRequest request) {
        // 验证用户名
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            throw new ValidationException("username", "用户名不能为空");
        }
        
        // 检查用户名是否已存在
        User existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }
        
        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        
        return userRepository.save(user);
    }
}
```

## 异常处理最佳实践

1. **明确的异常类型**：不同场景使用不同异常
2. **清晰的错误信息**：让用户知道错误原因
3. **统一的响应格式**：便于前端处理
4. **记录异常日志**：方便排查问题
5. **不要暴露敏感信息**：避免泄露系统细节

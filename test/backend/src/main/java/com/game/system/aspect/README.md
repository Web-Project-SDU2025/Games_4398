# AOP 切面

此目录用于存放 AOP（面向切面编程）相关代码。

## 什么是 AOP

AOP 允许我们在不修改原有代码的情况下，为程序添加额外的功能（如日志记录、性能监控、事务管理等）。

## 目录结构

```
aspect/
├── LoggingAspect.java       # 日志切面
├── PerformanceAspect.java   # 性能监控切面
└── README.md
```

## 核心概念

- **切面（Aspect）**：关注点的模块化，例如日志记录
- **连接点（Join Point）**：程序执行的某个点，例如方法调用
- **切入点（Pointcut）**：匹配连接点的表达式
- **通知（Advice）**：切面在特定连接点执行的动作
  - Before：方法执行前
  - After：方法执行后
  - AfterReturning：方法成功返回后
  - AfterThrowing：方法抛出异常后
  - Around：方法执行前后

## 示例

### LoggingAspect.java - 日志切面

```java
package com.game.system.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 日志切面
 * 自动记录 Controller 和 Service 层的方法调用日志
 */
@Aspect
@Component
public class LoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    /**
     * 定义切入点：匹配 Controller 层的所有方法
     */
    @Pointcut("execution(* com.game.system.controller..*(..))")
    public void controllerMethods() {
        // 切入点定义，方法体为空
    }
    
    /**
     * 定义切入点：匹配 Service 层的所有方法
     */
    @Pointcut("execution(* com.game.system.service..*(..))")
    public void serviceMethods() {
        // 切入点定义，方法体为空
    }
    
    /**
     * 前置通知
     * 在方法执行前记录日志
     * @param joinPoint 连接点信息
     */
    @Before("controllerMethods() || serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 获取参数
        Object[] args = joinPoint.getArgs();
        
        // 记录日志
        logger.info("开始执行：{}.{}，参数：{}", 
            className, methodName, Arrays.toString(args));
    }
    
    /**
     * 后置通知
     * 在方法执行后记录日志
     * @param joinPoint 连接点信息
     */
    @After("controllerMethods() || serviceMethods()")
    public void logAfter(JoinPoint joinPoint) {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 记录日志
        logger.info("执行完成：{}.{}", className, methodName);
    }
    
    /**
     * 返回通知
     * 在方法成功返回后记录日志
     * @param joinPoint 连接点信息
     * @param result 返回值
     */
    @AfterReturning(
        pointcut = "controllerMethods() || serviceMethods()",
        returning = "result"
    )
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 记录日志
        logger.info("执行成功：{}.{}，返回值：{}", 
            className, methodName, result);
    }
    
    /**
     * 异常通知
     * 在方法抛出异常后记录日志
     * @param joinPoint 连接点信息
     * @param exception 异常对象
     */
    @AfterThrowing(
        pointcut = "controllerMethods() || serviceMethods()",
        throwing = "exception"
    )
    public void logAfterThrowing(JoinPoint joinPoint, Exception exception) {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 记录日志
        logger.error("执行异常：{}.{}，异常信息：{}", 
            className, methodName, exception.getMessage());
    }
    
    /**
     * 环绕通知
     * 在方法执行前后都可以执行自定义逻辑
     * @param joinPoint 连接点信息
     * @return 方法返回值
     * @throws Throwable 异常
     */
    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        logger.info("方法开始：{}.{}", className, methodName);
        
        Object result = null;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.info("方法结束：{}.{}，耗时：{}ms", 
                className, methodName, duration);
            
            return result;
        } catch (Throwable throwable) {
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.error("方法异常：{}.{}，耗时：{}ms，异常：{}", 
                className, methodName, duration, throwable.getMessage());
            
            throw throwable;
        }
    }
}
```

### PerformanceAspect.java - 性能监控切面

```java
package com.game.system.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 性能监控切面
 * 监控方法执行时间，发现性能问题
 */
@Aspect
@Component
public class PerformanceAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);
    
    /**
     * 性能监控阈值（毫秒）
     * 超过这个时间会记录警告日志
     */
    private static final long SLOW_THRESHOLD = 1000;
    
    /**
     * 定义切入点：匹配所有 Service 方法
     */
    @Pointcut("execution(* com.game.system.service..*(..))")
    public void serviceMethods() {
        // 切入点定义，方法体为空
    }
    
    /**
     * 定义切入点：匹配所有 Repository 方法
     */
    @Pointcut("execution(* com.game.system.repository..*(..))")
    public void repositoryMethods() {
        // 切入点定义，方法体为空
    }
    
    /**
     * 监控方法执行时间
     * @param joinPoint 连接点信息
     * @return 方法返回值
     * @throws Throwable 异常
     */
    @Around("serviceMethods() || repositoryMethods()")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取方法名
        String methodName = joinPoint.getSignature().getName();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 如果执行时间超过阈值，记录警告日志
            if (duration > SLOW_THRESHOLD) {
                logger.warn("慢方法警告：{}.{} 执行时间：{}ms（阈值：{}ms）", 
                    className, methodName, duration, SLOW_THRESHOLD);
            } else {
                logger.debug("性能监控：{}.{} 执行时间：{}ms", 
                    className, methodName, duration);
            }
            
            return result;
        } catch (Throwable throwable) {
            // 即使方法抛出异常，也记录执行时间
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.error("方法异常：{}.{} 执行时间：{}ms", 
                className, methodName, duration);
            
            throw throwable;
        }
    }
}
```

## 切入点表达式

### 常用表达式

```java
// 匹配所有方法
execution(* *(..))

// 匹配指定包下的所有方法
execution(* com.game.system.service.*.*(..))

// 匹配指定包及其子包下的所有方法
execution(* com.game.system.service..*.*(..))

// 匹配指定类的所有方法
execution(* com.game.system.service.UserService.*(..))

// 匹配指定方法
execution(* com.game.system.service.UserService.getUserById(..))

// 匹配返回值为 User 的方法
execution(com.game.system.entity.User *(..))

// 匹配第一个参数为 Long 的方法
execution(* *(Long, ..))
```

### 组合表达式

```java
// 与运算
@Pointcut("execution(* com.game.system.service..*(..)) && args(id)")

// 或运算
@Pointcut("execution(* com.game.system.service..*(..)) || execution(* com.game.system.controller..*(..))")

// 非运算
@Pointcut("execution(* com.game.system..*(..)) && !execution(* com.game.system.util..*(..))")
```

## 自定义注解切面

### 定义注解

```java
package com.game.system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    
    /**
     * 操作类型
     */
    String value() default "";
    
    /**
     * 操作描述
     */
    String description() default "";
}
```

### 定义切面

```java
@Aspect
@Component
public class OperationLogAspect {
    
    @Autowired
    private OperationLogService operationLogService;
    
    /**
     * 匹配带有 @OperationLog 注解的方法
     */
    @Pointcut("@annotation(com.game.system.annotation.OperationLog)")
    public void operationLogMethods() {
        // 切入点定义
    }
    
    /**
     * 记录操作日志
     */
    @AfterReturning("operationLogMethods() && @annotation(operationLog)")
    public void logOperation(JoinPoint joinPoint, OperationLog operationLog) {
        // 获取操作类型和描述
        String operation = operationLog.value();
        String description = operationLog.description();
        
        // 保存操作日志
        operationLogService.save(operation, description);
    }
}
```

### 使用注解

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @PostMapping("/create")
    @OperationLog(value = "创建用户", description = "创建新用户账号")
    public ApiResponse<User> createUser(@RequestBody UserCreateRequest request) {
        User user = userService.createUser(request);
        return ApiResponse.success(user);
    }
    
    @DeleteMapping("/{id}")
    @OperationLog(value = "删除用户", description = "删除用户账号")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }
}
```

## 依赖配置

在 `pom.xml` 中添加 AOP 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

## 注意事项

1. **性能影响**：AOP 会增加一定的性能开销
2. **切入点精确**：避免匹配过多不需要的方法
3. **异常处理**：Around 通知需要正确处理异常
4. **事务管理**：注意 AOP 与事务的执行顺序
5. **调试困难**：AOP 代码在调试时不易跟踪

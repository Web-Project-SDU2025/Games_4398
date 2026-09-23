# 模板目录

此目录用于存放服务端渲染的模板文件。

## 支持的模板引擎

1. **Thymeleaf**（推荐）- Spring Boot 官方推荐
2. **FreeMarker** - 功能强大的模板引擎
3. **Velocity** - 简单易用的模板引擎
4. **Mustache** - 轻量级模板引擎

## 使用场景

1. **服务端渲染页面**：传统的 MVC 应用
2. **邮件模板**：发送 HTML 格式邮件
3. **PDF 生成**：动态生成 PDF 文档
4. **错误页面**：自定义错误页面

## Thymeleaf 示例

### 添加依赖（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

### 配置（application.properties）

```properties
# Thymeleaf 配置
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.cache=false
```

### 模板示例（email-template.html）

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>邮件模板</title>
</head>
<body>
    <h1>您好，<span th:text="${username}">用户</span>！</h1>
    <p>您的验证码是：<strong th:text="${code}">123456</strong></p>
    <p>该验证码将在 <span th:text="${expireMinutes}">5</span> 分钟后失效。</p>
</body>
</html>
```

### Controller 使用

```java
@Controller
public class EmailController {
    
    @Autowired
    private TemplateEngine templateEngine;
    
    /**
     * 渲染邮件模板
     * @return 邮件HTML内容
     */
    public String renderEmailTemplate() {
        // 准备模板数据
        Context context = new Context();
        context.setVariable("username", "张三");
        context.setVariable("code", "123456");
        context.setVariable("expireMinutes", 5);
        
        // 渲染模板
        String html = templateEngine.process("email-template", context);
        return html;
    }
}
```

## 邮件模板目录结构

```
templates/
├── email/              # 邮件模板
│   ├── register.html   # 注册邮件
│   ├── reset-password.html  # 重置密码邮件
│   └── notice.html     # 通知邮件
├── error/              # 错误页面模板
│   ├── 404.html
│   ├── 500.html
│   └── error.html
└── README.md
```

## 注意事项

1. **前后端分离项目**：通常不需要服务端模板，此目录可能用不到
2. **仅用于邮件**：如果只发送邮件，只需要邮件模板即可
3. **性能考虑**：生产环境记得开启模板缓存

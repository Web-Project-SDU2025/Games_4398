# 静态资源目录

此目录用于存放静态资源文件，这些文件可以直接通过 HTTP 访问。

## 目录结构

```
static/
├── images/         # 图片资源
├── css/           # 样式文件
├── js/            # JavaScript 文件
├── fonts/         # 字体文件
└── uploads/       # 用户上传的文件
```

## 访问方式

放在 `static/` 目录下的文件可以直接通过 URL 访问：

```
http://localhost:8080/images/logo.png
http://localhost:8080/css/style.css
http://localhost:8080/js/common.js
```

## 注意事项

1. **安全性**：不要在此目录存放敏感文件
2. **大小限制**：不建议存放过大的文件
3. **版本控制**：用户上传的文件不应提交到 Git
4. **CDN**：生产环境建议使用 CDN 加速

## 配置

在 `application.properties` 中可以配置静态资源路径：

```properties
# 静态资源路径配置
spring.web.resources.static-locations=classpath:/static/

# 文件上传配置
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

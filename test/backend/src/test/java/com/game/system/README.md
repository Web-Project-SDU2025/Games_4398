# 测试目录

此目录用于存放单元测试和集成测试代码。

## 目录结构

```
test/java/com/game/system/
├── controller/     # 控制器测试
├── service/        # 服务层测试
├── repository/     # 数据访问层测试
└── util/          # 工具类测试
```

## 测试规范

1. 测试类命名：被测试类名 + Test（例如：UserServiceTest）
2. 测试方法命名：test + 方法名 + 场景（例如：testFindUserById_Success）
3. 使用 @SpringBootTest 进行集成测试
4. 使用 @MockBean 模拟依赖
5. 测试覆盖率目标：80% 以上

## 示例

```java
@SpringBootTest
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @MockBean
    private UserRepository userRepository;
    
    @Test
    void testFindUserById_Success() {
        // 准备测试数据
        // 执行测试
        // 验证结果
    }
}
```

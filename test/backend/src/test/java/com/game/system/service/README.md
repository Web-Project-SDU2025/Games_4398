# 服务层测试

此目录用于存放服务层（Service）的单元测试。

## 测试要点

1. 测试业务逻辑的正确性
2. 测试边界条件和异常情况
3. 模拟数据访问层的返回结果
4. 验证事务处理是否正确

## 示例

```java
@SpringBootTest
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @MockBean
    private UserRepository userRepository;
    
    @Test
    void testFindUserById_UserExists() {
        // 准备测试数据
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        
        // 模拟 Repository 返回
        when(userRepository.findById(1L))
            .thenReturn(Optional.of(mockUser));
        
        // 执行测试
        User result = userService.findUserById(1L);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }
    
    @Test
    void testFindUserById_UserNotFound() {
        // 模拟用户不存在的情况
        when(userRepository.findById(999L))
            .thenReturn(Optional.empty());
        
        // 验证抛出异常
        assertThrows(ResourceNotFoundException.class, () -> {
            userService.findUserById(999L);
        });
    }
}
```

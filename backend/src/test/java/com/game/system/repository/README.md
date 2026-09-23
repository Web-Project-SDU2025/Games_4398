# 数据访问层测试

此目录用于存放数据访问层（Repository）的单元测试。

## 测试要点

1. 测试自定义查询方法的正确性
2. 验证数据库操作（增删改查）
3. 测试关联查询和复杂查询
4. 验证事务回滚机制

## 示例

```java
@DataJpaTest
class UserRepositoryTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void testFindByUsername_Success() {
        // 准备测试数据
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password123");
        entityManager.persist(user);
        entityManager.flush();
        
        // 执行查询
        Optional<User> found = userRepository.findByUsername("testuser");
        
        // 验证结果
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }
    
    @Test
    void testFindByUsername_NotFound() {
        // 查询不存在的用户
        Optional<User> found = userRepository.findByUsername("nonexistent");
        
        // 验证结果为空
        assertFalse(found.isPresent());
    }
}
```

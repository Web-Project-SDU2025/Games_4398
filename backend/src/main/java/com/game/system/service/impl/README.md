# 服务接口层

此目录用于存放服务层的接口定义。

## 设计原则

1. **面向接口编程**：定义接口，实现类放在 impl 子包中
2. **单一职责**：每个 Service 只负责一个业务实体
3. **命名规范**：接口名以业务实体名 + Service 结尾
4. **方法命名**：使用动词开头，清晰表达业务意图

## 常用方法命名规范

- `find*` - 查询方法（返回单个对象或 Optional）
- `get*` - 获取方法（如果不存在则抛出异常）
- `list*` - 查询方法（返回列表）
- `page*` - 分页查询方法
- `save*` - 保存方法（新增或更新）
- `create*` - 新增方法
- `update*` - 更新方法
- `delete*` - 删除方法
- `exists*` - 判断是否存在
- `count*` - 统计数量

## 示例

```java
/**
 * 用户服务接口
 * 提供用户相关的业务逻辑
 */
public interface UserService {
    
    /**
     * 根据用户ID查找用户
     * @param id 用户ID
     * @return 用户对象（如果存在）
     */
    Optional<User> findUserById(Long id);
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户对象（如果存在）
     */
    Optional<User> findUserByUsername(String username);
    
    /**
     * 获取用户（如果不存在则抛出异常）
     * @param id 用户ID
     * @return 用户对象
     * @throws ResourceNotFoundException 用户不存在时抛出
     */
    User getUserById(Long id);
    
    /**
     * 查询所有用户列表
     * @return 用户列表
     */
    List<User> listAllUsers();
    
    /**
     * 分页查询用户
     * @param pageNumber 页码（从0开始）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<User> pageUsers(int pageNumber, int pageSize);
    
    /**
     * 创建新用户
     * @param user 用户对象
     * @return 保存后的用户对象
     */
    User createUser(User user);
    
    /**
     * 更新用户信息
     * @param id 用户ID
     * @param user 用户对象
     * @return 更新后的用户对象
     */
    User updateUser(Long id, User user);
    
    /**
     * 删除用户
     * @param id 用户ID
     */
    void deleteUser(Long id);
    
    /**
     * 判断用户名是否已存在
     * @param username 用户名
     * @return true 存在，false 不存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 统计用户总数
     * @return 用户数量
     */
    long countUsers();
    
    /**
     * 修改用户密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @throws BusinessException 旧密码错误时抛出
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 重置用户密码
     * @param userId 用户ID
     * @param newPassword 新密码
     */
    void resetPassword(Long userId, String newPassword);
}
```

## 目录结构

```
service/
├── UserService.java           # 用户服务接口
├── RoleService.java           # 角色服务接口
├── MenuService.java           # 菜单服务接口
├── GameService.java           # 游戏服务接口
├── impl/                      # 服务实现类
│   ├── UserServiceImpl.java
│   ├── RoleServiceImpl.java
│   ├── MenuServiceImpl.java
│   └── GameServiceImpl.java
└── README.md
```

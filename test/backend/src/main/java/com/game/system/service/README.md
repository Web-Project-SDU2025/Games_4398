# Service 业务逻辑层目录

## 说明
此目录用于存放 Service 接口和实现类（业务逻辑层），用于处理业务逻辑。
每个 Service 对应一个或多个 Repository，封装业务逻辑，供 Controller 调用。

## TODO: 需要创建的 Service 接口和实现类

### 1. UserService - 用户业务服务

**接口：UserService.java**
```java
public interface UserService {
    // 用户注册
    User register(String username, String password, String email);
    
    // 用户登录（返回 JWT Token）
    String login(String username, String password);
    
    // 根据ID获取用户信息
    User getUserById(Long id);
    
    // 更新用户信息
    User updateUser(Long id, Map<String, Object> userInfo);
    
    // 修改密码
    void changePassword(Long id, String oldPassword, String newPassword);
}
```

**实现类：UserServiceImpl.java**
```java
@Service
public class UserServiceImpl implements UserService {
    
    // TODO: [数据库连接] 注入 UserRepository
    // private final UserRepository userRepository;
    
    @Override
    public User register(String username, String password, String email) {
        // TODO: [数据库连接] 实现用户注册逻辑
        // 1. 检查用户名是否已存在
        // 2. 密码加密（使用 BCrypt）
        // 3. 创建用户对象
        // 4. 保存到数据库
        // 5. 返回用户信息
    }
    
    @Override
    public String login(String username, String password) {
        // TODO: [数据库连接] 实现用户登录逻辑
        // 1. 从数据库查询用户
        // 2. 验证密码
        // 3. 生成 JWT Token
        // 4. 返回 Token
    }
}
```

---

### 2. GameService - 游戏业务服务

**接口：GameService.java**
```java
public interface GameService {
    // 获取游戏列表（分页）
    Page<Game> getGameList(int page, int size);
    
    // 根据ID获取游戏详情
    Game getGameById(Long id);
    
    // 搜索游戏
    List<Game> searchGames(String keyword);
    
    // 根据分类获取游戏
    List<Game> getGamesByCategory(String category);
    
    // 添加游戏（管理员）
    Game addGame(Map<String, Object> gameInfo);
    
    // 更新游戏信息（管理员）
    Game updateGame(Long id, Map<String, Object> gameInfo);
    
    // 删除游戏（管理员）
    void deleteGame(Long id);
}
```

**实现类：GameServiceImpl.java**
```java
@Service
public class GameServiceImpl implements GameService {
    
    // TODO: [数据库连接] 注入 GameRepository
    // private final GameRepository gameRepository;
    
    @Override
    public Page<Game> getGameList(int page, int size) {
        // TODO: [数据库连接] 实现获取游戏列表（分页）
        // 使用 gameRepository.findAll(PageRequest.of(page, size))
    }
    
    @Override
    public Game getGameById(Long id) {
        // TODO: [数据库连接] 实现根据ID获取游戏
        // 使用 gameRepository.findById(id)
    }
}
```

---

### 3. UserGameListService - 用户游戏清单业务服务

**接口：UserGameListService.java**
```java
public interface UserGameListService {
    // 获取用户的游戏清单
    List<UserGameList> getUserGameList(Long userId);
    
    // 添加游戏到清单
    UserGameList addGameToList(Long userId, Long gameId, Integer status);
    
    // 更新游戏状态
    UserGameList updateGameStatus(Long userId, Long gameId, Integer status);
    
    // 从清单中移除游戏
    void removeGameFromList(Long userId, Long gameId);
    
    // 更新游戏时长
    UserGameList updatePlayTime(Long userId, Long gameId, Integer playTime);
    
    // 根据状态筛选游戏清单
    List<UserGameList> getGameListByStatus(Long userId, Integer status);
}
```

**实现类：UserGameListServiceImpl.java**
```java
@Service
public class UserGameListServiceImpl implements UserGameListService {
    
    // TODO: [数据库连接] 注入 UserGameListRepository 和 GameRepository
    // private final UserGameListRepository userGameListRepository;
    // private final GameRepository gameRepository;
    
    @Override
    public List<UserGameList> getUserGameList(Long userId) {
        // TODO: [数据库连接] 实现获取用户游戏清单
        // 使用 userGameListRepository.findByUserId(userId)
    }
    
    @Override
    public UserGameList addGameToList(Long userId, Long gameId, Integer status) {
        // TODO: [数据库连接] 实现添加游戏到清单
        // 1. 检查游戏是否存在
        // 2. 检查是否已添加过
        // 3. 创建 UserGameList 对象
        // 4. 保存到数据库
    }
}
```

---

### 4. RoleService - 角色业务服务

**接口：RoleService.java**
```java
public interface RoleService {
    // 获取所有角色
    List<Role> getAllRoles();
    
    // 根据ID获取角色
    Role getRoleById(Long id);
    
    // 创建角色
    Role createRole(Map<String, Object> roleInfo);
    
    // 更新角色
    Role updateRole(Long id, Map<String, Object> roleInfo);
    
    // 删除角色
    void deleteRole(Long id);
}
```

---

### 5. MenuService - 菜单业务服务

**接口：MenuService.java**
```java
public interface MenuService {
    // 获取所有菜单（树形结构）
    List<Menu> getAllMenus();
    
    // 根据角色获取菜单
    List<Menu> getMenusByRoleId(Long roleId);
    
    // 创建菜单
    Menu createMenu(Map<String, Object> menuInfo);
    
    // 更新菜单
    Menu updateMenu(Long id, Map<String, Object> menuInfo);
    
    // 删除菜单
    void deleteMenu(Long id);
}
```

---

### 6. DictionaryService - 字典业务服务

**接口：DictionaryService.java**
```java
public interface DictionaryService {
    // 获取所有字典类型
    List<DictionaryType> getAllDictionaryTypes();
    
    // 根据字典类型编码获取字典数据
    List<DictionaryData> getDictionaryDataByCode(String typeCode);
    
    // 创建字典类型
    DictionaryType createDictionaryType(Map<String, Object> typeInfo);
    
    // 创建字典数据
    DictionaryData createDictionaryData(Map<String, Object> dataInfo);
}
```

---

## 注意事项

1. Service 层是业务逻辑的核心，负责：
   - 参数校验
   - 业务规则验证
   - 调用 Repository 操作数据库
   - 异常处理
   - 事务管理

2. 使用 `@Service` 注解标记 Service 实现类

3. 使用构造器注入依赖（推荐），而不是 `@Autowired` 字段注入

4. 复杂业务逻辑使用 `@Transactional` 注解管理事务

5. Service 方法应该抛出业务异常，由统一异常处理器处理

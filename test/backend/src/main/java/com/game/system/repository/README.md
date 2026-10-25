# Repository 数据访问层目录

## 说明
此目录用于存放 Repository 接口（数据访问层），用于操作数据库。
每个 Repository 接口对应一个实体类，提供数据库的增删改查操作。

## TODO: 需要创建的 Repository 接口

### 1. UserRepository.java - 用户数据访问接口
```java
public interface UserRepository extends JpaRepository<User, Long> {
    // 根据用户名查询用户（用于登录）
    Optional<User> findByUsername(String username);
    
    // 根据邮箱查询用户
    Optional<User> findByEmail(String email);
    
    // 根据手机号查询用户
    Optional<User> findByPhone(String phone);
    
    // 检查用户名是否存在
    boolean existsByUsername(String username);
}
```

### 2. RoleRepository.java - 角色数据访问接口
```java
public interface RoleRepository extends JpaRepository<Role, Long> {
    // 根据角色编码查询角色
    Optional<Role> findByCode(String code);
}
```

### 3. MenuRepository.java - 菜单数据访问接口
```java
public interface MenuRepository extends JpaRepository<Menu, Long> {
    // 查询某个角色的所有菜单
    List<Menu> findByRoleId(Long roleId);
    
    // 查询父菜单下的所有子菜单
    List<Menu> findByParentId(Long parentId);
}
```

### 4. GameRepository.java - 游戏数据访问接口
```java
public interface GameRepository extends JpaRepository<Game, Long> {
    // 根据游戏名称模糊查询
    List<Game> findByNameContaining(String name);
    
    // 根据分类查询游戏
    List<Game> findByCategory(String category);
    
    // 查询上架状态的游戏
    List<Game> findByStatus(Integer status);
}
```

### 5. UserGameListRepository.java - 用户游戏清单数据访问接口
```java
public interface UserGameListRepository extends JpaRepository<UserGameList, Long> {
    // 查询某个用户的所有游戏清单
    List<UserGameList> findByUserId(Long userId);
    
    // 查询某个用户某个状态的游戏清单
    List<UserGameList> findByUserIdAndStatus(Long userId, Integer status);
    
    // 检查用户是否已添加某个游戏
    boolean existsByUserIdAndGameId(Long userId, Long gameId);
}
```

### 6. DictionaryTypeRepository.java - 字典类型数据访问接口
```java
public interface DictionaryTypeRepository extends JpaRepository<DictionaryType, Long> {
    // 根据字典编码查询字典类型
    Optional<DictionaryType> findByCode(String code);
}
```

### 7. DictionaryDataRepository.java - 字典数据访问接口
```java
public interface DictionaryDataRepository extends JpaRepository<DictionaryData, Long> {
    // 查询某个字典类型下的所有字典数据
    List<DictionaryData> findByTypeId(Long typeId);
}
```

### 8. OperationLogRepository.java - 操作日志数据访问接口
```java
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    // 查询某个用户的操作日志
    List<OperationLog> findByUserId(Long userId);
    
    // 按时间倒序查询日志
    List<OperationLog> findAllByOrderByCreateTimeDesc();
}
```

## 注意事项

1. Repository 接口继承 `JpaRepository<实体类, 主键类型>`
2. JpaRepository 自动提供基础的 CRUD 方法：
   - `save(entity)` - 保存或更新
   - `findById(id)` - 根据 ID 查询
   - `findAll()` - 查询所有
   - `deleteById(id)` - 根据 ID 删除
   - `count()` - 统计数量
3. 自定义查询方法遵循命名规范：
   - `findBy` + 字段名 - 查询
   - `existsBy` + 字段名 - 检查是否存在
   - `deleteBy` + 字段名 - 删除
4. 使用 `@Query` 注解编写复杂查询（JPQL 或原生 SQL）

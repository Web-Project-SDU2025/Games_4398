# Entity 实体类目录

## 说明
此目录用于存放数据库实体类（Entity），每个实体类对应数据库中的一张表。

## TODO: 需要创建的实体类

### 1. User.java - 用户实体
对应数据库表：`user`
```java
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private String avatar;
    // ... 其他字段
}
```

### 2. Role.java - 角色实体
对应数据库表：`role`

### 3. Menu.java - 菜单实体
对应数据库表：`menu`

### 4. Game.java - 游戏实体
对应数据库表：`game`

### 5. UserGameList.java - 用户游戏清单实体
对应数据库表：`user_game_list`

### 6. DictionaryType.java - 字典类型实体
对应数据库表：`dictionary_type`

### 7. DictionaryData.java - 字典数据实体
对应数据库表：`dictionary_data`

### 8. OperationLog.java - 操作日志实体
对应数据库表：`operation_log`

## 创建实体类的注意事项

1. 使用 JPA 注解：`@Entity`、`@Table`、`@Id`、`@Column` 等
2. 字段名使用驼峰命名法（Java 规范）
3. 数据库列名使用下划线命名法（数据库规范）
4. 使用 `@Column(name = "column_name")` 映射字段和列名
5. 日期字段使用 `LocalDateTime` 类型
6. 添加必要的 getter/setter 方法（或使用 Lombok 的 `@Data` 注解）

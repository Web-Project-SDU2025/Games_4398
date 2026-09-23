# 数据库连接相关的 TODO 清单

本文档汇总了项目中所有需要连接数据库的地方，按优先级排列。

---

## 📋 开发顺序建议

### 第一步：创建实体类（Entity）
位置：`backend/src/main/java/com/game/system/entity/`

必须先创建实体类，因为 Repository 和 Service 都依赖实体类。

#### 优先级 1 - 核心实体（必须先完成）
1. ✅ **User.java** - 用户实体（登录、注册依赖）
2. ✅ **Role.java** - 角色实体（权限管理依赖）

#### 优先级 2 - 业务实体
3. ✅ **Game.java** - 游戏实体
4. ✅ **UserGameList.java** - 用户游戏清单实体

#### 优先级 3 - 系统实体
5. ✅ **Menu.java** - 菜单实体
6. ✅ **DictionaryType.java** - 字典类型实体
7. ✅ **DictionaryData.java** - 字典数据实体
8. ✅ **OperationLog.java** - 操作日志实体

详细说明请查看：[entity/README.md](src/main/java/com/game/system/entity/README.md)

---

### 第二步：创建 Repository 接口
位置：`backend/src/main/java/com/game/system/repository/`

Repository 接口用于数据库操作，继承 `JpaRepository`。

#### 优先级 1 - 核心 Repository
1. ✅ **UserRepository.java** - 用户数据访问接口
2. ✅ **RoleRepository.java** - 角色数据访问接口

#### 优先级 2 - 业务 Repository
3. ✅ **GameRepository.java** - 游戏数据访问接口
4. ✅ **UserGameListRepository.java** - 用户游戏清单数据访问接口

#### 优先级 3 - 系统 Repository
5. ✅ **MenuRepository.java** - 菜单数据访问接口
6. ✅ **DictionaryTypeRepository.java** - 字典类型数据访问接口
7. ✅ **DictionaryDataRepository.java** - 字典数据数据访问接口
8. ✅ **OperationLogRepository.java** - 操作日志数据访问接口

详细说明请查看：[repository/README.md](src/main/java/com/game/system/repository/README.md)

---

### 第三步：完善 Service 业务逻辑层
位置：`backend/src/main/java/com/game/system/service/`

#### 已有文件需要完善

##### 1. UserDetailsServiceImpl.java（认证核心）
文件位置：`service/UserDetailsServiceImpl.java`

**TODO 清单：**
- [ ] 注入 UserRepository
- [ ] 实现 `loadUserByUsername()` 方法
  - 从数据库查询用户
  - 获取用户角色
  - 构建 UserDetails 对象

**依赖：**
- User 实体类
- UserRepository 接口

---

#### 需要创建的 Service

##### 2. UserService（用户业务服务）
**接口：** `service/UserService.java`  
**实现类：** `service/impl/UserServiceImpl.java`

**TODO 清单：**
- [ ] 注入 UserRepository
- [ ] 实现用户注册逻辑
- [ ] 实现用户登录逻辑（生成 JWT）
- [ ] 实现获取用户信息
- [ ] 实现更新用户信息
- [ ] 实现修改密码

##### 3. GameService（游戏业务服务）
**接口：** `service/GameService.java`  
**实现类：** `service/impl/GameServiceImpl.java`

**TODO 清单：**
- [ ] 注入 GameRepository
- [ ] 实现获取游戏列表（分页）
- [ ] 实现根据 ID 获取游戏
- [ ] 实现搜索游戏
- [ ] 实现根据分类获取游戏
- [ ] 实现添加/更新/删除游戏（管理员功能）

##### 4. UserGameListService（用户游戏清单业务服务）
**接口：** `service/UserGameListService.java`  
**实现类：** `service/impl/UserGameListServiceImpl.java`

**TODO 清单：**
- [ ] 注入 UserGameListRepository 和 GameRepository
- [ ] 实现获取用户游戏清单
- [ ] 实现添加游戏到清单
- [ ] 实现更新游戏状态
- [ ] 实现从清单中移除游戏
- [ ] 实现更新游戏时长

详细说明请查看：[service/README.md](src/main/java/com/game/system/service/README.md)

---

### 第四步：完善 Controller 控制器层
位置：`backend/src/main/java/com/game/system/controller/`

#### 已创建的 Controller 需要完善

##### 1. UserController.java
文件位置：`controller/UserController.java`

**TODO 清单：**
- [ ] 注入 UserService
- [ ] 实现用户注册接口 `POST /api/user/register`
- [ ] 实现用户登录接口 `POST /api/user/login`
- [ ] 实现获取当前用户信息接口 `GET /api/user/info`
- [ ] 实现修改用户信息接口 `PUT /api/user/update`
- [ ] 实现修改密码接口 `PUT /api/user/password`

##### 2. GameController.java
文件位置：`controller/GameController.java`

**TODO 清单：**
- [ ] 注入 GameService
- [ ] 实现获取游戏列表接口 `GET /api/game/list`
- [ ] 实现根据 ID 获取游戏详情接口 `GET /api/game/{id}`
- [ ] 实现搜索游戏接口 `GET /api/game/search`
- [ ] 实现根据分类获取游戏接口 `GET /api/game/category/{category}`
- [ ] 实现添加游戏接口 `POST /api/game/add`（管理员）
- [ ] 实现更新游戏信息接口 `PUT /api/game/update/{id}`（管理员）
- [ ] 实现删除游戏接口 `DELETE /api/game/delete/{id}`（管理员）

##### 3. UserGameListController.java
文件位置：`controller/UserGameListController.java`

**TODO 清单：**
- [ ] 注入 UserGameListService
- [ ] 实现获取当前用户的游戏清单接口 `GET /api/user-game/list`
- [ ] 实现添加游戏到清单接口 `POST /api/user-game/add`
- [ ] 实现更新游戏状态接口 `PUT /api/user-game/update-status`
- [ ] 实现从清单中移除游戏接口 `DELETE /api/user-game/remove/{gameId}`
- [ ] 实现更新游戏时长接口 `PUT /api/user-game/update-playtime`
- [ ] 实现根据状态筛选游戏清单接口 `GET /api/user-game/list-by-status`

---

## 📊 开发进度追踪

### 实体类（Entity）
- [ ] User.java
- [ ] Role.java
- [ ] Game.java
- [ ] UserGameList.java
- [ ] Menu.java
- [ ] DictionaryType.java
- [ ] DictionaryData.java
- [ ] OperationLog.java

### Repository 接口
- [ ] UserRepository.java
- [ ] RoleRepository.java
- [ ] GameRepository.java
- [ ] UserGameListRepository.java
- [ ] MenuRepository.java
- [ ] DictionaryTypeRepository.java
- [ ] DictionaryDataRepository.java
- [ ] OperationLogRepository.java

### Service 层
- [ ] UserDetailsServiceImpl.java（完善）
- [ ] UserService + UserServiceImpl
- [ ] GameService + GameServiceImpl
- [ ] UserGameListService + UserGameListServiceImpl
- [ ] RoleService + RoleServiceImpl
- [ ] MenuService + MenuServiceImpl
- [ ] DictionaryService + DictionaryServiceImpl

### Controller 层
- [ ] UserController.java（完善）
- [ ] GameController.java（完善）
- [ ] UserGameListController.java（完善）
- [ ] RoleController.java（新建）
- [ ] MenuController.java（新建）
- [ ] DictionaryController.java（新建）

---

## 🎯 快速开始建议

### 最小可用系统（MVP）

如果你想快速看到登录功能运行，建议按以下顺序开发：

1. ✅ **创建 User 实体类**
2. ✅ **创建 Role 实体类**
3. ✅ **创建 UserRepository 接口**
4. ✅ **创建 RoleRepository 接口**
5. ✅ **完善 UserDetailsServiceImpl**
6. ✅ **创建 UserService 和 UserServiceImpl**
7. ✅ **完善 UserController（注册和登录接口）**
8. ✅ **测试登录流程**

完成以上 8 步后，你就可以：
- 注册新用户
- 用户登录获取 JWT Token
- 使用 Token 访问受保护的接口

---

## 📚 相关文档

- [entity/README.md](src/main/java/com/game/system/entity/README.md) - 实体类详细说明
- [repository/README.md](src/main/java/com/game/system/repository/README.md) - Repository 详细说明
- [service/README.md](src/main/java/com/game/system/service/README.md) - Service 详细说明
- [DATABASE_CONFIGURATION.md](DATABASE_CONFIGURATION.md) - 数据库配置说明
- [QUICKSTART.md](../QUICKSTART.md) - 快速开始指南
- [DEVELOPMENT_GUIDE.md](../DEVELOPMENT_GUIDE.md) - 完整开发指南

---

## ⚠️ 注意事项

1. **严格按照顺序开发**：Entity → Repository → Service → Controller
2. **每完成一个层次，及时测试**：不要等所有代码都写完再测试
3. **使用环境变量配置数据库**：不要在代码中硬编码数据库密码
4. **代码和注释使用中文**：面向初学者友好
5. **遇到问题及时记录**：便于后续排查和优化

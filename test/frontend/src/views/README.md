# 页面组件

此目录用于存放页面级组件。

## 与 components 的区别

- **components/**：可复用的通用组件（按钮、表单、表格等）
- **views/**：页面级组件（用户管理页面、角色管理页面等）

## 目录结构

```
views/
├── auth/                    # 认证相关页面
│   ├── Login.vue           # 登录页面
│   ├── Register.vue        # 注册页面
│   └── ForgotPassword.vue  # 忘记密码页面
├── dashboard/              # 仪表板
│   └── Dashboard.vue       # 仪表板首页
├── system/                 # 系统管理
│   ├── UserManagement.vue  # 用户管理
│   ├── RoleManagement.vue  # 角色管理
│   ├── MenuManagement.vue  # 菜单管理
│   └── DictManagement.vue  # 字典管理
├── game/                   # 游戏管理
│   ├── GameList.vue        # 游戏列表
│   └── GameDetail.vue      # 游戏详情
├── Home.vue                # 主页
├── NotFound.vue            # 404 页面
└── README.md
```

## 页面组件命名规范

1. 文件名使用大驼峰命名（PascalCase）
2. 页面名称应清晰表达用途
3. 组件内部也使用相同的名称

## 示例

### Login.vue - 登录页面

```vue
<template>
  <div class="login-container">
    <div class="login-box">
      <h1 class="login-title">4398游戏管理系统</h1>
      
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-item">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="formData.username"
            type="text"
            placeholder="请输入用户名"
            required
          />
        </div>
        
        <div class="form-item">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            required
          />
        </div>
        
        <button type="submit" class="login-button" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      
      <div class="login-footer">
        <a href="/register">注册账号</a>
        <a href="/forgot-password">忘记密码</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store'
import { authApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

// 表单数据
const formData = ref({
  username: '',
  password: ''
})

// 加载状态
const loading = ref(false)

/**
 * 处理登录
 */
async function handleLogin() {
  loading.value = true
  
  try {
    // 调用登录接口
    const response = await authApi.login(
      formData.value.username,
      formData.value.password
    )
    
    if (response.code === 200) {
      // 保存登录信息到 store
      userStore.login(response.data.token, response.data.userInfo)
      
      // 跳转到首页
      router.push('/')
    } else {
      alert('登录失败：' + response.message)
    }
  } catch (error) {
    console.error('登录错误', error)
    alert('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.login-title {
  margin: 0 0 30px 0;
  text-align: center;
  font-size: 24px;
  color: #333;
}

.login-form {
  margin-bottom: 20px;
}

.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.form-item input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-item input:focus {
  outline: none;
  border-color: #667eea;
}

.login-button {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 4px;
  background: #667eea;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
}

.login-button:hover {
  background: #5568d3;
}

.login-button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.login-footer {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.login-footer a {
  color: #667eea;
  text-decoration: none;
}

.login-footer a:hover {
  text-decoration: underline;
}
</style>
```

### UserManagement.vue - 用户管理页面

```vue
<template>
  <div class="user-management">
    <h2>用户管理</h2>
    
    <!-- 搜索栏 -->
    <div class="search-bar">
      <input
        v-model="searchKeyword"
        type="text"
        placeholder="搜索用户名或邮箱"
      />
      <button @click="handleSearch">搜索</button>
      <button @click="handleAdd">添加用户</button>
    </div>
    
    <!-- 用户列表 -->
    <div v-if="loading">加载中...</div>
    <table v-else class="user-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>邮箱</th>
          <th>角色</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in userList" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.email }}</td>
          <td>{{ user.roleName }}</td>
          <td>{{ user.status === 1 ? '启用' : '禁用' }}</td>
          <td>{{ user.createTime }}</td>
          <td>
            <button @click="handleEdit(user)">编辑</button>
            <button @click="handleDelete(user.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    
    <!-- 分页 -->
    <div class="pagination">
      <button @click="prevPage" :disabled="pageNumber === 1">上一页</button>
      <span>第 {{ pageNumber }} 页，共 {{ totalPages }} 页</span>
      <button @click="nextPage" :disabled="pageNumber === totalPages">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUser } from '@/composables/use-user'
import { usePagination } from '@/composables/use-pagination'

// 使用组合式函数
const { userList, loading, fetchUserList, deleteUser } = useUser()
const { pageNumber, totalPages, prevPage, nextPage } = usePagination()

// 搜索关键词
const searchKeyword = ref('')

/**
 * 组件挂载时获取用户列表
 */
onMounted(() => {
  fetchUserList()
})

/**
 * 处理搜索
 */
function handleSearch() {
  console.log('搜索:', searchKeyword.value)
  // 实现搜索逻辑
}

/**
 * 处理添加用户
 */
function handleAdd() {
  console.log('添加用户')
  // 打开添加用户对话框
}

/**
 * 处理编辑用户
 * @param user 用户信息
 */
function handleEdit(user: any) {
  console.log('编辑用户:', user)
  // 打开编辑用户对话框
}

/**
 * 处理删除用户
 * @param userId 用户ID
 */
async function handleDelete(userId: number) {
  const confirmed = confirm('确定要删除这个用户吗？')
  if (confirmed) {
    const success = await deleteUser(userId)
    if (success) {
      alert('删除成功')
    } else {
      alert('删除失败')
    }
  }
}
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.search-bar input {
  flex: 1;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.search-bar button {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: #409EFF;
  color: #fff;
  cursor: pointer;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
}

.user-table th,
.user-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid #ddd;
}

.user-table th {
  background: #f5f5f5;
  font-weight: bold;
}

.user-table button {
  margin-right: 8px;
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.pagination {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
```

## 注意事项

1. **页面组件应该是容器组件**：负责数据获取和业务逻辑
2. **展示组件放在 components**：可复用的 UI 组件
3. **保持页面组件简洁**：复杂逻辑应该提取到 composables
4. **统一命名规范**：便于维护和查找

# 组合式函数（Composables）

此目录用于存放 Vue 3 组合式函数，封装可复用的状态逻辑。

## 什么是 Composables

Composables 是 Vue 3 组合式 API 的核心模式，用于在多个组件之间共享有状态的逻辑。

## 与工具函数的区别

- **utils/**：无状态的纯函数（日期格式化、字符串处理）
- **composables/**：有状态的逻辑（响应式数据、生命周期）

## 目录结构

```
composables/
├── use-user.ts          # 用户相关逻辑
├── use-pagination.ts    # 分页逻辑
├── use-loading.ts       # 加载状态
├── use-form.ts          # 表单处理
└── README.md
```

## 命名规范

- 文件名：`use-xxx.ts`（kebab-case）
- 函数名：`useXxx()`（camelCase）
- 导出函数以 `use` 开头

## 示例

### use-user.ts - 用户管理逻辑

```typescript
/**
 * 用户管理组合式函数
 * 封装用户相关的状态和操作
 */
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { userApi } from '@/api'

/**
 * 用户信息接口
 */
interface User {
  id: number
  username: string
  email: string
  phone: string
  roleName: string
  status: number
  createTime: string
}

/**
 * 返回值接口
 */
interface UseUserReturn {
  userList: Ref<User[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  totalCount: Ref<number>
  fetchUserList: () => Promise<void>
  getUserById: (id: number) => Promise<User | null>
  createUser: (userData: any) => Promise<boolean>
  updateUser: (id: number, userData: any) => Promise<boolean>
  deleteUser: (id: number) => Promise<boolean>
}

/**
 * 用户管理组合式函数
 * @returns 用户状态和操作方法
 */
export function useUser(): UseUserReturn {
  // 用户列表
  const userList = ref<User[]>([])
  
  // 加载状态
  const loading = ref<boolean>(false)
  
  // 错误信息
  const error = ref<string | null>(null)
  
  // 总数
  const totalCount = ref<number>(0)
  
  /**
   * 获取用户列表
   */
  async function fetchUserList(): Promise<void> {
    loading.value = true
    error.value = null
    
    try {
      const response = await userApi.getUserList()
      
      if (response.code === 200) {
        userList.value = response.data.list
        totalCount.value = response.data.total
      } else {
        error.value = response.message
      }
    } catch (err) {
      error.value = '获取用户列表失败'
      console.error('获取用户列表错误', err)
    } finally {
      loading.value = false
    }
  }
  
  /**
   * 根据 ID 获取用户
   * @param id 用户 ID
   * @returns 用户信息，不存在返回 null
   */
  async function getUserById(id: number): Promise<User | null> {
    loading.value = true
    error.value = null
    
    try {
      const response = await userApi.getUserById(id)
      
      if (response.code === 200) {
        return response.data
      } else {
        error.value = response.message
        return null
      }
    } catch (err) {
      error.value = '获取用户信息失败'
      console.error('获取用户信息错误', err)
      return null
    } finally {
      loading.value = false
    }
  }
  
  /**
   * 创建用户
   * @param userData 用户数据
   * @returns 是否成功
   */
  async function createUser(userData: any): Promise<boolean> {
    loading.value = true
    error.value = null
    
    try {
      const response = await userApi.createUser(userData)
      
      if (response.code === 200) {
        // 重新获取用户列表
        await fetchUserList()
        return true
      } else {
        error.value = response.message
        return false
      }
    } catch (err) {
      error.value = '创建用户失败'
      console.error('创建用户错误', err)
      return false
    } finally {
      loading.value = false
    }
  }
  
  /**
   * 更新用户
   * @param id 用户 ID
   * @param userData 用户数据
   * @returns 是否成功
   */
  async function updateUser(id: number, userData: any): Promise<boolean> {
    loading.value = true
    error.value = null
    
    try {
      const response = await userApi.updateUser(id, userData)
      
      if (response.code === 200) {
        // 重新获取用户列表
        await fetchUserList()
        return true
      } else {
        error.value = response.message
        return false
      }
    } catch (err) {
      error.value = '更新用户失败'
      console.error('更新用户错误', err)
      return false
    } finally {
      loading.value = false
    }
  }
  
  /**
   * 删除用户
   * @param id 用户 ID
   * @returns 是否成功
   */
  async function deleteUser(id: number): Promise<boolean> {
    loading.value = true
    error.value = null
    
    try {
      const response = await userApi.deleteUser(id)
      
      if (response.code === 200) {
        // 重新获取用户列表
        await fetchUserList()
        return true
      } else {
        error.value = response.message
        return false
      }
    } catch (err) {
      error.value = '删除用户失败'
      console.error('删除用户错误', err)
      return false
    } finally {
      loading.value = false
    }
  }
  
  return {
    userList,
    loading,
    error,
    totalCount,
    fetchUserList,
    getUserById,
    createUser,
    updateUser,
    deleteUser
  }
}
```

### use-pagination.ts - 分页逻辑

```typescript
/**
 * 分页组合式函数
 * 封装分页相关的状态和操作
 */
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

/**
 * 返回值接口
 */
interface UsePaginationReturn {
  pageNumber: Ref<number>
  pageSize: Ref<number>
  totalCount: Ref<number>
  totalPages: ComputedRef<number>
  hasNextPage: ComputedRef<boolean>
  hasPrevPage: ComputedRef<boolean>
  setPage: (page: number) => void
  setPageSize: (size: number) => void
  setTotalCount: (total: number) => void
  nextPage: () => void
  prevPage: () => void
  goToFirstPage: () => void
  goToLastPage: () => void
}

/**
 * 分页组合式函数
 * @param initialPageSize 初始每页数量，默认 10
 * @returns 分页状态和操作方法
 */
export function usePagination(initialPageSize: number = 10): UsePaginationReturn {
  // 当前页码（从 1 开始）
  const pageNumber = ref<number>(1)
  
  // 每页数量
  const pageSize = ref<number>(initialPageSize)
  
  // 总记录数
  const totalCount = ref<number>(0)
  
  /**
   * 计算总页数
   */
  const totalPages = computed<number>(() => {
    if (totalCount.value === 0) {
      return 1
    }
    return Math.ceil(totalCount.value / pageSize.value)
  })
  
  /**
   * 是否有下一页
   */
  const hasNextPage = computed<boolean>(() => {
    return pageNumber.value < totalPages.value
  })
  
  /**
   * 是否有上一页
   */
  const hasPrevPage = computed<boolean>(() => {
    return pageNumber.value > 1
  })
  
  /**
   * 设置页码
   * @param page 页码
   */
  function setPage(page: number): void {
    if (page < 1) {
      pageNumber.value = 1
    } else if (page > totalPages.value) {
      pageNumber.value = totalPages.value
    } else {
      pageNumber.value = page
    }
  }
  
  /**
   * 设置每页数量
   * @param size 每页数量
   */
  function setPageSize(size: number): void {
    pageSize.value = size
    // 重置到第一页
    pageNumber.value = 1
  }
  
  /**
   * 设置总记录数
   * @param total 总记录数
   */
  function setTotalCount(total: number): void {
    totalCount.value = total
    
    // 如果当前页超过总页数，跳转到最后一页
    if (pageNumber.value > totalPages.value) {
      pageNumber.value = totalPages.value
    }
  }
  
  /**
   * 下一页
   */
  function nextPage(): void {
    if (hasNextPage.value) {
      pageNumber.value = pageNumber.value + 1
    }
  }
  
  /**
   * 上一页
   */
  function prevPage(): void {
    if (hasPrevPage.value) {
      pageNumber.value = pageNumber.value - 1
    }
  }
  
  /**
   * 跳转到第一页
   */
  function goToFirstPage(): void {
    pageNumber.value = 1
  }
  
  /**
   * 跳转到最后一页
   */
  function goToLastPage(): void {
    pageNumber.value = totalPages.value
  }
  
  return {
    pageNumber,
    pageSize,
    totalCount,
    totalPages,
    hasNextPage,
    hasPrevPage,
    setPage,
    setPageSize,
    setTotalCount,
    nextPage,
    prevPage,
    goToFirstPage,
    goToLastPage
  }
}
```

### use-loading.ts - 加载状态管理

```typescript
/**
 * 加载状态组合式函数
 * 封装加载状态的管理
 */
import { ref } from 'vue'
import type { Ref } from 'vue'

/**
 * 返回值接口
 */
interface UseLoadingReturn {
  loading: Ref<boolean>
  startLoading: () => void
  stopLoading: () => void
  withLoading: <T>(fn: () => Promise<T>) => Promise<T>
}

/**
 * 加载状态组合式函数
 * @returns 加载状态和操作方法
 */
export function useLoading(): UseLoadingReturn {
  // 加载状态
  const loading = ref<boolean>(false)
  
  /**
   * 开始加载
   */
  function startLoading(): void {
    loading.value = true
  }
  
  /**
   * 停止加载
   */
  function stopLoading(): void {
    loading.value = false
  }
  
  /**
   * 包装异步函数，自动管理加载状态
   * @param fn 异步函数
   * @returns 包装后的函数
   */
  async function withLoading<T>(fn: () => Promise<T>): Promise<T> {
    startLoading()
    try {
      const result = await fn()
      return result
    } finally {
      stopLoading()
    }
  }
  
  return {
    loading,
    startLoading,
    stopLoading,
    withLoading
  }
}
```

### use-form.ts - 表单处理

```typescript
/**
 * 表单处理组合式函数
 * 封装表单状态和验证逻辑
 */
import { ref, reactive } from 'vue'
import type { Ref, UnwrapNestedRefs } from 'vue'

/**
 * 表单错误接口
 */
interface FormErrors {
  [key: string]: string
}

/**
 * 返回值接口
 */
interface UseFormReturn<T> {
  formData: UnwrapNestedRefs<T>
  errors: Ref<FormErrors>
  isValid: Ref<boolean>
  validate: () => boolean
  setError: (field: string, message: string) => void
  clearError: (field: string) => void
  clearAllErrors: () => void
  resetForm: () => void
}

/**
 * 表单处理组合式函数
 * @param initialData 初始表单数据
 * @param validateFn 验证函数
 * @returns 表单状态和操作方法
 */
export function useForm<T extends object>(
  initialData: T,
  validateFn?: (data: T) => FormErrors
): UseFormReturn<T> {
  // 表单数据（响应式）
  const formData = reactive<T>({ ...initialData })
  
  // 错误信息
  const errors = ref<FormErrors>({})
  
  // 是否有效
  const isValid = ref<boolean>(true)
  
  /**
   * 验证表单
   * @returns 是否通过验证
   */
  function validate(): boolean {
    // 清空之前的错误
    errors.value = {}
    
    // 如果没有提供验证函数，直接返回 true
    if (!validateFn) {
      isValid.value = true
      return true
    }
    
    // 执行验证
    const validationErrors = validateFn(formData as T)
    
    // 保存错误信息
    errors.value = validationErrors
    
    // 判断是否有错误
    const hasErrors = Object.keys(validationErrors).length > 0
    isValid.value = !hasErrors
    
    return !hasErrors
  }
  
  /**
   * 设置字段错误
   * @param field 字段名
   * @param message 错误信息
   */
  function setError(field: string, message: string): void {
    errors.value[field] = message
    isValid.value = false
  }
  
  /**
   * 清除字段错误
   * @param field 字段名
   */
  function clearError(field: string): void {
    delete errors.value[field]
    
    // 如果没有错误了，设置为有效
    if (Object.keys(errors.value).length === 0) {
      isValid.value = true
    }
  }
  
  /**
   * 清除所有错误
   */
  function clearAllErrors(): void {
    errors.value = {}
    isValid.value = true
  }
  
  /**
   * 重置表单
   */
  function resetForm(): void {
    // 重置数据
    Object.assign(formData, initialData)
    
    // 清空错误
    clearAllErrors()
  }
  
  return {
    formData,
    errors,
    isValid,
    validate,
    setError,
    clearError,
    clearAllErrors,
    resetForm
  }
}
```

## 在组件中使用

```vue
<template>
  <div>
    <h2>用户管理</h2>
    
    <!-- 加载状态 -->
    <div v-if="loading">加载中...</div>
    
    <!-- 错误信息 -->
    <div v-if="error" class="error">{{ error }}</div>
    
    <!-- 用户列表 -->
    <table v-else>
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>邮箱</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in userList" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.email }}</td>
          <td>
            <button @click="handleDelete(user.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    
    <!-- 分页 -->
    <div class="pagination">
      <button @click="prevPage" :disabled="!hasPrevPage">上一页</button>
      <span>第 {{ pageNumber }} 页，共 {{ totalPages }} 页</span>
      <button @click="nextPage" :disabled="!hasNextPage">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUser } from '@/composables/use-user'
import { usePagination } from '@/composables/use-pagination'

// 使用用户管理逻辑
const {
  userList,
  loading,
  error,
  totalCount,
  fetchUserList,
  deleteUser
} = useUser()

// 使用分页逻辑
const {
  pageNumber,
  pageSize,
  totalPages,
  hasNextPage,
  hasPrevPage,
  nextPage,
  prevPage,
  setTotalCount
} = usePagination(10)

/**
 * 组件挂载时获取用户列表
 */
onMounted(async () => {
  await fetchUserList()
  setTotalCount(totalCount.value)
})

/**
 * 处理删除用户
 */
async function handleDelete(userId: number) {
  const confirmed = confirm('确定要删除这个用户吗？')
  if (confirmed) {
    const success = await deleteUser(userId)
    if (success) {
      alert('删除成功')
    }
  }
}
</script>
```

## 最佳实践

1. **单一职责**：每个 composable 只负责一件事
2. **返回对象**：使用对象解构，便于选择性使用
3. **响应式数据**：使用 ref/reactive 管理状态
4. **命名规范**：use 开头，清晰表达用途
5. **类型安全**：使用 TypeScript 定义接口
6. **可复用性**：设计通用的 composable

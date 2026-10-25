<template>
  <div class="login-container">
    <div class="login-box">
      <h1>4398游戏管理系统</h1>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            type="text"
            id="username"
            v-model="formData.username"
            required
            placeholder="请输入用户名"
          />
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input
            type="password"
            id="password"
            v-model="formData.password"
            required
            placeholder="请输入密码"
          />
        </div>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div v-if="message" class="message" :class="messageType">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { userApi } from '@/api'
import { storage } from '@/utils/common'

// 表单数据
const formData = reactive({
  username: '',
  password: ''
})

// 状态
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')

// 显示消息
const showMessage = (msg: string, type: 'success' | 'error' = 'success') => {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

// 处理登录
const handleSubmit = async () => {
  try {
    loading.value = true

    const response = await userApi.login({
      username: formData.username,
      password: formData.password
    })

    if (response.code === 0 && response.data) {
      // 保存token和用户信息
      storage.set('token', response.data.token)
      storage.set('userInfo', response.data.userInfo)

      showMessage('登录成功！正在跳转...', 'success')

      // 跳转到主页
      setTimeout(() => {
        window.location.href = '/views/home.html'
      }, 1000)
    } else {
      showMessage(response.msg || '登录失败', 'error')
    }
  } catch (error: any) {
    console.error('登录失败:', error)
    showMessage(error.message || '登录失败，请检查网络连接', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0a0d12 0%, #161d2b 100%);
}

.login-box {
  background: #0f131c;
  padding: 3rem;
  border-radius: 1rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  width: 100%;
  max-width: 400px;
  border: 1px solid #1e2636;
}

h1 {
  color: #38bdf8;
  text-align: center;
  margin-bottom: 2rem;
  font-size: clamp(1.5rem, 4vw, 2rem);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.form-group {
  margin-bottom: 1.5rem;
}

label {
  display: block;
  color: #94a3b8;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
}

input {
  width: 100%;
  padding: 0.75rem 1rem;
  background: #161d2b;
  border: 1px solid #1e2636;
  border-radius: 0.5rem;
  color: #e2e8f0;
  font-size: 1rem;
  transition: all 0.2s;
}

input:focus {
  outline: none;
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.1);
}

input::placeholder {
  color: #475569;
}

.btn-primary {
  width: 100%;
  padding: 0.875rem;
  background: #38bdf8;
  color: #0a0d12;
  border: none;
  border-radius: 999px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 0.5rem;
}

.btn-primary:hover:not(:disabled) {
  background: #0ea5e9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(56, 189, 248, 0.3);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.message {
  margin-top: 1.5rem;
  padding: 0.875rem;
  border-radius: 0.5rem;
  text-align: center;
  font-size: 0.875rem;
  font-weight: 500;
  animation: slideIn 0.3s ease-out;
}

.message.success {
  background: rgba(110, 231, 183, 0.1);
  color: #6ee7b7;
  border: 1px solid rgba(110, 231, 183, 0.2);
}

.message.error {
  background: rgba(248, 113, 113, 0.1);
  color: #f87171;
  border: 1px solid rgba(248, 113, 113, 0.2);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

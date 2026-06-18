<template>
  <div class="login-wrapper">
    <!-- 动态网格背景 -->
    <div class="grid-bg"></div>

    <div class="login-card">
      <!-- Logo -->
      <div class="logo-area">
        <span class="logo-icon">⬡</span>
        <h1>AGNES AI</h1>
      </div>

      <p class="subtitle">{{ isLogin ? '登录以继续' : '注册新账号' }}</p>

      <!-- 表单 -->
      <div class="form-area">
        <div class="input-group">
          <span class="input-icon">📱</span>
          <input
              v-model="phone"
              type="tel"
              placeholder="手机号"
              class="auth-input"
              maxlength="11"
              inputmode="numeric"
          />
        </div>

        <div class="input-group">
          <span class="input-icon">🔑</span>
          <input
              v-model="apiKey"
              type="password"
              placeholder="API 密钥"
              class="auth-input"
          />
        </div>

        <button class="auth-btn" @click="submit" :disabled="loading">
          <span v-if="!loading">{{ isLogin ? '登录' : '注册' }}</span>
          <span v-else>⏳ 处理中...</span>
        </button>
      </div>

      <p class="switch-text" @click="toggleMode">
        {{ isLogin ? '没有账号？去注册' : '已有账号？去登录' }}
      </p>

      <!-- 新增：引导获取 API 密钥 -->
      <div class="api-hint">
        🔗 还没有 API 密钥？
        <a href="https://apihub.agnes-ai.com" target="_blank" rel="noopener noreferrer">
          前往获取
        </a>
      </div>

      <transition name="fade">
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const isLogin = ref(true)
const phone = ref('')
const apiKey = ref('')
const loading = ref(false)
const errorMsg = ref('')

function toggleMode() {
  isLogin.value = !isLogin.value
  errorMsg.value = ''
}

async function submit() {
  errorMsg.value = ''
  if (!phone.value || !/^1\d{10}$/.test(phone.value)) {
    errorMsg.value = '请输入有效的手机号'
    return
  }
  if (!apiKey.value.trim()) {
    errorMsg.value = '请输入 API 密钥'
    return
  }

  loading.value = true
  const url = isLogin.value ? '/api/auth/login' : '/api/auth/register'
  try {
    const res = await axios.post(url, { phone: phone.value, apiKey: apiKey.value })
    if (isLogin.value) {
      localStorage.setItem('token', res.data.token)
      router.push('/')
    } else {
      alert('注册成功，请登录')
      isLogin.value = true
      phone.value = ''
      apiKey.value = ''
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.message || err.response?.data || '请求失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ========== 全局自适应 ========== */
.login-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #0a0c0f;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

.grid-bg {
  position: absolute;
  inset: 0;
  background-image:
      linear-gradient(rgba(0, 255, 255, 0.08) 1px, transparent 1px),
      linear-gradient(90deg, rgba(0, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  animation: gridMove 8s linear infinite;
  pointer-events: none;
}

@keyframes gridMove {
  0% { background-position: 0 0, 0 0; }
  100% { background-position: 40px 40px, -40px 40px; }
}

/* ========== 登录卡片 ========== */
.login-card {
  width: 100%;
  max-width: 420px;
  margin: 0 20px;
  background: rgba(10, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow: 0 0 30px rgba(0, 255, 255, 0.2), inset 0 0 30px rgba(0, 255, 255, 0.05);
  border-radius: 16px;
  padding: clamp(20px, 5vw, 40px);
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
}

/* Logo */
.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.logo-icon {
  font-size: clamp(1.8rem, 8vw, 2.2rem);
  color: #0ff;
  filter: drop-shadow(0 0 10px #0ff);
  animation: glowPulse 2s infinite alternate;
}

@keyframes glowPulse {
  from { filter: drop-shadow(0 0 5px #0ff); }
  to   { filter: drop-shadow(0 0 15px #0ff) drop-shadow(0 0 30px #0ff); }
}

h1 {
  font-size: clamp(1.4rem, 6vw, 1.8rem);
  font-weight: 600;
  color: #0ff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
  letter-spacing: 2px;
  margin: 0;
}

.subtitle {
  color: rgba(0, 255, 255, 0.6);
  font-size: clamp(0.8rem, 3vw, 1rem);
  margin-bottom: 20px;
}

/* 表单 */
.form-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 12px;
  font-size: 1.1rem;
  pointer-events: none;
}

.auth-input {
  width: 100%;
  padding: 12px 12px 12px 42px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 25px;
  color: #fff;
  font-size: clamp(0.9rem, 3.5vw, 1rem);
  outline: none;
  transition: all 0.3s;
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.15);
}

.auth-input:focus {
  border-color: #0ff;
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.5);
}

.auth-input::placeholder {
  color: rgba(0, 255, 255, 0.4);
}

/* 按钮 */
.auth-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 25px;
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  color: #fff;
  font-weight: bold;
  font-size: 1rem;
  cursor: pointer;
  box-shadow: 0 0 15px rgba(0, 114, 255, 0.6);
  transition: all 0.3s;
  margin-top: 6px;
}

.auth-btn:hover:not(:disabled) {
  transform: scale(1.02);
  box-shadow: 0 0 25px rgba(0, 114, 255, 0.9);
}

.auth-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 切换文字 */
.switch-text {
  margin-top: 18px;
  color: rgba(0, 255, 255, 0.6);
  cursor: pointer;
  font-size: 0.9rem;
  transition: color 0.3s;
  text-align: center;
}

.switch-text:hover {
  color: #0ff;
  text-shadow: 0 0 8px rgba(0, 255, 255, 0.5);
}

/* 错误消息 */
.error-msg {
  width: 100%;
  margin-top: 16px;
  padding: 10px;
  background: rgba(255, 50, 50, 0.15);
  border: 1px solid rgba(255, 50, 50, 0.5);
  border-radius: 8px;
  color: #ff5252;
  text-align: center;
  font-size: 0.9rem;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ========== 平板自适应 ========== */
@media (max-width: 768px) {
  .login-card {
    max-width: 400px;
    margin: 0 16px;
    border-radius: 12px;
  }
}

/* ========== 手机自适应 ========== */
@media (max-width: 480px) {
  .login-wrapper {
    align-items: stretch; /* 让卡片上下撑开 */
  }

  .login-card {
    margin: 0;
    max-width: 100%;
    height: 100%;
    border-radius: 0;
    border: none;
    box-shadow: none;
    backdrop-filter: blur(10px);
    justify-content: center;
    padding: 24px 20px;
  }

  .grid-bg {
    background-size: 30px 30px; /* 网格缩小一点 */
  }
}

.api-hint {
  margin-top: 14px;
  font-size: 0.85rem;
  color: rgba(0, 255, 255, 0.6);
  text-align: center;
}

.api-hint a {
  color: #0ff;
  text-decoration: none;
  font-weight: bold;
  margin-left: 4px;
  transition: text-shadow 0.3s;
}

.api-hint a:hover {
  text-shadow: 0 0 10px #0ff;
}
</style>
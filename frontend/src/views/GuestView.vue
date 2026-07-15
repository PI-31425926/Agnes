<template>
  <div class="app-wrapper">
    <div class="grid-bg"></div>
    <div class="main-container">
      <!-- 头部 -->
      <header class="chat-header">
        <div class="logo">
          <span class="logo-icon">⬡</span>
          <h1>AGNES AI · 游客模式</h1>
        </div>
        <div class="header-right">
          <span class="guest-badge">👤 游客</span>
          <router-link to="/login" class="login-link">登录 / 注册</router-link>
        </div>
      </header>

      <!-- 引导内容 -->
      <div class="guide-body">
        <div class="welcome-card">
          <span class="welcome-icon">👋</span>
          <h2>欢迎体验 AGNES AI</h2>
          <p class="welcome-desc">
            游客模式下您可以浏览页面，但无法使用 AI 功能。<br>
            注册登录后即可使用聊天、图片生成、视频生成等全部功能。
          </p>
          <div class="feature-list">
            <div class="feature-item">
              <span class="feature-icon">💬</span>
              <span>AI 对话</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">🎨</span>
              <span>文生图</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">🖼️</span>
              <span>图生图</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">🎬</span>
              <span>视频生成</span>
            </div>
          </div>
          <router-link to="/login" class="cta-btn">立即登录使用全部功能</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

// 设置游客模式标志
onMounted(() => {
  localStorage.setItem('isGuest', 'true')
})
</script>

<style scoped>
.app-wrapper {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: #0a0c0f;
  display: flex;
  justify-content: center;
  align-items: center;
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

.main-container {
  width: 100%;
  max-width: 800px;
  height: 90dvh;
  margin: 0 16px;
  background: rgba(10, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow: 0 0 30px rgba(0, 255, 255, 0.2), inset 0 0 30px rgba(0, 255, 255, 0.05);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #0ff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}
.logo {
  display: flex;
  align-items: center;
  gap: 6px;
}
.logo-icon {
  font-size: 1.2rem;
  animation: glowPulse 2s infinite alternate;
}
@keyframes glowPulse {
  from { filter: drop-shadow(0 0 5px #0ff); }
  to   { filter: drop-shadow(0 0 15px #0ff) drop-shadow(0 0 30px #0ff); }
}
.chat-header h1 {
  font-size: clamp(1rem, 4vw, 1.4rem);
  font-weight: 600;
  letter-spacing: 1px;
  margin: 0;
  color: #0ff;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.guest-badge {
  color: #0f0;
  font-size: 0.85rem;
  text-shadow: 0 0 8px #0f0;
}
.login-link {
  color: #0ff;
  text-decoration: none;
  font-size: 0.85rem;
  padding: 4px 12px;
  border: 1px solid rgba(0,255,255,0.3);
  border-radius: 15px;
  transition: all 0.3s;
}
.login-link:hover {
  background: rgba(0,255,255,0.1);
  box-shadow: 0 0 10px rgba(0,255,255,0.5);
}

/* 引导内容 */
.guide-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow-y: auto;
}
.welcome-card {
  text-align: center;
  max-width: 480px;
}
.welcome-icon {
  font-size: 3rem;
  display: block;
  margin-bottom: 12px;
}
.welcome-card h2 {
  color: #0ff;
  font-size: 1.5rem;
  margin: 0 0 12px;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}
.welcome-desc {
  color: rgba(0, 255, 255, 0.7);
  font-size: 0.95rem;
  line-height: 1.6;
  margin-bottom: 24px;
}
.feature-list {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 28px;
  flex-wrap: wrap;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: rgba(0, 255, 255, 0.8);
  font-size: 0.9rem;
  padding: 6px 12px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 20px;
  background: rgba(0, 255, 255, 0.05);
}
.feature-icon {
  font-size: 1.1rem;
}
.cta-btn {
  display: inline-block;
  padding: 12px 32px;
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  color: #fff;
  font-weight: bold;
  font-size: 1rem;
  border-radius: 25px;
  text-decoration: none;
  box-shadow: 0 0 15px rgba(0, 114, 255, 0.6);
  transition: all 0.3s;
}
.cta-btn:hover {
  box-shadow: 0 0 25px rgba(0, 114, 255, 0.9);
  transform: scale(1.02);
}

@media (max-width: 768px) {
  .main-container { height: 95dvh; margin: 0 8px; border-radius: 12px; }
  .feature-list { gap: 8px; }
  .feature-item { font-size: 0.8rem; padding: 4px 10px; }
}
@media (max-width: 480px) {
  .main-container { height: 100dvh; margin: 0; border-radius: 0; border-left: none; border-right: none; }
  .chat-header h1 { font-size: 1.1rem; }
  .welcome-card h2 { font-size: 1.2rem; }
}
</style>

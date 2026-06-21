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
          <router-link to="/login" class="login-link">登录</router-link>
        </div>
      </header>

      <!-- 限流提示横幅 -->
      <div v-if="rateLimited" class="rate-limit-banner">
        ⏳ 游客模式每分钟只能使用一次，请稍后再试或 <router-link to="/login">登录</router-link>
      </div>

      <!-- 对话界面 -->
      <div class="chat-body">
        <div class="chat-box" ref="chatBox">
          <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['message-row', msg.role]">
            <div class="bubble">
              <span class="content">{{ msg.content }}</span>
              <!-- 朗读按钮（仅AI消息显示） -->
              <button
                  v-if="msg.role === 'assistant'"
                  class="speak-btn"
                  @click="toggleSpeak(idx, msg.content)"
                  :title="speakingIndex === idx ? '停止朗读' : '朗读此消息'"
              >
                {{ speakingIndex === idx ? '⏹️' : '🔊' }}
              </button>
            </div>
          </div>
        </div>

        <div class="input-area">
          <input
              v-model="chatInput"
              @keydown.enter="handleChatEnter"
              @compositionstart="isChatComposing = true"
              @compositionend="isChatComposing = false"
              placeholder="输入消息..."
              :disabled="rateLimited"
          />
          <button @click="sendChat" :disabled="rateLimited || sending">
            <span class="btn-icon" v-if="!sending">▶</span>
            <span class="btn-icon" v-else>⏳</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import axios from 'axios'

const chatMessages = ref([])
const chatInput = ref('')
const chatBox = ref(null)
const sending = ref(false)
const rateLimited = ref(false)
const isChatComposing = ref(false)

// TTS 朗读相关
const speakingIndex = ref(-1)

// 处理 Enter 发送
function handleChatEnter(e) {
  if (isChatComposing.value) return
  e.preventDefault()
  sendChat()
}

// 发送消息
async function sendChat() {
  const text = chatInput.value.trim()
  if (!text || sending.value || rateLimited.value) return

  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  const aiMessage = { role: 'assistant', content: '' }
  chatMessages.value.push(aiMessage)
  const aiIndex = chatMessages.value.length - 1

  sending.value = true
  try {
    const res = await axios.post('/api/guest/chat', { message: text })
    // 游客接口返回格式：{ reply: "xxx" }
    if (res.data && res.data.reply) {
      chatMessages.value[aiIndex].content = res.data.reply
    } else {
      chatMessages.value[aiIndex].content = '无回复'
    }
  } catch (e) {
    if (e.response && e.response.status === 429) {
      rateLimited.value = true
      chatMessages.value[aiIndex].content = '游客模式每分钟只能使用一次，请稍后再试或登录。'
    } else {
      chatMessages.value[aiIndex].content = '请求失败：' + (e.response?.data?.message || e.message)
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

// TTS 朗读
function toggleSpeak(idx, text) {
  if (speakingIndex.value === idx) {
    speechSynthesis.cancel()
    speakingIndex.value = -1
    return
  }
  speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.onstart = () => { speakingIndex.value = idx }
  utterance.onend = () => { speakingIndex.value = -1 }
  utterance.onerror = () => { speakingIndex.value = -1 }
  speechSynthesis.speak(utterance)
}

onMounted(() => {
  // 游客进入时，可以检查是否被限流（不主动检查，由请求触发）
})

onUnmounted(() => {
  speechSynthesis.cancel()
})
</script>

<style scoped>
/* ===== 整体复用主风格 ===== */
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

/* 头部 */
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

/* 限流横幅 */
.rate-limit-banner {
  background: rgba(255, 200, 0, 0.1);
  border-bottom: 1px solid rgba(255, 200, 0, 0.4);
  color: #ffcc00;
  text-align: center;
  padding: 8px;
  font-size: 0.9rem;
}
.rate-limit-banner a {
  color: #0ff;
  font-weight: bold;
}

/* 对话区域 */
.chat-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-box::-webkit-scrollbar { width: 4px; }
.chat-box::-webkit-scrollbar-track { background: transparent; }
.chat-box::-webkit-scrollbar-thumb { background: #0ff; border-radius: 10px; box-shadow: 0 0 6px #0ff; }

.message-row { display: flex; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }

.bubble {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: clamp(0.85rem, 3vw, 1rem);
  line-height: 1.5;
  word-break: break-word;
  position: relative;
}
.message-row.user .bubble {
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.3);
  box-shadow: 0 0 15px rgba(0,114,255,0.6);
  border-bottom-right-radius: 4px;
}
.message-row.assistant .bubble {
  background: rgba(0,255,255,0.05);
  color: #e0f7ff;
  border: 1px solid rgba(0,255,255,0.4);
  box-shadow: 0 0 15px rgba(0,255,255,0.2);
  border-bottom-left-radius: 4px;
  backdrop-filter: blur(5px);
}

/* 输入区 */
.input-area {
  padding: 10px 12px;
  border-top: 1px solid rgba(0,255,255,0.2);
  display: flex;
  gap: 8px;
}
.input-area input {
  flex: 1;
  padding: 10px 16px;
  background: rgba(0,10,20,0.8);
  border: 1px solid rgba(0,255,255,0.4);
  border-radius: 25px;
  color: #fff;
  font-size: 0.95rem;
  outline: none;
  transition: all 0.3s;
  box-shadow: 0 0 10px rgba(0,255,255,0.2);
  min-width: 0;
}
.input-area input:focus {
  border-color: #0ff;
  box-shadow: 0 0 20px rgba(0,255,255,0.5), 0 0 40px rgba(0,255,255,0.2);
}
.input-area input:disabled { opacity: 0.5; }
.input-area button {
  width: 44px; height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  border: none;
  color: #fff;
  font-size: 1rem;
  cursor: pointer;
  box-shadow: 0 0 15px rgba(0,114,255,0.6);
  transition: transform 0.2s, box-shadow 0.3s;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.input-area button:hover:not(:disabled) { transform: scale(1.05); box-shadow: 0 0 25px rgba(0,114,255,0.9); }
.input-area button:disabled { opacity: 0.6; cursor: not-allowed; }

/* 朗读按钮 */
.speak-btn {
  position: absolute;
  top: 2px; right: 2px;
  background: rgba(0,255,255,0.1);
  border: 1px solid rgba(0,255,255,0.3);
  color: #0ff;
  font-size: 0.7rem;
  width: 22px; height: 22px;
  border-radius: 50%;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.3s;
  backdrop-filter: blur(4px);
}
.speak-btn:hover { background: rgba(0,255,255,0.25); box-shadow: 0 0 8px #0ff; }

/* 响应式 */
@media (max-width: 768px) {
  .main-container { height: 95dvh; margin: 0 8px; border-radius: 12px; }
  .chat-box { padding: 12px 8px; }
  .bubble { max-width: 90%; padding: 8px 12px; }
}
@media (max-width: 480px) {
  .main-container { height: 100dvh; margin: 0; border-radius: 0; border-left: none; border-right: none; }
  .chat-header h1 { font-size: 1.1rem; }
  .input-area { padding: 8px 10px; }
}
</style>
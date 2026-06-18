<template>
  <div class="app-wrapper">
    <div class="grid-bg"></div>

    <div class="main-container">
      <!-- 头部（三个标签） -->
      <header class="chat-header">
        <div class="logo">
          <span class="logo-icon">⬡</span>
          <h1>AGNES AI</h1>
        </div>
        <div class="tabs">
          <button
              :class="['tab-btn', { active: currentTab === 'chat' }]"
              @click="currentTab = 'chat'"
          >💬 对话</button>
          <button
              :class="['tab-btn', { active: currentTab === 'text2img' }]"
              @click="currentTab = 'text2img'"
          >🎨 文生图</button>
          <button
              :class="['tab-btn', { active: currentTab === 'img2img' }]"
              @click="currentTab = 'img2img'"
          >🖼️ 图生图</button>
          <button
              :class="['tab-btn', { active: currentTab === 'video' }]"
              @click="currentTab = 'video'"
          >🎬 视频</button>
        </div>
        <span class="status">● 在线</span>
      </header>

      <!-- 对话界面 -->
      <div v-if="currentTab === 'chat'" class="chat-body">
        <div class="chat-box" ref="chatBox">
          <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['message-row', msg.role]">
            <div class="bubble">
              <span class="content">{{ msg.content }}</span>
            </div>
          </div>
        </div>
        <div class="input-area">
          <input
              v-model="chatInput"
              @keyup.enter="sendChat"
              placeholder="输入消息..."
          />
          <button @click="sendChat">
            <span class="btn-icon">▶</span>
          </button>
        </div>
      </div>

      <!-- 文生图界面（原“生图”功能） -->
      <div v-if="currentTab === 'text2img'" class="image-body">
        <div class="image-panel">
          <div class="prompt-area">
            <textarea
                v-model="text2imgPrompt"
                placeholder="请输入图片描述，例如：白毛红瞳双马尾萝莉"
                rows="3"
            ></textarea>
            <div class="options-row">
              <select v-model="text2imgSize">
                <option value="1024x768">1024x768（横版）</option>
                <option value="768x1024">768x1024（竖版）</option>
                <option value="1024x1024">1024x1024（方形）</option>
              </select>
              <button class="generate-btn" @click="generateText2img" :disabled="text2imgGenerating">
                <span v-if="!text2imgGenerating">⚡ 生成</span>
                <span v-else>⏳ 生成中...</span>
              </button>
            </div>
          </div>
          <div v-if="text2imgResult" class="result-area">
            <img :src="text2imgResult" alt="生成的图片" class="result-image"/>
            <div class="result-actions">
              <a :href="text2imgResult" target="_blank" class="action-link">🔗 新窗口打开</a>
            </div>
          </div>
          <div v-else class="empty-state">
            <span>👆 输入描述后点击生成</span>
          </div>
        </div>
      </div>

      <!-- 图生图界面 -->
      <div v-if="currentTab === 'img2img'" class="image-body">
        <div class="image-panel">
          <div class="upload-row">
            <label class="upload-btn">
              📁 选择图片
              <input type="file" accept="image/*" @change="handleFileUpload" hidden />
            </label>
            <div v-if="uploadedImagePreview" class="preview-box">
              <img :src="uploadedImagePreview" class="preview-img" />
              <button class="remove-btn" @click="clearUploadedImage">✕</button>
            </div>
          </div>
          <div class="prompt-area">
            <textarea
                v-model="img2imgPrompt"
                placeholder="描述想要的修改，例如：让物体变成哑光黑色，保留原有构图"
                rows="2"
            ></textarea>
            <div class="options-row">
              <select v-model="img2imgSize">
                <option value="1024x768">1024x768（横版）</option>
                <option value="768x1024">768x1024（竖版）</option>
                <option value="1024x1024">1024x1024（方形）</option>
              </select>
              <button
                  class="generate-btn"
                  @click="generateImg2img"
                  :disabled="!uploadedImageBase64 || img2imgGenerating"
              >
                <span v-if="!img2imgGenerating">⚡ 生成</span>
                <span v-else>⏳ 生成中...</span>
              </button>
            </div>
          </div>
          <div v-if="img2imgResult" class="result-area">
            <img :src="img2imgResult" alt="生成的图片" class="result-image"/>
            <div class="result-actions">
              <a :href="img2imgResult" target="_blank" class="action-link">🔗 新窗口打开</a>
            </div>
          </div>
          <div v-else class="empty-state">
            <span>🖼️ 上传图片并输入描述</span>
          </div>
        </div>
      </div>
      <!-- 视频生成界面 -->
      <div v-if="currentTab === 'video'" class="image-body">
        <div class="image-panel">
          <div class="prompt-area">
      <textarea
          v-model="videoPrompt"
          placeholder="描述视频内容，例如：A cinematic shot of a cat walking on the beach at sunset..."
          rows="3"
      ></textarea>
            <div class="options-row">
              <div class="param-group">
                <label>宽</label>
                <input type="number" v-model.number="videoWidth" class="param-input" />
              </div>
              <div class="param-group">
                <label>高</label>
                <input type="number" v-model.number="videoHeight" class="param-input" />
              </div>
              <div class="param-group">
                <label>帧数</label>
                <input type="number" v-model.number="videoNumFrames" class="param-input" />
              </div>
              <div class="param-group">
                <label>帧率</label>
                <input type="number" v-model.number="videoFrameRate" class="param-input" />
              </div>
              <button
                  class="generate-btn"
                  @click="startVideoGeneration"
                  :disabled="videoGenerating"
              >
                <span v-if="!videoGenerating">⚡ 生成</span>
                <span v-else>⏳ 生成中...</span>
              </button>
            </div>
          </div>

          <!-- 进度和状态 -->
          <div v-if="videoError" class="error-message">{{ videoError }}</div>
          <div v-if="videoGenerating" class="progress-area">
            <span>状态：{{ videoStatus }}，进度：{{ videoProgress }}%</span>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: videoProgress + '%' }"></div>
            </div>
          </div>
          <div v-if="videoUrl" class="result-area">
            <video :src="videoUrl" controls class="result-video"></video>
            <div class="result-actions">
              <a :href="videoUrl" target="_blank" class="action-link">🔗 新窗口打开</a>
            </div>
          </div>
          <div v-if="!videoUrl && !videoGenerating && !videoError" class="empty-state">
            <span>🎬 输入描述后生成视频</span>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

// 当前标签
const currentTab = ref('chat')

// ==================== 对话 ====================
const chatMessages = ref([])
const chatInput = ref('')
const chatBox = ref(null)

async function sendChat() {
  const text = chatInput.value.trim()
  if (!text) return
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text })
    })
    if (!res.ok) throw new Error('网络错误')
    const data = await res.json()
    chatMessages.value.push({ role: 'assistant', content: data.reply })
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '请求失败：' + e.message })
  }
  await nextTick()
  chatBox.value.scrollTop = chatBox.value.scrollHeight
}

watch(currentTab, async (tab) => {
  if (tab === 'chat') {
    await nextTick()
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  }
})

// ==================== 文生图 ====================
const text2imgPrompt = ref('')
const text2imgSize = ref('1024x768')
const text2imgResult = ref(null)
const text2imgGenerating = ref(false)

async function generateText2img() {
  const prompt = text2imgPrompt.value.trim()
  if (!prompt) return
  text2imgGenerating.value = true
  text2imgResult.value = null
  try {
    const res = await fetch('/api/image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt, size: text2imgSize.value })
    })
    if (!res.ok) throw new Error('生成失败')
    const data = await res.json()
    text2imgResult.value = data.url
  } catch (e) {
    alert('图片生成失败：' + e.message)
  } finally {
    text2imgGenerating.value = false
  }
}

// ==================== 图生图 ====================
const img2imgPrompt = ref('')
const img2imgSize = ref('1024x768')
const img2imgResult = ref(null)
const img2imgGenerating = ref(false)
const uploadedImagePreview = ref(null)      // 预览用 data URL
const uploadedImageBase64 = ref(null)       // 完整的 data URI (带前缀)

function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (event) => {
    // result 是 data:image/png;base64,... 或 data:image/jpeg;base64,...
    const dataUri = event.target.result
    uploadedImagePreview.value = dataUri
    uploadedImageBase64.value = dataUri
  }
  reader.readAsDataURL(file)  // 自动生成带前缀的 Base64
}

function clearUploadedImage() {
  uploadedImagePreview.value = null
  uploadedImageBase64.value = null
  // 清除 file input
  const fileInput = document.querySelector('input[type="file"]')
  if (fileInput) fileInput.value = ''
}

async function generateImg2img() {
  if (!uploadedImageBase64.value) return
  const prompt = img2imgPrompt.value.trim()
  img2imgGenerating.value = true
  img2imgResult.value = null
  try {
    const res = await fetch('/api/image/to-image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt,
        size: img2imgSize.value,
        imageBase64: uploadedImageBase64.value
      })
    })
    if (!res.ok) throw new Error('生成失败')
    const data = await res.json()
    img2imgResult.value = data.url
  } catch (e) {
    alert('图生图失败：' + e.message)
  } finally {
    img2imgGenerating.value = false
  }
}

// ==================== 视频生成 ====================
const videoPrompt = ref('')
const videoWidth = ref(1152)
const videoHeight = ref(768)
const videoNumFrames = ref(121)
const videoFrameRate = ref(24)

const videoGenerating = ref(false)
const videoTaskId = ref('')
const videoId = ref('')
const videoStatus = ref('')
const videoProgress = ref(0)
const videoUrl = ref('')
const videoError = ref('')
let videoPollingTimer = null

async function startVideoGeneration() {
  const prompt = videoPrompt.value.trim()
  if (!prompt) return

  videoGenerating.value = true
  videoUrl.value = ''
  videoError.value = ''
  videoProgress.value = 0

  try {
    // 1. 创建任务
    const createRes = await fetch('/api/video/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt,
        width: videoWidth.value,
        height: videoHeight.value,
        numFrames: videoNumFrames.value,
        frameRate: videoFrameRate.value
      })
    })
    const createData = await createRes.json()
    if (!createRes.ok || createData.code !== 200) {
      videoError.value = createData.message || '创建视频任务失败'
      videoGenerating.value = false
      return
    }

    videoTaskId.value = createData.data.taskId
    videoId.value = createData.data.videoId
    videoStatus.value = createData.data.status
    videoProgress.value = createData.data.progress

    // 2. 开始轮询
    pollVideoStatus()
  } catch (e) {
    videoError.value = '网络错误：' + e.message
    videoGenerating.value = false
  }
}

function pollVideoStatus() {
  videoPollingTimer = setInterval(async () => {
    try {
      const res = await fetch(`/api/video/status?video_id=${videoId.value}`)
      const data = await res.json()
      if (res.ok && data.code === 200) {
        videoStatus.value = data.data.status
        videoProgress.value = data.data.progress

        if (data.data.status === 'completed') {
          clearInterval(videoPollingTimer)
          videoGenerating.value = false
          videoUrl.value = data.data.url
        } else if (data.data.status === 'failed') {
          clearInterval(videoPollingTimer)
          videoGenerating.value = false
          videoError.value = data.data.error || '视频生成失败'
        }
      } else {
        clearInterval(videoPollingTimer)
        videoGenerating.value = false
        videoError.value = data.message || '查询视频状态失败'
      }
    } catch (e) {
      clearInterval(videoPollingTimer)
      videoGenerating.value = false
      videoError.value = '轮询网络错误：' + e.message
    }
  }, 10000) // 每5秒查一次
}

// 页面卸载时清除定时器
import { onUnmounted } from 'vue'
onUnmounted(() => {
  if (videoPollingTimer) clearInterval(videoPollingTimer)
})
</script>

<style scoped>
/* ===== 外层容器 ===== */
.app-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
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

/* ===== 主容器 ===== */
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

/* ===== 头部 & 标签切换 ===== */
.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #0ff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
  flex-wrap: wrap;
  gap: 8px;
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

.tabs {
  display: flex;
  gap: 6px;
}

.tab-btn {
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #0ff;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
}

.tab-btn.active {
  background: #00c9ff;
  color: #000;
  border-color: #00c9ff;
  box-shadow: 0 0 12px rgba(0, 201, 255, 0.7);
}

.status {
  color: #0f0;
  font-size: 0.8rem;
  text-shadow: 0 0 8px #0f0;
  white-space: nowrap;
}

/* ===== 对话区域（原有样式微调） ===== */
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

/* 滚动条 */
.chat-box::-webkit-scrollbar,
.image-body::-webkit-scrollbar {
  width: 4px;
}
.chat-box::-webkit-scrollbar-track,
.image-body::-webkit-scrollbar-track {
  background: transparent;
}
.chat-box::-webkit-scrollbar-thumb,
.image-body::-webkit-scrollbar-thumb {
  background: #0ff;
  border-radius: 10px;
  box-shadow: 0 0 6px #0ff;
}

.message-row {
  display: flex;
}
.message-row.user {
  justify-content: flex-end;
}
.message-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: clamp(0.85rem, 3vw, 1rem);
  line-height: 1.5;
  word-break: break-word;
}

.message-row.user .bubble {
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 0 15px rgba(0, 114, 255, 0.6);
  border-bottom-right-radius: 4px;
}

.message-row.assistant .bubble {
  background: rgba(0, 255, 255, 0.05);
  color: #e0f7ff;
  border: 1px solid rgba(0, 255, 255, 0.4);
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.2);
  border-bottom-left-radius: 4px;
  backdrop-filter: blur(5px);
}

.input-area {
  padding: 10px 12px;
  border-top: 1px solid rgba(0, 255, 255, 0.2);
  display: flex;
  gap: 8px;
}

.input-area input {
  flex: 1;
  padding: 10px 16px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 25px;
  color: #fff;
  font-size: 0.95rem;
  outline: none;
  transition: all 0.3s;
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.2);
  min-width: 0;
}

.input-area input:focus {
  border-color: #0ff;
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.5), 0 0 40px rgba(0, 255, 255, 0.2);
}

.input-area input::placeholder {
  color: rgba(0, 255, 255, 0.5);
}

.input-area button {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  border: none;
  color: #fff;
  font-size: 1rem;
  cursor: pointer;
  box-shadow: 0 0 15px rgba(0, 114, 255, 0.6);
  transition: transform 0.2s, box-shadow 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.input-area button:hover {
  transform: scale(1.05);
  box-shadow: 0 0 25px rgba(0, 114, 255, 0.9);
}

/* ===== 生图区域 ===== */
.image-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.image-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}

.prompt-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prompt-area textarea {
  width: 100%;
  padding: 12px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 12px;
  color: #fff;
  font-size: 1rem;
  resize: vertical;
  outline: none;
  transition: all 0.3s;
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.2);
  font-family: inherit;
}

.prompt-area textarea:focus {
  border-color: #0ff;
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.5);
}

.options-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.options-row select {
  padding: 8px 12px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 20px;
  color: #0ff;
  outline: none;
  cursor: pointer;
}

.generate-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #00c9ff, #0072ff);
  color: #fff;
  font-weight: bold;
  cursor: pointer;
  box-shadow: 0 0 15px rgba(0, 114, 255, 0.6);
  transition: all 0.3s;
}

.generate-btn:hover:not(:disabled) {
  box-shadow: 0 0 25px rgba(0, 114, 255, 0.9);
  transform: scale(1.02);
}

.generate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.result-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.result-image {
  max-width: 100%;
  max-height: 50vh;
  border-radius: 12px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.3);
  object-fit: contain;
}

.result-actions {
  display: flex;
  gap: 10px;
}

.action-link {
  color: #0ff;
  text-decoration: none;
  font-size: 0.9rem;
  padding: 6px 12px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 15px;
  transition: all 0.3s;
}

.action-link:hover {
  background: rgba(0, 255, 255, 0.1);
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 255, 255, 0.3);
  font-size: 1.2rem;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .main-container {
    height: 95dvh;
    margin: 0 8px;
    border-radius: 12px;
  }
  .chat-box {
    padding: 12px 8px;
  }
  .bubble {
    max-width: 90%;
    padding: 8px 12px;
  }
}

@media (max-width: 480px) {
  .main-container {
    height: 100dvh;
    margin: 0;
    border-radius: 0;
    border-left: none;
    border-right: none;
  }
  .chat-header h1 {
    font-size: 1.1rem;
  }
  .tab-btn {
    font-size: 0.75rem;
    padding: 2px 10px;
  }
  .input-area {
    padding: 8px 10px;
  }
}

/* ===== 图生图上传区域 ===== */
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.upload-btn {
  display: inline-block;
  padding: 8px 16px;
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 20px;
  color: #0ff;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 0.9rem;
}

.upload-btn:hover {
  background: rgba(0, 255, 255, 0.2);
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
}

.preview-box {
  position: relative;
  width: 80px;
  height: 80px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 8px;
  overflow: hidden;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0,0,0,0.7);
  color: #fff;
  border: none;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 文生图/图生图共用的 result-area 等前面已有 */
/* 视频相关样式 */
.param-group {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #0ff;
  font-size: 0.85rem;
}

.param-input {
  width: 60px;
  padding: 4px 6px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 6px;
  color: #fff;
  text-align: center;
  outline: none;
}

.progress-area {
  margin-top: 10px;
  color: #0ff;
  font-size: 0.9rem;
}

.progress-bar {
  height: 6px;
  background: rgba(0, 255, 255, 0.2);
  border-radius: 3px;
  margin-top: 6px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #00c9ff;
  box-shadow: 0 0 8px #0ff;
  transition: width 0.3s;
}

.result-video {
  max-width: 100%;
  max-height: 60vh;
  border-radius: 12px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.2);
}
</style>
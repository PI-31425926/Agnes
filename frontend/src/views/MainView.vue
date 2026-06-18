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
          <button class="logout-btn" @click="logout">🚪 退出</button>
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
      <div v-if="currentTab === 'video'" class="video-body">
        <div class="video-panel">
          <!-- 新建任务折叠区 -->
          <details class="create-section">
            <summary>🎬 新建视频任务</summary>
            <div class="prompt-area">
              <textarea v-model="videoPrompt" placeholder="描述视频内容..." rows="2"></textarea>
              <div class="options-row">
                <div class="param-group"><label>宽</label><input type="number" v-model.number="videoWidth" class="param-input"/></div>
                <div class="param-group"><label>高</label><input type="number" v-model.number="videoHeight" class="param-input"/></div>
                <div class="param-group">
                  <label>时长</label>
                  <select v-model.number="videoNumFrames" class="param-select">
                    <option :value="81">3秒 (81帧)</option>
                    <option :value="121">5秒 (121帧)</option>
                    <option :value="241">10秒 (241帧)</option>
                    <option :value="409">17秒 (409帧)</option>
                  </select>
                </div>
                <div class="param-group"><label>帧率</label><input type="number" v-model.number="videoFrameRate" class="param-input"/></div>
                <button class="generate-btn" @click="submitVideoTask">⚡ 提交</button>
              </div>
            </div>
          </details>

          <!-- 任务列表 -->
          <div class="task-list" v-if="videoTasks.length > 0">
            <h3>任务队列</h3>
            <div v-for="task in sortedVideoTasks" :key="task.videoId" class="task-item">
              <!-- 删除按钮（右上角） -->
              <button class="delete-btn" @click="deleteVideoTask(task.videoId)">🗑️删除</button>
              <div class="task-info">
                <span class="task-prompt">{{ task.prompt || '无描述' }}</span>
                <span :class="['task-status', task.status]">{{ statusMap[task.status] || task.status }}</span>
              </div>
              <div class="task-progress" v-if="task.status !== 'completed' && task.status !== 'failed'">
                <div class="progress-bar"><div class="progress-fill" :style="{ width: task.progress + '%' }"></div></div>
                <span>{{ task.progress }}%</span>
              </div>
              <div v-if="task.status === 'completed' && task.url" class="task-result">
                <video :src="task.url" controls class="mini-video"></video>
                <a :href="task.url" target="_blank" class="action-link">🔗 新窗口打开</a>
              </div>
              <div v-else-if="task.status === 'failed'" class="task-error">{{ task.error || '生成失败' }}</div>
            </div>
          </div>
          <div v-else class="empty-state">🎬 提交一个视频任务开始</div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
const currentTab = ref('chat')
import { ref, nextTick, watch, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

// ==================== 工具函数 ====================
// 从 axios 响应中提取数据，兼容 { data: { ... } } 和 { ... } 两种格式
function unwrapResponse(response) {
  const body = response.data
  // 如果返回体里有 data 字段，且 data 是对象，则展开，否则直接返回 body
  if (body && typeof body.data === 'object' && body.data !== null) {
    return body.data
  }
  return body
}

// 提取错误信息
function getErrorMessage(error, defaultMsg = '请求失败') {
  if (error.response && error.response.data) {
    const data = error.response.data
    return data.message || (data.error && data.error.message) || defaultMsg
  }
  return error.message || defaultMsg
}

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
    const res = await axios.post('/api/chat', { message: text })
    const data = unwrapResponse(res)   // 尝试解包
    const reply = data?.reply || data?.content || '无回复'
    chatMessages.value.push({ role: 'assistant', content: reply })
  } catch (e) {
    const msg = getErrorMessage(e, '请求失败')
    chatMessages.value.push({ role: 'assistant', content: '请求失败：' + msg })
  }
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
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
    const res = await axios.post('/api/image', {
      prompt,
      size: text2imgSize.value
    })
    const data = unwrapResponse(res)
    if (data && data.url) {
      text2imgResult.value = data.url
    } else {
      alert('生成失败：返回数据异常')
    }
  } catch (e) {
    alert('图片生成失败：' + getErrorMessage(e))
  } finally {
    text2imgGenerating.value = false
  }
}

// ==================== 图生图 ====================
const img2imgPrompt = ref('')
const img2imgSize = ref('1024x768')
const img2imgResult = ref(null)
const img2imgGenerating = ref(false)
const uploadedImagePreview = ref(null)
const uploadedImageBase64 = ref(null)

function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (event) => {
    const dataUri = event.target.result
    uploadedImagePreview.value = dataUri
    uploadedImageBase64.value = dataUri
  }
  reader.readAsDataURL(file)
}

function clearUploadedImage() {
  uploadedImagePreview.value = null
  uploadedImageBase64.value = null
  const fileInput = document.querySelector('input[type="file"]')
  if (fileInput) fileInput.value = ''
}

async function generateImg2img() {
  if (!uploadedImageBase64.value) return
  const prompt = img2imgPrompt.value.trim()
  img2imgGenerating.value = true
  img2imgResult.value = null
  try {
    const res = await axios.post('/api/image/to-image', {
      prompt,
      size: img2imgSize.value,
      imageBase64: uploadedImageBase64.value
    })
    const data = unwrapResponse(res)
    if (data && data.url) {
      img2imgResult.value = data.url
    } else {
      alert('图生图失败：返回数据异常')
    }
  } catch (e) {
    alert('图生图失败：' + getErrorMessage(e))
  } finally {
    img2imgGenerating.value = false
  }
}

// ==================== 视频生成（队列模式） ====================
const videoPrompt = ref('')
const videoWidth = ref(1152)
const videoHeight = ref(768)
const videoNumFrames = ref(121)
const videoFrameRate = ref(24)

const videoTasks = ref([])
const previousCompleted = ref(new Set())
let taskListTimer = null

const statusMap = {
  queued: '排队中',
  processing: '生成中',
  completed: '已完成',
  failed: '失败'
}

const sortedVideoTasks = computed(() => {
  return [...videoTasks.value].sort((a, b) => b.createdAt - a.createdAt)
})

// 提交新视频任务
async function submitVideoTask() {
  const prompt = videoPrompt.value.trim()
  if (!prompt) return
  try {
    const res = await axios.post('/api/video/generate', {
      prompt,
      width: videoWidth.value,
      height: videoHeight.value,
      numFrames: videoNumFrames.value,
      frameRate: videoFrameRate.value
    })
    const data = unwrapResponse(res)
    if (data && data.taskId) {
      fetchVideoTasks()     // 刷新队列
      videoPrompt.value = ''
    } else {
      alert('提交失败：返回数据异常')
    }
  } catch (e) {
    alert('提交失败：' + getErrorMessage(e))
  }
}

// 拉取任务列表
async function fetchVideoTasks() {
  try {
    const res = await axios.get('/api/video/tasks')
    const data = unwrapResponse(res)
    // 任务列表可能直接是数组，也可能是 { tasks: [...] }
    const tasks = Array.isArray(data) ? data : (data?.tasks || data?.data || [])
    if (tasks.length > 0 || videoTasks.value.length > 0) {
      tasks.forEach(task => {
        if (task.status === 'completed' && !previousCompleted.value.has(task.videoId)) {
          if (Notification.permission === 'granted') {
            new Notification('视频生成完成', { body: task.prompt || '您的视频已就绪' })
          }
          previousCompleted.value.add(task.videoId)
        }
      })
      videoTasks.value = tasks
    }
  } catch (err) {
    console.error('拉取任务列表失败', err)
  }
}

// 删除视频任务
async function deleteVideoTask(videoId) {
  try {
    await axios.delete(`/api/video/tasks/${videoId}`)
    fetchVideoTasks()
  } catch (e) {
    alert('删除失败：' + getErrorMessage(e))
  }
}

// 监听视频 Tab
watch(currentTab, (newTab) => {
  if (newTab === 'video') {
    if (Notification.permission === 'default') {
      Notification.requestPermission()
    }
    fetchVideoTasks()
    taskListTimer = setInterval(fetchVideoTasks, 20000)
  } else {
    if (taskListTimer) {
      clearInterval(taskListTimer)
      taskListTimer = null
    }
  }
})

// 退出登录
function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}

onUnmounted(() => {
  if (taskListTimer) clearInterval(taskListTimer)
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

/* 视频面板样式 */
.video-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.video-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.create-section {
  border: 1px solid rgba(0,255,255,0.2);
  border-radius: 8px;
  padding: 12px;
}
.create-section summary {
  color: #0ff;
  cursor: pointer;
  font-size: 1.1rem;
}
.task-list h3 {
  color: #0ff;
  margin: 0 0 8px;
}
.task-item {
  background: rgba(0,255,255,0.05);
  border: 1px solid rgba(0,255,255,0.2);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}
.task-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #ddd;
}
.task-prompt {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 10px;
}
.task-status {
  font-size: 0.8rem;
  padding: 2px 8px;
  border-radius: 10px;
}
.task-status.queued { background: #555; color: #aaa; }
.task-status.processing { background: #0072ff; color: white; }
.task-status.completed { background: #00c853; color: white; }
.task-status.failed { background: #ff1744; color: white; }
.task-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  color: #0ff;
}
.task-progress .progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(0,255,255,0.2);
  border-radius: 2px;
  overflow: hidden;
}
.task-progress .progress-fill {
  height: 100%;
  background: #0ff;
  box-shadow: 0 0 6px #0ff;
  transition: width 0.3s;
}
.task-result {
  margin-top: 8px;
}
.mini-video {
  max-width: 100%;
  max-height: 200px;
  border-radius: 8px;
}
.task-error {
  color: #ff5252;
  font-size: 0.9rem;
  margin-top: 6px;
}

.delete-btn {
  background: none;
  border: none;
  color: #ff5252;
  cursor: pointer;
  font-size: 1rem;
}
</style>
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
        <!-- 管理员入口按钮 -->
        <button
            v-if="userRole === 'ADMIN'"
            class="admin-entry-btn"
            @click="goAdmin"
        >🛡️ 管理面板</button>
        <span class="status">● 在线</span>
      </header>

      <!-- 对话界面 -->
      <div v-if="currentTab === 'chat'" class="chat-body">
        <!-- 对话选择栏 -->
        <div class="conversation-bar">
          <select v-model="activeConversationId" @change="switchConversation" class="conv-select">
            <option value="" disabled>选择或创建对话</option>
            <option v-for="conv in conversations" :key="conv.id" :value="conv.id">
              {{ conv.title || '新对话' }}
            </option>
          </select>
          <button @click="createNewConversation" class="new-conv-btn">＋ 新对话</button>
          <button v-if="activeConversationId" @click="deleteCurrentConversation" class="del-conv-btn">🗑️</button>
        </div>
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
          <!-- 文件上传按钮 -->
          <label class="upload-file-btn" title="上传文件">
            📎
            <input type="file" @change="handleFileUpload" accept=".txt,.doc,.docx,.pdf,.xls,.xlsx" hidden />
          </label>
          <!-- 图片上传按钮 -->
          <label class="upload-image-btn" title="上传图片">
            📷
            <input type="file" @change="(e) => selectImageFile(e.target.files[0])" accept="image/jpeg,image/png,image/webp" hidden />
          </label>
          <div class="input-wrapper">
            <!-- 文件标签 -->
            <div v-if="hasUploadedFile" class="file-tag">
              📄 {{ uploadedFileName }}
              <button class="remove-file-btn" @click="clearUploadedFile">✕</button>
            </div>
            <!-- 图片预览标签 -->
            <div v-if="selectedImage" class="image-preview-bar">
              <img :src="selectedImage.previewUrl" class="image-preview-thumb" />
              <span class="image-preview-label">图片</span>
              <button class="remove-image-btn" @click="removeSelectedImage">✕</button>
              <span v-if="isUploadingImage" class="uploading-indicator">上传中...</span>
            </div>
            <input
                v-model="chatInput"
                @keyup.enter="sendChat"
                :placeholder="hasUploadedFile ? '输入问题（留空默认提问）' : '输入消息...'"
                @paste="handlePaste"
                @drop.prevent="handleDrop"
            />
          </div>
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
                @keyup.enter="generateText2img"
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
        <!-- 文生图历史 -->
        <div v-if="imageHistory.length > 0" class="image-history">
          <h4>最近生成</h4>
          <div class="history-grid">
            <div v-for="(item, idx) in imageHistory" :key="idx" class="history-item">
              <img :src="item.url" :alt="item.prompt" />
              <span class="history-type">{{ item.type === 'text2img' ? '文生图' : '图生图' }}</span>
              <span class="history-prompt">{{ item.prompt }}</span>
              <a :href="item.url" target="_blank">查看原图</a>
            </div>
          </div>
        </div>
      </div>

      <!-- 图生图界面 -->
      <div v-if="currentTab === 'img2img'" class="image-body">
        <div class="image-panel">
          <div class="upload-row">
            <label class="upload-btn">
              📁 选择图片
              <input type="file" accept="image/*" @change="handleImageUpload" hidden />
            </label>
            <div v-if="uploadedImagePreview" class="preview-box">
              <img :src="uploadedImagePreview" class="preview-img" />
              <button class="remove-btn" @click="clearUploadedImage">✕</button>
            </div>
          </div>
          <div class="prompt-area">
            <textarea
                v-model="img2imgPrompt"
                @keyup.enter="generateImg2img"
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
        <!-- 图生图历史 -->
        <div v-if="imageHistory.length > 0" class="image-history">
          <h4>最近生成</h4>
          <div class="history-grid">
            <div v-for="(item, idx) in imageHistory" :key="idx" class="history-item">
              <img :src="item.url" :alt="item.prompt" />
              <span class="history-type">{{ item.type === 'text2img' ? '文生图' : '图生图' }}</span>
              <span class="history-prompt">{{ item.prompt }}</span>
              <a :href="item.url" target="_blank">查看原图</a>
            </div>
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
                <!-- 模式选择器 -->
                <div class="param-group">
                  <label>模式</label>
                  <select v-model="videoMode" class="param-select">
                    <option value="ti2vid">文生视频</option>
                    <option value="i2vid">图生视频</option>
                    <option value="keyframes">关键帧动画</option>
                  </select>
                </div>
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
              <!-- 图生视频：单图上传 -->
              <div v-if="videoMode === 'i2vid'" class="video-image-upload">
                <div class="upload-row">
                  <label class="upload-btn">
                    📁 选择图片
                    <input type="file" accept="image/jpeg,image/png,image/webp" @change="handleVideoImageUpload" hidden />
                  </label>
                  <div v-if="videoImageFiles.length > 0" class="preview-box">
                    <img :src="videoImageFiles[0].previewUrl" class="preview-img" />
                    <button class="remove-btn" @click="removeVideoImage(0)">✕</button>
                    <span v-if="isUploadingVideoImages" class="uploading-indicator">上传中...</span>
                  </div>
                </div>
              </div>
              <!-- 关键帧动画：多图上传 -->
              <div v-if="videoMode === 'keyframes'" class="video-image-upload">
                <div class="upload-row">
                  <label class="upload-btn">
                    📁 选择关键帧（最多10张）
                    <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="handleVideoKeyframeUpload" hidden />
                  </label>
                </div>
                <div v-if="videoKeyframeFiles.length > 0" class="keyframe-previews">
                  <div v-for="(kf, idx) in videoKeyframeFiles" :key="idx" class="keyframe-thumb">
                    <img :src="kf.previewUrl" class="preview-img" />
                    <button class="remove-btn" @click="removeVideoKeyframe(idx)">✕</button>
                  </div>
                </div>
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
import { ref, nextTick, watch, computed, onUnmounted, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { uploadImage as apiUploadImage } from '@/api/chat'

const router = useRouter()

// 用户角色（从 localStorage 读取，登录时已存储）
const userRole = ref(localStorage.getItem('role'))

// 跳转到管理页面
function goAdmin() {
  router.push('/admin')
}

// ==================== 工具函数 ====================
// 提取错误信息
function getErrorMessage(error, defaultMsg = '请求失败') {
  if (error.response && error.response.data) {
    const data = error.response.data
    return data.message || (data.error && data.error.message) || defaultMsg
  }
  return error.message || defaultMsg
}

// Canvas 图片压缩：将图片压缩至 maxSizeBytes 以内
function compressImage(file, maxSizeBytes) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const img = new Image()
      img.onload = () => {
        // 先 resize 到最大边长 1920
        let width = img.width
        let height = img.height
        const maxDim = 1920
        if (width > maxDim || height > maxDim) {
          if (width > height) {
            height = (height / width) * maxDim
            width = maxDim
          } else {
            width = (width / height) * maxDim
            height = maxDim
          }
        }

        let quality = 0.8
        let blob = null

        function tryCompress() {
          const canvas = document.createElement('canvas')
          canvas.width = Math.round(width)
          canvas.height = Math.round(height)
          const ctx = canvas.getContext('2d')
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

          canvas.toBlob((b) => {
            if (b && b.size <= maxSizeBytes) {
              resolve(b)
            } else if (quality > 0.3) {
              quality -= 0.1
              tryCompress()
            } else {
              // 最低质量仍超限，返回最小体积
              resolve(b)
            }
          }, 'image/jpeg', quality)
        }
        tryCompress()
      }
      img.src = e.target.result
    }
    reader.readAsDataURL(file)
  })
}

// 选择图片文件（按钮/拖拽/粘贴共用入口）
async function selectImageFile(file) {
  if (!file) return

  // 校验格式
  const ext = '.' + (file.name.split('.').pop() || '').toLowerCase()
  const typeValid = ALLOWED_IMAGE_TYPES.includes(file.type)
  const extValid = ALLOWED_IMAGE_EXTENSIONS.includes(ext)
  if (!typeValid && !extValid) {
    alert('不支持的图片格式，仅支持 jpg/png/webp')
    return
  }

  // 如果已有图片，提示先移除
  if (selectedImage.value) {
    alert('已有一张图片，请先移除当前图片')
    return
  }

  let processedFile = file

  // 超过 1MB 则压缩
  if (file.size > IMAGE_MAX_SIZE) {
    try {
      processedFile = await compressImage(file, IMAGE_MAX_SIZE)
    } catch (e) {
      alert('图片压缩失败：' + e.message)
      return
    }
  }

  // 生成预览
  const reader = new FileReader()
  reader.onload = (e) => {
    selectedImage.value = {
      file: processedFile,
      previewUrl: e.target.result,
      url: null  // 上传成功后填充
    }
  }
  reader.readAsDataURL(processedFile)
}

// 移除已选图片
function removeSelectedImage() {
  if (selectedImage.value && selectedImage.value.previewUrl) {
    URL.revokeObjectURL(selectedImage.value.previewUrl)
  }
  selectedImage.value = null
}

// 处理粘贴图片（Ctrl+V）
function handlePaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const file = item.getAsFile()
      if (file) selectImageFile(file)
      return
    }
  }
}

// 处理拖拽图片
function handleDrop(e) {
  e.preventDefault()
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  const file = files[0]
  if (file.type.startsWith('image/')) {
    selectImageFile(file)
  }
}

// ==================== 对话 ====================
const chatMessages = ref([])
const chatInput = ref('')
const chatBox = ref(null)

// ==================== 多轮对话管理 ====================
const conversations = ref([])
const activeConversationId = ref('')
let activeConversationTitle = ''

async function loadConversations() {
  try {
    const res = await request.get('/conversations')
    conversations.value = (res || []).map(c => ({
      ...c,
      title: decodeURIComponent(c.title || '新对话')
    }))
  } catch (e) {}
}

async function createNewConversation() {
  try {
    const res = await request.post('/conversations', '')
    conversations.value.unshift({ ...res, title: decodeURIComponent(res.title || '新对话') })
    activeConversationId.value = '' + res.id
    activeConversationTitle = ''
    chatMessages.value = []
    await loadChatHistory()
  } catch (e) {
    alert('创建对话失败：' + e.message)
  }
}

async function switchConversation() {
  chatMessages.value = []
  await loadChatHistory()
}

async function deleteCurrentConversation() {
  if (!activeConversationId.value) return
  if (!confirm('确定删除此对话？')) return
  try {
    await request.delete(`/conversations/${activeConversationId.value}`)
    conversations.value = conversations.value.filter(c => Number(c.id) !== Number(activeConversationId.value))
    activeConversationId.value = ''
    activeConversationTitle = ''
    chatMessages.value = []
  } catch (e) {
    alert('删除失败：' + e.message)
  }
}

async function loadChatHistory() {
  try {
    const cid = activeConversationId.value || undefined
    const res = await request.get('/chat/history', { params: cid ? { conversationId: cid } : {} })
    if (res && res.length > 0) {
      res.forEach(msg => {
        chatMessages.value.push({ role: msg.role, content: msg.content })
      })
      await nextTick()
      scrollToBottom()
      stopTts()
      speechSynthesis.cancel()
    }
  } catch (e) {}
}

// ==================== 文件上传 ====================
const hasUploadedFile = ref(false)
const uploadedFileName = ref('')
//const isUploading = ref(false)

// ==================== 图片上传 ====================
const selectedImage = ref(null)  // { file, previewUrl, url }
const isUploadingImage = ref(false)
const IMAGE_MAX_SIZE = 1 * 1024 * 1024  // 1MB
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ALLOWED_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp']

// TTS 自动朗读相关
// TTS 自动朗读（缓冲句子）
let ttsBuffer = ''             // 文本缓冲区
let ttsQueue = []              // 语音队列
let isTtsSpeaking = false      // 是否正在朗读
let ttsFlushTimer = null       // 定时刷新定时器

async function sendChat() {
  // 从 DOM 取原始值，绕过 Vue 响应式
  const inputEl = document.querySelector('.input-wrapper input')
  const rawValue = inputEl ? inputEl.value : ''
  let userMessage = typeof rawValue === 'string' ? rawValue : String(rawValue || '')
  userMessage = userMessage.trim()
  console.log('[sendChat] inputEl=', !!inputEl, 'rawValue=', JSON.stringify(rawValue), 'userMessage=', JSON.stringify(userMessage))
  // 有上传文件时允许空消息，后端会用默认问题
  if (!userMessage && !hasUploadedFile.value) {
    console.log('[sendChat] Returning early: no message and no file')
    return;
  }

  // 立即保存原始消息，避免任何作用域问题
  const originalMessage = userMessage;

  // 如果有图片，先上传到 OSS
  if (selectedImage.value && !selectedImage.value.url) {
    isUploadingImage.value = true
    try {
      const url = await apiUploadImage(selectedImage.value.file)
      selectedImage.value.url = url
      console.log('[Chat] Image uploaded, URL:', url)
    } catch (e) {
      chatMessages.value[aiIndex].content = '图片上传失败：' + getErrorMessage(e)
      isUploadingImage.value = false
      return
    } finally {
      isUploadingImage.value = false
    }
  }

  // 停止任何正在进行的语音朗读
  stopTts()

  chatMessages.value.push({ role: 'user', content: userMessage });
  chatInput.value = '';
  const aiMessage = { role: 'assistant', content: '' };
  chatMessages.value.push(aiMessage);
  const aiIndex = chatMessages.value.length - 1;

  try {
    const token = localStorage.getItem('token');
    const body = { message: originalMessage }
    if (activeConversationId.value) {
      body.conversationId = activeConversationId.value
      console.log('[Chat] Sending conversationId:', activeConversationId.value)
    } else {
      console.log('[Chat] No conversationId, value="' + activeConversationId.value + '"')
    }
    // 附加图片 URL
    if (selectedImage.value && selectedImage.value.url) {
      body.imageUrl = selectedImage.value.url
      console.log('[Chat] Sending imageUrl:', selectedImage.value.url)
    }
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      // 非 200 响应，读取错误文本
      const errorText = await response.text();
      chatMessages.value[aiIndex].content = `请求失败 (${response.status}): ${errorText}`;
      return;
    }

    // 正常流式读取
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim();
          if (data === '[DONE]') {
            // 流结束，强制输出缓冲区剩余文本
            flushTtsBuffer(true)
            return;
          }
          chatMessages.value[aiIndex].content += data;
          // 将文本块加入语音队列
          //speakChunk(data)
          addToTtsBuffer(data)   // 改为缓冲
          await nextTick();
          scrollToBottom();
        }
      }
    }
  } catch (e) {
    chatMessages.value[aiIndex].content = '请求失败：' + e.message;
  } finally {
    flushTtsBuffer(true)  // 确保缓冲区清空
    // 发送成功后清空图片选择
    if (selectedImage.value) {
      if (selectedImage.value.previewUrl) {
        URL.revokeObjectURL(selectedImage.value.previewUrl)
      }
      selectedImage.value = null
    }
    // 自动更新对话标题（首次消息）
    console.log('[AutoTitle] activeConversationId=', activeConversationId.value, ', activeConversationTitle=', activeConversationTitle)
    if (activeConversationId.value && !activeConversationTitle) {
      activeConversationTitle = originalMessage.substring(0, Math.min(20, originalMessage.length))
      console.log('[AutoTitle] Updating conversation', activeConversationId.value, 'to', activeConversationTitle)
      try {
        await request.put(`/conversations/${activeConversationId.value}/auto-title`, { title: originalMessage })
        console.log('[AutoTitle] Success')
      } catch (e2) {
        console.error('[AutoTitle] Failed:', e2.message)
      }
    }
  }
}

// 将文本片段加入缓冲区
function addToTtsBuffer(chunk) {
  ttsBuffer += chunk
  // 启动一个延迟检查，如果短时间无新数据则自动刷新
  if (ttsFlushTimer) clearTimeout(ttsFlushTimer)
  ttsFlushTimer = setTimeout(() => flushTtsBuffer(false), 300) // 300ms无新数据则刷新
}

// 刷新缓冲区，按句子边界切分并朗读
function flushTtsBuffer(force) {
  if (ttsFlushTimer) {
    clearTimeout(ttsFlushTimer)
    ttsFlushTimer = null
  }
  if (ttsBuffer.length === 0) return

  // 如果强制刷新，或者缓冲区包含句子分隔符，或者长度超过阈值
  const sentenceEnders = /[。！？；\n,.!?;]/g
  if (force || sentenceEnders.test(ttsBuffer) || ttsBuffer.length > 80) {
    // 切分句子（保留分隔符在句子末尾）
    const parts = ttsBuffer.split(/(?<=[。！？；\n,.!?;])/g)
    // 最后一个可能是不完整的句子，保留在缓冲区
    if (!force && parts.length > 1 && !sentenceEnders.test(parts[parts.length - 1])) {
      ttsBuffer = parts.pop()
    } else {
      ttsBuffer = ''
    }
    // 过滤空串并加入语音队列
    for (const part of parts) {
      const trimmed = part.trim()
      if (trimmed) {
        const utterance = new SpeechSynthesisUtterance(trimmed)
        utterance.rate = 1
        utterance.volume = 1
        ttsQueue.push(utterance)
      }
    }
    // 如果还未播放，启动播放
    if (!isTtsSpeaking) {
      playNextInQueue()
    }
  }
}

// 依次播放队列中的语音
function playNextInQueue() {
  if (ttsQueue.length === 0) {
    isTtsSpeaking = false
    return
  }
  isTtsSpeaking = true
  const utterance = ttsQueue.shift()
  utterance.onend = () => {
    playNextInQueue()
  }
  utterance.onerror = () => {
    playNextInQueue()  // 出错跳过
  }
  speechSynthesis.speak(utterance)
}

// 停止朗读并清空所有状态
function stopTts() {
  speechSynthesis.cancel()
  ttsQueue = []
  isTtsSpeaking = false
  ttsBuffer = ''
  if (ttsFlushTimer) {
    clearTimeout(ttsFlushTimer)
    ttsFlushTimer = null
  }
}

function scrollToBottom() {
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight;
  }
}

// 文件上传相关
const isUploading = ref(false)

async function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return

  const allowedExtensions = ['.txt', '.doc', '.docx', '.pdf', '.xls', '.xlsx']
  const fileName = file.name.toLowerCase()
  const isValid = allowedExtensions.some(ext => fileName.endsWith(ext))
  if (!isValid) {
    alert('仅支持txt、Word、PDF、Excel文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  isUploading.value = true
  try {
    const res = await request.post('/chat/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    hasUploadedFile.value = true
    uploadedFileName.value = file.name
  } catch (e) {
    alert('文件上传失败：' + (e.response?.data || e.message))
  } finally {
    isUploading.value = false
    const fileInput = document.querySelector('input[type="file"]')
    if (fileInput) fileInput.value = ''
  }
}

// 清除上传文件
async function clearUploadedFile() {
  try {
    await request.delete('/chat/upload')
  } catch (e) {}
  hasUploadedFile.value = false
  uploadedFileName.value = ''
}

// ==================== TTS 朗读 ====================
const speakingIndex = ref(-1)   // 当前朗读的消息索引，-1表示未朗读

function toggleSpeak(idx, text) {
  // 如果点击的是正在朗读的消息，则停止
  if (speakingIndex.value === idx) {
    speechSynthesis.cancel()
    speakingIndex.value = -1
    return
  }
  // 否则停止任何正在朗读的内容，然后开始朗读当前消息
  speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  // 可选：设置默认语音（中文需系统支持）
  utterance.onstart = () => { speakingIndex.value = idx }
  utterance.onend = () => { speakingIndex.value = -1 }
  utterance.onerror = () => { speakingIndex.value = -1 }
  speechSynthesis.speak(utterance)
}

/*async function sendChat() {
  const text = chatInput.value.trim()
  if (!text) return
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  try {
    const res = await request.post('/chat', { message: text })
    const reply = res?.reply || res?.content || '无回复'
    chatMessages.value.push({ role: 'assistant', content: reply })
  } catch (e) {
    const msg = getErrorMessage(e, '请求失败')
    chatMessages.value.push({ role: 'assistant', content: '请求失败：' + msg })
  }
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}*/

watch(currentTab, async (tab) => {
  if (tab === 'chat') {
    await nextTick()
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  }
})

onMounted(async () => {
  // 加载对话历史
  try {
    const cid = activeConversationId.value || undefined
    const res = await request.get('/chat/history', { params: cid ? { conversationId: cid } : {} })
    console.log('[History] Chat history response:', res)
    if (res && res.length > 0) {
      res.forEach(msg => {
        chatMessages.value.push({
          role: msg.role,
          content: msg.content
        })
      })
      await nextTick()
      scrollToBottom()
      stopTts()
      speechSynthesis.cancel()
    }
  } catch (e) {}

  // 加载对话列表并创建默认对话
  await loadConversations()
  if (conversations.value.length === 0) {
    await createNewConversation()
  } else {
    activeConversationId.value = '' + conversations.value[0].id
    await loadChatHistory()
  }

  // 如果当前标签是图片相关，加载图片历史（处理刷新情况）
  if (currentTab.value === 'text2img' || currentTab.value === 'img2img') {
    loadImageHistory()
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
    const res = await request.post('/image', {
      prompt,
      size: text2imgSize.value
    })
    if (res && res.url) {
      text2imgResult.value = res.url
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

function handleImageUpload(e) {
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
    const res = await request.post('/image/to-image', {
      prompt,
      size: img2imgSize.value,
      imageBase64: uploadedImageBase64.value
    })
    if (res && res.url) {
      img2imgResult.value = res.url
    } else {
      alert('图生图失败：返回数据异常')
    }
  } catch (e) {
    alert('图生图失败：' + getErrorMessage(e))
  } finally {
    img2imgGenerating.value = false
  }
}

// 图片历史列表
const imageHistory = ref([])

// 加载图片历史
async function loadImageHistory() {
  try {
    const res = await request.get('/image/history')
    if (res) {
      imageHistory.value = res
    }
  } catch (e) {}
}

// 监听标签切换，当进入文生图或图生图标签时加载历史
watch(currentTab, (newTab) => {
  if (newTab === 'text2img' || newTab === 'img2img') {
    loadImageHistory()
  }
})

// ==================== 视频生成（WebSocket 推送 + 降级轮询） ====================
const videoPrompt = ref('')
const videoWidth = ref(1152)
const videoHeight = ref(768)
const videoNumFrames = ref(121)
const videoFrameRate = ref(24)
const videoMode = ref('ti2vid')  // ti2vid / i2vid / keyframes

// 视频图片上传（复用 OSS 上传）
const videoImageFiles = ref([])  // 图生视频单图
const videoKeyframeFiles = ref([])  // 关键帧多图
const videoImageUrls = ref([])  // 已上传的图片 URL 数组
const isUploadingVideoImages = ref(false)

// 模式切换时清空图片
watch(videoMode, (newMode) => {
  if (newMode === 'i2vid') {
    videoKeyframeFiles.value = []
  } else if (newMode === 'keyframes') {
    videoImageFiles.value = []
  }
})

const videoTasks = ref([])
const previousCompleted = ref(new Set())

let wsConnected = false
let wsSession = null
let wsRetryCount = 0
const WS_MAX_RETRIES = 5
const WS_RECONNECT_DELAY = 3000
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

// 拉取任务列表（降级轮询用）
async function fetchVideoTasks() {
  try {
    const res = await request.get('/video/tasks')
    const tasks = Array.isArray(res) ? res : (res || [])
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
  } catch (err) {}
}

// 通过 WebSocket 更新单个任务
function updateTaskFromWS(task) {
  const idx = videoTasks.value.findIndex(t => t.videoId === task.videoId)
  if (idx >= 0) {
    videoTasks.value[idx] = { ...videoTasks.value[idx], ...task }
  } else {
    videoTasks.value.push(task)
  }
  // 标记已完成的任务
  if (task.status === 'completed') {
    previousCompleted.value.add(task.videoId)
  }
}

// 连接 WebSocket
function connectVideoWebSocket() {
  if (wsConnected && wsSession) return

  const token = localStorage.getItem('token')
  if (!token) return

  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${proto}//${location.host}/api/ws/video?token=${token}`

  try {
    wsSession = new WebSocket(wsUrl)
  } catch (e) {
    console.error('WebSocket 创建失败:', e)
    startFallbackPolling()
    return
  }

  wsSession.onopen = () => {
    console.log('WebSocket 已连接')
    wsConnected = true
    wsRetryCount = 0
    stopFallbackPolling()
  }

  wsSession.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'video_status' || msg.type === 'video_completed') {
        updateTaskFromWS(msg.data)
      }
    } catch (e) {
      console.error('WebSocket 消息解析失败:', e)
    }
  }

  wsSession.onclose = () => {
    console.log('WebSocket 断开')
    wsConnected = false
    wsSession = null
    // 重试或降级
    if (wsRetryCount < WS_MAX_RETRIES) {
      wsRetryCount++
      setTimeout(connectVideoWebSocket, WS_RECONNECT_DELAY)
    } else {
      console.log('WebSocket 重连失败，启用降级轮询')
      startFallbackPolling()
    }
  }

  wsSession.onerror = (err) => {
    console.error('WebSocket 错误:', err)
    wsSession.close()
  }
}

// 降级轮询
function startFallbackPolling() {
  if (taskListTimer) return
  taskListTimer = setInterval(fetchVideoTasks, 30000)
}

function stopFallbackPolling() {
  if (taskListTimer) {
    clearInterval(taskListTimer)
    taskListTimer = null
  }
}

// 关闭 WebSocket
function closeVideoWebSocket() {
  if (wsSession) {
    wsSession.close()
    wsSession = null
  }
  wsConnected = false
  stopFallbackPolling()
}

// 删除视频任务
async function deleteVideoTask(videoId) {
  try {
    await request.delete(`/video/tasks/${videoId}`)
    fetchVideoTasks()
  } catch (e) {
    alert('删除失败：' + getErrorMessage(e))
  }
}

// ==================== 视频图片上传 ====================
const ALLOWED_VIDEO_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const VIDEO_IMAGE_MAX_SIZE = 5 * 1024 * 1024  // 5MB

// 图生视频单图上传
function handleVideoImageUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  videoImageFiles.value = [{ file, previewUrl: null }]
  const reader = new FileReader()
  reader.onload = (event) => {
    videoImageFiles.value[0].previewUrl = event.target.result
  }
  reader.readAsDataURL(file)
}

// 关键帧多图上传
function handleVideoKeyframeUpload(e) {
  const files = Array.from(e.target.files || [])
  if (files.length === 0) return
  if (files.length > 10) {
    alert('关键帧最多支持 10 张图片')
    return
  }
  console.log('[Video] 关键帧选择', files.length, '张文件:', files.map(f => f.name))
  videoKeyframeFiles.value = files.map(file => ({ file, previewUrl: null }))
  console.log('[Video] videoKeyframeFiles 长度:', videoKeyframeFiles.value.length)
  // 逐个读取预览
  videoKeyframeFiles.value.forEach((kf, idx) => {
    const reader = new FileReader()
    reader.onload = (event) => {
      kf.previewUrl = event.target.result
      console.log('[Video] 预览加载完成 #', idx, kf.previewUrl ? 'OK' : 'FAIL')
    }
    reader.readAsDataURL(files[idx])
  })
  // 重置 input，允许重复选择同一文件
  e.target.value = ''
}

// 删除图生视频图片
function removeVideoImage() {
  videoImageFiles.value = []
  videoImageUrls.value = []
}

// 删除关键帧图片
function removeVideoKeyframe(idx) {
  videoKeyframeFiles.value.splice(idx, 1)
  videoImageUrls.value = videoImageUrls.value.filter((_, i) => i !== idx)
}

// 上传图片到 OSS（单张）
async function uploadVideoImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/video/upload-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 上传图片到 OSS（多张）
async function uploadVideoImages(files) {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return request.post('/video/upload-images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 提交视频任务（更新版）
async function submitVideoTask() {
  const prompt = videoPrompt.value.trim()
  if (!prompt) return

  // 图生视频需要图片
  if (videoMode.value === 'i2vid' && videoImageFiles.value.length === 0) {
    alert('图生视频需要上传一张参考图片')
    return
  }

  // 关键帧需要图片
  if (videoMode.value === 'keyframes' && videoKeyframeFiles.value.length === 0) {
    alert('关键帧动画需要上传至少一张参考图片')
    return
  }

  try {
    // 上传图片到 OSS
    let imageUrls = []
    if (videoMode.value === 'i2vid') {
      isUploadingVideoImages.value = true
      const file = videoImageFiles.value[0]?.file
      if (file) {
        const url = await uploadVideoImage(file)
        imageUrls = [url]
      }
      isUploadingVideoImages.value = false
    } else if (videoMode.value === 'keyframes') {
      isUploadingVideoImages.value = true
      const files = videoKeyframeFiles.value.map(kf => kf.file)
      if (files.length > 0) {
        const urls = await uploadVideoImages(files)
        imageUrls = urls
      }
      isUploadingVideoImages.value = false
    }

    const res = await request.post('/video/generate', {
      prompt,
      mode: videoMode.value,
      imageUrls,
      width: videoWidth.value,
      height: videoHeight.value,
      numFrames: videoNumFrames.value,
      frameRate: videoFrameRate.value
    })
    if (res && res.taskId) {
      fetchVideoTasks()
      videoPrompt.value = ''
      videoImageFiles.value = []
      videoKeyframeFiles.value = []
      videoImageUrls.value = []
    } else {
      alert('提交失败：返回数据异常')
    }
  } catch (e) {
    alert('提交失败：' + getErrorMessage(e))
  }
}

// 监听视频 Tab
watch(currentTab, (newTab) => {
  if (newTab === 'video') {
    if (Notification.permission === 'default') {
      Notification.requestPermission()
    }
    fetchVideoTasks()
    connectVideoWebSocket()
  } else {
    closeVideoWebSocket()
  }
})

// 退出登录
function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}

onUnmounted(() => {
  closeVideoWebSocket()
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

/* ===== 对话选择栏 ===== */
.conversation-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.15);
  background: rgba(0, 255, 255, 0.03);
}
.conv-select {
  flex: 1;
  padding: 4px 8px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 6px;
  color: #0ff;
  font-size: 0.85rem;
  outline: none;
  cursor: pointer;
}
.conv-select option {
  background: #0a0c0f;
  color: #0ff;
}
.new-conv-btn, .del-conv-btn {
  padding: 4px 10px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 6px;
  background: rgba(0, 255, 255, 0.1);
  color: #0ff;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
}
.new-conv-btn:hover { background: rgba(0, 255, 255, 0.2); }
.del-conv-btn { color: #ff5252; border-color: rgba(255, 82, 82, 0.3); background: rgba(255, 82, 82, 0.05); }
.del-conv-btn:hover { background: rgba(255, 82, 82, 0.15); }

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

/* 视频图片上传区域 */
.video-image-upload {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 255, 255, 0.15);
}

.keyframe-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.keyframe-thumb {
  position: relative;
  width: 72px;
  height: 72px;
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.keyframe-thumb:hover {
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.4);
}

/* 模式选择器下拉框样式 */
.param-select {
  padding: 4px 8px;
  background: rgba(0, 10, 20, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 6px;
  color: #fff;
  font-size: 0.85rem;
  outline: none;
  cursor: pointer;
}

.param-select option {
  background: #0a0c0f;
  color: #0ff;
}

.image-history {
  margin-top: 20px;
}
.image-history h4 {
  color: #0ff;
  margin-bottom: 10px;
}
.history-grid {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.history-item {
  width: 120px;
  background: rgba(0,255,255,0.05);
  border: 1px solid rgba(0,255,255,0.2);
  border-radius: 8px;
  padding: 6px;
  text-align: center;
}
.history-item img {
  width: 100%;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}
.history-type {
  display: block;
  color: #00c9ff;
  font-size: 0.7rem;
}
.history-prompt {
  display: block;
  color: #ccc;
  font-size: 0.75rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.history-item a {
  color: #0ff;
  font-size: 0.7rem;
}

/* 朗读按钮 */
.speak-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #0ff;
  font-size: 0.7rem;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  backdrop-filter: blur(4px);
}
.speak-btn:hover {
  background: rgba(0, 255, 255, 0.25);
  box-shadow: 0 0 8px #0ff;
}

.upload-file-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0,255,255,0.1);
  border: 1px solid rgba(0,255,255,0.3);
  color: #0ff;
  font-size: 1.1rem;
  cursor: pointer;
  transition: all 0.3s;
  margin-right: 8px;
}
.upload-file-btn:hover {
  background: rgba(0,255,255,0.25);
  box-shadow: 0 0 10px #0ff;
}

.upload-image-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0,255,255,0.1);
  border: 1px solid rgba(0,255,255,0.3);
  color: #0ff;
  font-size: 1.1rem;
  cursor: pointer;
  transition: all 0.3s;
  margin-right: 8px;
}
.upload-image-btn:hover {
  background: rgba(0,255,255,0.25);
  box-shadow: 0 0 10px #0ff;
}

.image-preview-bar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(0,255,255,0.08);
  border: 1px solid rgba(0,255,255,0.3);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 0.8rem;
  color: #0ff;
  width: fit-content;
}
.image-preview-thumb {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid rgba(0,255,255,0.3);
}
.image-preview-label {
  color: rgba(0,255,255,0.7);
}
.remove-image-btn {
  background: none;
  border: none;
  color: #ff5252;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0 2px;
  line-height: 1;
}
.uploading-indicator {
  color: rgba(0,255,255,0.5);
  font-size: 0.75rem;
}

.input-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.file-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(0,255,255,0.1);
  border: 1px solid rgba(0,255,255,0.3);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.8rem;
  color: #0ff;
  width: fit-content;
}
.remove-file-btn {
  background: none;
  border: none;
  color: #ff5252;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0 2px;
}
.admin-entry-btn {
  background: linear-gradient(135deg, #ff9900, #ff6600);
  border: 1px solid rgba(255, 153, 0, 0.5);
  color: #fff;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 0 12px rgba(255, 153, 0, 0.4);
}
.admin-entry-btn:hover {
  box-shadow: 0 0 20px rgba(255, 153, 0, 0.8);
  transform: scale(1.05);
}
</style>
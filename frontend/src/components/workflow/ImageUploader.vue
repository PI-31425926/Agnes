<template>
  <div class="image-uploader">
    <input
      ref="fileInput"
      type="file"
      :accept="acceptedTypes"
      :multiple="multiple"
      style="display: none"
      @change="onFileSelected"
    />
    <button
      @click="triggerFileSelect"
      :disabled="uploading"
      class="upload-btn"
      :class="{ 'upload-btn--loading': uploading }"
    >
      {{ uploading ? '上传中...' : '📷 上传图片' }}
    </button>
    <div v-if="errorMsg" class="upload-error">{{ errorMsg }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import request from '@/api/request'

const props = defineProps({
  multiple: { type: Boolean, default: false },
})

const emit = defineEmits(['uploaded'])

const fileInput = ref(null)
const uploading = ref(false)
const errorMsg = ref('')

const acceptedTypes = 'image/jpeg,image/png,image/webp'
const MAX_SIZE = 5 * 1024 * 1024 // 5MB
const MAX_MULTIPLE_COUNT = 10

function triggerFileSelect() {
  fileInput.value?.click()
}

async function onFileSelected(event) {
  const files = event.target.files
  if (!files || files.length === 0) return

  errorMsg.value = ''

  // Validate single file size
  for (let i = 0; i < files.length; i++) {
    if (files[i].size > MAX_SIZE) {
      errorMsg.value = '图片大小不能超过 5MB'
      event.target.value = ''
      return
    }
  }

  // Limit multiple count
  if (props.multiple && files.length > MAX_MULTIPLE_COUNT) {
    errorMsg.value = `最多上传 ${MAX_MULTIPLE_COUNT} 张图片`
    event.target.value = ''
    return
  }

  try {
    if (props.multiple) {
      await uploadMultiple(Array.from(files))
    } else {
      await uploadSingle(files[0])
    }
  } catch (e) {
    errorMsg.value = e.message || '上传失败'
  } finally {
    event.target.value = ''
  }
}

async function uploadSingle(file) {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = await request.post('/image/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    emit('uploaded', url)
  } finally {
    uploading.value = false
  }
}

async function uploadMultiple(files) {
  uploading.value = true
  try {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    const urls = await request.post('/video/upload-images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    // urls is an array of URLs
    if (Array.isArray(urls)) {
      urls.forEach(url => emit('uploaded', url))
    }
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-btn {
  width: 100%;
  background: rgba(0, 255, 255, 0.08);
  border: 1px solid #0ff;
  color: #0ff;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
  margin-bottom: 8px;
}
.upload-btn:hover:not(:disabled) {
  background: rgba(0, 255, 255, 0.15);
}
.upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.upload-btn--loading {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.upload-error {
  margin-top: 4px;
  font-size: 11px;
  color: #f88;
  word-break: break-word;
}
</style>

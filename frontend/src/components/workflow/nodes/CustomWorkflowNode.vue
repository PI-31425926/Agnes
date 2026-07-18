<template>
  <div class="custom-node" :class="statusClass">
    <div class="node-header">
      <span class="node-icon">{{ nodeInfo.icon }}</span>
      <span class="node-label">{{ nodeInfo.label }}</span>
      <span v-if="data._executionStatus" class="status-badge" :class="data._executionStatus.toLowerCase()">
        {{ statusText }}
      </span>
    </div>
    <!-- Image preview for image nodes -->
    <div v-if="imagePreviewUrl" class="node-image-preview">
      <img :src="imagePreviewUrl" alt="preview" />
    </div>
    <!-- Video preview for video nodes -->
    <div v-if="videoPreviewUrl" class="node-video-preview">
      <video :src="videoPreviewUrl" controls preload="metadata" @error="onVideoError" ref="videoEl"></video>
      <a :href="videoPreviewUrl" target="_blank" class="video-link">查看完整视频</a>
      <div v-if="videoError" class="video-error">{{ videoError }}</div>
    </div>
    <!-- Text output preview -->
    <div v-if="!imagePreviewUrl && !videoPreviewUrl && textOutput" class="node-output">
      <pre class="output-text">{{ textOutput }}</pre>
    </div>
    <Handle
      v-if="hasInput"
      type="target"
      :position="Position.Left"
      :is-connectable="true"
      class="handle handle-input"
    />
    <Handle
      v-if="hasOutput"
      type="source"
      :position="Position.Right"
      :is-connectable="true"
      class="handle handle-output"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { NODE_TYPES } from '../nodeTypes.js'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
  nodeType: { type: String, required: true },
})

const nodeInfo = computed(() => NODE_TYPES[props.nodeType] || { icon: '⚙️', label: props.nodeType, category: 'text' })
const hasInput = computed(() => props.nodeType !== 'text_input')
const hasOutput = computed(() => {
  const info = NODE_TYPES[props.nodeType]
  return info && !info.consumer
})

const statusClass = computed(() => {
  const s = props.data._executionStatus
  if (s === 'SUCCESS') return 'status-success'
  if (s === 'FAILED') return 'status-failed'
  return ''
})

const statusText = computed(() => {
  const s = props.data._executionStatus
  if (s === 'SUCCESS') return '✓'
  if (s === 'FAILED') return '✗'
  return ''
})

const imageData = computed(() => {
  const out = props.data._output
  if (!out || typeof out !== 'object') return null
  if (out.image_url) return { type: 'image', url: out.image_url }
  if (out.url) return { type: 'image', url: out.url }
  return null
})

const videoData = computed(() => {
  const out = props.data._output
  if (!out || typeof out !== 'object') return null
  if (out.video_url) return { type: 'video', url: out.video_url }
  if (out.url) return { type: 'video', url: out.url }
  return null
})

const imagePreviewUrl = computed(() => {
  const d = imageData.value
  return d && d.type === 'image' ? d.url : null
})

const videoPreviewUrl = computed(() => {
  const d = videoData.value
  return d && d.type === 'video' ? d.url : null
})

const videoError = ref(false)

function onVideoError() {
  videoError.value = '视频加载失败，点击查看'
}

const textOutput = computed(() => {
  const out = props.data._output
  if (!out) return ''
  if (typeof out === 'string') return out.substring(0, 120) + (out.length > 120 ? '...' : '')
  // Pick the most meaningful text field
  const textFields = ['prompt', 'response', 'refined_prompt', 'description']
  for (const key of textFields) {
    if (out[key] && typeof out[key] === 'string') {
      const s = out[key]
      return s.substring(0, 120) + (s.length > 120 ? '...' : '')
    }
  }
  // Fallback: show any remaining field
  const skip = new Set(['prompt', 'response', 'refined_prompt', 'description', 'image_url', 'video_url', 'url', '_executionStatus', '_output'])
  for (const [k, v] of Object.entries(out)) {
    if (!skip.has(k) && typeof v === 'string' && v) {
      return v.substring(0, 120) + (v.length > 120 ? '...' : '')
    }
  }
  return ''
})

const colors = { text: '#0af', image: '#a0f', video: '#f80' }
const borderColor = computed(() => colors[nodeInfo.value.category] || '#0ff')
</script>

<style scoped>
.custom-node {
  background: #0d1520;
  border: 1px solid #1a2a3a;
  border-left: 3px solid v-bind('borderColor');
  border-radius: 8px;
  padding: 8px;
  min-width: 140px;
  cursor: default;
  transition: border-color 0.3s, box-shadow 0.3s;
}

.custom-node.status-success {
  border-color: #0a0;
  border-left-color: #0f0;
}
.custom-node.status-failed {
  border-color: #600;
  border-left-color: #f00;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.node-icon { font-size: 16px; }
.node-label { font-size: 12px; color: #e0e8f0; font-weight: 500; flex: 1; }

.status-badge {
  font-size: 14px;
  font-weight: bold;
}
.status-badge.success { color: #0f0; }
.status-badge.failed { color: #f00; }

.node-image-preview {
  margin-top: 4px;
  border-radius: 4px;
  overflow: hidden;
  max-height: 200px;
  display: flex;
  justify-content: center;
}
.node-image-preview img {
  max-width: 100%;
  max-height: 200px;
  width: auto;
  height: auto;
  display: block;
  border-radius: 4px;
  object-fit: contain;
}

.node-video-preview {
  margin-top: 4px;
  border-radius: 4px;
  overflow: hidden;
  max-height: 200px;
  display: flex;
  justify-content: center;
}
.node-video-preview video {
  max-width: 100%;
  max-height: 200px;
  width: auto;
  height: auto;
  display: block;
  border-radius: 4px;
  object-fit: contain;
}

.video-link {
  display: block;
  font-size: 10px;
  color: #0ff;
  text-align: center;
  padding: 2px;
  text-decoration: none;
}
.video-link:hover { text-decoration: underline; }

.video-error {
  font-size: 10px;
  color: #fa0;
  padding: 4px;
  text-align: center;
}

.node-output {
  margin-top: 4px;
  padding: 4px 6px;
  background: #0a1018;
  border-radius: 4px;
  max-height: 60px;
  overflow: hidden;
}

.output-text {
  font-size: 10px;
  color: #8ab;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.3;
}

.handle {
  width: 10px !important;
  height: 10px !important;
  background: #0ff !important;
  border: 1px solid #0a0e17 !important;
}
.handle:hover { opacity: 1; transform: scale(1.3); }
</style>

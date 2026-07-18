<template>
  <div class="config-panel">
    <div class="panel-header">
      <span>节点配置 — {{ nodeInfo.label }}</span>
      <div class="header-actions">
        <button @click="$emit('run-node')" class="run-btn" title="执行此节点">▶ 执行</button>
        <button @click="$emit('delete-node')" class="delete-btn" title="删除节点">×</button>
        <button @click="$emit('close')" class="close-btn">×</button>
      </div>
    </div>
    <div class="panel-body">
      <!-- Refine prompt button for generation nodes -->
      <div v-if="isGenerateNode" class="refine-section">
        <button
          @click="onRefinePrompt"
          :disabled="!canRefine || refining"
          class="refine-btn"
          :class="{ 'refine-btn--loading': refining }"
        >
          {{ refining ? '优化中...' : '✨ 优化提示词' }}
        </button>
        <div v-if="refineError" class="refine-error">{{ refineError }}</div>
      </div>

      <!-- Execution result display -->
      <div v-if="executionResults[node.id]" class="execution-result">
        <div class="result-label">执行结果:</div>
        <pre class="result-content">{{ formatOutput(executionResults[node.id]) }}</pre>
      </div>

      <!-- text_input -->
      <div v-if="type === 'text_input'" class="form-group">
        <label>文本内容</label>
        <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="6" class="form-input" placeholder="输入初始文本内容..." />
      </div>

      <!-- text_refine -->
      <div v-if="type === 'text_refine'" class="form-group">
        <label>待优化的文本</label>
        <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="3" class="form-input" placeholder="支持 ${nodeId.field} 变量引用" />
      </div>
      <div v-if="type === 'text_refine'" class="form-group">
        <label>System Prompt</label>
        <textarea :value="nodeData.system_prompt" @input="onFieldChange('system_prompt', $event.target.value)" rows="4" class="form-input" />
      </div>

      <!-- text_chat -->
      <div v-if="type === 'text_chat'" class="form-group">
        <label>对话内容</label>
        <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="4" class="form-input" placeholder="支持 ${nodeId.field} 变量引用" />
      </div>

      <!-- image nodes -->
      <template v-if="['text_to_image', 'image_to_image'].includes(type)">
        <div class="form-group">
          <label>Prompt</label>
          <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="3" class="form-input" placeholder="支持 ${nodeId.field} 变量引用" />
        </div>
        <div class="form-group">
          <label>尺寸</label>
          <select :value="nodeData.size" @change="onFieldChange('size', $event.target.value)" class="form-input">
            <option value="1024x768">1024×768 (4:3)</option>
            <option value="768x1024">768×1024 (3:4)</option>
            <option value="1024x1024">1024×1024 (1:1)</option>
            <option value="1152x640">1152×640 (16:9)</option>
            <option value="640x1152">640×1152 (9:16)</option>
          </select>
        </div>
      </template>

      <!-- image_understand -->
      <div v-if="type === 'image_understand'" class="form-group">
        <label>分析指令</label>
        <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="3" class="form-input" placeholder="例如: 描述这张图片的内容" />
      </div>

      <!-- video nodes -->
      <template v-if="['text_to_video', 'image_to_video'].includes(type)">
        <div class="form-group">
          <label>Prompt</label>
          <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="3" class="form-input" placeholder="支持 ${nodeId.field} 变量引用" />
        </div>
        <div class="form-group"><label>宽度</label><input :value="nodeData.width" @input="onFieldChange('width', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>高度</label><input :value="nodeData.height" @input="onFieldChange('height', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>帧数</label><input :value="nodeData.num_frames" @input="onFieldChange('num_frames', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>帧率</label><input :value="nodeData.frame_rate" @input="onFieldChange('frame_rate', Number($event.target.value))" type="number" class="form-input" /></div>
      </template>

      <!-- keyframe_animation -->
      <div v-if="type === 'keyframe_animation'" class="form-group">
        <label>Prompt</label>
        <textarea :value="nodeData.prompt" @input="onFieldChange('prompt', $event.target.value)" rows="2" class="form-input" />
      </div>
      <div v-if="type === 'keyframe_animation'" class="form-group">
        <label>关键帧图片 (逗号分隔 URL)</label>
        <textarea :value="imageUrlsStr" @input="onImageUrlsChange($event.target.value)" rows="3" class="form-input" />
      </div>
      <div v-if="type === 'keyframe_animation'">
        <div class="form-group"><label>宽度</label><input :value="nodeData.width" @input="onFieldChange('width', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>高度</label><input :value="nodeData.height" @input="onFieldChange('height', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>帧数</label><input :value="nodeData.num_frames" @input="onFieldChange('num_frames', Number($event.target.value))" type="number" class="form-input" /></div>
        <div class="form-group"><label>帧率</label><input :value="nodeData.frame_rate" @input="onFieldChange('frame_rate', Number($event.target.value))" type="number" class="form-input" /></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { NODE_TYPES } from './nodeTypes.js'
import { usePromptRefine } from '../../composables/usePromptRefine.js'

const props = defineProps({
  node: { type: Object, required: true },
  executionResults: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['configUpdate', 'close', 'deleteNode', 'runNode'])

const type = computed(() => props.node?.data?.rawType || props.node?.type || '')
const nodeData = computed(() => props.node?.data || {})
const nodeId = computed(() => props.node?.id || '')
const nodeInfo = NODE_TYPES[type.value] || { label: type.value, icon: '⚙️' }

const GENERATE_NODE_TYPES = ['text_to_image', 'image_to_image', 'text_to_video', 'image_to_video', 'keyframe_animation']
const isGenerateNode = computed(() => GENERATE_NODE_TYPES.includes(type.value))

const { refinePrompt, loading: refining, error: refineError } = usePromptRefine()
const canRefine = computed(() => {
  const prompt = nodeData.value?.prompt
  return prompt && prompt.trim().length > 0
})

async function onRefinePrompt() {
  const prompt = nodeData.value?.prompt
  if (!prompt || prompt.trim().length === 0) {
    alert('请先输入提示词内容')
    return
  }
  try {
    const refined = await refinePrompt(type.value, prompt)
    emit('configUpdate', { prompt: refined })
  } catch (e) {
    // Error already shown by usePromptRefine
  }
}

const imageUrlsStr = computed({
  get: () => (nodeData.value.image_urls || []).join(', '),
  set: (val) => {
    const urls = val.split(',').map(s => s.trim()).filter(Boolean)
    emit('configUpdate', { image_urls: urls })
  }
})

function onFieldChange(field, value) {
  console.log('[NodeConfigPanel] onFieldChange:', field, '=', value)
  emit('configUpdate', { [field]: value })
}

function onImageUrlsChange(val) {
  const urls = val.split(',').map(s => s.trim()).filter(Boolean)
  emit('configUpdate', { image_urls: urls })
}

function formatOutput(output) {
  if (!output) return '暂无结果'
  if (typeof output === 'string') return output
  return JSON.stringify(output, null, 2)
}
</script>

<style scoped>
.config-panel {
  width: 300px;
  min-width: 300px;
  background: #0d1520;
  border-left: 1px solid #1a2a3a;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #1a2a3a;
  font-size: 14px;
  font-weight: bold;
  color: #0ff;
}

.header-actions { display: flex; gap: 4px; }

.run-btn {
  background: rgba(0,255,0,0.1); border: 1px solid #0f0; color: #0f0;
  border-radius: 3px; padding: 2px 8px; cursor: pointer; font-size: 12px;
}
.run-btn:hover { background: rgba(0,255,0,0.2); }

.delete-btn {
  background: rgba(255,0,0,0.1); border: 1px solid #f00; color: #f00;
  border-radius: 3px; padding: 2px 8px; cursor: pointer; font-size: 12px;
}
.delete-btn:hover { background: rgba(255,0,0,0.2); }

.close-btn {
  background: none; border: none; color: #667; font-size: 18px;
  cursor: pointer; padding: 0 4px;
}
.close-btn:hover { color: #fff; }

.refine-section {
  margin-bottom: 12px;
}

.refine-btn {
  width: 100%;
  background: rgba(0, 255, 255, 0.08);
  border: 1px solid #0ff;
  color: #0ff;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
}
.refine-btn:hover:not(:disabled) {
  background: rgba(0, 255, 255, 0.15);
}
.refine-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.refine-btn--loading {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.refine-error {
  margin-top: 4px;
  font-size: 11px;
  color: #f88;
  word-break: break-word;
}

.panel-body { padding: 12px; }

.execution-result {
  margin-bottom: 12px; padding: 8px; background: #0a1a0a;
  border: 1px solid #0a0; border-radius: 4px;
}
.result-label { font-size: 11px; color: #0f0; margin-bottom: 4px; text-transform: uppercase; }
.result-content {
  font-size: 11px; color: #8f8; white-space: pre-wrap; word-break: break-word;
  margin: 0; max-height: 120px; overflow-y: auto;
}

.form-group { margin-bottom: 12px; }
.form-group label {
  display: block; font-size: 11px; color: #889; margin-bottom: 4px;
  text-transform: uppercase; letter-spacing: 0.5px;
}
.form-input {
  width: 100%; background: #1a2a3a; border: 1px solid #2a3a4a;
  border-radius: 4px; color: #c0d0e0; padding: 6px 8px;
  font-size: 13px; font-family: inherit; box-sizing: border-box;
}
.form-input:focus { outline: none; border-color: #0ff; }
textarea.form-input { resize: vertical; min-height: 60px; }
</style>

<template>
  <div class="workflow-view">
    <!-- Top toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <router-link to="/" class="back-btn">← 返回首页</router-link>
        <input v-model="workflowName" :disabled="!workflowId" class="workflow-name-input" placeholder="工作流名称" />
        <button @click="createNewWorkflow" class="btn btn-new">＋ 新建</button>
      </div>
      <div class="toolbar-right">
        <select v-model="selectedWorkflowId" @change="loadWorkflow" class="workflow-select">
          <option value="">-- 选择已有工作流 --</option>
          <option v-for="wf in userWorkflows" :key="wf.id" :value="wf.id">
            {{ wf.name }}
          </option>
        </select>
        <button @click="saveWorkflow" class="btn btn-save">保存</button>
        <button @click="exportWorkflow" class="btn btn-outline">导出</button>
        <button @click="deleteSelectedWorkflow" class="btn btn-delete" :disabled="!workflowId">删除</button>
        <button @click="runEntireWorkflow" class="btn btn-run" :disabled="isExecuting">
          {{ isExecuting ? '执行中...' : '运行全部' }}
        </button>
        <button @click="runSingleNode" class="btn btn-debug" :disabled="!selectedNode || isExecuting">
          ▶ 执行当前节点
        </button>
        <button v-if="isExecuting" @click="stopWorkflow" class="btn btn-stop">停止</button>
      </div>
    </div>

    <!-- Progress bar -->
    <div v-if="isExecuting" class="progress-bar">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      <span class="progress-text">{{ progressDone }}/{{ progressTotal }} 节点完成</span>
    </div>

    <!-- Main layout: palette | canvas | config panel -->
    <div class="workflow-body">
      <!-- Left: node palette -->
      <NodePalette @node-added="onNodeAdded" />

      <!-- Center: Vue Flow canvas -->
      <WorkflowCanvas
        :nodes="nodes"
        :edges="edges"
        @node-select="onNodeSelected"
        @node-added="onNodeAdded"
        @update:nodes="nodes = $event"
        @update:edges="edges = $event"
      />

      <!-- Right: node config panel -->
      <NodeConfigPanel
        v-if="selectedNode"
        :node="selectedNode"
        :execution-results="executionResults"
        @config-update="onConfigUpdate"
        @close="selectedNode = null"
        @delete-node="deleteSelectedNode"
        @run-node="runSingleNodeById(selectedNode.id)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import NodePalette from '../components/workflow/NodePalette.vue'
import WorkflowCanvas from '../components/workflow/WorkflowCanvas.vue'
import NodeConfigPanel from '../components/workflow/NodeConfigPanel.vue'
import { useWorkflowEditor } from '../composables/useWorkflowEditor.js'
import { useWorkflowExecution } from '../composables/useWorkflowExecution.js'
import * as workflowApi from '../api/workflow.js'

const router = useRouter()
const { nodes, edges, selectedNode, workflowName, userWorkflows, selectedWorkflowId,
        saveCanvasState, loadCanvasState, onNodeSelected, onNodeAdded }
      = useWorkflowEditor()

const { isExecuting, progressDone, progressTotal, progressPercent, connectWs, disconnectWs }
      = useWorkflowExecution()

let workflowId = ref(null)
let ws = null
let executionId = ref(null)
const executionResults = ref({})

// Load user's workflows on mount
onMounted(async () => {
  try {
    const list = await workflowApi.list()
    userWorkflows.value = list || []
  } catch (e) {
    console.error('Failed to load workflows:', e)
  }
})

// ---- CRUD ----

async function saveWorkflow() {
  try {
    const { nodes: n, edges: e } = saveCanvasState()
    const payload = {
      name: workflowName.value || '未命名工作流',
      definition: JSON.stringify({ nodes: n, edges: e })
    }

    if (workflowId.value) {
      await workflowApi.update(workflowId.value, payload)
      alert('保存成功')
    } else {
      const id = await workflowApi.create(payload)
      workflowId.value = id
      alert('保存成功')
    }
  } catch (e) {
    alert('保存失败: ' + e.message)
  }
}

// ---- Workflow CRUD ----

async function createNewWorkflow() {
  // Reset to blank state without saving to server
  workflowId.value = null
  workflowName.value = ''
  nodes.value = []
  edges.value = []
  selectedNode.value = null
  executionResults.value = {}
  selectedWorkflowId.value = ''
}

async function deleteSelectedWorkflow() {
  if (!workflowId.value) return
  if (!confirm('确定删除此工作流及其所有执行记录吗？此操作不可恢复。')) return

  try {
    await workflowApi.del(workflowId.value)
    workflowId.value = null
    workflowName.value = ''
    nodes.value = []
    edges.value = []
    selectedNode.value = null
    executionResults.value = {}
    selectedWorkflowId.value = ''
    alert('已删除')
  } catch (e) {
    alert('删除失败: ' + e.message)
  }
}

async function loadWorkflow() {
  const id = parseInt(selectedWorkflowId.value)
  if (!id) return

  try {
    const wf = await workflowApi.get(id)
    workflowId.value = id
    workflowName.value = wf.name
    executionResults.value = wf.executionResults || {}

    const def = typeof wf.definition === 'string'
      ? JSON.parse(wf.definition)
      : wf.definition

    loadCanvasState(def.nodes || [], def.edges || [], executionResults.value)
  } catch (e) {
    alert('加载失败: ' + e.message)
  }
}

function exportWorkflow() {
  const { nodes: n, edges: e } = saveCanvasState()
  const blob = new Blob([JSON.stringify({ nodes: n, edges: e }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (workflowName.value || 'workflow') + '.json'
  a.click()
  URL.revokeObjectURL(url)
}

// ---- Node management ----

function deleteSelectedNode() {
  if (!selectedNode.value) return
  const id = selectedNode.value.id
  if (!confirm(`确定删除节点 "${id}" 吗？`)) return

  nodes.value = nodes.value.filter(n => n.id !== id)
  // Also remove edges connected to this node
  edges.value = edges.value.filter(e => e.source !== id && e.target !== id)
  selectedNode.value = null
}

function onConfigUpdate(newData) {
  console.log('[Workflow] onConfigUpdate received:', newData)
  if (!selectedNode.value) {
    console.warn('[Workflow] onConfigUpdate: no selectedNode')
    return
  }
  const idx = nodes.value.findIndex(n => n.id === selectedNode.value.id)
  console.log('[Workflow] onConfigUpdate: idx=', idx, 'newData=', newData)
  if (idx !== -1) {
    nodes.value[idx] = {
      ...nodes.value[idx],
      data: { ...nodes.value[idx].data, ...newData }
    }
    console.log('[Workflow] onConfigUpdate: updated node.data.prompt =', nodes.value[idx].data.prompt)
  }
}

// ---- Execution ----

async function runEntireWorkflow() {
  if (!workflowId.value) {
    alert('请先保存工作流')
    return
  }

  try {
    const result = await workflowApi.execute(workflowId.value)
    executionId.value = result.executionId
    connectWs(executionId.value, onWorkflowEvent)
  } catch (e) {
    alert('执行失败: ' + e.message)
  }
}

async function runSingleNode() {
  if (!selectedNode.value) {
    alert('请先选择一个节点')
    return
  }
  await runSingleNodeById(selectedNode.value.id)
}

async function runSingleNodeById(nodeId) {
  if (!workflowId.value) {
    alert('请先保存工作流')
    return
  }

  try {
    const result = await workflowApi.executeSingleNode(workflowId.value, nodeId)
    executionId.value = result.executionId
    const output = result.output || result

    // Update node with execution result — reassign node data to trigger reactivity
    const nodeIndex = nodes.value.findIndex(n => n.id === nodeId)
    if (nodeIndex !== -1) {
      const node = nodes.value[nodeIndex]
      nodes.value[nodeIndex] = {
        ...node,
        data: { ...node.data, _executionStatus: result.status || 'SUCCESS', _output: output }
      }
      // Force Vue to re-render by triggering a reactive update
      nodes.value = [...nodes.value]
    }
    executionResults.value[nodeId] = output
  } catch (e) {
    alert('节点执行失败: ' + e.message)
  }
}

async function stopWorkflow() {
  try {
    await workflowApi.stop(workflowId.value)
    disconnectWs()
    ws = null
    isExecuting.value = false
  } catch (e) {
    alert('停止失败: ' + e.message)
  }
}

function onWorkflowEvent(event) {
  if (event.type === 'node_completed') {
    const { nodeId, outputSummary } = event
    if (nodeId) {
      executionResults.value[nodeId] = outputSummary || {}
      const node = nodes.value.find(n => n.id === nodeId)
      if (node && outputSummary) {
        node.data.output_summary = outputSummary
      }
    }
  } else if (event.type === 'execution_completed') {
    disconnectWs()
    ws = null
  }
}
</script>

<style scoped>
.workflow-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: #0a0e17;
  color: #c0d0e0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #0d1520;
  border-bottom: 1px solid #1a2a3a;
  gap: 12px;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: #0ff;
  text-decoration: none;
  font-size: 14px;
}
.back-btn:hover { text-decoration: underline; }

.workflow-title {
  font-size: 16px;
  font-weight: bold;
  color: #fff;
}

.workflow-name-input {
  background: #1a2a3a;
  border: 1px solid #2a3a4a;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 14px;
  color: #fff;
  width: 180px;
}
.workflow-name-input:focus {
  outline: none;
  border-color: #0ff;
}
.workflow-name-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-new { border-color: #0ff; color: #0ff; }
.btn-new:hover { background: rgba(0, 255, 255, 0.1); }

.workflow-select {
  background: #1a2a3a;
  color: #c0d0e0;
  border: 1px solid #2a3a4a;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
}

.btn {
  padding: 6px 16px;
  border: 1px solid #2a3a4a;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  background: #1a2a3a;
  color: #c0d0e0;
  transition: all 0.2s;
}
.btn:hover { background: #2a3a4a; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-save { border-color: #0ff; color: #0ff; }
.btn-save:hover { background: rgba(0, 255, 255, 0.1); }

.btn-run { border-color: #0f0; color: #0f0; }
.btn-run:hover { background: rgba(0, 255, 0, 0.1); }

.btn-debug { border-color: #fa0; color: #fa0; }
.btn-debug:hover { background: rgba(255, 170, 0, 0.1); }

.btn-stop { border-color: #f00; color: #f00; }
.btn-stop:hover { background: rgba(255, 0, 0, 0.1); }

.btn-outline { border-color: #666; }

.btn-delete { border-color: #f44; color: #f44; }
.btn-delete:hover:not(:disabled) { background: rgba(255, 68, 68, 0.1); }

.progress-bar {
  height: 4px;
  background: #1a2a3a;
  position: relative;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #0ff, #0af);
  transition: width 0.3s;
}
.progress-text {
  position: absolute;
  top: 8px;
  right: 16px;
  font-size: 11px;
  color: #0ff;
}

.workflow-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>

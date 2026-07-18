<template>
  <div class="workflow-canvas">
    <VueFlow
      :nodes="nodes"
      :edges="edges"
      :default-viewport="{ zoom: 1, x: 0, y: 0 }"
      fit-view-on-init
      :edge-types="{ workflowEdge: WorkflowEdge }"
      @node-click="onNodeClick"
      @pane-click="onPaneClick"
      @drop="onDrop"
      @dragover="onDragOver"
      @connect="onConnect"
    >
      <template #node-workflow="nodeProps">
        <CustomWorkflowNode
          :id="nodeProps.id"
          :data="nodeProps.data"
          :node-type="nodeProps.data.rawType"
        />
      </template>
      <Background />
      <Controls />
      <MiniMap />
    </VueFlow>
  </div>
</template>

<script setup>
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import CustomWorkflowNode from './nodes/CustomWorkflowNode.vue'
import WorkflowEdge from './edges/WorkflowEdge.vue'

const props = defineProps({
  nodes: { type: Array, required: true },
  edges: { type: Array, required: true },
})

const emit = defineEmits(['nodeSelect', 'nodeAdded', 'update:nodes', 'update:edges'])

const { screenToFlowCoordinate } = useVueFlow()

function onNodeClick(event) {
  emit('nodeSelect', event)
}

function onPaneClick() {
  emit('nodeSelect', { node: null })
}

function onDragOver(event) {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

function onDrop(event) {
  const nodeType = event.dataTransfer.getData('application/workflow-node-type')
  if (!nodeType) return

  const flowPosition = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  emit('nodeAdded', { type: nodeType, position: flowPosition })
}

function onConnect(params) {
  const newEdge = {
    id: `e${params.source}-${params.target}-${Date.now()}`,
    source: params.source,
    target: params.target,
    type: 'workflowEdge',
    animated: false,
    markerEnd: { type: 'arrow', color: '#0ff' },
  }
  emit('update:edges', [...props.edges, newEdge])
}
</script>

<style scoped>
.workflow-canvas {
  flex: 1;
  background: #0a0e17;
}
</style>

<template>
  <div class="node-palette">
    <div class="palette-header">节点库</div>
    <div v-for="cat in categories" :key="cat.id" class="palette-category">
      <div class="category-label">{{ cat.label }}</div>
      <div
        v-for="node in cat.nodes"
        :key="node.type"
        class="palette-node"
        draggable="true"
        @dragstart="onDragStart($event, node.type)"
      >
        <span class="node-icon">{{ node.icon }}</span>
        <span class="node-label">{{ node.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { categories } from './nodeTypes.js'

const emit = defineEmits(['nodeAdded'])

function onDragStart(event, nodeType) {
  event.dataTransfer.setData('application/workflow-node-type', nodeType)
  event.dataTransfer.effectAllowed = 'move'
}
</script>

<style scoped>
.node-palette {
  width: 200px;
  min-width: 200px;
  background: #0d1520;
  border-right: 1px solid #1a2a3a;
  overflow-y: auto;
  padding: 12px;
}

.palette-header {
  font-size: 14px;
  font-weight: bold;
  color: #0ff;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #1a2a3a;
}

.palette-category {
  margin-bottom: 12px;
}

.category-label {
  font-size: 11px;
  text-transform: uppercase;
  color: #667;
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.palette-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  margin-bottom: 4px;
  background: #1a2a3a;
  border: 1px solid #2a3a4a;
  border-radius: 4px;
  cursor: grab;
  font-size: 13px;
  transition: all 0.2s;
}
.palette-node:hover {
  background: #2a3a4a;
  border-color: #0ff;
}
.palette-node:active {
  cursor: grabbing;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  flex: 1;
}
</style>

<template>
  <path
    class="workflow-edge"
    :d="pathD"
  />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, default: 'right' },
  targetPosition: { type: String, default: 'left' },
})

const pathD = computed(() => {
  const sx = props.sourceX
  const sy = props.sourceY
  const tx = props.targetX
  const ty = props.targetY
  const dx = tx - sx
  const curvature = Math.max(Math.abs(dx) * 0.5, 50)
  return `M${sx},${sy} C${sx + curvature},${sy} ${tx - curvature},${ty} ${tx},${ty}`
})
</script>

<style scoped>
.workflow-edge {
  fill: none;
  stroke: #0ff;
  stroke-width: 1.5;
  opacity: 0.6;
}
</style>

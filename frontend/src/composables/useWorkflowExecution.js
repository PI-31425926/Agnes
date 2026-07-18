import { ref, onUnmounted } from 'vue'

export function useWorkflowExecution() {
  const isExecuting = ref(false)
  const progressDone = ref(0)
  const progressTotal = ref(0)
  const progressPercent = ref(0)

  let ws = null
  let executionId = null

  function connectWs(execId, onEvent) {
    executionId = execId
    isExecuting.value = true
    progressDone.value = 0
    progressTotal.value = 0

    const token = localStorage.getItem('token')?.replace('Bearer ', '') || ''
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/api/ws/workflow?token=${encodeURIComponent(token)}`

    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('Workflow WebSocket connected')
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (onEvent) onEvent(data)

        // Update progress based on event type
        if (data.type === 'node_completed') {
          progressDone.value++
        } else if (data.type === 'execution_completed') {
          isExecuting.value = false
          progressPercent.value = 100
        } else if (data.type === 'node_failed') {
          isExecuting.value = false
        }
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e)
      }
    }

    ws.onerror = (error) => {
      console.error('Workflow WebSocket error:', error)
    }

    ws.onclose = () => {
      console.log('Workflow WebSocket closed')
      isExecuting.value = false
    }

    return ws
  }

  function disconnectWs() {
    if (ws) {
      ws.close()
      ws = null
    }
    isExecuting.value = false
  }

  onUnmounted(() => {
    disconnectWs()
  })

  return {
    isExecuting,
    progressDone,
    progressTotal,
    progressPercent,
    connectWs,
    disconnectWs,
  }
}

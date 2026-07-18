import { ref } from 'vue'

let nodeIdCounter = 100

export function useWorkflowEditor() {
  const nodes = ref([])
  const edges = ref([])
  const selectedNode = ref(null)
  const workflowName = ref('')
  const userWorkflows = ref([])
  const selectedWorkflowId = ref('')

  function addNode(type, x, y) {
    const id = 'n' + (++nodeIdCounter)
    const node = {
      id,
      type: 'workflow',
      position: { x, y },
      data: {
        rawType: type,
        label: type,
        prompt: '',
        system_prompt: '',
        size: '1024x768',
        width: 1152,
        height: 768,
        num_frames: 121,
        frame_rate: 24,
        image_urls: [],
      },
      style: {
        background: '#0d1520',
        border: '1px solid #1a2a3a',
        borderRadius: '8px',
        padding: '8px',
        minWidth: '160px',
        color: '#c0d0e0',
        fontSize: '13px',
      },
    }
    nodes.value = [...nodes.value, node]
    return id
  }

  function onNodeSelected(event) {
    const nodeId = event?.node?.id
    selectedNode.value = nodeId ? nodes.value.find(n => n.id === nodeId) || null : null
  }

  function onNodeAdded({ type, position }) {
    const id = 'n' + (++nodeIdCounter)
    nodes.value = [...nodes.value, {
      id,
      // Vue Flow node type: use 'workflow' to match the registered template
      // Actual node type stored in data.rawType
      type: 'workflow',
      position,
      data: {
        rawType: type,
        label: type,
        prompt: '',
        system_prompt: '',
        size: '1024x768',
        width: 1152,
        height: 768,
        num_frames: 121,
        frame_rate: 24,
        image_urls: [],
      },
      style: {
        background: '#0d1520',
        border: '1px solid #1a2a3a',
        borderRadius: '8px',
        padding: '8px',
        minWidth: '160px',
        color: '#c0d0e0',
        fontSize: '13px',
      },
    }]
  }

  function saveCanvasState() {
    // Strip runtime-only fields (_executionStatus, _output) before saving
    const nodesToSave = nodes.value.map(n => {
      const rawType = n.data?.rawType || n.type
      const { _executionStatus, _output, ...cleanData } = n.data || {}
      return { ...n, type: rawType, data: cleanData }
    })
    return { nodes: nodesToSave, edges: edges.value }
  }

  function loadCanvasState(nodeList, edgeList, executionResults) {
    nodes.value = nodeList.map(n => {
      const nodeId = n.id
      // Restore runtime fields from saved data
      const restoredData = { ...n.data, rawType: n.rawType || n.data?.rawType || n.type }
      // Merge execution results from DB
      if (executionResults && executionResults[nodeId]) {
        restoredData._executionStatus = executionResults[nodeId]._executionStatus
        restoredData._output = executionResults[nodeId]._output
      }
      return { ...n, type: 'workflow', position: n.position || { x: 0, y: 0 }, data: restoredData }
    })
    edges.value = edgeList || []
  }

  return {
    nodes,
    edges,
    selectedNode,
    workflowName,
    userWorkflows,
    selectedWorkflowId,
    addNode,
    onNodeSelected,
    onNodeAdded,
    saveCanvasState,
    loadCanvasState,
  }
}

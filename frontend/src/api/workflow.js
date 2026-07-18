import request from './request.js'

export function list() {
  return request.get('/workflows').then(data => data || [])
}

export function get(id) {
  return request.get(`/workflows/${id}`).then(data => {
    // Also fetch the latest successful execution results
    return request.get(`/workflows/executions/latest/${id}`).then(results => {
      data.executionResults = results || {}
      return data
    }).catch(() => {
      data.executionResults = {}
      return data
    })
  })
}

export function create(payload) {
  return request.post('/workflows', payload)
}

export function update(id, payload) {
  return request.put(`/workflows/${id}`, payload)
}

export function del(id) {
  return request.delete(`/workflows/${id}`)
}

export function execute(id) {
  return request.post(`/workflows/${id}/execute`)
}

export function executeSingleNode(id, nodeId) {
  return request.post(`/workflows/${id}/execute`, { nodeId })
}

export function getExecution(executionId) {
  return request.get(`/workflows/executions/${executionId}`)
}

export function stop(id) {
  return request.post(`/workflows/${id}/stop`)
}

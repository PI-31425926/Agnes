import axios from 'axios'

const request = axios.create({ baseURL: '/api' })

// Request interceptor: attach Bearer token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor: auto-extract response.data.data from ApiResponse envelope
request.interceptors.response.use(
  response => {
    const apiRes = response.data
    if (apiRes && apiRes.code === 200) {
      return apiRes.data
    }
    // Business error
    return Promise.reject(new Error(apiRes?.message || '请求失败'))
  },
  error => {
    // 401 自动跳登录
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期'))
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default request

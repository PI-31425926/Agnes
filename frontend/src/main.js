import './assets/main.css'     // CSS 放在最顶部

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import axios from 'axios'

// axios 拦截器：自动携带 token
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// axios 响应拦截器：401 时自动跳登录
axios.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token')
            router.push('/login')
        }
        return Promise.reject(error)
    }
)

const app = createApp(App)
app.use(router)
app.mount('#app')
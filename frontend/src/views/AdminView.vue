<template>
  <div class="app-wrapper">
    <div class="grid-bg"></div>
    <div class="main-container">
      <!-- 头部 -->
      <header class="chat-header">
        <div class="logo">
          <span class="logo-icon">⬡</span>
          <h1>AGNES AI · 管理面板</h1>
        </div>
        <div class="header-right">
          <span class="admin-badge">🛡️ 管理员</span>
          <router-link to="/" class="back-link">返回首页</router-link>
        </div>
      </header>

      <!-- 标签切换 -->
      <div class="tabs">
        <button :class="['tab-btn', { active: adminTab === 'users' }]" @click="adminTab = 'users'">👥 用户管理</button>
        <button :class="['tab-btn', { active: adminTab === 'logs' }]" @click="adminTab = 'logs'">📊 操作日志</button>
      </div>

      <!-- 用户管理 -->
      <div v-if="adminTab === 'users'" class="content-area">
        <div v-if="usersLoading" class="loading">加载中...</div>
        <div v-else-if="usersError" class="error">{{ usersError }}</div>
        <div v-else class="table-wrapper">
          <table class="data-table">
            <thead>
            <tr>
              <th>ID</th>
              <th>手机号</th>
              <th>角色</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.id }}</td>
              <td>{{ user.phone }}</td>
              <td><span :class="['role-badge', user.role]">{{ user.role }}</span></td>
              <td>
                <button
                    v-if="user.role !== 'ADMIN'"
                    class="delete-btn"
                    @click="deleteUser(user.id)"
                    :disabled="deleting === user.id"
                >
                  {{ deleting === user.id ? '删除中...' : '删除' }}
                </button>
                <span v-else class="cant-delete">-</span>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 操作日志 -->
      <div v-if="adminTab === 'logs'" class="content-area">
        <div v-if="logsLoading" class="loading">加载中...</div>
        <div v-else-if="logsError" class="error">{{ logsError }}</div>
        <div v-else class="table-wrapper">
          <table class="data-table">
            <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>操作类型</th>
              <th>描述</th>
              <th>状态</th>
              <th>时间</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ log.id }}</td>
              <td>{{ log.username }}</td>
              <td>{{ log.operationType }}</td>
              <td class="desc-cell">{{ log.description }}</td>
              <td><span :class="['status-badge', log.resultStatus]">{{ log.resultStatus }}</span></td>
              <td class="time-cell">{{ formatTime(log.createTime) }}</td>
            </tr>
            </tbody>
          </table>
          <div class="pagination">
            <button :disabled="logPage <= 0" @click="loadLogs(logPage - 1)">上一页</button>
            <span>第 {{ logPage + 1 }} 页</span>
            <button :disabled="logs.length < logSize" @click="loadLogs(logPage + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const adminTab = ref('users')

// ========== 用户管理 ==========
const users = ref([])
const usersLoading = ref(false)
const usersError = ref('')
const deleting = ref(null)

async function loadUsers() {
  usersLoading.value = true
  usersError.value = ''
  try {
    const res = await axios.get('/api/admin/users')
    users.value = res.data
  } catch (e) {
    usersError.value = '加载用户失败：' + (e.response?.data?.message || e.message)
  } finally {
    usersLoading.value = false
  }
}

async function deleteUser(userId) {
  if (!confirm('确定要删除该用户吗？')) return
  deleting.value = userId
  try {
    await axios.delete(`/api/admin/users/${userId}`)
    users.value = users.value.filter(u => u.id !== userId)
  } catch (e) {
    alert('删除失败：' + (e.response?.data?.message || e.message))
  } finally {
    deleting.value = null
  }
}

// ========== 操作日志 ==========
const logs = ref([])
const logsLoading = ref(false)
const logsError = ref('')
const logPage = ref(0)
const logSize = 50

async function loadLogs(page = 0) {
  logsLoading.value = true
  logsError.value = ''
  logPage.value = page
  try {
    const res = await axios.get('/api/admin/logs', { params: { page, size: logSize } })
    logs.value = res.data
  } catch (e) {
    logsError.value = '加载日志失败：' + (e.response?.data?.message || e.message)
  } finally {
    logsLoading.value = false
  }
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadUsers()
  loadLogs()
})
</script>

<style scoped>
/* ===== 复用主风格背景 ===== */
.app-wrapper {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: #0a0c0f;
  display: flex;
  justify-content: center;
  align-items: center;
}
.grid-bg {
  position: absolute;
  inset: 0;
  background-image:
      linear-gradient(rgba(0, 255, 255, 0.08) 1px, transparent 1px),
      linear-gradient(90deg, rgba(0, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  animation: gridMove 8s linear infinite;
  pointer-events: none;
}
@keyframes gridMove {
  0% { background-position: 0 0, 0 0; }
  100% { background-position: 40px 40px, -40px 40px; }
}

.main-container {
  width: 100%;
  max-width: 1000px;
  height: 90dvh;
  margin: 0 16px;
  background: rgba(10, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(0, 255, 255, 0.3);
  box-shadow: 0 0 30px rgba(0, 255, 255, 0.2), inset 0 0 30px rgba(0, 255, 255, 0.05);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

/* 头部 */
.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #0ff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}
.logo { display: flex; align-items: center; gap: 6px; }
.logo-icon { font-size: 1.2rem; animation: glowPulse 2s infinite alternate; }
@keyframes glowPulse {
  from { filter: drop-shadow(0 0 5px #0ff); }
  to   { filter: drop-shadow(0 0 15px #0ff) drop-shadow(0 0 30px #0ff); }
}
.chat-header h1 {
  font-size: clamp(1rem, 4vw, 1.4rem);
  font-weight: 600;
  letter-spacing: 1px;
  margin: 0;
  color: #0ff;
}
.header-right { display: flex; align-items: center; gap: 16px; }
.admin-badge { color: #ff9900; font-size: 0.85rem; text-shadow: 0 0 8px #ff9900; }
.back-link {
  color: #0ff;
  text-decoration: none;
  font-size: 0.85rem;
  padding: 4px 12px;
  border: 1px solid rgba(0,255,255,0.3);
  border-radius: 15px;
  transition: all 0.3s;
}
.back-link:hover { background: rgba(0,255,255,0.1); box-shadow: 0 0 10px rgba(0,255,255,0.5); }

/* 标签 */
.tabs {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
}
.tab-btn {
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #0ff;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.3s;
}
.tab-btn.active { background: #00c9ff; color: #000; border-color: #00c9ff; box-shadow: 0 0 12px rgba(0, 201, 255, 0.7); }

/* 内容区 */
.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.loading, .error { color: #0ff; text-align: center; padding: 40px; }
.table-wrapper { overflow-x: auto; }

.data-table {
  width: 100%;
  border-collapse: collapse;
  color: #e0f7ff;
}
.data-table th, .data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(0, 255, 255, 0.15);
}
.data-table th {
  color: #0ff;
  font-weight: 600;
  font-size: 0.9rem;
  text-transform: uppercase;
}
.data-table tr:hover { background: rgba(0, 255, 255, 0.05); }
.desc-cell { max-width: 250px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.time-cell { white-space: nowrap; font-size: 0.85rem; }

/* 角色/状态徽章 */
.role-badge {
  font-size: 0.8rem;
  padding: 2px 8px;
  border-radius: 10px;
  text-transform: uppercase;
}
.role-badge.ADMIN { background: #ff9900; color: #000; }
.role-badge.USER { background: #00c9ff; color: #000; }
.status-badge {
  font-size: 0.8rem;
  padding: 2px 8px;
  border-radius: 10px;
}
.status-badge.SUCCESS { background: #00c853; color: white; }
.status-badge.FAILED { background: #ff1744; color: white; }

/* 删除按钮 */
.delete-btn {
  background: rgba(255, 50, 50, 0.15);
  border: 1px solid rgba(255, 80, 80, 0.4);
  color: #ff5252;
  border-radius: 4px;
  cursor: pointer;
  padding: 2px 8px;
  font-size: 0.8rem;
  transition: all 0.2s;
}
.delete-btn:hover:not(:disabled) { background: rgba(255, 0, 0, 0.3); box-shadow: 0 0 8px rgba(255,0,0,0.5); }
.cant-delete { color: #555; }

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  color: #0ff;
}
.pagination button {
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: #0ff;
  padding: 4px 12px;
  border-radius: 15px;
  cursor: pointer;
  transition: all 0.3s;
}
.pagination button:hover:not(:disabled) { background: rgba(0,255,255,0.25); }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }

/* 响应式 */
@media (max-width: 768px) {
  .main-container { height: 95dvh; margin: 0 8px; border-radius: 12px; }
}
@media (max-width: 480px) {
  .main-container { height: 100dvh; margin: 0; border-radius: 0; border-left: none; border-right: none; }
  .data-table th, .data-table td { padding: 8px 6px; font-size: 0.8rem; }
}
</style>
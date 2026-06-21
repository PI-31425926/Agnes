import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import MainView from '../views/MainView.vue'
import GuestView from "../views/GuestView.vue";
import AdminView from "../views/AdminView.vue";

const routes = [
  {
    path: '/',
    component: MainView,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    component: LoginView
  },
  {
    path: '/guest',
    component: GuestView
  },
  {
    path: '/admin',
    component: AdminView,
    meta: { requiresAuth: true, role: 'ADMIN' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.role) {
    const userRole = localStorage.getItem('role')
    if (userRole !== to.meta.role) {
      next('/')   // 权限不足，跳转首页
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
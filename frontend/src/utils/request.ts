import axios, { InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import router from '../router'
import { useAuthStore } from '../store/auth'

// 创建一个带有统一基础配置的 Axios 实例
const request = axios.create({ baseURL: '/api', timeout: 5000 })

// 标记是否正在执行 Token 刷新，防止多次重复刷新请求
let isRefreshing = false
// 暂存因 401 未授权而被挂起的并发请求回调队列
let retryQueue: Array<(token: string) => void> = []

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    // 注入 Access Token
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => response,
  async error => {
    const originalRequest = error.config
    
    // 如果后端返回 401 且该请求还未进行过刷新重试，说明需要静默刷新 Token
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true
      const authStore = useAuthStore()
      
      // 1. 如果当前已经处于刷新 Token 的状态，则直接挂起请求并存入重试队列
      if (isRefreshing) {
        return new Promise(resolve => {
          retryQueue.push((newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(request(originalRequest))
          })
        })
      }
      
      // 2. 否则开启刷新状态并尝试调用刷新接口
      isRefreshing = true
      
      try {
        const success = await authStore.refreshAccessToken()
        if (success) {
          const newToken = authStore.accessToken
          isRefreshing = false
          
          // 刷新成功，逐一重新发起队列中的被挂起请求
          retryQueue.forEach(callback => callback(newToken))
          retryQueue = []
          
          // 重新发起当前报错的请求
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        }
      } catch (refreshErr) {
        console.error('[响应拦截器] Token 静默刷新触发异常:', refreshErr)
      } finally {
        isRefreshing = false
      }
      
      // 3. 刷新失败，清理等待队列，重置认证状态，重定向至登录页
      retryQueue = []
      authStore.logout()
      router.push('/login')
    }
    
    return Promise.reject(error)
  }
)

export default request
export type { InternalAxiosRequestConfig, AxiosResponse }

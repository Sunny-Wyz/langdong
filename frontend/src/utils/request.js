import axios from 'axios'
import router from '../router'

// 创建一个带有统一基础配置的 Axios 实例
// baseURL: 所有请求自动拼接 /api 前缀
// timeout: 请求超时时间为 5000ms
const request = axios.create({ baseURL: '/api', timeout: 5000 })

// [关键代码] 请求拦截器（前置拦截）：在每个请求发出前注入 JWT Token
request.interceptors.request.use(config => {
  // 从浏览器 localStorage 中取出登录时存储的 Token
  const token = localStorage.getItem('token')
  // 如果 Token 存在，自动添加到请求头 Authorization 字段（Bearer 格式）
  // 后端 SecurityConfig.java 的 jwtFilter 会读取该字段进行身份验证
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// [关键代码] 响应拦截器（后置拦截）：对所有响应进行统一错误处理
request.interceptors.response.use(
  // 成功响应（HTTP 2xx）：直接透传 res 对象，不做额外处理
  res => res,
  // 错误响应（HTTP 4xx/5xx）：集中捕获异常
  err => {
    if (err.response?.status === 401) {
      // [核心] 401 未授权：Token 过期或无效，清除本地 Token 并跳转到登录页
      localStorage.removeItem('token')
      router.push('/login')
    }
    // 将错误继续向上抛出，让调用方的 catch 块可以捕获
    return Promise.reject(err)
  }
)

export default request

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/reference-theme.css'
import App from './App.vue'
import router from './router'
import request from './utils/request'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 全局属性挂载：将 $http 挂载为全局网络请求工具，兼容旧版组件语法
app.config.globalProperties.$http = request

app.mount('#app')

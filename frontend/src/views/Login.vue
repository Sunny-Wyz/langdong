<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="login-title">备件管理系统</h2>
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="密码" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-submit" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance | null>(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function submit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const { data } = await request.post('/auth/login', form)
      // 将单 Token 存入 store 维持基本会话
      authStore.setTokens(data.token, data.token, data.username)
      ElMessage.success('登录成功')
      router.push('/home')
    } catch (e: any) {
      const msg = e.response?.data?.message || '登录失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(180deg, #f6f8fc 0%, #ecf1fb 100%);
}

.login-card {
  width: 380px;
  border: 1px solid #e8eaee;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(15, 48, 134, 0.08);
}

.login-title {
  margin: 0 0 24px;
  text-align: center;
  color: #0f3086;
  font-size: 22px;
  font-weight: 700;
}

.login-submit {
  width: 100%;
  letter-spacing: 2px;
}
</style>

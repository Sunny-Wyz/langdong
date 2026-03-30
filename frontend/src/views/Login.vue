<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="login-title">备件管理系统</h2>
      <el-form :model="form" :rules="rules" ref="form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="密码" prefix-icon="el-icon-lock"
                    show-password @keyup.enter.native="submit" />
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

<script>
import request from '../utils/request'

export default {
  data() {
    return {
      loading: false,
      form: { username: '', password: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  methods: {
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          const { data } = await request.post('/auth/login', this.form)
          this.$store.commit('SET_TOKEN', data)
          this.$message.success('登录成功')
          this.$router.push('/home')
        } catch (e) {
          this.$message.error(e.response?.data?.message || '登录失败')
        } finally {
          this.loading = false
        }
      })
    }
  }
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

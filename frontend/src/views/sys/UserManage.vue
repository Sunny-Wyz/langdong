<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">👤</span>
          <div class="title">用户管理</div>
          <div class="head-btn-group">
            <el-button size="small" @click="handleAdd" v-if="hasPerm('sys:user:add')">
              新增用户
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="登录名" />
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="handleAssignRoles(scope.row)">分配角色</el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click="handleDelete(scope.row)"
              v-if="scope.row.username !== 'admin'"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialogType === 'add' ? '新增用户' : '编辑用户'" v-model="dialogVisible" width="500px">
      <el-form :model="form" ref="formRef" label-width="80px">
        <el-form-item label="登录名" prop="username">
          <el-input v-model="form.username" :disabled="dialogType === 'edit'" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" placeholder="留空则不修改，新增默认123456" />
        </el-form-item>
        <el-form-item label="角色分配" prop="roleIds" v-if="dialogType === 'add'">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色 (可多选)" style="width: 100%">
            <el-option v-for="role in allRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="单独分配角色" v-model="roleDialogVisible" width="400px">
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in allRoles" :label="role.id" :key="role.id" :value="role.id">
          {{ role.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const roleDialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance | null>(null)
const form = reactive({
  id: null as number | null,
  username: '',
  name: '',
  password: '',
  status: 1,
  roleIds: [] as number[]
})
const allRoles = ref<any[]>([])
const selectedRoleIds = ref<number[]>([])
const currentUserId = ref<number | null>(null)

function hasPerm(p: string) {
  return authStore.permissions.includes(p)
}

async function fetchData() {
  const res = await request.get('/users')
  tableData.value = res.data
}

async function fetchAllRoles() {
  const res = await request.get('/roles')
  allRoles.value = res.data
}

function handleAdd() {
  dialogType.value = 'add'
  form.id = null
  form.username = ''
  form.name = ''
  form.password = ''
  form.status = 1
  form.roleIds = []
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogType.value = 'edit'
  form.id = row.id
  form.username = row.username
  form.name = row.name
  form.password = ''
  form.status = row.status
  form.roleIds = []
  dialogVisible.value = true
}

async function handleSubmit() {
  if (dialogType.value === 'add') {
    try {
      const res = await request.post('/users', { ...form })
      if (res) {
        ElMessage.success('添加成功')
        dialogVisible.value = false
        fetchData()
      }
    } catch (e: any) {
      if (e.response) ElMessage.error(e.response.data)
    }
  } else {
    await request.put(`/users/${form.id}`, { ...form })
    ElMessage.success('由于状态和密码可能更新，请牢记处理结果')
    dialogVisible.value = false
    fetchData()
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除吗?', '提示')
    await request.delete(`/users/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleAssignRoles(row: any) {
  currentUserId.value = row.id
  const [allRes, ownRes] = await Promise.all([
    request.get('/roles'),
    request.get(`/users/${row.id}/roles`)
  ])
  allRoles.value = allRes.data
  selectedRoleIds.value = ownRes.data
  roleDialogVisible.value = true
}

async function saveRoles() {
  await request.post(`/users/${currentUserId.value}/roles`, { roleIds: selectedRoleIds.value })
  ElMessage.success('角色分配成功')
  roleDialogVisible.value = false
}

onMounted(() => {
  fetchData()
  fetchAllRoles()
})
</script>

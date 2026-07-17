<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">🔐</span>
          <div class="title">角色管理</div>
          <div class="head-btn-group">
            <el-button size="small" @click="handleAdd">新增角色</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="角色编码" sortable="custom" />
        <el-table-column prop="name" label="角色名称" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="handleAssignMenus(scope.row)">权限分配</el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click="handleDelete(scope.row)"
              v-if="scope.row.code !== 'ADMIN'"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialogType === 'add' ? '新增角色' : '编辑角色'" v-model="dialogVisible" width="500px">
      <el-form :model="form" ref="formRef" label-width="80px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="dialogType === 'edit'" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="分配角色权限" v-model="menuDialogVisible" width="500px">
      <el-tree
        ref="treeRef"
        :data="allMenus"
        show-checkbox
        node-key="id"
        :props="{ children: 'children', label: 'name' }"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, nextTick } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'

const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance | null>(null)
const form = reactive({
  id: null as number | null,
  code: '',
  name: '',
  remark: ''
})
const allMenus = ref<any[]>([])
const checkedMenuIds = ref<number[]>([])
const currentRoleId = ref<number | null>(null)
const treeRef = ref<any>(null)

watch(allMenus, (val) => {
  if (val && val.length > 0 && checkedMenuIds.value.length > 0) {
    nextTick(() => {
      if (treeRef.value) {
        treeRef.value.setCheckedKeys(checkedMenuIds.value)
      }
    })
  }
})

async function fetchData() {
  const res = await request.get('/roles')
  tableData.value = res.data
}

function handleAdd() {
  dialogType.value = 'add'
  form.id = null
  form.code = ''
  form.name = ''
  form.remark = ''
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogType.value = 'edit'
  form.id = row.id
  form.code = row.code
  form.name = row.name
  form.remark = row.remark
  dialogVisible.value = true
}

async function handleSubmit() {
  if (dialogType.value === 'add') {
    await request.post('/roles', { ...form })
  } else {
    await request.put(`/roles/${form.id}`, { ...form })
  }
  ElMessage.success('操作成功')
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除吗?', '提示')
    await request.delete(`/roles/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleAssignMenus(row: any) {
  currentRoleId.value = row.id
  checkedMenuIds.value = []
  allMenus.value = []
  menuDialogVisible.value = true
  const [allRes, ownRes] = await Promise.all([
    request.get('/menus'),
    request.get(`/roles/${row.id}/menus`)
  ])
  allMenus.value = allRes.data
  checkedMenuIds.value = ownRes.data
  nextTick(() => {
    if (treeRef.value) {
      treeRef.value.setCheckedKeys(checkedMenuIds.value)
    }
  })
}

async function saveMenus() {
  try {
    const keys = treeRef.value.getCheckedKeys()
    const halfKeys = treeRef.value.getHalfCheckedKeys()
    const allKeys = [...new Set([...keys, ...halfKeys])].map(Number)
    await request.post(`/roles/${currentRoleId.value}/menus`, { menuIds: allKeys })
    ElMessage.success('权限分配成功')
    menuDialogVisible.value = false
  } catch (e: any) {
    const msg = e.response?.data?.message || e.response?.data || e.message || '请求失败'
    ElMessage.error('权限分配失败: ' + msg)
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
    <div class="page-container location-profile-container">
        <el-card class="box-card">
            <template #header>
              <div class="phead header">
                <span class="title-icon">📁</span>
                <div class="title">货位档案管理</div>
                <div class="head-btn-group">
                <el-button v-if="hasAddPermission" style="float: right; margin-left: 10px;" size="small" @click="handleAdd">
                    新增货位
                </el-button>
                </div>
              </div>
            </template>

            <!-- 搜索和筛选器 -->
            <div class="filter-container">
                <el-select v-model="listQuery.zone" placeholder="所在专区" clearable
                    style="width: 200px; margin-right: 15px;">
                    <el-option v-for="i in 12" :key="i" :label="'专区' + i" :value="'专区' + i" />
                </el-select>
            </div>

            <!-- 数据表格 -->
            <el-table v-loading="loading" :data="filteredList" border style="width: 100%; margin-top: 15px;">
                <el-table-column prop="code" label="货位编码" width="120" sortable="custom" />
                <el-table-column prop="name" label="货位名称" min-width="150" />
                <el-table-column prop="zone" label="所属专区" width="120" >
                    <template #default="{ row }">
                        <el-tag>{{ row.zone }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="capacity" label="货位容量" width="120" />
                <el-table-column prop="remark" label="备注" show-overflow-tooltip />
                <el-table-column label="操作" :width="hasAddPermission ? 220 : 100" fixed="right" align="center">
                    <template #default="{ row }">
                        <el-button size="small" @click="handleViewSpareParts(row)">查看备件</el-button>
                        <el-button v-if="hasAddPermission" size="small" @click="handleEdit(row)">编辑</el-button>
                        <el-button v-if="hasAddPermission" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 新增/编辑弹窗 -->
        <el-dialog :title="dialogStatus === 'create' ? '新增货位' : '编辑货位'" v-model="dialogFormVisible" width="500px">
            <el-form ref="dataForm" :rules="rules" :model="temp" label-position="right" label-width="100px">
                <el-form-item label="货位编码" prop="code">
                    <el-input v-model="temp.code" placeholder="如: A-01-01" />
                </el-form-item>
                <el-form-item label="货位名称" prop="name">
                    <el-input v-model="temp.name" placeholder="请输入名称" />
                </el-form-item>
                <el-form-item label="所属专区" prop="zone">
                    <el-select v-model="temp.zone" placeholder="请选择专区" style="width: 100%;">
                        <el-option v-for="i in 12" :key="i" :label="'专区' + i" :value="'专区' + i" />
                    </el-select>
                </el-form-item>
                <el-form-item label="货位容量">
                    <el-input v-model="temp.capacity" placeholder="如: 50单位、大号等" />
                </el-form-item>
                <el-form-item label="备注">
                    <el-input type="textarea" v-model="temp.remark" :rows="3" />
                </el-form-item>
            </el-form>
            <template #footer>
              <div class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
              </div>
            </template>
        </el-dialog>

        <!-- 关联备件列表弹窗 -->
        <el-dialog :title="'【' + currentLoc.name + '】下的备件列表'" v-model="sparePartDialogVisible" width="800px">
            <el-table :data="locSpareParts" border style="width: 100%">
                <el-table-column prop="name" label="备件名称" />
                <el-table-column prop="model" label="型号" />
                <el-table-column prop="category" label="类别" />
                <el-table-column label="库存" width="100" >
                    <template #default="{ row }">
                        {{ row.quantity }} {{ row.unit }}
                    </template>
                </el-table-column>
            </el-table>
            <template #footer>
              <div class="dialog-footer">
                <el-button @click="sparePartDialogVisible = false">关闭</el-button>
              </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()

const hasAddPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('base:location:add') || username === 'admin'
})

const list = ref<any[]>([])
const loading = ref(true)
const listQuery = reactive({
    zone: ''
})

const dialogFormVisible = ref(false)
const dialogStatus = ref('')
const dataForm = ref<FormInstance | null>(null)

interface TempLoc {
    id?: number
    code: string
    name: string
    zone: string
    capacity: string
    remark: string
}

const temp = reactive<TempLoc>({
    id: undefined,
    code: '',
    name: '',
    zone: '',
    capacity: '',
    remark: ''
})

const rules = {
    code: [{ required: true, message: '货位编码必填', trigger: 'blur' }],
    name: [{ required: true, message: '货位名称必填', trigger: 'blur' }],
    zone: [{ required: true, message: '请选择专区', trigger: 'change' }]
}

const sparePartDialogVisible = ref(false)
const locSpareParts = ref<any[]>([])
const currentLoc = ref<any>({})

const filteredList = computed(() => {
    if (listQuery.zone) {
        return list.value.filter(item => item.zone === listQuery.zone)
    }
    return list.value
})

async function getList() {
    loading.value = true
    try {
        const res = await request.get('/locations')
        list.value = res.data
    } catch (e) {
        console.error('获取货位失败', e)
    } finally {
        loading.value = false
    }
}

function resetTemp() {
    temp.id = undefined
    temp.code = ''
    temp.name = ''
    temp.zone = ''
    temp.capacity = ''
    temp.remark = ''
}

function handleAdd() {
    resetTemp()
    dialogStatus.value = 'create'
    dialogFormVisible.value = true
    nextTick(() => {
        if (dataForm.value) dataForm.value.clearValidate()
    })
}

function createData() {
    if (!dataForm.value) return
    dataForm.value.validate(async (valid) => {
        if (valid) {
            try {
                await request.post('/locations', temp)
                getList()
                dialogFormVisible.value = false
                ElMessage.success('创建成功')
            } catch (e) {
                console.error(e)
            }
        }
    })
}

function handleEdit(row: any) {
    temp.id = row.id
    temp.code = row.code
    temp.name = row.name
    temp.zone = row.zone
    temp.capacity = row.capacity
    temp.remark = row.remark
    dialogStatus.value = 'update'
    dialogFormVisible.value = true
    nextTick(() => {
        if (dataForm.value) dataForm.value.clearValidate()
    })
}

function updateData() {
    if (!dataForm.value) return
    dataForm.value.validate(async (valid) => {
        if (valid) {
            try {
                await request.put(`/locations/${temp.id}`, temp)
                getList()
                dialogFormVisible.value = false
                ElMessage.success('更新成功')
            } catch (e) {
                console.error(e)
            }
        }
    })
}

function handleDelete(row: any) {
    ElMessageBox.confirm('确认删除该货位?', '提示', {
        type: 'warning'
    }).then(async () => {
        try {
            await request.delete(`/locations/${row.id}`)
            getList()
            ElMessage.success('删除成功')
        } catch (e) {
            console.error(e)
        }
    }).catch(() => { })
}

async function handleViewSpareParts(row: any) {
    currentLoc.value = row
    try {
        const res = await request.get(`/locations/${row.id}/spare-parts`)
        locSpareParts.value = res.data
        sparePartDialogVisible.value = true
    } catch (e) {
        console.error(e)
    }
}

onMounted(() => {
    getList()
})
</script>

<style scoped>
.location-profile-container {
    padding: 20px;
}
.title-icon {
  margin-right: 8px;
  font-size: 18px;
}
</style>

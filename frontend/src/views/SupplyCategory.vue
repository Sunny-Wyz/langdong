<template>
    <div class="page-container category-container">
        <el-card>
            <template #header>
              <div class="phead header">
                <span class="title-icon">📂</span>
                <div class="title">供货品类字典</div>
                <div class="head-btn-group">
                <el-button style="float: right;" type="primary" size="small" @click="handleAdd">新增品类</el-button>
                <el-button style="float: right; margin-right: 10px;" size="small"
                    @click="$router.push('/home/supplier-profiles')">返回供应商列表</el-button>
                </div>
              </div>
            </template>

            <el-table v-loading="loading" :data="list" border style="width: 100%">
                <el-table-column prop="code" label="品类编码" width="120" sortable="custom" />
                <el-table-column prop="name" label="品类名称" width="150" />
                <el-table-column prop="description" label="描述" />
                <el-table-column label="操作" width="150" align="center">
                    <template #default="{ row }">
                        <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-dialog :title="dialogStatus === 'create' ? '新增品类' : '编辑品类'" v-model="dialogFormVisible" width="400px">
            <el-form ref="dataForm" :rules="rules" :model="temp" label-position="right" label-width="80px">
                <el-form-item label="编码" prop="code">
                    <el-input v-model="temp.code" />
                </el-form-item>
                <el-form-item label="名称" prop="name">
                    <el-input v-model="temp.name" />
                </el-form-item>
                <el-form-item label="描述" prop="description">
                    <el-input type="textarea" v-model="temp.description" :rows="3" />
                </el-form-item>
            </el-form>
            <template #footer>
              <div class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
              </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'

const router = useRouter()

const list = ref<any[]>([])
const loading = ref(false)
const dialogFormVisible = ref(false)
const dialogStatus = ref('')
const dataForm = ref<FormInstance | null>(null)

interface TempCat {
    id?: number
    code: string
    name: string
    description: string
}

const temp = reactive<TempCat>({
    id: undefined,
    code: '',
    name: '',
    description: ''
})

const rules = {
    code: [{ required: true, message: '编码必填', trigger: 'blur' }],
    name: [{ required: true, message: '名称必填', trigger: 'blur' }]
}

async function getList() {
    loading.value = true
    try {
        const res = await request.get('/supply-categories')
        list.value = res.data
    } catch (e) {
        console.error(e)
    } finally {
        loading.value = false
    }
}

function resetTemp() {
    temp.id = undefined
    temp.code = ''
    temp.name = ''
    temp.description = ''
}

function handleAdd() {
    resetTemp()
    dialogStatus.value = 'create'
    dialogFormVisible.value = true
}

function createData() {
    if (!dataForm.value) return
    dataForm.value.validate(async (valid) => {
        if (valid) {
            try {
                await request.post('/supply-categories', temp)
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
    temp.description = row.description
    dialogStatus.value = 'update'
    dialogFormVisible.value = true
}

function updateData() {
    if (!dataForm.value) return
    dataForm.value.validate(async (valid) => {
        if (valid) {
            try {
                await request.put(`/supply-categories/${temp.id}`, temp)
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
    ElMessageBox.confirm('确认删除?', '提示', { type: 'warning' }).then(async () => {
        try {
            await request.delete(`/supply-categories/${row.id}`)
            getList()
            ElMessage.success('删除成功')
        } catch (e) {
            console.error(e)
        }
    }).catch(() => {})
}

onMounted(() => {
    getList()
})
</script>

<style scoped>
.category-container {
    padding: 20px;
}
.title-icon {
  margin-right: 8px;
  font-size: 18px;
}
</style>

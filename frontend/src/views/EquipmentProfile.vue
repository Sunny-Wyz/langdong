<template>
    <div class="page-container equipment-profile-container">
        <el-card class="box-card">
            <template #header>
              <div class="phead header">
                <span class="title-icon">⚙️</span>
                <div class="title">设备档案管理</div>
                <div class="head-btn-group">
                <el-button style="float: right; margin-left: 10px;" type="primary" size="small" @click="handleAdd">
                    新增设备
                </el-button>
                </div>
              </div>
            </template>

            <!-- 设备表格 -->
            <el-table v-loading="loading" :data="list" border style="width: 100%; margin-top: 15px;">
                <el-table-column prop="code" label="设备编码" width="120" sortable="custom" />
                <el-table-column prop="name" label="设备名称" min-width="150" />
                <el-table-column prop="model" label="规格型号" width="120" />
                <el-table-column prop="department" label="所属产线/部门" width="150" />
                <el-table-column prop="status" label="状态" width="100" align="center" >
                    <template #default="{ row }">
                        <el-tag :type="row.status === '正常' ? 'success' : 'warning'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" show-overflow-tooltip />
                <el-table-column label="操作" width="280" fixed="right" align="center">
                    <template #default="{ row }">
                        <el-button type="success" size="small" @click="handleManageSpareParts(row)">配套备件</el-button>
                        <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 新增/编辑设备弹窗 -->
        <el-dialog :title="dialogStatus === 'create' ? '新增设备' : '编辑设备'" v-model="dialogFormVisible" width="500px">
            <el-form ref="dataForm" :rules="rules" :model="temp" label-position="right" label-width="110px">
                <el-form-item label="设备编码" prop="code">
                    <el-input v-model="temp.code" placeholder="如: EQ-001" />
                </el-form-item>
                <el-form-item label="设备名称" prop="name">
                    <el-input v-model="temp.name" placeholder="请输入设备名称" />
                </el-form-item>
                <el-form-item label="规格型号" prop="model">
                    <el-input v-model="temp.model" placeholder="请输入型号参数" />
                </el-form-item>
                <el-form-item label="所属产线/部门">
                    <el-input v-model="temp.department" placeholder="如: 冲压车间" />
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                    <el-select v-model="temp.status" placeholder="请选择状态" style="width: 100%;">
                        <el-option label="正常" value="正常" />
                        <el-option label="维修中" value="维修中" />
                        <el-option label="报废" value="报废" />
                    </el-select>
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

        <!-- 配套备件管理弹窗 -->
        <el-dialog :title="'【' + currentEq.name + '】配套备件管理'" v-model="sparePartDialogVisible" width="800px">
            <div style="margin-bottom: 20px; text-align: right;">
                <el-select v-model="selectedNewSparePart" placeholder="请选择要加入配置的备件" filterable
                    style="width: 400px; margin-right: 10px;">
                    <el-option v-for="sp in allSpareParts" :key="sp.id" :label="sp.name + ' (' + sp.model + ')'"
                        :value="sp.id" />
                </el-select>
                <el-button type="primary" @click="addLinkedSparePart">添加关联</el-button>
            </div>

            <el-table :data="linkedSpareParts" border style="width: 100%" v-loading="spLoading">
                <el-table-column prop="name" label="备件名称" />
                <el-table-column prop="model" label="型号" />
                <el-table-column prop="category" label="类别" />
                <el-table-column label="目前库存" width="100" >
                    <template #default="{ row }">
                        {{ row.quantity }} {{ row.unit }}
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                    <template #default="{ row }">
                        <el-button type="danger" size="small" @click="removeLinkedSparePart(row)">移除</el-button>
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
import { ref, reactive, onMounted, nextTick } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'

const list = ref<any[]>([])
const loading = ref(true)
const dialogFormVisible = ref(false)
const dialogStatus = ref('')
const dataForm = ref<FormInstance | null>(null)

interface TempEq {
    id?: number
    code: string
    name: string
    model: string
    department: string
    status: string
    remark: string
}

const temp = reactive<TempEq>({
    id: undefined,
    code: '',
    name: '',
    model: '',
    department: '',
    status: '正常',
    remark: ''
})

const rules = {
    code: [{ required: true, message: '设备编码必填', trigger: 'blur' }],
    name: [{ required: true, message: '设备名称必填', trigger: 'blur' }]
}

// 备件关联
const sparePartDialogVisible = ref(false)
const currentEq = ref<any>({})
const linkedSpareParts = ref<any[]>([])
const allSpareParts = ref<any[]>([])
const selectedNewSparePart = ref<number | null>(null)
const spLoading = ref(false)

async function getList() {
    loading.value = true
    try {
        const res = await request.get('/equipments')
        list.value = res.data
    } catch (e) {
        console.error('获取设备列表失败', e)
    } finally {
        loading.value = false
    }
}

async function getAllSpareParts() {
    try {
        const res = await request.get('/spare-parts')
        allSpareParts.value = res.data
    } catch (e) {
        console.error(e)
    }
}

function resetTemp() {
    temp.id = undefined
    temp.code = ''
    temp.name = ''
    temp.model = ''
    temp.department = ''
    temp.status = '正常'
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
                await request.post('/equipments', temp)
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
    temp.model = row.model
    temp.department = row.department
    temp.status = row.status
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
                await request.put(`/equipments/${temp.id}`, temp)
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
    ElMessageBox.confirm('确认删除该设备台账? 如果有关联 of 备件也将同时解除绑定', '提示', {
        type: 'warning'
    }).then(async () => {
        try {
            await request.delete(`/equipments/${row.id}`)
            getList()
            ElMessage.success('删除成功')
        } catch (e) {
            console.error(e)
        }
    }).catch(() => { })
}

function handleManageSpareParts(row: any) {
    currentEq.value = row
    selectedNewSparePart.value = null
    sparePartDialogVisible.value = true
    refreshLinkedSpareParts()
}

async function refreshLinkedSpareParts() {
    spLoading.value = true
    try {
        const res = await request.get(`/equipments/${currentEq.value.id}/spare-parts`)
        linkedSpareParts.value = res.data
    } catch (e) {
        console.error(e)
    } finally {
        spLoading.value = false
    }
}

async function addLinkedSparePart() {
    if (!selectedNewSparePart.value) {
        ElMessage.warning('请先从下拉框选择一个备件')
        return
    }
    try {
        await request.post(`/equipments/${currentEq.value.id}/spare-parts`, {
            sparePartId: selectedNewSparePart.value
        })
        ElMessage.success('关联成功')
        selectedNewSparePart.value = null
        refreshLinkedSpareParts()
    } catch (e) {
        console.error(e)
    }
}

function removeLinkedSparePart(spRow: any) {
    ElMessageBox.confirm(`确认解除与备件 [${spRow.name}] 的关联吗？`, '移除关联', {
        type: 'warning'
    }).then(async () => {
        try {
            await request.delete(`/equipments/${currentEq.value.id}/spare-parts/${spRow.id}`)
            ElMessage.success('解除关联成功')
            refreshLinkedSpareParts()
        } catch (e) {
            console.error(e)
        }
    }).catch(() => { })
}

onMounted(() => {
    getList()
    getAllSpareParts()
})
</script>

<style scoped>
.equipment-profile-container {
    padding: 20px;
}
.title-icon {
  margin-right: 8px;
  font-size: 18px;
}
</style>

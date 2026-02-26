<template>
    <div class="equipment-profile-container">
        <el-card class="box-card">
            <div slot="header" class="clearfix">
                <span>设备档案管理</span>
                <el-button style="float: right; margin-left: 10px;" type="primary" size="small" @click="handleAdd">
                    新增设备
                </el-button>
            </div>

            <!-- 设备表格 -->
            <el-table v-loading="loading" :data="list" border style="width: 100%; margin-top: 15px;">
                <el-table-column prop="code" label="设备编码" width="120" />
                <el-table-column prop="name" label="设备名称" min-width="150" />
                <el-table-column prop="model" label="规格型号" width="120" />
                <el-table-column prop="department" label="所属产线/部门" width="150" />
                <el-table-column prop="status" label="状态" width="100" align="center">
                    <template slot-scope="{row}">
                        <el-tag :type="row.status === '正常' ? 'success' : 'warning'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" show-overflow-tooltip />
                <el-table-column label="操作" width="280" fixed="right" align="center">
                    <template slot-scope="{row}">
                        <el-button type="success" size="mini" @click="handleManageSpareParts(row)">配套备件</el-button>
                        <el-button type="primary" size="mini" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="mini" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 新增/编辑设备弹窗 -->
        <el-dialog :title="dialogStatus === 'create' ? '新增设备' : '编辑设备'" :visible.sync="dialogFormVisible" width="500px">
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
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
            </div>
        </el-dialog>

        <!-- 配套备件管理弹窗 -->
        <el-dialog :title="'【' + currentEq.name + '】配套备件管理'" :visible.sync="sparePartDialogVisible" width="800px">

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
                <el-table-column label="目前库存" width="100">
                    <template slot-scope="{row}">
                        {{ row.quantity }} {{ row.unit }}
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                    <template slot-scope="{row}">
                        <el-button type="danger" size="mini" @click="removeLinkedSparePart(row)">移除</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div slot="footer" class="dialog-footer">
                <el-button @click="sparePartDialogVisible = false">关闭</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request'

export default {
    name: 'EquipmentProfile',
    data() {
        return {
            list: [],
            loading: true,
            dialogFormVisible: false,
            dialogStatus: '',
            temp: {
                id: undefined,
                code: '',
                name: '',
                model: '',
                department: '',
                status: '正常',
                remark: ''
            },
            rules: {
                code: [{ required: true, message: '设备编码必填', trigger: 'blur' }],
                name: [{ required: true, message: '设备名称必填', trigger: 'blur' }]
            },

            // 备件关联
            sparePartDialogVisible: false,
            currentEq: {},
            linkedSpareParts: [],
            allSpareParts: [],
            selectedNewSparePart: null,
            spLoading: false
        }
    },
    created() {
        this.getList()
        this.getAllSpareParts()
    },
    methods: {
        getList() {
            this.loading = true
            request.get('/equipments').then(res => {
                this.list = res.data
                this.loading = false
            }).catch(() => {
                this.loading = false
            })
        },
        getAllSpareParts() {
            request.get('/spare-parts').then(res => {
                this.allSpareParts = res.data
            })
        },
        resetTemp() {
            this.temp = {
                id: undefined,
                code: '',
                name: '',
                model: '',
                department: '',
                status: '正常',
                remark: ''
            }
        },
        handleAdd() {
            this.resetTemp()
            this.dialogStatus = 'create'
            this.dialogFormVisible = true
            this.$nextTick(() => {
                this.$refs['dataForm'].clearValidate()
            })
        },
        createData() {
            this.$refs['dataForm'].validate((valid) => {
                if (valid) {
                    request.post('/equipments', this.temp).then(() => {
                        this.getList()
                        this.dialogFormVisible = false
                        this.$message.success('创建成功')
                    })
                }
            })
        },
        handleEdit(row) {
            this.temp = Object.assign({}, row)
            this.dialogStatus = 'update'
            this.dialogFormVisible = true
            this.$nextTick(() => {
                this.$refs['dataForm'].clearValidate()
            })
        },
        updateData() {
            this.$refs['dataForm'].validate((valid) => {
                if (valid) {
                    request.put(`/equipments/${this.temp.id}`, this.temp).then(() => {
                        this.getList()
                        this.dialogFormVisible = false
                        this.$message.success('更新成功')
                    })
                }
            })
        },
        handleDelete(row) {
            this.$confirm('确认删除该设备台账? 如果有关联的备件也将同时解除绑定', '提示', {
                type: 'warning'
            }).then(() => {
                request.delete(`/equipments/${row.id}`).then(() => {
                    this.getList()
                    this.$message.success('删除成功')
                })
            }).catch(() => { })
        },

        // 关联操作逻辑
        handleManageSpareParts(row) {
            this.currentEq = row
            this.selectedNewSparePart = null
            this.sparePartDialogVisible = true
            this.refreshLinkedSpareParts()
        },
        refreshLinkedSpareParts() {
            this.spLoading = true
            request.get(`/equipments/${this.currentEq.id}/spare-parts`).then(res => {
                this.linkedSpareParts = res.data
                this.spLoading = false
            })
        },
        addLinkedSparePart() {
            if (!this.selectedNewSparePart) {
                this.$message.warning("请先从下拉框选择一个备件")
                return
            }
            request.post(`/equipments/${this.currentEq.id}/spare-parts`, {
                sparePartId: this.selectedNewSparePart
            }).then(() => {
                this.$message.success("关联成功")
                this.selectedNewSparePart = null
                this.refreshLinkedSpareParts()
            })
        },
        removeLinkedSparePart(spRow) {
            this.$confirm(`确认解除与备件 [${spRow.name}] 的关联吗？`, '移除关联', {
                type: 'warning'
            }).then(() => {
                request.delete(`/equipments/${this.currentEq.id}/spare-parts/${spRow.id}`).then(() => {
                    this.$message.success("解除关联成功")
                    this.refreshLinkedSpareParts()
                })
            }).catch(() => { })
        }
    }
}
</script>

<style scoped>
.equipment-profile-container {
    padding: 20px;
}
</style>

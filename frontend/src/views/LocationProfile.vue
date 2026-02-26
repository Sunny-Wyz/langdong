<template>
    <div class="location-profile-container">
        <el-card class="box-card">
            <div slot="header" class="clearfix">
                <span>货位档案管理</span>
                <el-button style="float: right; margin-left: 10px;" type="primary" size="small" @click="handleAdd">
                    新增货位
                </el-button>
            </div>

            <!-- 搜索和筛选器 -->
            <div class="filter-container">
                <el-select v-model="listQuery.zone" placeholder="所在专区" clearable
                    style="width: 200px; margin-right: 15px;">
                    <el-option v-for="i in 12" :key="i" :label="'专区' + i" :value="'专区' + i" />
                </el-select>
            </div>

            <!-- 数据表格 -->
            <el-table v-loading="loading" :data="filteredList" border style="width: 100%; margin-top: 15px;">
                <el-table-column prop="code" label="货位编码" width="120" />
                <el-table-column prop="name" label="货位名称" min-width="150" />
                <el-table-column prop="zone" label="所属专区" width="120">
                    <template slot-scope="{row}">
                        <el-tag>{{ row.zone }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="capacity" label="货位容量" width="120" />
                <el-table-column prop="remark" label="备注" show-overflow-tooltip />
                <el-table-column label="操作" width="250" fixed="right" align="center">
                    <template slot-scope="{row}">
                        <el-button type="info" size="mini" @click="handleViewSpareParts(row)">查看备件</el-button>
                        <el-button type="primary" size="mini" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="mini" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 新增/编辑弹窗 -->
        <el-dialog :title="dialogStatus === 'create' ? '新增货位' : '编辑货位'" :visible.sync="dialogFormVisible" width="500px">
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
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
            </div>
        </el-dialog>

        <!-- 关联备件列表弹窗 -->
        <el-dialog :title="'【' + currentLoc.name + '】下的备件列表'" :visible.sync="sparePartDialogVisible" width="800px">
            <el-table :data="locSpareParts" border style="width: 100%">
                <el-table-column prop="name" label="备件名称" />
                <el-table-column prop="model" label="型号" />
                <el-table-column prop="category" label="类别" />
                <el-table-column label="库存" width="100">
                    <template slot-scope="{row}">
                        {{ row.quantity }} {{ row.unit }}
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
    name: 'LocationProfile',
    data() {
        return {
            list: [],
            loading: true,
            listQuery: {
                zone: ''
            },
            dialogFormVisible: false,
            dialogStatus: '',
            temp: {
                id: undefined,
                code: '',
                name: '',
                zone: '',
                capacity: '',
                remark: ''
            },
            rules: {
                code: [{ required: true, message: '货位编码必填', trigger: 'blur' }],
                name: [{ required: true, message: '货位名称必填', trigger: 'blur' }],
                zone: [{ required: true, message: '请选择专区', trigger: 'change' }]
            },
            // for spare parts dialog
            sparePartDialogVisible: false,
            locSpareParts: [],
            currentLoc: {}
        }
    },
    computed: {
        filteredList() {
            if (this.listQuery.zone) {
                return this.list.filter(item => item.zone === this.listQuery.zone)
            }
            return this.list
        }
    },
    created() {
        this.getList()
    },
    methods: {
        getList() {
            this.loading = true
            request.get('/locations').then(res => {
                this.list = res.data
                this.loading = false
            }).catch(() => {
                this.loading = false
            })
        },
        resetTemp() {
            this.temp = {
                id: undefined,
                code: '',
                name: '',
                zone: '',
                capacity: '',
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
                    request.post('/locations', this.temp).then(() => {
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
                    request.put(`/locations/${this.temp.id}`, this.temp).then(() => {
                        this.getList()
                        this.dialogFormVisible = false
                        this.$message.success('更新成功')
                    })
                }
            })
        },
        handleDelete(row) {
            this.$confirm('确认删除该货位?', '提示', {
                type: 'warning'
            }).then(() => {
                request.delete(`/locations/${row.id}`).then(() => {
                    this.getList()
                    this.$message.success('删除成功')
                })
            }).catch(() => { })
        },
        handleViewSpareParts(row) {
            this.currentLoc = row
            request.get(`/locations/${row.id}/spare-parts`).then(res => {
                this.locSpareParts = res.data
                this.sparePartDialogVisible = true
            })
        }
    }
}
</script>

<style scoped>
.location-profile-container {
    padding: 20px;
}
</style>

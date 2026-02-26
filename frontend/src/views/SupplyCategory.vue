<template>
    <div class="category-container">
        <el-card>
            <div slot="header" class="clearfix">
                <span>供货品类字典</span>
                <el-button style="float: right;" type="primary" size="small" @click="handleAdd">新增品类</el-button>
                <el-button style="float: right; margin-right: 10px;" size="small"
                    @click="$router.push('/home/supplier-profiles')">返回供应商列表</el-button>
            </div>

            <el-table v-loading="loading" :data="list" border style="width: 100%">
                <el-table-column prop="code" label="品类编码" width="120" />
                <el-table-column prop="name" label="品类名称" width="150" />
                <el-table-column prop="description" label="描述" />
                <el-table-column label="操作" width="150" align="center">
                    <template slot-scope="{row}">
                        <el-button type="primary" size="mini" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="mini" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-dialog :title="dialogStatus === 'create' ? '新增品类' : '编辑品类'" :visible.sync="dialogFormVisible" width="400px">
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
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request'

export default {
    name: 'SupplyCategory',
    data() {
        return {
            list: [],
            loading: false,
            dialogFormVisible: false,
            dialogStatus: '',
            temp: { id: undefined, code: '', name: '', description: '' },
            rules: {
                code: [{ required: true, message: '编码必填', trigger: 'blur' }],
                name: [{ required: true, message: '名称必填', trigger: 'blur' }]
            }
        }
    },
    created() { this.getList() },
    methods: {
        getList() {
            this.loading = true
            request.get('/supply-categories').then(res => {
                this.list = res.data
                this.loading = false
            })
        },
        resetTemp() { this.temp = { id: undefined, code: '', name: '', description: '' } },
        handleAdd() {
            this.resetTemp()
            this.dialogStatus = 'create'
            this.dialogFormVisible = true
        },
        createData() {
            this.$refs['dataForm'].validate((valid) => {
                if (valid) {
                    request.post('/supply-categories', this.temp).then(() => {
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
        },
        updateData() {
            this.$refs['dataForm'].validate((valid) => {
                if (valid) {
                    request.put(`/supply-categories/${this.temp.id}`, this.temp).then(() => {
                        this.getList()
                        this.dialogFormVisible = false
                        this.$message.success('更新成功')
                    })
                }
            })
        },
        handleDelete(row) {
            this.$confirm('确认删除?', '提示', { type: 'warning' }).then(() => {
                request.delete(`/supply-categories/${row.id}`).then(() => {
                    this.getList()
                    this.$message.success('删除成功')
                })
            })
        }
    }
}
</script>

<style scoped>
.category-container {
    padding: 20px;
}
</style>

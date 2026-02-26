<template>
    <div class="supplier-container">
        <el-card>
            <div slot="header" class="clearfix">
                <span>供应商档案管理</span>
                <el-button style="float: right;" type="primary" size="small" @click="handleAdd">新增供应商</el-button>
                <el-button style="float: right; margin-right: 10px;" type="info" size="small"
                    @click="goToCategory">品类字典</el-button>
            </div>

            <el-table v-loading="loading" :data="list" border style="width: 100%">
                <el-table-column prop="code" label="编号" width="100" />
                <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
                <el-table-column prop="contactPerson" label="联系人" width="100" />
                <el-table-column prop="phone" label="联系电话" width="120" />
                <el-table-column prop="unifiedSocialCreditCode" label="统一社会信用代码" width="180" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" width="80" align="center">
                    <template slot-scope="{row}">
                        <el-tag :type="row.status === '正常' ? 'success' : 'danger'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="280" align="center">
                    <template slot-scope="{row}">
                        <el-button type="success" size="mini" @click="handleCategories(row)">供货品类</el-button>
                        <el-button type="primary" size="mini" @click="handleEdit(row)">编辑</el-button>
                        <el-button type="danger" size="mini" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 供应商表单 -->
        <el-dialog :title="dialogStatus === 'create' ? '新增供应商' : '编辑供应商'" :visible.sync="dialogFormVisible"
            width="600px">
            <el-form ref="dataForm" :rules="rules" :model="temp" label-position="right" label-width="130px">
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="企业编码" prop="code">
                            <el-input v-model="temp.code" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="企业名称" prop="name">
                            <el-input v-model="temp.name" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-form-item label="统一社会信用代码">
                    <el-input v-model="temp.unifiedSocialCreditCode" />
                </el-form-item>
                <el-form-item label="开户行及账号">
                    <el-input v-model="temp.bankAccountInfo" />
                </el-form-item>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="联系人">
                            <el-input v-model="temp.contactPerson" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="联系电话">
                            <el-input v-model="temp.phone" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-form-item label="联系地址">
                    <el-input v-model="temp.address" />
                </el-form-item>
                <el-form-item label="状态" prop="status">
                    <el-select v-model="temp.status" style="width: 100%;">
                        <el-option label="正常" value="正常" />
                        <el-option label="停用" value="停用" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注">
                    <el-input type="textarea" v-model="temp.remark" :rows="2" />
                </el-form-item>
            </el-form>
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogFormVisible = false">取消</el-button>
                <el-button type="primary" @click="dialogStatus === 'create' ? createData() : updateData()">确定</el-button>
            </div>
        </el-dialog>

        <!-- 分配品类弹窗 -->
        <el-dialog :title="'分配【' + currentSupplier.name + '】的供货品类'" :visible.sync="categoryDialogVisible" width="500px">
            <el-select v-model="selectedCategories" multiple placeholder="请选择该供应商能提供的品类" style="width: 100%;">
                <el-option v-for="cat in allCategories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
            <div slot="footer" class="dialog-footer">
                <el-button @click="categoryDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveCategories">保存关联</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request'

export default {
    name: 'SupplierProfile',
    data() {
        return {
            list: [],
            loading: false,
            dialogFormVisible: false,
            dialogStatus: '',
            temp: {
                id: undefined, code: '', name: '', unifiedSocialCreditCode: '', bankAccountInfo: '',
                contactPerson: '', phone: '', address: '', status: '正常', remark: ''
            },
            rules: {
                code: [{ required: true, message: '必填', trigger: 'blur' }],
                name: [{ required: true, message: '必填', trigger: 'blur' }]
            },

            categoryDialogVisible: false,
            currentSupplier: {},
            allCategories: [],
            selectedCategories: []
        }
    },
    created() {
        this.getList()
        this.getAllCategories()
    },
    methods: {
        goToCategory() {
            this.$router.push('/home/supply-categories')
        },
        getList() {
            this.loading = true
            request.get('/suppliers').then(res => {
                this.list = res.data
                this.loading = false
            })
        },
        getAllCategories() {
            request.get('/supply-categories').then(res => {
                this.allCategories = res.data
            })
        },
        resetTemp() {
            this.temp = { id: undefined, code: '', name: '', unifiedSocialCreditCode: '', bankAccountInfo: '', contactPerson: '', phone: '', address: '', status: '正常', remark: '' }
        },
        handleAdd() {
            this.resetTemp()
            this.dialogStatus = 'create'
            this.dialogFormVisible = true
        },
        createData() {
            this.$refs['dataForm'].validate((valid) => {
                if (valid) {
                    request.post('/suppliers', this.temp).then(() => {
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
                    request.put(`/suppliers/${this.temp.id}`, this.temp).then(() => {
                        this.getList()
                        this.dialogFormVisible = false
                        this.$message.success('更新成功')
                    })
                }
            })
        },
        handleDelete(row) {
            this.$confirm('确认删除?', '提示', { type: 'warning' }).then(() => {
                request.delete(`/suppliers/${row.id}`).then(() => {
                    this.getList()
                    this.$message.success('删除成功')
                })
            })
        },
        handleCategories(row) {
            this.currentSupplier = row
            request.get(`/suppliers/${row.id}/categories`).then(res => {
                this.selectedCategories = res.data.map(c => c.id)
                this.categoryDialogVisible = true
            })
        },
        saveCategories() {
            request.post(`/suppliers/${this.currentSupplier.id}/categories`, { categoryIds: this.selectedCategories }).then(() => {
                this.$message.success('关联保存成功')
                this.categoryDialogVisible = false
            })
        }
    }
}
</script>

<style scoped>
.supplier-container {
    padding: 20px;
}
</style>

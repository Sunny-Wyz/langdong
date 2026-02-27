<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>用户管理</span>
                <el-button type="primary" size="small" @click="handleAdd"
                    v-if="hasPerm('sys:user:add')">新增用户</el-button>
            </div>

            <el-table :data="tableData" border style="width: 100%">
                <el-table-column prop="id" label="ID" width="80"></el-table-column>
                <el-table-column prop="username" label="登录名"></el-table-column>
                <el-table-column prop="name" label="姓名"></el-table-column>
                <el-table-column prop="status" label="状态">
                    <template slot-scope="scope">
                        <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
                            {{ scope.row.status === 1 ? '正常' : '停用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="创建时间" width="180"></el-table-column>
                <el-table-column label="操作" width="220" fixed="right">
                    <template slot-scope="scope">
                        <el-button size="mini" @click="handleEdit(scope.row)">编辑</el-button>
                        <el-button size="mini" type="success" @click="handleAssignRoles(scope.row)">分配角色</el-button>
                        <el-button size="mini" type="danger" @click="handleDelete(scope.row)"
                            v-if="scope.row.username !== 'admin'">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- Dialog -->
        <el-dialog :title="dialogType === 'add' ? '新增用户' : '编辑用户'" :visible.sync="dialogVisible" width="500px">
            <el-form :model="form" ref="form" label-width="80px">
                <el-form-item label="登录名" prop="username">
                    <el-input v-model="form.username" :disabled="dialogType === 'edit'"></el-input>
                </el-form-item>
                <el-form-item label="姓名" prop="name">
                    <el-input v-model="form.name"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="form.password" placeholder="留空则不修改，新增默认123456"></el-input>
                </el-form-item>
                <el-form-item label="角色分配" prop="roleIds" v-if="dialogType === 'add'">
                    <el-select v-model="form.roleIds" multiple placeholder="请选择角色 (可多选)" style="width: 100%">
                        <el-option v-for="role in allRoles" :key="role.id" :label="role.name" :value="role.id"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="状态" prop="status">
                    <el-switch v-model="form.status" :active-value="1" :inactive-value="0"></el-switch>
                </el-form-item>
            </el-form>
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleSubmit">确定</el-button>
            </div>
        </el-dialog>

        <!-- Assign Roles Dialog -->
        <el-dialog title="单独分配角色" :visible.sync="roleDialogVisible" width="400px">
            <el-checkbox-group v-model="selectedRoleIds">
                <el-checkbox v-for="role in allRoles" :label="role.id" :key="role.id">{{ role.name }}</el-checkbox>
            </el-checkbox-group>
            <div slot="footer">
                <el-button @click="roleDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveRoles">确定</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
export default {
    data() {
        return {
            tableData: [],
            dialogVisible: false,
            roleDialogVisible: false,
            dialogType: 'add',
            form: { id: null, username: '', name: '', password: '', status: 1, roleIds: [] },
            allRoles: [],
            selectedRoleIds: [],
            currentUserId: null
        }
    },
    created() {
        this.fetchData()
        this.fetchAllRoles()
    },
    methods: {
        hasPerm(p) {
            return this.$store.state.permissions.includes(p)
        },
        async fetchData() {
            const res = await this.$http.get('/users')
            this.tableData = res.data
        },
        async fetchAllRoles() {
            const res = await this.$http.get('/roles')
            this.allRoles = res.data
        },
        handleAdd() {
            this.dialogType = 'add'
            this.form = { id: null, username: '', name: '', password: '', status: 1, roleIds: [] }
            this.dialogVisible = true
        },
        handleEdit(row) {
            this.dialogType = 'edit'
            this.form = { ...row, password: '' }
            this.dialogVisible = true
        },
        async handleSubmit() {
            if (this.dialogType === 'add') {
                const res = await this.$http.post('/users', this.form).catch(e => {
                    if (e.response) this.$message.error(e.response.data)
                })
                if (res) {
                    this.$message.success('添加成功')
                    this.dialogVisible = false
                    this.fetchData()
                }
            } else {
                await this.$http.put(`/users/${this.form.id}`, this.form)
                this.$message.success('由于状态和密码可能更新，请牢记处理结果')
                this.dialogVisible = false
                this.fetchData()
            }
        },
        async handleDelete(row) {
            this.$confirm('确定删除吗?', '提示').then(async () => {
                await this.$http.delete(`/users/${row.id}`)
                this.$message.success('删除成功')
                this.fetchData()
            }).catch(() => { })
        },
        async handleAssignRoles(row) {
            this.currentUserId = row.id
            const [allRes, ownRes] = await Promise.all([
                this.$http.get('/roles'),
                this.$http.get(`/users/${row.id}/roles`)
            ])
            this.allRoles = allRes.data
            this.selectedRoleIds = ownRes.data
            this.roleDialogVisible = true
        },
        async saveRoles() {
            await this.$http.post(`/users/${this.currentUserId}/roles`, { roleIds: this.selectedRoleIds })
            this.$message.success('角色分配成功')
            this.roleDialogVisible = false
        }
    }
}
</script>

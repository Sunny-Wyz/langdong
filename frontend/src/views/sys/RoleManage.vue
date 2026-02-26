<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>角色管理</span>
                <el-button type="primary" size="small" @click="handleAdd">新增角色</el-button>
            </div>

            <el-table :data="tableData" border style="width: 100%">
                <el-table-column prop="id" label="ID" width="80"></el-table-column>
                <el-table-column prop="code" label="角色编码"></el-table-column>
                <el-table-column prop="name" label="角色名称"></el-table-column>
                <el-table-column prop="remark" label="备注"></el-table-column>
                <el-table-column label="操作" width="220" fixed="right">
                    <template slot-scope="scope">
                        <el-button size="mini" @click="handleEdit(scope.row)">编辑</el-button>
                        <el-button size="mini" type="success" @click="handleAssignMenus(scope.row)">权限分配</el-button>
                        <el-button size="mini" type="danger" @click="handleDelete(scope.row)"
                            v-if="scope.row.code !== 'ADMIN'">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-dialog :title="dialogType === 'add' ? '新增角色' : '编辑角色'" :visible.sync="dialogVisible" width="500px">
            <el-form :model="form" ref="form" label-width="80px">
                <el-form-item label="角色编码" prop="code">
                    <el-input v-model="form.code" :disabled="dialogType === 'edit'"></el-input>
                </el-form-item>
                <el-form-item label="角色名称" prop="name">
                    <el-input v-model="form.name"></el-input>
                </el-form-item>
                <el-form-item label="备注" prop="remark">
                    <el-input v-model="form.remark"></el-input>
                </el-form-item>
            </el-form>
            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleSubmit">确定</el-button>
            </div>
        </el-dialog>

        <el-dialog title="分配菜单与权限" :visible.sync="menuDialogVisible" width="500px">
            <el-tree ref="tree" :data="allMenus" show-checkbox node-key="id"
                :props="{ children: 'children', label: 'name' }">
            </el-tree>
            <div slot="footer">
                <el-button @click="menuDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveMenus">确定</el-button>
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
            menuDialogVisible: false,
            dialogType: 'add',
            form: { id: null, code: '', name: '', remark: '' },
            allMenus: [],
            currentRoleId: null
        }
    },
    created() {
        this.fetchData()
    },
    methods: {
        async fetchData() {
            const res = await this.$http.get('/roles')
            this.tableData = res.data
        },
        handleAdd() {
            this.dialogType = 'add'
            this.form = { id: null, code: '', name: '', remark: '' }
            this.dialogVisible = true
        },
        handleEdit(row) {
            this.dialogType = 'edit'
            this.form = { ...row }
            this.dialogVisible = true
        },
        async handleSubmit() {
            if (this.dialogType === 'add') {
                await this.$http.post('/roles', this.form)
            } else {
                await this.$http.put(`/roles/${this.form.id}`, this.form)
            }
            this.$message.success('操作成功')
            this.dialogVisible = false
            this.fetchData()
        },
        async handleDelete(row) {
            this.$confirm('确定删除吗?', '提示').then(async () => {
                await this.$http.delete(`/roles/${row.id}`)
                this.$message.success('删除成功')
                this.fetchData()
            }).catch(() => { })
        },
        async handleAssignMenus(row) {
            this.currentRoleId = row.id
            const [allRes, ownRes] = await Promise.all([
                this.$http.get('/menus'),
                this.$http.get(`/roles/${row.id}/menus`)
            ])
            this.allMenus = allRes.data
            this.menuDialogVisible = true
            this.$nextTick(() => {
                this.$refs.tree.setCheckedKeys(ownRes.data)
            })
        },
        async saveMenus() {
            const keys = this.$refs.tree.getCheckedKeys()
            const halfKeys = this.$refs.tree.getHalfCheckedKeys()
            const allKeys = [...keys, ...halfKeys]
            await this.$http.post(`/roles/${this.currentRoleId}/menus`, { menuIds: allKeys })
            this.$message.success('菜单分配成功')
            this.menuDialogVisible = false
        }
    }
}
</script>

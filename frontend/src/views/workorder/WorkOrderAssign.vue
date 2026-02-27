<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>在线派工</span>
            </div>

            <el-table :data="workOrders" border stripe v-loading="loading">
                <el-table-column prop="workOrderNo" label="工单编号" width="200"></el-table-column>
                <el-table-column label="故障设备">
                    <template slot-scope="scope">
                        {{ scope.row.deviceName }} <span style="color:#999">({{ scope.row.deviceCode }})</span>
                    </template>
                </el-table-column>
                <el-table-column prop="reporterName" label="报修人" width="100"></el-table-column>
                <el-table-column label="紧急程度" width="90" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="reportTime" label="报修时间" width="160">
                    <template slot-scope="scope">{{ formatTime(scope.row.reportTime) }}</template>
                </el-table-column>
                <el-table-column prop="faultDesc" label="故障描述" show-overflow-tooltip></el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                    <template slot-scope="scope">
                        <el-button type="primary" size="mini" @click="openAssign(scope.row)">派工</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 派工抽屉 -->
        <el-drawer title="在线派工" :visible.sync="drawerVisible" size="480px" :before-close="closeDrawer">
            <div style="padding: 24px">
                <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
                    <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
                    <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
                    <el-descriptions-item label="故障描述">{{ currentWo.faultDesc }}</el-descriptions-item>
                    <el-descriptions-item label="紧急程度">
                        <el-tag :type="levelType(currentWo.faultLevel)" size="small">{{ currentWo.faultLevel }}</el-tag>
                    </el-descriptions-item>
                </el-descriptions>

                <el-form :model="assignForm" :rules="assignRules" ref="assignForm" label-width="110px">
                    <el-form-item label="维修人员" prop="assigneeId">
                        <el-select v-model="assignForm.assigneeId" placeholder="请选择维修人员" style="width: 100%" filterable>
                            <el-option v-for="u in userList" :key="u.id"
                                :label="u.name || u.username" :value="u.id"></el-option>
                        </el-select>
                    </el-form-item>
                    <el-form-item label="计划完成时间" prop="planFinish">
                        <el-date-picker v-model="assignForm.planFinish" type="datetime"
                            placeholder="请选择计划完成时间" style="width: 100%"
                            value-format="yyyy-MM-ddTHH:mm:ss"></el-date-picker>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="submitAssign" :loading="submitting">确认派工</el-button>
                        <el-button @click="closeDrawer">取消</el-button>
                    </el-form-item>
                </el-form>
            </div>
        </el-drawer>
    </div>
</template>

<script>
export default {
    data() {
        return {
            workOrders: [],
            loading: false,
            drawerVisible: false,
            currentWo: {},
            userList: [],
            assignForm: { assigneeId: null, planFinish: null },
            assignRules: {
                assigneeId: [{ required: true, message: '请选择维修人员', trigger: 'change' }],
                planFinish: [{ required: true, message: '请选择计划完成时间', trigger: 'change' }]
            },
            submitting: false
        };
    },
    created() {
        this.loadList();
        this.loadUsers();
    },
    methods: {
        async loadList() {
            this.loading = true;
            try {
                const res = await this.$http.get('/work-orders', { params: { orderStatus: '报修' } });
                this.workOrders = res.data || [];
            } catch (e) {
                this.$message.error('加载工单列表失败');
            } finally {
                this.loading = false;
            }
        },
        async loadUsers() {
            try {
                const res = await this.$http.get('/users');
                this.userList = res.data || [];
            } catch (e) {
                console.error('加载用户列表失败', e);
            }
        },
        openAssign(row) {
            this.currentWo = row;
            this.assignForm = { assigneeId: null, planFinish: null };
            this.drawerVisible = true;
        },
        closeDrawer() {
            this.drawerVisible = false;
        },
        async submitAssign() {
            this.$refs.assignForm.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    await this.$http.put(`/work-orders/${this.currentWo.id}/assign`, this.assignForm);
                    this.$message.success('派工成功！');
                    this.drawerVisible = false;
                    this.loadList();
                } catch (e) {
                    const msg = e.response && e.response.data && e.response.data.message;
                    this.$message.error(msg || '派工失败，请稍后重试');
                } finally {
                    this.submitting = false;
                }
            });
        },
        levelType(level) {
            if (level === '紧急') return 'danger';
            if (level === '一般') return 'warning';
            return 'info';
        },
        formatTime(t) {
            if (!t) return '-';
            return t.replace('T', ' ').substring(0, 16);
        }
    }
};
</script>

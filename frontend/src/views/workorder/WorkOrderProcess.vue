<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>维修过程记录</span>
            </div>

            <el-table :data="workOrders" border stripe v-loading="loading">
                <el-table-column prop="workOrderNo" label="工单编号" width="200"></el-table-column>
                <el-table-column label="故障设备">
                    <template slot-scope="scope">
                        {{ scope.row.deviceName }} <span style="color:#999">({{ scope.row.deviceCode }})</span>
                    </template>
                </el-table-column>
                <el-table-column prop="assigneeName" label="维修人员" width="100"></el-table-column>
                <el-table-column label="紧急程度" width="90" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="计划完成时间" width="160">
                    <template slot-scope="scope">{{ formatTime(scope.row.planFinish) }}</template>
                </el-table-column>
                <el-table-column prop="faultDesc" label="故障描述" show-overflow-tooltip></el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                    <template slot-scope="scope">
                        <el-button type="warning" size="mini" @click="openProcess(scope.row)">填写记录</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 维修记录抽屉 -->
        <el-drawer title="维修过程记录" :visible.sync="drawerVisible" size="520px" :before-close="closeDrawer">
            <div style="padding: 24px">
                <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
                    <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
                    <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
                    <el-descriptions-item label="故障描述">{{ currentWo.faultDesc }}</el-descriptions-item>
                    <el-descriptions-item label="维修人员">{{ currentWo.assigneeName }}</el-descriptions-item>
                </el-descriptions>

                <el-form :model="processForm" :rules="processRules" ref="processForm" label-width="110px">
                    <el-form-item label="故障根因分析" prop="faultCause">
                        <el-input type="textarea" :rows="4" v-model="processForm.faultCause"
                            placeholder="请填写故障的根本原因分析"></el-input>
                    </el-form-item>
                    <el-form-item label="维修方案描述" prop="repairMethod">
                        <el-input type="textarea" :rows="4" v-model="processForm.repairMethod"
                            placeholder="请填写本次维修的具体方案和操作步骤"></el-input>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="submitProcess" :loading="submitting">保存记录</el-button>
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
            processForm: { faultCause: '', repairMethod: '' },
            processRules: {
                faultCause: [{ required: true, message: '请填写故障根因分析', trigger: 'blur' }],
                repairMethod: [{ required: true, message: '请填写维修方案描述', trigger: 'blur' }]
            },
            submitting: false
        };
    },
    created() {
        this.loadList();
    },
    methods: {
        async loadList() {
            this.loading = true;
            try {
                const res = await this.$http.get('/work-orders', { params: { orderStatus: '已派工' } });
                this.workOrders = res.data || [];
            } catch (e) {
                this.$message.error('加载工单列表失败');
            } finally {
                this.loading = false;
            }
        },
        openProcess(row) {
            this.currentWo = row;
            this.processForm = { faultCause: row.faultCause || '', repairMethod: row.repairMethod || '' };
            this.drawerVisible = true;
        },
        closeDrawer() {
            this.drawerVisible = false;
        },
        async submitProcess() {
            this.$refs.processForm.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    await this.$http.put(`/work-orders/${this.currentWo.id}/process`, this.processForm);
                    this.$message.success('维修记录保存成功，工单状态已更新为"维修中"');
                    this.drawerVisible = false;
                    this.loadList();
                } catch (e) {
                    const msg = e.response && e.response.data && e.response.data.message;
                    this.$message.error(msg || '保存失败，请稍后重试');
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

<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>完工确认</span>
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
                    <template slot-scope="scope">
                        <span :style="isOverdue(scope.row) ? 'color:#F56C6C' : ''">
                            {{ formatTime(scope.row.planFinish) }}
                            <el-tag v-if="isOverdue(scope.row)" type="danger" size="mini" style="margin-left:4px">逾期</el-tag>
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                    <template slot-scope="scope">
                        <el-button type="success" size="mini" @click="openComplete(scope.row)">完工确认</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 完工确认抽屉 -->
        <el-drawer title="完工确认" :visible.sync="drawerVisible" size="520px" :before-close="closeDrawer">
            <div style="padding: 24px">
                <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
                    <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
                    <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
                    <el-descriptions-item label="故障根因">{{ currentWo.faultCause }}</el-descriptions-item>
                    <el-descriptions-item label="维修方案">{{ currentWo.repairMethod }}</el-descriptions-item>
                </el-descriptions>

                <el-form :model="completeForm" :rules="completeRules" ref="completeForm" label-width="120px">
                    <el-form-item label="实际完成时间" prop="actualFinish">
                        <el-date-picker v-model="completeForm.actualFinish" type="datetime"
                            placeholder="请选择实际完成时间" style="width: 100%"
                            value-format="yyyy-MM-ddTHH:mm:ss"></el-date-picker>
                    </el-form-item>
                    <el-form-item label="人工费用(元)">
                        <el-input-number v-model="completeForm.laborCost" :min="0" :precision="2"
                            :step="100" style="width: 100%"></el-input-number>
                    </el-form-item>
                    <el-form-item label="外协费用(元)">
                        <el-input-number v-model="completeForm.outsourceCost" :min="0" :precision="2"
                            :step="100" style="width: 100%"></el-input-number>
                    </el-form-item>
                    <el-form-item label="备件费用">
                        <el-alert type="info" :closable="false" show-icon>
                            <span slot="title">备件费用由系统根据关联领用单自动汇总，无需手动填写</span>
                        </el-alert>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="success" @click="submitComplete" :loading="submitting"
                            icon="el-icon-circle-check">确认完工</el-button>
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
            completeForm: { actualFinish: null, laborCost: 0, outsourceCost: 0 },
            completeRules: {
                actualFinish: [{ required: true, message: '请选择实际完成时间', trigger: 'change' }]
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
                const res = await this.$http.get('/work-orders', { params: { orderStatus: '维修中' } });
                this.workOrders = res.data || [];
            } catch (e) {
                this.$message.error('加载工单列表失败');
            } finally {
                this.loading = false;
            }
        },
        openComplete(row) {
            this.currentWo = row;
            this.completeForm = { actualFinish: null, laborCost: 0, outsourceCost: 0 };
            this.drawerVisible = true;
        },
        closeDrawer() {
            this.drawerVisible = false;
        },
        async submitComplete() {
            this.$refs.completeForm.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    await this.$http.put(`/work-orders/${this.currentWo.id}/complete`, this.completeForm);
                    this.$message.success('完工确认成功！工单已归档。');
                    this.drawerVisible = false;
                    this.loadList();
                } catch (e) {
                    const msg = e.response && e.response.data && e.response.data.message;
                    this.$message.error(msg || '操作失败，请稍后重试');
                } finally {
                    this.submitting = false;
                }
            });
        },
        isOverdue(row) {
            if (!row.planFinish) return false;
            return new Date(row.planFinish) < new Date();
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

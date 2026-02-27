<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>审批领用申请</span>
                <el-button type="primary" size="small" @click="loadData" icon="el-icon-refresh"
                    style="float: right">刷新</el-button>
            </div>

            <el-table :data="list" border stripe style="width: 100%" v-loading="loading">
                <el-table-column prop="reqNo" label="单号" width="180"></el-table-column>
                <el-table-column prop="applicantName" label="申请人" width="120"></el-table-column>
                <el-table-column prop="deviceName" label="关联设备" width="180">
                    <template slot-scope="{row}">
                        {{ row.deviceName ? `${row.deviceName}(${row.deviceCode})` : '-' }}
                    </template>
                </el-table-column>
                <el-table-column prop="isUrgent" label="加急" width="80" align="center">
                    <template slot-scope="{row}">
                        <el-tag :type="row.isUrgent ? 'danger' : 'info'" size="small">{{ row.isUrgent ? '是' : '否'
                            }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="applyTime" label="申请时间" width="180">
                    <template slot-scope="{row}">
                        {{ formatTime(row.applyTime) }}
                    </template>
                </el-table-column>
                <el-table-column prop="remark" label="事由" show-overflow-tooltip></el-table-column>
                <el-table-column label="操作" width="120" align="center">
                    <template slot-scope="{row}">
                        <el-button type="primary" size="small" @click="openDrawer(row)">查阅审批</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && list.length === 0" description="暂无待审批的领用申请"></el-empty>
        </el-card>

        <el-drawer title="领用申请单审批" :visible.sync="drawerVisible" size="50%">
            <div style="padding: 20px" v-loading="detailLoading" v-if="currentReq">
                <el-descriptions title="单据基本信息" :column="2" border>
                    <el-descriptions-item label="领用单号">{{ currentReq.reqNo }}</el-descriptions-item>
                    <el-descriptions-item label="申请人">{{ currentReq.applicantName }}</el-descriptions-item>
                    <el-descriptions-item label="关联工单">{{ currentReq.workOrderNo || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="关联设备">{{ currentReq.deviceName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="是否加急">
                        <el-tag :type="currentReq.isUrgent ? 'danger' : 'info'">{{ currentReq.isUrgent ? '是' : '否'
                            }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="申请时间">{{ formatTime(currentReq.applyTime) }}</el-descriptions-item>
                    <el-descriptions-item label="事由说明" :span="2">{{ currentReq.remark || '无' }}</el-descriptions-item>
                </el-descriptions>

                <div style="margin-top: 20px">
                    <h4>申请物料明细</h4>
                    <el-table :data="currentItems" border style="width: 100%">
                        <el-table-column prop="sparePartCode" label="备件编码" width="150"></el-table-column>
                        <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                        <el-table-column prop="applyQty" label="申请数量" width="100" align="center"></el-table-column>
                    </el-table>
                </div>

                <div style="margin-top: 30px">
                    <h4>审批意见批注</h4>
                    <el-input type="textarea" :rows="3" v-model="approveRemark" placeholder="如驳回，请填写驳回理由"></el-input>

                    <div style="margin-top: 20px; text-align: center">
                        <el-button type="danger" @click="submitApproval('REJECT')" icon="el-icon-close"
                            :loading="submitting">驳回重填</el-button>
                        <el-button type="success" @click="submitApproval('APPROVE')" icon="el-icon-check"
                            :loading="submitting">同意放行</el-button>
                    </div>
                </div>
            </div>
        </el-drawer>
    </div>
</template>

<script>
export default {
    data() {
        return {
            list: [],
            loading: false,
            drawerVisible: false,
            detailLoading: false,
            currentReq: null,
            currentItems: [],
            approveRemark: '',
            submitting: false
        }
    },
    created() {
        this.loadData();
    },
    methods: {
        formatTime(t) {
            return t ? t.replace('T', ' ').substring(0, 19) : '-';
        },
        async loadData() {
            this.loading = true;
            try {
                const res = await this.$http.get('/requisitions?status=PENDING');
                this.list = res.data || [];
            } catch (e) {
                this.$message.error('获取待审批列表失败');
            } finally {
                this.loading = false;
            }
        },
        async openDrawer(row) {
            this.currentReq = row;
            this.currentItems = [];
            this.approveRemark = '';
            this.drawerVisible = true;
            this.detailLoading = true;
            try {
                const res = await this.$http.get(`/requisitions/${row.id}`);
                // 重新获取一下详情以确保最新
                if (res.data) {
                    this.currentReq = res.data.info;
                    this.currentItems = res.data.items || [];
                }
            } catch (e) {
                this.$message.error('明细加载失败');
            } finally {
                this.detailLoading = false;
            }
        },
        async submitApproval(action) {
            if (action === 'REJECT' && !this.approveRemark) {
                this.$message.warning('驳回时必须填写审批意见');
                return;
            }
            this.submitting = true;
            try {
                await this.$http.put(`/requisitions/${this.currentReq.id}/approve`, {
                    action: action,
                    remark: this.approveRemark
                });
                this.$message.success(action === 'APPROVE' ? '已同意申请' : '已驳回申请');
                this.drawerVisible = false;
                this.loadData();
            } catch (e) {
                this.$message.error('操作失败，请重试');
            } finally {
                this.submitting = false;
            }
        }
    }
}
</script>

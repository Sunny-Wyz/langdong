<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>领用出库确认</span>
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
                <el-table-column label="操作" width="120" align="center">
                    <template slot-scope="{row}">
                        <el-button type="success" size="small" @click="openDialog(row)">发料出库</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && list.length === 0" description="暂无待出库的申请单"></el-empty>
        </el-card>

        <el-dialog title="领用出库清单确认" :visible.sync="dialogVisible" width="60%">
            <div v-loading="detailLoading" v-if="currentReq">
                <p style="margin-bottom: 15px; color: #606266">
                    <strong>单号：</strong> {{ currentReq.reqNo }} &nbsp;&nbsp;|&nbsp;&nbsp;
                    <strong>申请人：</strong> {{ currentReq.applicantName }}
                </p>

                <el-table :data="currentItems" border style="width: 100%">
                    <el-table-column prop="sparePartCode" label="备件编码" width="150"></el-table-column>
                    <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                    <el-table-column prop="applyQty" label="申请数量" width="100" align="center"></el-table-column>
                    <el-table-column label="实发数量" width="150" align="center">
                        <template slot-scope="{row}">
                            <el-input-number v-model="row.outQty" :min="0" :max="row.applyQty"
                                size="small"></el-input-number>
                        </template>
                    </el-table-column>
                </el-table>

                <div style="margin-top: 10px; color: #E6A23C; font-size: 13px">
                    <i class="el-icon-warning"></i> 提示：若库存不足，可修改实发数量。确认出库后将同步扣减系统总库台账。
                </div>
            </div>
            <span slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitOutbound" :loading="submitting">确认出库</el-button>
            </span>
        </el-dialog>
    </div>
</template>

<script>
export default {
    data() {
        return {
            list: [],
            loading: false,
            dialogVisible: false,
            detailLoading: false,
            currentReq: null,
            currentItems: [],
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
                const res = await this.$http.get('/requisitions?status=APPROVED');
                this.list = res.data || [];
            } catch (e) {
                this.$message.error('获取待出库列表失败');
            } finally {
                this.loading = false;
            }
        },
        async openDialog(row) {
            this.currentReq = row;
            this.currentItems = [];
            this.dialogVisible = true;
            this.detailLoading = true;
            try {
                const res = await this.$http.get(`/requisitions/${row.id}`);
                if (res.data) {
                    this.currentReq = res.data.info;
                    // 初始化默认实发数量 = 申请数量
                    this.currentItems = (res.data.items || []).map(item => ({
                        ...item,
                        outQty: item.applyQty
                    }));
                }
            } catch (e) {
                this.$message.error('明细加载失败');
            } finally {
                this.detailLoading = false;
            }
        },
        async submitOutbound() {
            this.submitting = true;
            try {
                const payload = {
                    items: this.currentItems.map(i => ({
                        itemId: i.id,
                        outQty: i.outQty
                    }))
                };
                await this.$http.put(`/requisitions/${this.currentReq.id}/outbound`, payload);
                this.$message.success('出库成功，库存台账已扣除！');
                this.dialogVisible = false;
                this.loadData();
            } catch (e) {
                this.$message.error('出库失败，请重试');
            } finally {
                this.submitting = false;
            }
        }
    }
}
</script>

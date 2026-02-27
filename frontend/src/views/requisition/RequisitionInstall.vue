<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>领用安装登记</span>
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
                        <el-button type="primary" size="small" @click="openDialog(row)" plain>登记安装</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && list.length === 0" description="暂无待安装登记的单据（需先出库完成）"></el-empty>
        </el-card>

        <el-dialog title="安装位置登记" :visible.sync="dialogVisible" width="60%">
            <div v-loading="detailLoading" v-if="currentReq">
                <el-descriptions :column="3" border style="margin-bottom: 20px">
                    <el-descriptions-item label="领用单号">{{ currentReq.reqNo }}</el-descriptions-item>
                    <el-descriptions-item label="关联设备">{{ currentReq.deviceName || '独立领用' }}</el-descriptions-item>
                    <el-descriptions-item label="出库审批人">{{ currentReq.approverName || '-' }}</el-descriptions-item>
                </el-descriptions>

                <el-table :data="currentItems" border style="width: 100%">
                    <el-table-column prop="sparePartCode" label="备件编码" width="150"></el-table-column>
                    <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                    <el-table-column prop="outQty" label="实领数量" width="100" align="center">
                        <template slot-scope="{row}">
                            <el-tag type="success" size="medium">{{ row.outQty }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="安装位置说明 (如：3号电机左后盖)" min-width="200">
                        <template slot-scope="{row}">
                            <el-input v-model="row.installLoc" placeholder="请输入实际安装部位"></el-input>
                        </template>
                    </el-table-column>
                </el-table>

            </div>
            <span slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitInstall" :loading="submitting">确认安装结案</el-button>
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
                const res = await this.$http.get('/requisitions?status=OUTBOUND');
                this.list = res.data || [];
            } catch (e) {
                this.$message.error('获取待安装登记列表失败');
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
                    this.currentItems = res.data.items || [];
                    // 仅过滤出真正出库了的数据 (有些可能实发为0)
                    this.currentItems = this.currentItems.filter(i => i.outQty > 0);
                }
            } catch (e) {
                this.$message.error('明细加载失败');
            } finally {
                this.detailLoading = false;
            }
        },
        async submitInstall() {
            this.submitting = true;
            try {
                const payload = {
                    items: this.currentItems.map(i => ({
                        itemId: i.id,
                        installLoc: i.installLoc || '机台默认位置'
                    }))
                };
                await this.$http.put(`/requisitions/${this.currentReq.id}/install`, payload);
                this.$message.success('安装登记成功！工单全流程闭环！');
                this.dialogVisible = false;
                this.loadData();
            } catch (e) {
                this.$message.error('安装登记失败，请重试');
            } finally {
                this.submitting = false;
            }
        }
    }
}
</script>

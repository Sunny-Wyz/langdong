<template>
    <div style="padding:24px">
        <el-card>
            <div slot="header">
                <span>预警任务中心</span>
                <el-badge :value="totalCount" :max="99" type="danger" style="margin-left:12px"></el-badge>
            </div>

            <el-tabs v-model="activeTab" @tab-click="onTabClick">
                <!-- 低库存预警 -->
                <el-tab-pane label="低库存预警" name="lowStock">
                    <template slot="label">
                        低库存预警
                        <el-badge v-if="warnings.lowStock.length" :value="warnings.lowStock.length" type="danger"
                            style="margin-left:4px"></el-badge>
                    </template>
                    <el-table :data="warnings.lowStock" border size="small" v-loading="loading">
                        <el-table-column prop="severity" label="紧急程度" width="90" align="center">
                            <template slot-scope="scope">
                                <el-tag :type="scope.row.severity === '紧急' ? 'danger' : 'warning'" size="small">{{
                                    scope.row.severity }}</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="title" label="预警标题"></el-table-column>
                        <el-table-column prop="detail" label="详情"></el-table-column>
                        <el-table-column label="操作" width="100" align="center">
                            <template slot-scope="scope">
                                <el-button size="mini" type="primary" @click="navigate(scope.row)">去处理</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <!-- 逾期工单 -->
                <el-tab-pane name="overdueWO">
                    <template slot="label">
                        逾期工单
                        <el-badge v-if="warnings.overdueWO.length" :value="warnings.overdueWO.length" type="warning"
                            style="margin-left:4px"></el-badge>
                    </template>
                    <el-table :data="warnings.overdueWO" border size="small" v-loading="loading">
                        <el-table-column prop="title" label="预警标题"></el-table-column>
                        <el-table-column prop="detail" label="详情"></el-table-column>
                        <el-table-column label="操作" width="100" align="center">
                            <template slot-scope="scope">
                                <el-button size="mini" type="warning" @click="navigate(scope.row)">去处理</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <!-- 采购逾期 -->
                <el-tab-pane name="overduePO">
                    <template slot="label">
                        采购延期
                        <el-badge v-if="warnings.overduePO.length" :value="warnings.overduePO.length" type="warning"
                            style="margin-left:4px"></el-badge>
                    </template>
                    <el-table :data="warnings.overduePO" border size="small" v-loading="loading">
                        <el-table-column prop="title" label="预警标题"></el-table-column>
                        <el-table-column prop="detail" label="详情"></el-table-column>
                        <el-table-column label="操作" width="100" align="center">
                            <template slot-scope="scope">
                                <el-button size="mini" type="warning" @click="navigate(scope.row)">去处理</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>
            </el-tabs>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            activeTab: 'lowStock',
            loading: false,
            totalCount: 0,
            warnings: { lowStock: [], overdueWO: [], overduePO: [] }
        };
    },
    created() { this.load(); },
    methods: {
        async load() {
            this.loading = true;
            try {
                const res = await this.$http.get('/warnings');
                const d = res.data || {};
                this.warnings.lowStock = d.lowStock || [];
                this.warnings.overdueWO = d.overdueWO || [];
                this.warnings.overduePO = d.overduePO || [];
                this.totalCount = d.totalCount || 0;
            } catch (e) {
                this.$message.error('加载预警数据失败');
            } finally { this.loading = false; }
        },
        navigate(row) {
            if (row.targetPath) this.$router.push(row.targetPath);
        },
        onTabClick() { }
    }
};
</script>

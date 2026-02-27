<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>库存台账</span>
                <el-button type="primary" size="small" @click="refreshData" icon="el-icon-refresh">刷新</el-button>
            </div>

            <el-tabs v-model="activeTab" @tab-click="handleTabClick">
                <!-- Tab 1: 备件总览 -->
                <el-tab-pane label="备件总览" name="summary">
                    <el-table :data="summaryList" border stripe style="width: 100%" v-loading="summaryLoading">
                        <el-table-column prop="sparePartCode" label="备件编码" width="140"></el-table-column>
                        <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                        <el-table-column prop="quantity" label="总库存量（件）" width="150" align="center">
                            <template slot-scope="scope">
                                <el-tag :type="scope.row.quantity > 0 ? 'success' : 'danger'" size="medium">
                                    {{ scope.row.quantity }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="updatedAt" label="最后入库更新时间" width="200">
                            <template slot-scope="scope">
                                {{ formatTime(scope.row.updatedAt) }}
                            </template>
                        </el-table-column>
                    </el-table>
                    <el-empty v-if="!summaryLoading && summaryList.length === 0" description="暂无备件总览数据"></el-empty>
                </el-tab-pane>

                <!-- Tab 2: 货位明细 -->
                <el-tab-pane label="货位明细看板" name="location">
                    <!-- 过滤栏 -->
                    <div style="margin-bottom: 15px; display: flex; gap: 15px;">
                        <el-select v-model="filterZone" placeholder="筛选货位专区" clearable style="width: 180px;">
                            <el-option v-for="i in 12" :key="i" :label="'专区' + i" :value="'专区' + i" />
                        </el-select>
                        <el-input v-model="filterKeyword" placeholder="搜索货位名称或备件名称" clearable
                            style="width: 250px;"></el-input>
                    </div>

                    <el-table :data="filteredLocationList" border stripe style="width: 100%"
                        v-loading="locationLoading">
                        <el-table-column prop="locationZone" label="大区" width="120">
                            <template slot-scope="{row}">
                                <el-tag type="info">{{ row.locationZone || '未指派' }}</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="locationName" label="货位名称 (编码)" width="200">
                            <template slot-scope="{row}">
                                {{ row.locationName }} <span style="color:#909399;font-size:12px;">({{ row.locationCode
                                    }})</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="sparePartName" label="存储备件 (编码)">
                            <template slot-scope="{row}">
                                {{ row.sparePartName }} <span style="color:#909399;font-size:12px;">({{
                                    row.sparePartCode }})</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="quantity" label="货位存放数" width="120" align="center">
                            <template slot-scope="{row}">
                                <span style="font-weight:bold; color:#409EFF">{{ row.quantity }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="updatedAt" label="最新上架时间" width="200">
                            <template slot-scope="{row}">
                                {{ formatTime(row.updatedAt) }}
                            </template>
                        </el-table-column>
                    </el-table>
                    <el-empty v-if="!locationLoading && filteredLocationList.length === 0"
                        description="尚无货位上架明细"></el-empty>
                </el-tab-pane>
            </el-tabs>

        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            activeTab: 'summary',
            // 汇总数据
            summaryList: [],
            summaryLoading: false,
            // 货位数据
            locationList: [],
            locationLoading: false,
            filterZone: '',
            filterKeyword: ''
        }
    },
    computed: {
        filteredLocationList() {
            return this.locationList.filter(item => {
                let matchZone = true;
                if (this.filterZone) {
                    matchZone = item.locationZone === this.filterZone;
                }
                let matchKy = true;
                if (this.filterKeyword) {
                    const kw = this.filterKeyword.toLowerCase();
                    const ln = (item.locationName || '').toLowerCase();
                    const sn = (item.sparePartName || '').toLowerCase();
                    const sc = (item.sparePartCode || '').toLowerCase();
                    matchKy = ln.includes(kw) || sn.includes(kw) || sc.includes(kw);
                }
                return matchZone && matchKy;
            });
        }
    },
    created() {
        this.loadSummaryData();
        // 预加载
        this.loadLocationData();
    },
    methods: {
        formatTime(t) {
            return t ? t.replace('T', ' ').substring(0, 19) : '-';
        },
        refreshData() {
            if (this.activeTab === 'summary') {
                this.loadSummaryData();
            } else {
                this.loadLocationData();
            }
        },
        handleTabClick() {
            if (this.activeTab === 'summary' && this.summaryList.length === 0) {
                this.loadSummaryData();
            } else if (this.activeTab === 'location' && this.locationList.length === 0) {
                this.loadLocationData();
            }
        },
        async loadSummaryData() {
            this.summaryLoading = true;
            try {
                const res = await this.$http.get('/stock-ledger');
                this.summaryList = res.data || [];
            } catch (e) {
                this.$message.error('获取库存汇总失败');
            } finally {
                this.summaryLoading = false;
            }
        },
        async loadLocationData() {
            this.locationLoading = true;
            try {
                const res = await this.$http.get('/stock-ledger/locations');
                this.locationList = res.data || [];
            } catch (e) {
                this.$message.error('获取货位明细失败');
            } finally {
                this.locationLoading = false;
            }
        }
    }
}
</script>

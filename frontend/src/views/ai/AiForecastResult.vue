<template>
    <div class="page-container ai-forecast-container">
        <el-card shadow="hover">
            <div slot="header" class="phead header">
                <i class="el-icon-s-data" />
                <div class="title">需求预测结果</div>
                <div class="head-btn-group">
                    <el-button type="text" @click="goJobCenter" v-if="hasJobCenterPermission">任务中心</el-button>
                    <el-button type="text" :loading="triggeringForecast" @click="triggerForecast" v-if="hasTriggerPermission">手动触发重算</el-button>
                </div>
            </div>

            <div v-if="hasProgressPermission && runStatus" style="margin-bottom: 14px;">
                <el-alert
                    :title="runStatusTitle"
                    :type="runStatusAlertType"
                    :closable="false"
                    show-icon
                >
                    <div v-if="isRunActive" style="margin-top: 8px;">
                        <el-progress :percentage="runStatus.percent || 0" :stroke-width="14" />
                        <div style="font-size: 12px; color: #606266; margin-top: 4px;">
                            阶段：{{ runStatus.stage || '-' }}
                            ｜处理：{{ runStatus.processed || 0 }}/{{ runStatus.total || 0 }}
                            ｜失败：{{ runStatus.failed || 0 }}
                        </div>
                    </div>
                </el-alert>
            </div>

            <!-- 搜索栏 -->
            <el-form :inline="true" :model="searchForm" class="search-form" size="small">
                <el-form-item label="预测目标月份">
                    <el-date-picker v-model="searchForm.month" type="month" placeholder="选择月份" value-format="yyyy-MM"
                        clearable>
                    </el-date-picker>
                </el-form-item>
                <el-form-item label="备件编码">
                    <el-input v-model="searchForm.partCode" placeholder="输入备件编码或名称" clearable></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
                    <el-button icon="el-icon-refresh-left" @click="resetSearch">重置</el-button>
                </el-form-item>
            </el-form>

            <!-- 数据表格 -->
            <el-table :data="tableData" border style="width: 100%" v-loading="loading">
                <el-table-column prop="partCode" label="备件编码" width="120"  sortable="custom"></el-table-column>
                <el-table-column prop="partName" label="备件名称" min-width="150" show-overflow-tooltip ></el-table-column>
                <el-table-column prop="forecastMonth" label="预测月份" width="100" ></el-table-column>
                <el-table-column prop="algoType" label="算法" width="100" >
                    <template slot-scope="scope">
                        <el-tag :type="getAlgoTagType(scope.row.algoType)" size="small">
                            {{ scope.row.algoType }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="predictQty" label="预测消耗量" width="120" >
                    <template slot-scope="scope">
                        <span style="font-weight: bold; color: #409EFF">{{ scope.row.predictQty }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="demand3Months" label="未来3个月累计需求" width="150" >
                    <template slot-scope="scope">
                        <span style="font-weight: bold; color: #67C23A">{{ scope.row.demand3Months ?? 'N/A' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="90% 置信区间" width="180" >
                    <template slot-scope="scope">
                        [ {{ scope.row.lowerBound }} , {{ scope.row.upperBound }} ]
                    </template>
                </el-table-column>
                <el-table-column prop="mase" label="MASE指标" width="100" >
                    <template slot-scope="scope">
                        <span :style="{ color: scope.row.mase > 1.0 ? '#F56C6C' : '#67C23A' }">
                            {{ scope.row.mase || 'N/A' }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column prop="createTime" label="计算时间" width="160" ></el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                    <template slot-scope="scope">
                        <el-button @click="showTrend(scope.row)" type="text" size="small"
                            icon="el-icon-data-line">历史趋势</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination-container">
                <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
                    :current-page="page" :page-sizes="[10, 20, 50, 100]" :page-size="size"
                    layout="total, sizes, prev, pager, next, jumper" :total="total">
                </el-pagination>
            </div>
        </el-card>

        <!-- 趋势图弹窗 -->
        <el-dialog :title="chartTitle" :visible.sync="chartVisible" width="70%" @opened="renderChart">
            <div id="trendChart" style="width: 100%; height: 400px;" v-loading="chartLoading"></div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request'
import * as echarts from 'echarts'

export default {
    name: 'AiForecastResult',
    data() {
        return {
            searchForm: { month: '', partCode: '' },
            tableData: [],
            loading: false,
            page: 1,
            size: 20,
            total: 0,

            // 图表相关
            chartVisible: false,
            chartLoading: false,
            chartTitle: '预测趋势分析',
            chartInstance: null,
            currentChartData: null,
            triggeringForecast: false,
            runStatus: null,
            progressPollTimer: null
        }
    },
    computed: {
        hasTriggerPermission() {
            // 从 vuex 获取权限列表和用户名
            const permissions = this.$store.state.permissions || []
            const username = this.$store.state.username
            return permissions.includes('ai:forecast:trigger') || username === 'admin'
        },
        hasJobCenterPermission() {
            const permissions = this.$store.state.permissions || []
            const username = this.$store.state.username
            return permissions.includes('ai:forecast:list') || username === 'admin'
        },
        hasProgressPermission() {
            const permissions = this.$store.state.permissions || []
            const username = this.$store.state.username
            return permissions.includes('ai:forecast:list') || permissions.includes('ai:forecast:trigger') || username === 'admin'
        },
        isRunActive() {
            return this.runStatus && this.runStatus.status === 'RUNNING'
        },
        runStatusTitle() {
            if (!this.runStatus) {
                return ''
            }
            if (this.runStatus.status === 'RUNNING') {
                return this.runStatus.message || '重算任务执行中'
            }
            if (this.runStatus.status === 'SUCCESS') {
                return this.runStatus.message || '重算任务已完成'
            }
            if (this.runStatus.status === 'FAILED') {
                return this.runStatus.message || '重算任务执行失败'
            }
            return this.runStatus.message || '暂无运行中的重算任务'
        },
        runStatusAlertType() {
            if (!this.runStatus) {
                return 'info'
            }
            if (this.runStatus.status === 'RUNNING') {
                return 'warning'
            }
            if (this.runStatus.status === 'SUCCESS') {
                return 'success'
            }
            if (this.runStatus.status === 'FAILED') {
                return 'error'
            }
            return 'info'
        }
    },
    created() {
        this.fetchData()
        if (this.hasProgressPermission) {
            this.fetchRunStatus(true)
        }
    },
    beforeDestroy() {
        this.stopProgressPolling()
    },
    methods: {
        fetchData() {
            this.loading = true
            request.get('/ai/forecast/result', {
                params: {
                    month: this.searchForm.month,
                    partCode: this.searchForm.partCode,
                    page: this.page,
                    size: this.size
                }
            }).then(res => {
                if (res.data) {
                    this.tableData = res.data.list || []
                    this.total = res.data.total || 0
                }
            }).finally(() => {
                this.loading = false
            })
        },
        handleSearch() {
            this.page = 1
            this.fetchData()
        },
        resetSearch() {
            this.searchForm = { month: '', partCode: '' }
            this.handleSearch()
        },
        handleSizeChange(val) {
            this.size = val
            this.page = 1
            this.fetchData()
        },
        handleCurrentChange(val) {
            this.page = val
            this.fetchData()
        },
        triggerForecast() {
            this.$confirm('此操作将启动全量备件特征分析和算法预测任务，该任务耗时较长，是否继续？', '手动触发', {
                confirmButtonText: '确定启动',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                this.triggeringForecast = true
                request.post('/ai/forecast/trigger').then(res => {
                    if (res && res.data && res.data.runStatus) {
                        this.runStatus = res.data.runStatus
                    }
                    this.$message.success(res.data.message || '重算任务已启动')
                    this.startProgressPolling()
                }).catch(error => {
                    const msg = (error && error.response && error.response.data && error.response.data.message)
                        ? error.response.data.message
                        : '触发重算失败'
                    this.$message.error(msg)
                }).finally(() => {
                    this.triggeringForecast = false
                })
            }).catch(() => { })
        },
        fetchRunStatus(silent = true) {
            if (!this.hasProgressPermission) {
                return Promise.resolve()
            }
            return request.get('/ai/forecast/trigger/status')
                .then(res => {
                    this.runStatus = res.data || null
                    if (this.isRunActive && !this.progressPollTimer) {
                        this.startProgressPolling()
                    }
                    if (!this.isRunActive) {
                        this.stopProgressPolling()
                        if (this.runStatus && this.runStatus.status === 'SUCCESS') {
                            this.fetchData()
                        }
                    }
                })
                .catch(error => {
                    if (error && error.response && error.response.status === 403) {
                        this.stopProgressPolling()
                        return
                    }
                    if (!silent) {
                        const msg = (error && error.response && error.response.data && error.response.data.message)
                            ? error.response.data.message
                            : '获取重算进度失败'
                        this.$message.error(msg)
                    }
                })
        },
        startProgressPolling() {
            if (this.progressPollTimer) {
                return
            }
            this.progressPollTimer = setInterval(() => {
                this.fetchRunStatus(true)
            }, 3000)
            this.fetchRunStatus(true)
        },
        stopProgressPolling() {
            if (!this.progressPollTimer) {
                return
            }
            clearInterval(this.progressPollTimer)
            this.progressPollTimer = null
        },
        goJobCenter() {
            this.$router.push('/ai/job-center')
        },
        getAlgoTagType(algo) {
            if (algo === 'RF') return 'success'
            if (algo === 'SBA') return 'warning'
            if (algo === 'FALLBACK') return 'info'
            return ''
        },

        // 图表趋势
        showTrend(row) {
            this.chartTitle = `[${row.partCode}] ${row.partName} - 预测趋势分析`
            this.chartVisible = true
            this.chartLoading = true

            // 调用未实现的一个 API 拿寻历史结果，如果有实现的话
            request.get(`/ai/forecast/result/${row.partCode}`).then(res => {
                this.currentChartData = res.data || []
            }).catch((e) => {
                // Mock data fallback if endpoint missing
                console.warn('Endpoint misconfiguration for history, using self-mock data.')
                this.currentChartData = [row]
            }).finally(() => {
                this.chartLoading = false
                if (this.chartVisible) {
                    this.$nextTick(() => {
                        this.renderChart()
                    })
                }
            })
        },
        renderChart() {
            const dom = document.getElementById('trendChart')
            if (!dom || !this.currentChartData) return

            if (this.chartInstance) {
                this.chartInstance.dispose()
            }
            this.chartInstance = echarts.init(dom)

            const data = this.currentChartData
            const xData = data.map(item => item.forecastMonth)
            const predictData = data.map(item => item.predictQty)
            const lowerData = data.map(item => item.lowerBound)
            const upperData = data.map(item => item.upperBound)

            const option = {
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'cross' }
                },
                legend: {
                    data: ['预测量', '下界', '上界']
                },
                grid: {
                    left: '3%', right: '4%', bottom: '3%', containLabel: true
                },
                xAxis: {
                    type: 'category',
                    boundaryGap: false,
                    data: xData
                },
                yAxis: {
                    type: 'value',
                    name: '需求量 (件)'
                },
                series: [
                    {
                        name: '上界',
                        type: 'line',
                        data: upperData,
                        lineStyle: { opacity: 0 },
                        symbol: 'none'
                    },
                    {
                        name: '下界',
                        type: 'line',
                        data: lowerData,
                        lineStyle: { opacity: 0 },
                        symbol: 'none',
                        areaStyle: {
                            color: '#d9ecff',
                            origin: 'start'
                        }
                    },
                    {
                        name: '预测量',
                        type: 'line',
                        data: predictData,
                        symbol: 'circle',
                        symbolSize: 8,
                        itemStyle: { color: '#409EFF' },
                        lineStyle: { width: 3, color: '#409EFF' }
                    }
                ]
            }
            this.chartInstance.setOption(option)
        }
    }
}
</script>

<style scoped>
.ai-forecast-container {
    padding: 20px;
}

.search-form {
    margin-bottom: 20px;
}

.pagination-container {
    margin-top: 20px;
    text-align: right;
}
</style>

<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>库存台账</span>
                <el-button type="primary" size="small" @click="loadData" icon="el-icon-refresh">刷新</el-button>
            </div>

            <el-table :data="list" border stripe style="width: 100%" v-loading="loading">
                <el-table-column prop="sparePartCode" label="备件编码" width="140"></el-table-column>
                <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                <el-table-column prop="quantity" label="当前库存（件）" width="150" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="scope.row.quantity > 0 ? 'success' : 'danger'" size="medium">
                            {{ scope.row.quantity }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="updatedAt" label="最后更新时间" width="180">
                    <template slot-scope="scope">
                        {{ scope.row.updatedAt ? scope.row.updatedAt.replace('T', ' ').substring(0, 19) : '-' }}
                    </template>
                </el-table-column>
            </el-table>

            <el-empty v-if="!loading && list.length === 0" description="暂无库存记录，请先完成收货入库操作"></el-empty>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            list: [],
            loading: false
        }
    },
    created() {
        this.loadData()
    },
    methods: {
        async loadData() {
            this.loading = true
            try {
                const res = await this.$http.get('/stock-ledger')
                this.list = res.data || []
            } catch (e) {
                this.$message.error('获取库存台账失败')
            } finally {
                this.loading = false
            }
        }
    }
}
</script>

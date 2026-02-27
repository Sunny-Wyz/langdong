<template>
    <div class="shelving-container" style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>货位上架工作台 (Pending Shelving)</span>
                <el-button type="primary" size="small" @click="fetchPendingItems" icon="el-icon-refresh">刷新</el-button>
            </div>

            <!-- 待上架明细列表 -->
            <el-table :data="pendingItems" border style="width: 100%" v-loading="loading">
                <el-table-column prop="receiptCode" label="收货入库单号" width="180"></el-table-column>
                <el-table-column prop="sparePartCode" label="备件编码" width="120"></el-table-column>
                <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                <el-table-column label="上架进度" width="200" align="center">
                    <template slot-scope="{row}">
                        <el-progress :percentage="Math.round((row.shelvedQuantity || 0) / row.actualQuantity * 100)"
                            :format="() => `${row.shelvedQuantity || 0} / ${row.actualQuantity}`"
                            :status="(row.shelvedQuantity || 0) === row.actualQuantity ? 'success' : 'exception'">
                        </el-progress>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="220" align="center" fixed="right">
                    <template slot-scope="{row}">
                        <el-button type="primary" size="mini" @click="handleShelving(row)"
                            :disabled="(row.shelvedQuantity || 0) >= row.actualQuantity">一键分配</el-button>
                        <el-button type="success" size="mini" @click="handlePrintLabel(row)"
                            icon="el-icon-printer">打标签</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 分配货位弹窗 -->
        <el-dialog :title="`分配货位: ${currentItem.sparePartName}`" :visible.sync="dialogVisible" width="600px">
            <div style="margin-bottom: 15px;">
                <span style="font-weight: bold;">入库总数：</span>{{ currentItem.actualQuantity }} 件
                <span style="margin-left: 20px; font-weight: bold;">未分配剩余数：</span>
                <span style="color: #F56C6C;">{{ remainingQty - totalDistributing }}</span> 件
            </div>

            <el-table :data="distributions" border size="small">
                <el-table-column label="选择目标货位">
                    <template slot-scope="scope">
                        <el-select v-model="scope.row.locationId" filterable placeholder="选择货位" style="width: 100%">
                            <el-option v-for="loc in locationOptions" :key="loc.id"
                                :label="`${loc.zone} - ${loc.name} (容量:${loc.capacity})`" :value="loc.id">
                            </el-option>
                        </el-select>
                    </template>
                </el-table-column>
                <el-table-column label="分配数量" width="150">
                    <template slot-scope="scope">
                        <el-input-number v-model="scope.row.putQty" :min="1" :max="remainingQty" size="small"
                            style="width: 100%"></el-input-number>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                    <template slot-scope="scope">
                        <el-button type="danger" icon="el-icon-delete" circle size="mini"
                            @click="removeDistLine(scope.$index)"></el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div style="margin-top: 15px;">
                <el-button type="primary" plain icon="el-icon-plus" size="small" @click="addDistLine">增加货位</el-button>
            </div>

            <div slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitDistribution"
                    :disabled="distributions.length === 0 || remainingQty - totalDistributing < 0">确认上架</el-button>
            </div>
        </el-dialog>

        <!-- 打印标签专属隐形区域 -->
        <div id="print-area" v-show="false">
            <div class="label-container">
                <div class="label-title">货位标签</div>
                <div class="label-row"><strong>备件编码:</strong> {{ printData.sparePartCode }}</div>
                <div class="label-row"><strong>备件名称:</strong> {{ printData.sparePartName }}</div>
                <div class="label-row"><strong>入库批次:</strong> {{ printData.receiptCode }}</div>
                <div class="label-qrcode">
                    <img src="https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=example" alt="QR"
                        width="80" />
                </div>
            </div>
        </div>
    </div>
</template>

<script>
export default {
    data() {
        return {
            pendingItems: [],
            locationOptions: [],
            loading: false,

            // 分配货位弹窗
            dialogVisible: false,
            currentItem: {},
            distributions: [], // [{locationId: null, putQty: 1}]

            // 打印数据
            printData: {}
        }
    },
    computed: {
        remainingQty() {
            return this.currentItem.actualQuantity - (this.currentItem.shelvedQuantity || 0);
        },
        totalDistributing() {
            return this.distributions.reduce((sum, item) => sum + (item.putQty || 0), 0);
        }
    },
    created() {
        this.fetchPendingItems();
        this.fetchLocations();
    },
    methods: {
        async fetchPendingItems() {
            this.loading = true;
            try {
                const res = await this.$http.get('/shelving/pending');
                this.pendingItems = res.data;
            } catch (e) {
                this.$message.error('加载待上架列表失败');
            } finally {
                this.loading = false;
            }
        },
        async fetchLocations() {
            try {
                const res = await this.$http.get('/locations');
                this.locationOptions = res.data;
            } catch (e) {
                // error
            }
        },
        handleShelving(row) {
            this.currentItem = row;
            this.distributions = [
                { locationId: null, putQty: this.remainingQty } // 默认一条线，放入全量剩余数
            ];
            this.dialogVisible = true;
        },
        addDistLine() {
            const left = this.remainingQty - this.totalDistributing;
            this.distributions.push({ locationId: null, putQty: left > 0 ? left : 1 });
        },
        removeDistLine(idx) {
            this.distributions.splice(idx, 1);
        },
        async submitDistribution() {
            // 检查是否有未选货位的
            const invalid = this.distributions.some(d => !d.locationId || !d.putQty);
            if (invalid) {
                return this.$message.warning("请完善每次分配的货位与数量");
            }

            const payload = [{
                stockInItemId: this.currentItem.id,
                distributions: this.distributions
            }];

            try {
                await this.$http.post('/shelving/submit', payload);
                this.$message.success('已成功上架！');
                this.dialogVisible = false;
                this.fetchPendingItems();
            } catch (e) {
                this.$message.error(e.response && e.response.data ? e.response.data : '上架失败');
            }
        },
        handlePrintLabel(row) {
            this.printData = row;
            this.$nextTick(() => {
                const printContent = document.getElementById('print-area').innerHTML;
                const originalContent = document.body.innerHTML;

                document.body.innerHTML = `
          <html>
            <head><title>Print Label</title>
              <style>
                body { padding: 0; margin: 0; }
                .label-container { width: 300px; padding: 15px; border: 2px solid #000; font-family: sans-serif; }
                .label-title { text-align: center; font-size: 20px; font-weight: bold; margin-bottom: 10px; border-bottom: 2px solid #000; padding-bottom: 5px; }
                .label-row { font-size: 14px; margin-bottom: 8px; }
                .label-qrcode { text-align: center; margin-top: 10px; }
              </style>
            </head>
            <body>${printContent}</body>
          </html>
        `;

                window.print();

                // 恢复内容 (生产环境建议用 iframe，这里为简单演示)
                document.body.innerHTML = originalContent;
                window.location.reload();
            });
        }
    }
}
</script>

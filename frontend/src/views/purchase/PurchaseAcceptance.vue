<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header"><span>到货验收</span></div>

            <div v-if="order" style="margin-bottom: 24px">
                <el-descriptions :column="4" border size="small" title="订单信息">
                    <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
                    <el-descriptions-item label="备件">{{ order.sparePartCode }} {{ order.sparePartName
                        }}</el-descriptions-item>
                    <el-descriptions-item label="采购数量">{{ order.orderQty }}</el-descriptions-item>
                    <el-descriptions-item label="供应商">{{ order.supplierName }}</el-descriptions-item>
                    <el-descriptions-item label="单价(元)">{{ order.unitPrice || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="期望到货">{{ order.expectedDate }}</el-descriptions-item>
                    <el-descriptions-item label="当前状态">
                        <el-tag type="primary" size="small">{{ order.orderStatus }}</el-tag>
                    </el-descriptions-item>
                </el-descriptions>
            </div>

            <el-form :model="form" :rules="rules" ref="form" label-width="130px" style="max-width: 600px">
                <el-form-item label="实际到货数量" prop="receivedQty">
                    <el-input-number v-model="form.receivedQty" :min="0" style="width: 160px"></el-input-number>
                </el-form-item>

                <el-form-item label="质量是否合格" prop="qualified">
                    <el-radio-group v-model="form.qualified">
                        <el-radio :label="true">合格 — 验收通过，自动入库</el-radio>
                        <el-radio :label="false">不合格 — 验收失败，退换货</el-radio>
                    </el-radio-group>
                </el-form-item>

                <el-form-item label="验收意见/原因" prop="remark">
                    <el-input type="textarea" :rows="3" v-model="form.remark"
                        :placeholder="form.qualified === false ? '请填写退换货原因' : '选填'"></el-input>
                </el-form-item>

                <el-alert v-if="form.qualified === true" type="success" :closable="false" title="验收通过后将自动生成入库记录，库存将相应增加"
                    style="margin-bottom: 16px"></el-alert>
                <el-alert v-if="form.qualified === false" type="error" :closable="false" title="验收失败，请填写退换货原因，采购员将收到通知"
                    style="margin-bottom: 16px"></el-alert>

                <el-form-item>
                    <el-button type="primary" @click="submitAcceptance" :loading="submitting"
                        :disabled="form.qualified === null">提交验收结果</el-button>
                    <el-button @click="$router.push('/home/purchase-orders')">返回订单列表</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            order: null,
            form: { receivedQty: 0, qualified: null, remark: '' },
            rules: {
                receivedQty: [{ required: true, message: '请输入实际到货数量', trigger: 'blur' }],
                qualified: [{ required: true, message: '请选择验收结果', trigger: 'change' }]
            },
            submitting: false
        };
    },
    created() {
        const orderId = this.$route.query.orderId;
        if (orderId) this.loadOrder(orderId);
    },
    methods: {
        async loadOrder(id) {
            const res = await this.$http.get(`/purchase-orders/${id}`);
            this.order = res.data;
            this.form.receivedQty = this.order.orderQty;
        },
        submitAcceptance() {
            this.$refs.form.validate(async valid => {
                if (!valid) return;
                const confirmMsg = this.form.qualified
                    ? '确认验收通过？系统将自动生成入库记录。'
                    : '确认验收失败并发起退换货流程？';
                await this.$confirm(confirmMsg, '二次确认', { type: 'warning' });
                this.submitting = true;
                try {
                    await this.$http.put(`/purchase-orders/${this.order.id}/accept`, this.form);
                    this.$message.success(this.form.qualified ? '验收通过，入库已完成！' : '已记录验收失败，请跟进退换货。');
                    this.$router.push('/home/purchase-orders');
                } catch (e) {
                    this.$message.error('提交失败，请稍后重试');
                } finally { this.submitting = false; }
            });
        }
    }
};
</script>

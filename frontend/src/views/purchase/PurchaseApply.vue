<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header"><span>发起采购申请</span></div>

            <el-form :model="form" :rules="rules" ref="form" label-width="120px" style="max-width: 700px">
                <el-form-item label="备件" prop="sparePartId">
                    <el-select v-model="form.sparePartId" placeholder="请选择备件" style="width: 100%" filterable
                        @change="onPartChange">
                        <el-option v-for="p in spareParts" :key="p.id" :label="`${p.code}  ${p.name}`"
                            :value="p.id"></el-option>
                    </el-select>
                </el-form-item>

                <el-form-item label="供应商" prop="supplierId">
                    <el-select v-model="form.supplierId" placeholder="请选择供应商" style="width: 100%" filterable>
                        <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"></el-option>
                    </el-select>
                </el-form-item>

                <el-form-item label="采购数量" prop="orderQty">
                    <el-input-number v-model="form.orderQty" :min="1" style="width: 180px"></el-input-number>
                </el-form-item>

                <el-form-item label="期望到货日期" prop="expectedDate">
                    <el-date-picker v-model="form.expectedDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期"
                        style="width: 200px"></el-date-picker>
                </el-form-item>

                <el-form-item label="备注">
                    <el-input type="textarea" :rows="3" v-model="form.remark" placeholder="请输入采购原因或备注"></el-input>
                </el-form-item>

                <el-alert v-if="suggestId" type="info" :closable="false" title="本次采购由系统补货建议触发，提交后将自动更新建议状态为「已采购」"
                    style="margin-bottom: 16px"></el-alert>

                <el-form-item>
                    <el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button>
                    <el-button @click="reset">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            form: { sparePartId: null, supplierId: null, orderQty: 1, expectedDate: '', remark: '' },
            rules: {
                sparePartId: [{ required: true, message: '请选择备件', trigger: 'change' }],
                supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
                orderQty: [{ required: true, message: '请输入采购数量', trigger: 'blur' }],
                expectedDate: [{ required: true, message: '请选择期望到货日期', trigger: 'change' }]
            },
            spareParts: [], suppliers: [],
            submitting: false,
            suggestId: null
        };
    },
    created() {
        this.loadOptions();
        // 从补货建议跳转过来时预填数据
        const q = this.$route.query;
        if (q.suggestId) {
            this.suggestId = Number(q.suggestId);
            this.form.orderQty = Number(q.qty) || 1;
            // 找到对应备件
            this.$nextTick(() => {
                const part = this.spareParts.find(p => p.code === q.partCode);
                if (part) this.form.sparePartId = part.id;
            });
        }
    },
    methods: {
        async loadOptions() {
            const [p, s] = await Promise.all([
                this.$http.get('/spare-parts'),
                this.$http.get('/suppliers')
            ]);
            this.spareParts = p.data || [];
            this.suppliers = s.data || [];
            // 补货建议预填备件（需等 spareParts 加载完）
            if (this.$route.query.partCode) {
                const part = this.spareParts.find(x => x.code === this.$route.query.partCode);
                if (part) this.form.sparePartId = part.id;
            }
        },
        onPartChange() { },
        submit() {
            this.$refs.form.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    const payload = { ...this.form, reorderSuggestId: this.suggestId };
                    await this.$http.post('/purchase-orders', payload);
                    this.$message.success('采购申请提交成功！');
                    this.reset();
                    this.suggestId = null;
                } catch (e) {
                    this.$message.error('提交失败，请稍后重试');
                } finally { this.submitting = false; }
            });
        },
        reset() {
            this.$refs.form.resetFields();
        }
    }
};
</script>

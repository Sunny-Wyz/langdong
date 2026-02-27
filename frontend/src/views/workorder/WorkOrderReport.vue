<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>故障报修</span>
            </div>

            <el-form :model="form" :rules="rules" ref="reportForm" label-width="120px" style="max-width: 800px">
                <el-row :gutter="20">
                    <el-col :span="12">
                        <el-form-item label="故障设备" prop="deviceId">
                            <el-select v-model="form.deviceId" placeholder="请选择故障设备" style="width: 100%" filterable>
                                <el-option v-for="eq in equipmentList" :key="eq.id"
                                    :label="eq.name + ' (' + eq.code + ')'" :value="eq.id"></el-option>
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="紧急程度" prop="faultLevel">
                            <el-radio-group v-model="form.faultLevel">
                                <el-radio-button label="紧急">
                                    <span style="color: #F56C6C">紧急</span>
                                </el-radio-button>
                                <el-radio-button label="一般">一般</el-radio-button>
                                <el-radio-button label="计划">计划</el-radio-button>
                            </el-radio-group>
                        </el-form-item>
                    </el-col>
                </el-row>

                <el-form-item label="故障描述" prop="faultDesc">
                    <el-input type="textarea" :rows="4" v-model="form.faultDesc"
                        placeholder="请详细描述故障现象、发生时间及影响范围"></el-input>
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="submitReport" :loading="submitting"
                        icon="el-icon-s-promotion">提交报修</el-button>
                    <el-button @click="resetForm" icon="el-icon-refresh">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            form: {
                deviceId: null,
                faultLevel: '一般',
                faultDesc: ''
            },
            rules: {
                deviceId: [{ required: true, message: '请选择故障设备', trigger: 'change' }],
                faultLevel: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
                faultDesc: [{ required: true, message: '请填写故障描述', trigger: 'blur' },
                             { min: 5, message: '描述不少于5个字符', trigger: 'blur' }]
            },
            equipmentList: [],
            submitting: false
        };
    },
    created() {
        this.loadEquipments();
    },
    methods: {
        async loadEquipments() {
            try {
                const res = await this.$http.get('/equipments');
                this.equipmentList = res.data || [];
            } catch (e) {
                this.$message.error('加载设备列表失败');
            }
        },
        resetForm() {
            this.$refs.reportForm.resetFields();
            this.form.faultLevel = '一般';
        },
        submitReport() {
            this.$refs.reportForm.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    await this.$http.post('/work-orders/report', this.form);
                    this.$message.success('报修工单提交成功！');
                    this.resetForm();
                } catch (e) {
                    this.$message.error('提交失败，请稍后重试');
                } finally {
                    this.submitting = false;
                }
            });
        }
    }
};
</script>

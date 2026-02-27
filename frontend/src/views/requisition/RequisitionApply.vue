<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>发起领用申请</span>
            </div>

            <el-form :model="form" :rules="rules" ref="applyForm" label-width="120px" style="max-width: 800px">
                <!-- 基础信息 -->
                <el-row :gutter="20">
                    <el-col :span="12">
                        <el-form-item label="关联工单号" prop="workOrderNo">
                            <el-input v-model="form.workOrderNo" placeholder="请输入关联维修工单号（选填）"></el-input>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="关联设备" prop="deviceId">
                            <el-select v-model="form.deviceId" placeholder="请选择关联设备（选填）" style="width: 100%" clearable>
                                <el-option v-for="eq in equipmentList" :key="eq.id"
                                    :label="eq.name + ' (' + eq.code + ')'" :value="eq.id"></el-option>
                            </el-select>
                        </el-form-item>
                    </el-col>
                </el-row>

                <el-row :gutter="20">
                    <el-col :span="12">
                        <el-form-item label="是否紧急" prop="isUrgent">
                            <el-switch v-model="form.isUrgent" active-text="是" inactive-text="否"></el-switch>
                        </el-form-item>
                    </el-col>
                </el-row>

                <el-form-item label="申请备注" prop="remark">
                    <el-input type="textarea" :rows="3" v-model="form.remark" placeholder="请输入用途或备注说明"></el-input>
                </el-form-item>

                <!-- 备件明细列表 -->
                <el-form-item label="申请物料明细" required>
                    <div style="margin-bottom: 10px;">
                        <el-button type="primary" size="small" icon="el-icon-plus"
                            @click="showSparePartDialog">添加备件</el-button>
                    </div>
                    <el-table :data="form.items" border style="width: 100%">
                        <el-table-column prop="sparePartCode" label="备件编码" width="150"></el-table-column>
                        <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                        <el-table-column label="申请数量" width="180">
                            <template slot-scope="scope">
                                <el-input-number v-model="scope.row.applyQty" :min="1" size="small"></el-input-number>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="100" align="center">
                            <template slot-scope="scope">
                                <el-button type="danger" icon="el-icon-delete" circle size="mini"
                                    @click="removeItem(scope.$index)"></el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="submitApply" :loading="submitting">提交申请</el-button>
                    <el-button @click="resetForm">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 备件选择弹窗 -->
        <el-dialog title="选择备件" :visible.sync="dialogVisible" width="60%">
            <el-input v-model="searchKey" placeholder="搜索备件名称或编码" style="width: 250px; margin-bottom: 15px;"
                clearable></el-input>
            <el-table :data="filteredSpareParts" border style="width: 100%" height="400"
                @selection-change="handleSelectionChange" ref="sparePartTable">
                <el-table-column type="selection" width="55" :selectable="checkSelectable"></el-table-column>
                <el-table-column prop="code" label="备件编码" width="150"></el-table-column>
                <el-table-column prop="name" label="备件名称"></el-table-column>
                <el-table-column prop="price" label="参考价格(元)" width="120"></el-table-column>
                <el-table-column prop="stockQuantity" label="当前总库存" width="100">
                    <template slot-scope="scope">
                        <el-tag :type="scope.row.quantity > 0 ? 'success' : 'danger'">{{ scope.row.quantity }}</el-tag>
                    </template>
                </el-table-column>
            </el-table>
            <span slot="footer" class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="confirmSelection">确定引入</el-button>
            </span>
        </el-dialog>
    </div>
</template>

<script>
export default {
    data() {
        return {
            form: {
                workOrderNo: '',
                deviceId: null,
                isUrgent: false,
                remark: '',
                items: []
            },
            rules: {},
            equipmentList: [],
            sparePartList: [],
            searchKey: '',
            dialogVisible: false,
            selectedParts: [],
            submitting: false
        };
    },
    computed: {
        filteredSpareParts() {
            if (!this.searchKey) return this.sparePartList;
            const k = this.searchKey.toLowerCase();
            return this.sparePartList.filter(s =>
                (s.name && s.name.toLowerCase().includes(k)) ||
                (s.code && s.code.toLowerCase().includes(k))
            );
        }
    },
    created() {
        this.loadEquipments();
        this.loadSpareParts();
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
        async loadSpareParts() {
            try {
                const res = await this.$http.get('/spare-parts');
                this.sparePartList = res.data || [];
            } catch (e) {
                this.$message.error('加载备件列表失败');
            }
        },
        showSparePartDialog() {
            this.selectedParts = [];
            this.dialogVisible = true;
            if (this.$refs.sparePartTable) {
                this.$refs.sparePartTable.clearSelection();
            }
        },
        checkSelectable(row) {
            // 已经添加过的不能再选
            return !this.form.items.some(i => i.sparePartId === row.id);
        },
        handleSelectionChange(val) {
            this.selectedParts = val;
        },
        confirmSelection() {
            if (this.selectedParts.length === 0) {
                this.$message.warning('请至少选择一项');
                return;
            }
            this.selectedParts.forEach(p => {
                this.form.items.push({
                    sparePartId: p.id,
                    sparePartCode: p.code,
                    sparePartName: p.name,
                    applyQty: 1
                });
            });
            this.dialogVisible = false;
        },
        removeItem(index) {
            this.form.items.splice(index, 1);
        },
        resetForm() {
            this.$refs.applyForm.resetFields();
            this.form.items = [];
        },
        submitApply() {
            if (this.form.items.length === 0) {
                this.$message.warning('请至少添加一条申请明细！');
                return;
            }
            this.$refs.applyForm.validate(async valid => {
                if (!valid) return;
                this.submitting = true;
                try {
                    await this.$http.post('/requisitions/apply', this.form);
                    this.$message.success('领用申请提交成功！');
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

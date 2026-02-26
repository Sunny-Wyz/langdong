<template>
  <div class="spare-part-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <span class="title">备件列表</span>
      <el-button type="primary" icon="el-icon-plus" @click="dialogVisible = true">增加备件</el-button>
    </div>

    <!-- 备件表格 -->
    <el-table :data="list" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="备件名称" min-width="120" />
      <el-table-column prop="model" label="型号规格" min-width="120" />
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="quantity" label="库存数量" width="90" align="center" />
      <el-table-column prop="unit" label="单位" width="70" align="center" />
      <el-table-column prop="price" label="单价（元）" width="110" align="right">
        <template slot-scope="{ row }">
          {{ row.price != null ? Number(row.price).toFixed(2) : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="supplier" label="供应商" min-width="120" />
      <el-table-column prop="locationId" label="所属货位" min-width="120" show-overflow-tooltip>
        <template slot-scope="{ row }">
          {{ getLocationName(row.locationId) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template slot-scope="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template slot-scope="{ row }">
          <el-button type="primary" size="mini" icon="el-icon-edit" @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" size="mini" icon="el-icon-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 增加/修改备件对话框 -->
    <el-dialog :title="form.id ? '修改备件' : '增加备件'" :visible.sync="dialogVisible" width="560px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="spareForm" label-width="90px">
        <el-form-item label="备件名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入备件名称" />
        </el-form-item>
        <el-form-item label="型号规格" prop="model">
          <el-input v-model="form.model" placeholder="请输入型号规格" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="库存数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" placeholder="个/套/件" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="单价（元）" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-input v-model="form.category" placeholder="请输入类别" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplier">
              <el-input v-model="form.supplier" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属货位" prop="locationId">
              <el-select v-model="form.locationId" placeholder="请选择分配货位" clearable style="width: 100%">
                <el-option v-for="loc in locations" :key="loc.id" :label="loc.name + ' (' + loc.zone + ')'"
                  :value="loc.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'SparePartList',
  data() {
    return {
      list: [],
      locations: [],
      loading: false,
      dialogVisible: false,
      submitting: false,
      form: {
        id: null,
        name: '',
        model: '',
        quantity: 0,
        unit: '个',
        price: null,
        category: '',
        supplier: '',
        remark: '',
        locationId: null
      },
      rules: {
        name: [{ required: true, message: '请输入备件名称', trigger: 'blur' }],
        quantity: [{ required: true, message: '请输入库存数量', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.fetchList()
    this.fetchLocations()
  },
  methods: {
    async fetchLocations() {
      try {
        const res = await request.get('/locations')
        this.locations = res.data
      } catch (e) {
        console.error('获取货位失败', e)
      }
    },
    getLocationName(id) {
      if (!id) return '—'
      const loc = this.locations.find(l => l.id === id)
      return loc ? loc.name : id
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await request.get('/spare-parts')
        this.list = res.data
      } catch (e) {
        this.$message.error('获取备件列表失败')
      } finally {
        this.loading = false
      }
    },
    submitForm() {
      this.$refs.spareForm.validate(async valid => {
        if (!valid) return
        this.submitting = true
        try {
          if (this.form.id) {
            await request.put(`/spare-parts/${this.form.id}`, this.form)
            this.$message.success('备件修改成功')
          } else {
            await request.post('/spare-parts', this.form)
            this.$message.success('备件添加成功')
          }
          this.dialogVisible = false
          this.fetchList()
        } catch (e) {
          this.$message.error('添加失败，请重试')
        } finally {
          this.submitting = false
        }
      })
    },
    resetForm() {
      this.$refs.spareForm && this.$refs.spareForm.resetFields()
      this.form = { id: null, name: '', model: '', quantity: 0, unit: '个', price: null, category: '', supplier: '', remark: '', locationId: null }
    },
    handleEdit(row) {
      this.form = { ...row }
      this.dialogVisible = true
    },
    handleDelete(row) {
      this.$confirm(`确定要删除备件"${row.name}"吗？此操作不可恢复。`, '提示', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          await request.delete(`/spare-parts/${row.id}`)
          this.$message.success('删除成功')
          this.fetchList()
        } catch (e) {
          this.$message.error('删除失败，请重试')
        }
      }).catch(() => { })
    },
    formatDate(val) {
      if (!val) return '—'
      return new Date(val).toLocaleString('zh-CN', { hour12: false })
    }
  }
}
</script>

<style scoped>
.spare-part-container {
  padding: 24px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
</style>

<template>
  <div class="page-container">
    <div class="page-section">
      <div class="phead header">
        <i class="el-icon-s-data" />
        <div class="title">备件列表</div>
        <div class="head-btn-group">
          <el-button size="mini" icon="el-icon-search" @click="handleFilter">查询</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetFilter">重置</el-button>
          <el-button size="mini" type="success" icon="el-icon-upload2" @click="importDialogVisible = true">批量导入</el-button>
          <el-button size="mini" type="primary" icon="el-icon-plus" @click="dialogVisible = true">增加备件</el-button>
        </div>
      </div>

      <div class="filter-card">
        <div class="filter-row">
          <el-input
            v-model="search.keyword"
            placeholder="搜索编码 / 名称 / 型号"
            prefix-icon="el-icon-search"
            clearable
            style="width: 260px"
            @clear="handleFilter"
            @keyup.enter.native="handleFilter"
          />
          <el-select v-model="search.categoryId" placeholder="全部类别" clearable filterable style="width: 190px" @change="handleFilter">
            <template v-if="hasSubCategories">
              <el-option-group v-for="group in categoryOptions" :key="group.id" :label="group.name">
                <el-option v-for="item in group.children" :key="item.id" :label="item.name" :value="item.id" />
              </el-option-group>
            </template>
            <template v-else>
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </template>
          </el-select>
          <span class="total-hint">共 {{ filteredList.length }} 条</span>
        </div>
      </div>
    </div>

    <section id="data-table" class="page-section table-wrap">
      <el-table :data="pagedList" border stripe v-loading="loading" style="width: 100%"
        :default-sort="{ prop: 'code', order: 'ascending' }" @sort-change="handleSortChange">
      <el-table-column type="index" label="#" width="50" :index="indexMethod" />
      <el-table-column prop="code" label="备件编码" width="110" sortable="custom" />
      <el-table-column prop="name" label="备件名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="model" label="型号规格" min-width="120" show-overflow-tooltip />
      <el-table-column prop="categoryId" label="类别" width="110" show-overflow-tooltip>
        <template slot-scope="{ row }">
          {{ getCategoryName(row.categoryId) }}
        </template>
      </el-table-column>
      <el-table-column prop="quantity" label="库存数量" width="90" align="center">
        <template slot-scope="{ row }">
          <span :class="{ 'low-stock': row.quantity <= 5 && row.quantity > 0, 'zero-stock': row.quantity === 0 }">
            {{ row.quantity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="60" align="center" />
      <el-table-column prop="price" label="单价（元）" width="100" align="right">
        <template slot-scope="{ row }">
          {{ row.price != null ? Number(row.price).toFixed(2) : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="supplierId" label="供应商" min-width="120" show-overflow-tooltip>
        <template slot-scope="{ row }">
          {{ getSupplierName(row.supplierId) }}
        </template>
      </el-table-column>
      <el-table-column prop="locationId" label="所属货位" width="140" show-overflow-tooltip>
        <template slot-scope="{ row }">
          {{ getLocationName(row.locationId) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" icon="el-icon-edit" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="small" icon="el-icon-delete" style="color: #F56C6C" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      </el-table>
    </section>

    <div class="pagination-container pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page.sync="pagination.page"
        :page-size.sync="pagination.size"
        :page-sizes="[15, 30, 50, 100]"
        :total="filteredList.length"
      />
    </div>

    <!-- 增加/修改备件对话框 -->
    <el-dialog :title="form.id ? '修改备件' : '增加备件'" :visible.sync="dialogVisible" width="560px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="spareForm" label-width="90px">
        <el-form-item label="备件编码" prop="code" v-if="form.id">
          <el-input v-model="form.code" disabled />
        </el-form-item>
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
            <el-form-item label="类别" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择类别" style="width: 100%" filterable>
                <template v-if="hasSubCategories">
                  <el-option-group v-for="group in categoryOptions" :key="group.id" :label="group.name">
                    <el-option v-for="item in group.children" :key="item.id" :label="item.name" :value="item.id">
                      <span style="float: left">{{ item.name }}</span>
                      <span style="float: right; color: #8492a6; font-size: 13px">{{ item.code }}</span>
                    </el-option>
                  </el-option-group>
                </template>
                <template v-else>
                  <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id">
                    <span style="float: left">{{ item.name }}</span>
                    <span style="float: right; color: #8492a6; font-size: 13px">{{ item.code }}</span>
                  </el-option>
                </template>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable clearable style="width: 100%">
                <el-option v-for="sup in suppliers" :key="sup.id" :label="sup.name" :value="sup.id" />
              </el-select>
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

    <!-- 批量导入对话框 -->
    <el-dialog title="批量导入备件" :visible.sync="importDialogVisible" width="400px">
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :http-request="handleImport"
        :show-file-list="false"
        accept=".xlsx,.xls"
      >
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <div class="el-upload__tip" slot="tip">请上传包含数据的 Excel 文件</div>
      </el-upload>
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
      suppliers: [],
      categories: [],
      loading: false,
      dialogVisible: false,
      importDialogVisible: false,
      submitting: false,
      importing: false,
      search: {
        keyword: '',
        categoryId: null
      },
      pagination: {
        page: 1,
        size: 15
      },
      sortState: {
        prop: 'code',
        order: 'ascending'
      },
      form: {
        id: null,
        code: '',
        name: '',
        model: '',
        quantity: 0,
        unit: '个',
        price: null,
        categoryId: null,
        supplierId: null,
        remark: '',
        locationId: null
      },
      rules: {
        name: [{ required: true, message: '请输入备件名称', trigger: 'blur' }],
        categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
        quantity: [{ required: true, message: '请输入库存数量', trigger: 'blur' }]
      }
    }
  },
  computed: {
    hasSubCategories() {
      return this.categories.some(c => c.parentId)
    },
    categoryOptions() {
      const topLevel = this.categories.filter(c => !c.parentId)
      return topLevel.map(parent => ({
        ...parent,
        children: this.categories.filter(c => c.parentId === parent.id)
      }))
    },
    filteredList() {
      let result = this.list
      if (this.search.categoryId) {
        result = result.filter(r => r.categoryId === this.search.categoryId)
      }
      if (this.search.keyword) {
        const kw = this.search.keyword.toLowerCase()
        result = result.filter(r =>
          (r.code && r.code.toLowerCase().includes(kw)) ||
          (r.name && r.name.toLowerCase().includes(kw)) ||
          (r.model && r.model.toLowerCase().includes(kw))
        )
      }
      return result
    },
    sortedList() {
      const { prop, order } = this.sortState
      if (!prop || !order) {
        return this.filteredList
      }
      const sorted = [...this.filteredList]
      const factor = order === 'ascending' ? 1 : -1
      sorted.sort((a, b) => {
        const left = a[prop]
        const right = b[prop]

        if (left === null || left === undefined) return 1
        if (right === null || right === undefined) return -1

        if (typeof left === 'number' && typeof right === 'number') {
          return (left - right) * factor
        }

        return String(left).localeCompare(String(right), 'zh-Hans-CN') * factor
      })
      return sorted
    },
    pagedList() {
      const start = (this.pagination.page - 1) * this.pagination.size
      return this.sortedList.slice(start, start + this.pagination.size)
    }
  },
  watch: {
    filteredList() {
      this.pagination.page = 1
    }
  },
  created() {
    this.fetchList()
    this.fetchLocations()
    this.fetchSuppliers()
    this.fetchCategories()
  },
  methods: {
    indexMethod(index) {
      return (this.pagination.page - 1) * this.pagination.size + index + 1
    },
    handleFilter() {
      this.pagination.page = 1
    },
    resetFilter() {
      this.search.keyword = ''
      this.search.categoryId = null
      this.pagination.page = 1
    },
    handleSortChange({ prop, order }) {
      this.sortState = {
        prop,
        order
      }
      this.pagination.page = 1
    },
    async fetchLocations() {
      try {
        const res = await request.get('/locations')
        this.locations = res.data
      } catch (e) {
        console.error('获取货位失败', e)
      }
    },
    async fetchSuppliers() {
      try {
        const res = await request.get('/suppliers')
        this.suppliers = res.data
      } catch (e) {
        console.error('获取供应商失败', e)
      }
    },
    async fetchCategories() {
      try {
        const res = await request.get('/spare-categories')
        this.categories = res.data
      } catch (e) {
        console.error('获取分类失败', e)
      }
    },
    getLocationName(id) {
      if (!id) return '—'
      const loc = this.locations.find(l => l.id === id)
      return loc ? loc.name : id
    },
    getSupplierName(id) {
      if (!id) return '—'
      const sup = this.suppliers.find(s => s.id === id)
      return sup ? sup.name : id
    },
    getCategoryName(id) {
      if (!id) return '—'
      const cat = this.categories.find(c => c.id === id)
      return cat ? cat.name : id
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
          this.$message.error('操作失败，请重试')
        } finally {
          this.submitting = false
        }
      })
    },
    resetForm() {
      this.$refs.spareForm && this.$refs.spareForm.resetFields()
      this.form = { id: null, code: '', name: '', model: '', quantity: 0, unit: '个', price: null, categoryId: null, supplierId: null, remark: '', locationId: null }
    },
    async handleImport(params) {
      const formData = new FormData()
      formData.append('file', params.file)
      this.importing = true
      try {
        const res = await request.post('/spare-parts/import', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        const data = res.data || res
        this.$message.success(`导入完成：成功 ${data.successCount} 条，失败 ${data.failCount} 条。${data.failMsgs || ''}`)
        this.importDialogVisible = false
        this.fetchList()
      } catch (e) {
        this.$message.error('导入失败，请检查文件格式或重试')
      } finally {
        this.importing = false
      }
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
    }
  }
}
</script>

<style scoped>
.table-wrap {
  padding: 0 12px 12px;
}

.total-hint {
  color: #909399;
  font-size: 13px;
  margin-left: auto;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  background: #fff;
  border: 1px solid #e8eaee;
  border-radius: 5px;
  box-shadow: 0 0 5px #ecedf2;
}

.low-stock {
  color: #E6A23C;
  font-weight: 600;
}

.zero-stock {
  color: #F56C6C;
  font-weight: 600;
}

@media (max-width: 960px) {
  .table-wrap {
    padding: 0;
    border: 0;
    box-shadow: none;
    background: transparent;
  }

  .pagination-wrap {
    margin-top: 8px;
  }
}
</style>

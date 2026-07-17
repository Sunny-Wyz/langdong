<template>
  <div class="page-container">
    <div class="page-section">
      <div class="phead header">
        <span class="title-icon">📊</span>
        <div class="title">备件列表</div>
        <div class="head-btn-group">
          <el-button size="small" @click="handleFilter">🔍 查询</el-button>
          <el-button size="small" @click="resetFilter">🔄 重置</el-button>
          <el-button v-if="hasImportPermission" size="small" type="success" @click="importDialogVisible = true">📤 批量导入</el-button>
          <el-button v-if="hasAddPermission" size="small" @click="dialogVisible = true">➕ 增加备件</el-button>
        </div>
      </div>

      <div class="filter-card">
        <div class="filter-row">
          <el-input
            v-model="search.keyword"
            placeholder="搜索编码 / 名称 / 型号"
            clearable
            style="width: 260px"
            @clear="handleFilter"
            @keyup.enter="handleFilter"
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
          <template #default="{ row }">
            {{ getCategoryName(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="库存数量" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.quantity <= 5 && row.quantity > 0, 'zero-stock': row.quantity === 0 }">
              {{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="price" label="单价（元）" width="100" align="right">
          <template #default="{ row }">
            {{ row.price != null ? Number(row.price).toFixed(2) : '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="supplierId" label="供应商" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getSupplierName(row.supplierId) }}
          </template>
        </el-table-column>
        <el-table-column prop="locationId" label="所属货位" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getLocationName(row.locationId) }}
          </template>
        </el-table-column>
        <el-table-column v-if="hasAddPermission" label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">✏️ 编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">🗑️ 删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div class="pagination-container pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[15, 30, 50, 100]"
        :total="filteredList.length"
      />
    </div>

    <!-- 增加/修改备件对话框 -->
    <el-dialog :title="form.id ? '修改备件' : '增加备件'" v-model="dialogVisible" width="560px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
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
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog title="批量导入备件" v-model="importDialogVisible" width="400px">
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :http-request="handleImport"
        :show-file-list="false"
        accept=".xlsx,.xls"
      >
        <span class="upload-icon">📁</span>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">请上传包含数据的 Excel 文件</div>
        </template>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()

const hasAddPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('base:spare:add') || username === 'admin'
})

const hasImportPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('base:spare:import') || username === 'admin'
})

// State variables
const list = ref<any[]>([])
const locations = ref<any[]>([])
const suppliers = ref<any[]>([])
const categories = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const submitting = ref(false)
const importing = ref(false)

const search = reactive({
  keyword: '',
  categoryId: null as number | null
})

const pagination = reactive({
  page: 1,
  size: 15
})

const sortState = reactive({
  prop: 'code',
  order: 'ascending'
})

const formRef = ref<FormInstance | null>(null)
const form = reactive({
  id: null as number | null,
  code: '',
  name: '',
  model: '',
  quantity: 0,
  unit: '个',
  price: null as number | null,
  categoryId: null as number | null,
  supplierId: null as number | null,
  remark: '',
  locationId: null as number | null
})

const rules = {
  name: [{ required: true, message: '请输入备件名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入库存数量', trigger: 'blur' }]
}

// Computeds
const hasSubCategories = computed(() => {
  return categories.value.some(c => c.parentId)
})

const categoryOptions = computed(() => {
  const topLevel = categories.value.filter(c => !c.parentId)
  return topLevel.map(parent => ({
    ...parent,
    children: categories.value.filter(c => c.parentId === parent.id)
  }))
})

const filteredList = computed(() => {
  let result = list.value
  if (search.categoryId) {
    result = result.filter(r => r.categoryId === search.categoryId)
  }
  if (search.keyword) {
    const kw = search.keyword.toLowerCase()
    result = result.filter(r =>
      (r.code && r.code.toLowerCase().includes(kw)) ||
      (r.name && r.name.toLowerCase().includes(kw)) ||
      (r.model && r.model.toLowerCase().includes(kw))
    )
  }
  return result
})

const sortedList = computed(() => {
  const { prop, order } = sortState
  if (!prop || !order) {
    return filteredList.value
  }
  const sorted = [...filteredList.value]
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
})

const pagedList = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return sortedList.value.slice(start, start + pagination.size)
})

// Watchers
watch(filteredList, () => {
  pagination.page = 1
})

// Methods
function indexMethod(index: number) {
  return (pagination.page - 1) * pagination.size + index + 1
}

function handleFilter() {
  pagination.page = 1
}

function resetFilter() {
  search.keyword = ''
  search.categoryId = null
  pagination.page = 1
}

function handleSortChange({ prop, order }: { prop: string, order: string }) {
  sortState.prop = prop
  sortState.order = order
  pagination.page = 1
}

async function fetchLocations() {
  try {
    const res = await request.get('/locations')
    locations.value = res.data
  } catch (e) {
    console.error('获取货位失败', e)
  }
}

async function fetchSuppliers() {
  try {
    const res = await request.get('/suppliers')
    suppliers.value = res.data
  } catch (e) {
    console.error('获取供应商失败', e)
  }
}

async function fetchCategories() {
  try {
    const res = await request.get('/spare-categories')
    categories.value = res.data
  } catch (e) {
    console.error('获取分类失败', e)
  }
}

function getLocationName(id: number | null) {
  if (!id) return '—'
  const loc = locations.value.find(l => l.id === id)
  return loc ? loc.name : id
}

function getSupplierName(id: number | null) {
  if (!id) return '—'
  const sup = suppliers.value.find(s => s.id === id)
  return sup ? sup.name : id
}

function getCategoryName(id: number | null) {
  if (!id) return '—'
  const cat = categories.value.find(c => c.id === id)
  return cat ? cat.name : id
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/spare-parts')
    list.value = res.data
  } catch (e) {
    ElMessage.error('获取备件列表失败')
  } finally {
    loading.value = false
  }
}

function submitForm() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.id) {
        await request.put(`/spare-parts/${form.id}`, form)
        ElMessage.success('备件修改成功')
      } else {
        await request.post('/spare-parts', form)
        ElMessage.success('备件添加成功')
      }
      dialogVisible.value = false
      fetchList()
    } catch (e) {
      ElMessage.error('操作失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}

function resetForm() {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.id = null
  form.code = ''
  form.name = ''
  form.model = ''
  form.quantity = 0
  form.unit = '个'
  form.price = null
  form.categoryId = null
  form.supplierId = null
  form.remark = ''
  form.locationId = null
}

function handleEdit(row: any) {
  form.id = row.id
  form.code = row.code
  form.name = row.name
  form.model = row.model
  form.quantity = row.quantity
  form.unit = row.unit
  form.price = row.price
  form.categoryId = row.categoryId
  form.supplierId = row.supplierId
  form.remark = row.remark
  form.locationId = row.locationId
  dialogVisible.value = true
}

function handleDelete(row: any) {
  ElMessageBox.confirm(`确定要删除备件"${row.name}"吗？此操作不可恢复。`, '提示', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/spare-parts/${row.id}`)
      ElMessage.success('删除成功')
      fetchList()
    } catch (e) {
      ElMessage.error('删除失败，请重试')
    }
  }).catch(() => { })
}

async function handleImport(params: any) {
  const formData = new FormData()
  formData.append('file', params.file)
  importing.value = true
  try {
    const res = await request.post('/spare-parts/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const data = res.data || res
    ElMessage.success(`导入完成：成功 ${data.successCount} 条，失败 ${data.failCount} 条。${data.failMsgs || ''}`)
    importDialogVisible.value = false
    fetchList()
  } catch (e) {
    ElMessage.error('导入失败，请检查文件格式或重试')
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  fetchList()
  fetchLocations()
  fetchSuppliers()
  fetchCategories()
})
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

.title-icon {
  margin-right: 8px;
  font-size: 18px;
}

.upload-icon {
  font-size: 40px;
  display: block;
  margin: 20px 0 10px;
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
  font-weight: bold;
}

.zero-stock {
  color: #F56C6C;
  font-weight: bold;
}
</style>

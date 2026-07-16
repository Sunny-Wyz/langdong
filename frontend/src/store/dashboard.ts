import { defineStore } from 'pinia'
import { ref } from 'vue'

// 使用 Pinia Setup Store 管理管理层看板大屏全局筛选状态，如选中的月份等
export const useDashboardStore = defineStore('dashboard', () => {
  const selectedMonth = ref<string>('')

  // 如果 selectedMonth 尚未赋值，自动初始化为当前时间的 yyyy-MM 格式
  if (!selectedMonth.value) {
    const d = new Date()
    selectedMonth.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }

  function setSelectedMonth(month: string) {
    selectedMonth.value = month
  }

  return {
    selectedMonth,
    setSelectedMonth
  }
})

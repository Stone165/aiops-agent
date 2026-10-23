<script setup>
import { computed } from 'vue'

const props = defineProps({
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pages: { type: Number, default: 0 },
  disabled: { type: Boolean, default: false },
  pageSizeOptions: { type: Array, default: () => [10, 20, 50] }
})

const emit = defineEmits(['update:page-num', 'update:page-size'])

const pageCount = computed(() => Math.max(props.pages, 1))

function changePage(nextPage) {
  if (props.disabled) return
  if (nextPage < 1 || nextPage > pageCount.value) return
  emit('update:page-num', nextPage)
}

function changePageSize(event) {
  emit('update:page-size', Number(event.target.value))
}
</script>

<template>
  <div class="pagination">
    <span class="pagination-info">
      共 {{ total }} 条 · 第 {{ pageNum }} / {{ pageCount }} 页
    </span>

    <div class="pagination-actions">
      <select
        class="page-size-select"
        :value="pageSize"
        :disabled="disabled"
        aria-label="每页条数"
        @change="changePageSize"
      >
        <option v-for="size in pageSizeOptions" :key="size" :value="size">
          {{ size }} 条/页
        </option>
      </select>

      <button
        type="button"
        class="button button-light"
        :disabled="disabled || pageNum <= 1"
        @click="changePage(pageNum - 1)"
      >
        上一页
      </button>
      <button
        type="button"
        class="button button-light"
        :disabled="disabled || pageNum >= pageCount"
        @click="changePage(pageNum + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

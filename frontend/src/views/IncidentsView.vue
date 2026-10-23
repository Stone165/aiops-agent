<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createIncident,
  deleteIncident,
  searchIncidents,
  updateIncidentResolution,
  updateIncidentStatus
} from '../api/incidents'
import { useNotice } from '../composables/useNotice'
import { formatDateTime, severityText, statusText, truncate } from '../utils/format'
import PaginationControls from '../components/PaginationControls.vue'
import IncidentDetailDrawer from '../components/IncidentDetailDrawer.vue'

const emit = defineEmits(['diagnose-incident'])
const { notify } = useNotice()

const incidents = ref([])
const total = ref(0)
const pages = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const saving = ref(false)
const detailSaving = ref(false)
const showCreateForm = ref(false)
const selectedIncident = ref(null)

const filters = reactive({
  keyword: '',
  status: '',
  severity: ''
})

const newIncident = reactive({
  title: '',
  description: '',
  deviceId: '',
  severity: 'MEDIUM'
})

let keywordTimer = null

async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (filters.status) params.status = filters.status
    if (filters.severity) params.severity = filters.severity
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()

    const data = await searchIncidents(params)

    if ((!data.records || data.records.length === 0)
      && pageNum.value > 1
      && data.total > 0) {
      pageNum.value = Math.max(1, data.pages || 1)
      return load()
    }

    incidents.value = data.records || []
    total.value = data.total || 0
    pages.value = data.pages || 0
  } catch (error) {
    notify(error.userMessage || '读取工单列表失败。', 'error')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  pageNum.value = 1
  load()
}

function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(applyFilters, 300)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.severity = ''
  applyFilters()
}

function changePage(nextPage) {
  pageNum.value = nextPage
  load()
}

function changePageSize(size) {
  pageSize.value = size
  pageNum.value = 1
  load()
}

async function submitCreate() {
  if (!newIncident.title.trim() || !newIncident.description.trim()) {
    notify('工单标题和故障描述不能为空。', 'error')
    return
  }

  saving.value = true
  try {
    await createIncident({
      title: newIncident.title.trim(),
      description: newIncident.description.trim(),
      deviceId: newIncident.deviceId.trim() || null,
      severity: newIncident.severity
    })

    notify('工单创建成功。')
    newIncident.title = ''
    newIncident.description = ''
    newIncident.deviceId = ''
    newIncident.severity = 'MEDIUM'
    showCreateForm.value = false
    pageNum.value = 1
    await load()
  } catch (error) {
    notify(error.userMessage || '创建工单失败。', 'error')
  } finally {
    saving.value = false
  }
}

function replaceIncident(updated) {
  const index = incidents.value.findIndex(item => item.id === updated.id)
  if (index >= 0) incidents.value[index] = updated
}

async function changeStatus(item, status) {
  if (status === item.status) return

  try {
    const data = await updateIncidentStatus(item.id, status)
    replaceIncident(data.incident)
    if (selectedIncident.value?.id === data.incident.id) {
      selectedIncident.value = data.incident
    }
    notify(`工单 #${item.id} 已更新为“${statusText(data.incident.status)}”。`)
  } catch (error) {
    notify(error.userMessage || '更新工单状态失败。', 'error')
    await load()
  }
}

async function removeIncident(item) {
  const confirmed = window.confirm(
    `确定删除工单 #${item.id}「${item.title}」吗？此操作不可撤销。`
  )
  if (!confirmed) return

  try {
    await deleteIncident(item.id)
    if (selectedIncident.value?.id === item.id) selectedIncident.value = null
    notify(`工单 #${item.id} 已删除。`)
    await load()
  } catch (error) {
    notify(error.userMessage || '删除工单失败。', 'error')
  }
}

function openDetail(item) {
  selectedIncident.value = item
}

async function saveDetail(payload) {
  if (!selectedIncident.value) return

  detailSaving.value = true
  try {
    const data = await updateIncidentResolution(selectedIncident.value.id, payload)
    replaceIncident(data.incident)
    selectedIncident.value = data.incident
    notify('工单处理记录已保存。')
  } catch (error) {
    notify(error.userMessage || '保存处理记录失败。', 'error')
  } finally {
    detailSaving.value = false
  }
}

function diagnose(item) {
  emit('diagnose-incident', item)
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2>故障工单列表</h2>
        <p>管理故障记录、处理状态与优先级</p>
      </div>
      <div class="toolbar-actions">
        <button class="button button-light" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新数据 ↻' }}
        </button>
        <button class="button button-primary" @click="showCreateForm = !showCreateForm">
          {{ showCreateForm ? '取消创建' : '+ 新建工单' }}
        </button>
      </div>
    </div>

    <form v-if="showCreateForm" class="create-form" @submit.prevent="submitCreate">
      <h3>创建故障工单</h3>

      <label for="incident-title">工单标题 *</label>
      <input
        id="incident-title"
        v-model="newIncident.title"
        placeholder="例如：业务接口响应超时"
        required
        maxlength="200"
      />

      <label for="incident-device">设备编号</label>
      <input
        id="incident-device"
        v-model="newIncident.deviceId"
        placeholder="例如：server-001"
        maxlength="100"
      />

      <label for="incident-severity">严重程度</label>
      <select id="incident-severity" v-model="newIncident.severity">
        <option value="LOW">低</option>
        <option value="MEDIUM">中</option>
        <option value="HIGH">高</option>
        <option value="CRITICAL">严重</option>
      </select>

      <label for="incident-description">故障描述 *</label>
      <textarea
        id="incident-description"
        v-model="newIncident.description"
        rows="4"
        placeholder="描述故障现象、影响范围和已观察到的错误"
        required
        maxlength="5000"
      />

      <button class="button button-primary" type="submit" :disabled="saving">
        {{ saving ? '正在保存…' : '保存工单' }}
      </button>
    </form>

    <div class="filter-bar">
      <input
        v-model="filters.keyword"
        class="filter-keyword"
        placeholder="搜索标题、描述或设备编号"
        maxlength="100"
        @input="onKeywordInput"
        @keyup.enter="applyFilters"
      />

      <select v-model="filters.status" aria-label="按状态筛选" @change="applyFilters">
        <option value="">全部状态</option>
        <option value="OPEN">待处理</option>
        <option value="IN_PROGRESS">处理中</option>
        <option value="RESOLVED">已解决</option>
        <option value="CLOSED">已关闭</option>
      </select>

      <select v-model="filters.severity" aria-label="按严重程度筛选" @change="applyFilters">
        <option value="">全部等级</option>
        <option value="LOW">低</option>
        <option value="MEDIUM">中</option>
        <option value="HIGH">高</option>
        <option value="CRITICAL">严重</option>
      </select>

      <button class="button button-primary" @click="applyFilters">筛选</button>
      <button class="button button-light" @click="resetFilters">重置</button>
    </div>

    <div v-if="loading" class="loading">正在加载工单…</div>

    <div v-else-if="incidents.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>工单标题</th>
            <th>设备编号</th>
            <th>严重程度</th>
            <th>状态</th>
            <th>故障描述</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in incidents" :key="item.id">
            <td>{{ item.id }}</td>
            <td class="primary-cell">{{ item.title }}</td>
            <td>{{ item.deviceId || '—' }}</td>
            <td>
              <span class="severity" :class="'sev-' + item.severity">
                {{ severityText(item.severity) }}
              </span>
            </td>
            <td>
              <select
                class="status-select"
                :value="item.status"
                aria-label="修改工单状态"
                @change="changeStatus(item, $event.target.value)"
              >
                <option value="OPEN">待处理</option>
                <option value="IN_PROGRESS">处理中</option>
                <option value="RESOLVED">已解决</option>
                <option value="CLOSED">已关闭</option>
              </select>
            </td>
            <td class="description-cell">{{ truncate(item.description, 60) }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td>
              <div class="row-actions">
                <button class="button button-light button-small" @click="openDetail(item)">
                  详情
                </button>
                <button class="button button-light button-small" @click="diagnose(item)">
                  诊断
                </button>
                <button class="button button-danger button-small" @click="removeIncident(item)">
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">▤</div>
      <strong>没有符合条件的工单</strong>
      <p>调整筛选条件，或创建一条新的故障工单。</p>
    </div>

    <PaginationControls
      v-if="total > 0"
      :page-num="pageNum"
      :page-size="pageSize"
      :total="total"
      :pages="pages"
      :disabled="loading"
      @update:page-num="changePage"
      @update:page-size="changePageSize"
    />

    <IncidentDetailDrawer
      :incident="selectedIncident"
      :saving="detailSaving"
      @close="selectedIncident = null"
      @save="saveDetail"
      @diagnose="diagnose"
    />
  </section>
</template>

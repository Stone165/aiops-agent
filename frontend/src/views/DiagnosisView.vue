<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  fetchDiagnosisRecord,
  fetchDiagnosisRecords,
  runAgentDiagnosis,
  runRuleDiagnosis
} from '../api/diagnosis'
import { useNotice } from '../composables/useNotice'
import { formatDateTime } from '../utils/format'
import PaginationControls from '../components/PaginationControls.vue'

const props = defineProps({
  preset: { type: Object, default: null }
})

const emit = defineEmits(['preset-consumed'])
const { notify } = useNotice()

const mode = ref('agent')
const snapshotMode = ref('LOCAL')
const running = ref(false)
const result = ref(null)

const form = reactive({
  deviceId: 'local-machine',
  description: '本机运行状态检查'
})

const history = ref([])
const historyTotal = ref(0)
const historyPages = ref(0)
const historyPageNum = ref(1)
const historyPageSize = ref(10)
const historyLoading = ref(false)
const historyDeviceId = ref('')

watch(
  () => props.preset,
  async preset => {
    if (!preset) return
    form.deviceId = preset.deviceId || 'local-machine'
    form.description = preset.description || preset.title || '本机运行状态检查'
    mode.value = preset.mode || 'agent'
    snapshotMode.value = preset.snapshotMode || 'LOCAL'
    result.value = null
    emit('preset-consumed')

    if (preset.autoRun) {
      await nextTick()
      await runDiagnosis()
    }
  },
  { immediate: true }
)

async function runDiagnosis(allowFallback = true) {
  if (!form.deviceId.trim() || !form.description.trim()) {
    notify('请填写设备编号和故障描述。', 'error')
    return
  }

  running.value = true
  result.value = null

  try {
    const payload = {
      deviceId: form.deviceId.trim(),
      description: form.description.trim(),
      snapshotMode: snapshotMode.value
    }
    const data = mode.value === 'agent'
      ? await runAgentDiagnosis(payload)
      : await runRuleDiagnosis(payload)

    result.value = data

    if (data.recordId) {
      notify(`诊断完成，已归档为记录 #${data.recordId}。`)
    } else {
      notify('诊断完成，但结果未能归档，请检查数据库状态。', 'info')
    }

    historyPageNum.value = 1
    await loadHistory()
  } catch (error) {
    if (allowFallback && mode.value === 'agent' && error.response?.status === 503) {
      mode.value = 'rule'
      notify('AI 服务未配置或不可用，已自动切换为规则诊断。', 'info')
      running.value = false
      await runDiagnosis(false)
      return
    }

    notify(error.userMessage || '诊断失败，请检查后端与 AI 配置。', 'error')
  } finally {
    running.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const params = {
      pageNum: historyPageNum.value,
      pageSize: historyPageSize.value
    }
    if (historyDeviceId.value.trim()) {
      params.deviceId = historyDeviceId.value.trim()
    }

    const data = await fetchDiagnosisRecords(params)
    history.value = data.records || []
    historyTotal.value = data.total || 0
    historyPages.value = data.pages || 0
  } catch (error) {
    notify(error.userMessage || '读取诊断历史失败。', 'error')
  } finally {
    historyLoading.value = false
  }
}

function filterHistory() {
  historyPageNum.value = 1
  loadHistory()
}

function changeHistoryPage(nextPage) {
  historyPageNum.value = nextPage
  loadHistory()
}

function changeHistoryPageSize(size) {
  historyPageSize.value = size
  historyPageNum.value = 1
  loadHistory()
}

async function viewRecord(id) {
  try {
    const record = await fetchDiagnosisRecord(id)
    result.value = { ...record, recordId: record.id }
    notify(`已加载历史诊断记录 #${id}。`, 'info')
  } catch (error) {
    notify(error.userMessage || '读取诊断记录失败。', 'error')
  }
}

onMounted(loadHistory)
</script>

<template>
  <div class="diagnosis-layout">
    <section class="panel diagnosis-form-panel">
      <div class="panel-heading">
        <div>
          <h2>提交诊断任务</h2>
          <p>
            {{ snapshotMode === 'TEXT'
              ? '仅根据工单描述分析，不读取本机指标'
              : '使用本机实时快照与工具结果进行分析' }}
          </p>
        </div>
        <span class="ai-badge">✳ AI AGENT</span>
      </div>

      <div class="toggle-label">数据来源</div>
      <div class="mode-toggle" role="group" aria-label="数据来源">
        <button
          type="button"
          class="mode-button"
          :class="{ active: snapshotMode === 'LOCAL' }"
          @click="snapshotMode = 'LOCAL'"
        >
          本机快照
        </button>
        <button
          type="button"
          class="mode-button"
          :class="{ active: snapshotMode === 'TEXT' }"
          @click="snapshotMode = 'TEXT'"
        >
          仅分析描述
        </button>
      </div>

      <div class="toggle-label">诊断模式</div>
      <div class="mode-toggle" role="group" aria-label="诊断模式">
        <button
          type="button"
          class="mode-button"
          :class="{ active: mode === 'agent' }"
          @click="mode = 'agent'"
        >
          AI Agent
        </button>
        <button
          type="button"
          class="mode-button"
          :class="{ active: mode === 'rule' }"
          @click="mode = 'rule'"
        >
          规则诊断
        </button>
      </div>

      <form @submit.prevent="runDiagnosis">
        <label for="diagnosis-device">设备编号</label>
        <input
          id="diagnosis-device"
          v-model="form.deviceId"
          maxlength="100"
          placeholder="例如：本机主机名"
        />

        <label for="diagnosis-description">故障现象描述</label>
        <textarea
          id="diagnosis-description"
          v-model="form.description"
          rows="5"
          maxlength="4000"
          placeholder="请描述错误现象、发生时间及影响范围"
        />

        <div class="form-hint">
          <template v-if="snapshotMode === 'TEXT'">
            工单文本模式不会读取本机 CPU、内存等指标，只分析你填写的故障描述。
            请把关键指标或错误现象写进描述。
          </template>
          <template v-else>
            本机快照模式会读取后端所在机器的实时 CPU、内存、磁盘、网络和服务状态。
          </template>
          <br />
          <template v-if="mode === 'agent'">
            AI 模式需要后端配置 DEEPSEEK_API_KEY；不可用时自动降级为规则诊断。
          </template>
          <template v-else>
            规则模式不调用大模型，无需 API Key。
          </template>
        </div>

        <button class="button button-primary submit-button" type="submit" :disabled="running">
          {{ running ? '正在分析，请稍候…' : (mode === 'agent' ? '✳ 开始 AI 诊断' : '开始规则诊断') }}
        </button>
      </form>
    </section>

    <section class="panel result-panel">
      <div class="panel-heading">
        <div>
          <h2>诊断报告</h2>
          <p>分析结果与工具调用信息</p>
        </div>
        <span class="report-icon">✳</span>
      </div>

      <div v-if="result" class="result-content">
        <div class="result-heading">
          <div class="result-label">诊断结论</div>
          <div class="result-badges">
            <span class="badge" :class="result.recordId ? 'badge-success' : 'badge-warning'">
              {{ result.recordId ? `已归档 #${result.recordId}` : '未归档' }}
            </span>
            <span class="badge">{{ result.monitorSnapshot ? '本机快照' : '文本分析' }}</span>
            <span class="badge">诊断记录</span>
          </div>
        </div>

        <div v-if="result.monitorSnapshot" class="snapshot-card">
          <div class="snapshot-heading">
            <span>诊断开始时的本机快照</span>
            <span>{{ formatDateTime(result.monitorSnapshot.collectedAt) }}</span>
          </div>
          <div class="snapshot-grid">
            <div>
              <span>设备</span>
              <strong>{{ result.monitorSnapshot.deviceId }}</strong>
            </div>
            <div>
              <span>CPU</span>
              <strong>{{ result.monitorSnapshot.metrics?.cpuUsage ?? '—' }}%</strong>
            </div>
            <div>
              <span>内存</span>
              <strong>{{ result.monitorSnapshot.metrics?.memoryUsage ?? '—' }}%</strong>
            </div>
            <div>
              <span>磁盘</span>
              <strong>{{ result.monitorSnapshot.metrics?.diskUsage ?? '—' }}%</strong>
            </div>
            <div>
              <span>网络</span>
              <strong>
                {{ result.monitorSnapshot.network?.reachable
                  ? `${result.monitorSnapshot.network.latencyMs} ms`
                  : '不可达' }}
              </strong>
            </div>
            <div>
              <span>本地服务</span>
              <strong>
                {{ result.monitorSnapshot.service?.serviceName || '—' }}
                {{ result.monitorSnapshot.service?.running ? '运行中' : '未运行' }}
              </strong>
            </div>
          </div>
        </div>

        <div class="answer-text">
          {{ result.answer || '接口已返回，但没有提供文本结论。' }}
        </div>

        <div v-if="result.findings?.length" class="result-section">
          <h3>发现</h3>
          <ul>
            <li v-for="finding in result.findings" :key="finding">{{ finding }}</li>
          </ul>
        </div>

        <div v-if="result.suggestions?.length" class="result-section">
          <h3>处理建议</h3>
          <ul>
            <li v-for="suggestion in result.suggestions" :key="suggestion">{{ suggestion }}</li>
          </ul>
        </div>

        <div class="result-meta">
          <div><span>设备编号</span><strong>{{ result.deviceId || form.deviceId }}</strong></div>
          <div><span>工具调用次数</span><strong>{{ result.toolCallCount ?? '—' }}</strong></div>
          <div><span>生成时间</span><strong>{{ formatDateTime(result.createdAt) }}</strong></div>
        </div>

        <div v-if="result.toolCalls?.length" class="tool-call-list">
          <h3>工具调用明细</h3>
          <details
            v-for="(call, index) in result.toolCalls"
            :key="`${call.tool}-${index}`"
            class="tool-call-item"
          >
            <summary>
              <span class="tool-name">{{ call.tool }}</span>
              <span class="tool-state">调用 #{{ index + 1 }}</span>
            </summary>
            <pre>{{ JSON.stringify(call, null, 2) }}</pre>
          </details>
        </div>

        <details class="raw-result">
          <summary>查看完整接口响应</summary>
          <pre>{{ JSON.stringify(result, null, 2) }}</pre>
        </details>
      </div>

      <div v-else class="empty-state report-empty">
        <div class="empty-icon ai-empty">✳</div>
        <strong>等待诊断任务</strong>
        <p>填写左侧故障信息并点击开始诊断，或从下方历史记录中打开一次结果。</p>
      </div>
    </section>
  </div>

  <section class="panel history-panel">
    <div class="panel-heading">
      <div>
        <h2>诊断历史</h2>
        <p>所有诊断结果按时间倒序归档</p>
      </div>
      <div class="toolbar-actions">
        <input
          v-model="historyDeviceId"
          class="filter-keyword"
          maxlength="100"
          placeholder="按设备编号筛选"
          @keyup.enter="filterHistory"
        />
        <button class="button button-light" @click="filterHistory">筛选</button>
        <button class="button button-light" :disabled="historyLoading" @click="loadHistory">
          {{ historyLoading ? '加载中…' : '刷新 ↻' }}
        </button>
      </div>
    </div>

    <div v-if="historyLoading" class="loading">正在加载诊断历史…</div>

    <div v-else-if="history.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>时间</th>
            <th>设备</th>
            <th>故障描述</th>
            <th>工具调用</th>
            <th>数据</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in history" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td>{{ item.deviceId }}</td>
            <td class="description-cell">{{ item.description }}</td>
            <td>{{ item.toolCallCount }}</td>
            <td>
              <span class="badge">{{ item.monitorSnapshot ? '本机' : '文本' }}</span>
            </td>
            <td>
              <button class="button button-light button-small" @click="viewRecord(item.id)">
                查看
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="empty-state">
      <strong>暂无诊断记录</strong>
      <p>完成一次诊断后，结果会自动出现在这里。</p>
    </div>

    <PaginationControls
      v-if="historyTotal > 0"
      :page-num="historyPageNum"
      :page-size="historyPageSize"
      :total="historyTotal"
      :pages="historyPages"
      :disabled="historyLoading"
      @update:page-num="changeHistoryPage"
      @update:page-size="changeHistoryPageSize"
    />
  </section>
</template>

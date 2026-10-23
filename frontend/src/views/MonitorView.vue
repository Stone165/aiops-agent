<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { fetchLocalMonitor } from '../api/monitor'
import { useNotice } from '../composables/useNotice'
import { formatDateTime } from '../utils/format'

const emit = defineEmits(['diagnose'])
const { notify } = useNotice()

const snapshot = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const liveError = ref('')
const diagnosisDescription = ref('本机运行状态检查')
const history = ref([])

let pollTimer = null
let stopped = false

const metrics = computed(() => snapshot.value?.metrics || {})
const network = computed(() => snapshot.value?.network || {})
const service = computed(() => snapshot.value?.service || {})

function sparklinePoints(key) {
  const values = history.value.map(item => Number(item[key]) || 0)
  if (values.length < 2) return ''

  return values.map((value, index) => {
    const x = (index / (values.length - 1)) * 100
    const y = 30 - Math.min(Math.max(value, 0), 100) / 100 * 28
    return `${x.toFixed(2)},${y.toFixed(2)}`
  }).join(' ')
}

const cpuPoints = computed(() => sparklinePoints('cpuUsage'))
const memoryPoints = computed(() => sparklinePoints('memoryUsage'))
const diskPoints = computed(() => sparklinePoints('diskUsage'))

function formatGb(bytes) {
  const value = Number(bytes)
  return Number.isFinite(value)
    ? `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
    : '—'
}

function remember(sample) {
  const values = sample?.metrics || {}
  history.value.push({
    cpuUsage: Number(values.cpuUsage) || 0,
    memoryUsage: Number(values.memoryUsage) || 0,
    diskUsage: Number(values.diskUsage) || 0
  })
  if (history.value.length > 30) history.value.shift()
}

async function load(silent = false) {
  if (silent) {
    refreshing.value = true
  } else {
    loading.value = true
  }

  try {
    const data = await fetchLocalMonitor()
    snapshot.value = data
    liveError.value = ''
    remember(data)
  } catch (error) {
    if (silent) {
      liveError.value = '自动刷新失败，正在等待下一次重试。'
    } else {
      notify(error.userMessage || '读取本机监控失败。', 'error')
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function poll() {
  if (stopped) return
  await load(true)
  if (!stopped) {
    pollTimer = setTimeout(poll, 3000)
  }
}

async function refresh() {
  await load(true)
}

function diagnose() {
  if (!snapshot.value) {
    notify('监控数据尚未就绪，请稍后重试。', 'error')
    return
  }

  emit('diagnose', {
    deviceId: snapshot.value.deviceId,
    description: diagnosisDescription.value.trim() || '本机运行状态检查'
  })
}

onMounted(() => {
  poll()
})

onUnmounted(() => {
  stopped = true
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2>本机实时监控</h2>
        <p>实时采集本机 CPU、内存、磁盘、网络与本地服务状态，每 3 秒自动刷新</p>
      </div>
      <span class="ai-badge">本机实时</span>
    </div>

    <div class="monitor-live">
      <span class="live-indicator"><i></i> 实时更新中</span>
      <span v-if="snapshot" class="monitor-live-time">
        最近采集 {{ formatDateTime(snapshot.collectedAt) }}
      </span>
      <span v-if="liveError" class="live-error">{{ liveError }}</span>
    </div>

    <div class="monitor-actions">
      <input
        v-model="diagnosisDescription"
        class="filter-keyword"
        maxlength="4000"
        placeholder="描述要诊断的现象，例如：本机响应变慢"
        @keyup.enter="diagnose"
      />
      <button class="button button-primary" :disabled="!snapshot" @click="diagnose">
        ✳ 发起故障诊断
      </button>
      <button class="button button-light" :disabled="refreshing" @click="refresh">
        {{ refreshing ? '采集中…' : '立即刷新 ↻' }}
      </button>
    </div>

    <div v-if="loading && !snapshot" class="loading">正在采集本机实时指标…</div>

    <template v-else-if="snapshot">
      <div class="monitor-grid">
        <div class="monitor-card">
          <div class="monitor-card-head">
            <span>CPU 使用率</span>
            <span class="monitor-card-state" :class="{ warning: metrics.cpuHigh }">
              {{ metrics.cpuHigh ? '偏高' : '正常' }}
            </span>
          </div>
          <strong>{{ metrics.cpuUsage ?? '—' }}%</strong>
          <div class="progress"><i :style="{ width: `${Math.min(metrics.cpuUsage || 0, 100)}%` }"></i></div>
          <svg v-if="cpuPoints" class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
            <polyline :points="cpuPoints" fill="none" stroke="#e9a34d" stroke-width="2" />
          </svg>
          <small>{{ metrics.availableProcessors ?? '—' }} 核 · {{ metrics.osName }} / {{ metrics.osArch }}</small>
        </div>

        <div class="monitor-card">
          <div class="monitor-card-head">
            <span>内存使用率</span>
            <span class="monitor-card-state" :class="{ warning: metrics.memoryHigh }">
              {{ metrics.memoryHigh ? '偏高' : '正常' }}
            </span>
          </div>
          <strong>{{ metrics.memoryUsage ?? '—' }}%</strong>
          <div class="progress memory">
            <i :style="{ width: `${Math.min(metrics.memoryUsage || 0, 100)}%` }"></i>
          </div>
          <svg v-if="memoryPoints" class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
            <polyline :points="memoryPoints" fill="none" stroke="#6879e8" stroke-width="2" />
          </svg>
          <small>{{ formatGb(metrics.totalMemoryBytes) }} 总内存</small>
        </div>

        <div class="monitor-card">
          <div class="monitor-card-head">
            <span>磁盘使用率</span>
            <span class="monitor-card-state" :class="{ warning: metrics.diskHigh }">
              {{ metrics.diskHigh ? '偏高' : '正常' }}
            </span>
          </div>
          <strong>{{ metrics.diskUsage ?? '—' }}%</strong>
          <div class="progress disk">
            <i :style="{ width: `${Math.min(metrics.diskUsage || 0, 100)}%` }"></i>
          </div>
          <svg v-if="diskPoints" class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
            <polyline :points="diskPoints" fill="none" stroke="#df626e" stroke-width="2" />
          </svg>
          <small>{{ formatGb(metrics.freeDiskBytes) }} 可用</small>
        </div>

        <div class="monitor-card">
          <div class="monitor-card-head">
            <span>网络连通性</span>
            <span class="monitor-card-state" :class="{ warning: !network.reachable }">
              {{ network.reachable ? '可达' : '不可达' }}
            </span>
          </div>
          <strong>{{ network.reachable ? `${network.latencyMs} ms` : '—' }}</strong>
          <div class="progress network">
            <i :style="{ width: `${network.reachable ? Math.min(network.latencyMs || 0, 100) : 0}%` }"></i>
          </div>
          <small>目标 {{ network.targetHost }}:{{ network.targetPort }}</small>
        </div>

        <div class="monitor-card">
          <div class="monitor-card-head">
            <span>本地服务</span>
            <span class="monitor-card-state" :class="{ warning: !service.running }">
              {{ service.running ? '运行中' : '未运行' }}
            </span>
          </div>
          <strong :class="{ 'service-down': !service.running }">
            {{ service.serviceName || '—' }}
          </strong>
          <div class="service-status" :class="{ 'service-up': service.running }">
            <i></i> {{ service.running ? '端口可连接' : '端口不可连接' }}
          </div>
          <small>{{ service.host }}:{{ service.port }}</small>
        </div>

        <div class="monitor-card monitor-host-card">
          <div class="monitor-card-head">
            <span>主机信息</span>
            <span class="monitor-card-state">本机</span>
          </div>
          <strong class="host-name">{{ snapshot.deviceId }}</strong>
          <div class="monitor-host-meta">
            <span>{{ metrics.osName }}</span>
            <span>{{ metrics.availableProcessors }} 核</span>
            <span>{{ metrics.osArch }}</span>
          </div>
          <small>快照时间 {{ formatDateTime(snapshot.collectedAt) }}</small>
        </div>
      </div>

      <p class="monitor-note">
        数据来自本机实时采集，网络目标和服务目标可通过
        <code>MONITOR_NETWORK_HOST</code>、<code>MONITOR_SERVICE_PORT</code> 等环境变量调整。
        点击“发起故障诊断”会跳转到诊断页面，并由后端自动捕获一份最新快照开始规则或 AI 分析。
      </p>
    </template>

    <div v-else class="empty-state">
      <strong>暂无监控数据</strong>
      <p>自动刷新将在 3 秒后重试，也可以点击“立即刷新”。</p>
      <button class="button button-light" @click="refresh">重新采集</button>
    </div>
  </section>
</template>

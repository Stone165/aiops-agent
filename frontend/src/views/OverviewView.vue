<script setup>
import { onMounted, ref } from 'vue'
import { fetchIncidentStats, searchIncidents } from '../api/incidents'
import { useNotice } from '../composables/useNotice'
import { formatDateTime, severityText, statusText } from '../utils/format'

const emit = defineEmits(['navigate', 'diagnose-incident'])
const { notify } = useNotice()

const stats = ref({
  total: 0,
  open: 0,
  inProgress: 0,
  resolved: 0,
  closed: 0,
  critical: 0,
  high: 0
})
const recent = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [statsData, pageData] = await Promise.all([
      fetchIncidentStats(),
      searchIncidents({ pageNum: 1, pageSize: 5 })
    ])
    stats.value = statsData
    recent.value = pageData.records || []
  } catch (error) {
    notify(error.userMessage || '加载运维总览失败。', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-top"><span>工单总数</span><span class="stat-icon blue">▤</span></div>
        <div class="stat-number">{{ stats.total }}</div>
        <div class="stat-foot">全部故障工单</div>
      </div>
      <div class="stat-card">
        <div class="stat-top"><span>待处理工单</span><span class="stat-icon orange">◷</span></div>
        <div class="stat-number">{{ stats.open }}</div>
        <div class="stat-foot">状态为待处理</div>
      </div>
      <div class="stat-card">
        <div class="stat-top"><span>高优先级故障</span><span class="stat-icon red">△</span></div>
        <div class="stat-number">{{ stats.critical + stats.high }}</div>
        <div class="stat-foot">严重或高等级工单</div>
      </div>
      <div class="stat-card">
        <div class="stat-top"><span>已解决工单</span><span class="stat-icon purple">✓</span></div>
        <div class="stat-number">{{ stats.resolved }}</div>
        <div class="stat-foot">状态为已解决</div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>最近故障工单</h2>
          <p>数据来自后端工单接口，按创建时间倒序</p>
        </div>
        <div class="toolbar-actions">
          <button class="button button-light" :disabled="loading" @click="load">
            {{ loading ? '刷新中…' : '刷新数据 ↻' }}
          </button>
          <button class="button button-light" @click="emit('navigate', 'incidents')">
            查看全部 →
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading">正在加载工单…</div>

      <div v-else-if="recent.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>工单标题</th>
              <th>设备编号</th>
              <th>严重程度</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in recent" :key="item.id">
              <td class="primary-cell">{{ item.title }}</td>
              <td>{{ item.deviceId || '—' }}</td>
              <td>
                <span class="severity" :class="'sev-' + item.severity">
                  {{ severityText(item.severity) }}
                </span>
              </td>
              <td>{{ statusText(item.status) }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td>
                <button
                  class="button button-light button-small"
                  @click="emit('diagnose-incident', item)"
                >
                  诊断
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">▤</div>
        <strong>暂时没有可显示的工单</strong>
        <p>可以进入故障工单页面创建第一条工单，或先体验 AI 故障诊断。</p>
        <button class="button button-light" @click="emit('navigate', 'incidents')">
          去创建工单
        </button>
      </div>
    </section>

    <section class="panel quick-panel">
      <div>
        <h2>智能诊断中心</h2>
        <p>提交设备和故障现象，由 Agent 根据可用工具获取证据并生成分析。</p>
      </div>
      <button class="button button-primary" @click="emit('navigate', 'diagnosis')">
        开始诊断 →
      </button>
    </section>
  </div>
</template>

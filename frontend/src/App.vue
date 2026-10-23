<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useNotice } from './composables/useNotice'
import OverviewView from './views/OverviewView.vue'
import IncidentsView from './views/IncidentsView.vue'
import DiagnosisView from './views/DiagnosisView.vue'
import MonitorView from './views/MonitorView.vue'

const menuItems = [
  { key: 'overview', icon: '▦', label: '运维总览' },
  { key: 'incidents', icon: '▤', label: '故障工单' },
  { key: 'diagnosis', icon: '✳', label: 'AI 故障诊断' },
  { key: 'monitor', icon: '⌁', label: '设备监控' }
]

const validMenuKeys = new Set(menuItems.map(item => item.key))

function menuFromHash() {
  const key = window.location.hash.replace('#', '')
  return validMenuKeys.has(key) ? key : 'overview'
}

const activeMenu = ref(menuFromHash())
const diagnosisPreset = ref(null)
const { notice, clearNotice } = useNotice()

const pageInfo = computed(() => ({
  overview: {
    eyebrow: 'INTELLIGENT OPERATIONS',
    title: '运维总览',
    subtitle: '统一管理故障工单，利用 AI 辅助分析运维问题。'
  },
  incidents: {
    eyebrow: 'INCIDENT MANAGEMENT',
    title: '故障工单',
    subtitle: '创建、筛选和跟踪故障工单，沉淀根因与处理记录。'
  },
  diagnosis: {
    eyebrow: 'AI DIAGNOSIS',
    title: 'AI 故障诊断',
    subtitle: '支持本机实时快照与工单文本两种数据来源，生成可归档的诊断结论。'
  },
  monitor: {
    eyebrow: 'DEVICE OBSERVABILITY',
    title: '设备监控',
    subtitle: '实时查看本机 CPU、内存、磁盘、网络与本地服务状态。'
  }
})[activeMenu.value] || {
  eyebrow: 'INTELLIGENT OPERATIONS',
  title: '运维总览',
  subtitle: '统一管理故障工单，利用 AI 辅助分析运维问题。'
})

function navigate(key) {
  if (!validMenuKeys.has(key)) return
  activeMenu.value = key
  if (key !== 'diagnosis') diagnosisPreset.value = null
  if (window.location.hash !== `#${key}`) {
    window.location.hash = key
  }
}

function openDiagnosis() {
  diagnosisPreset.value = null
  navigate('diagnosis')
}

function diagnoseIncident(incident) {
  diagnosisPreset.value = {
    incidentId: incident.id,
    title: incident.title,
    deviceId: incident.deviceId || '',
    description: incident.description || incident.title || '',
    snapshotMode: 'TEXT'
  }
  navigate('diagnosis')
}

function diagnoseMonitor({ deviceId, description }) {
  diagnosisPreset.value = {
    deviceId,
    description,
    autoRun: true,
    snapshotMode: 'LOCAL'
  }
  navigate('diagnosis')
}

function consumePreset() {
  diagnosisPreset.value = null
}

function handleHashChange() {
  const key = menuFromHash()
  activeMenu.value = key
  if (key !== 'diagnosis') diagnosisPreset.value = null
}

onMounted(() => window.addEventListener('hashchange', handleHashChange))
onUnmounted(() => window.removeEventListener('hashchange', handleHashChange))
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">A</div>
        <div>
          <div class="brand-name">AIOps Agent</div>
          <div class="brand-subtitle">智能运维诊断平台</div>
        </div>
      </div>

      <div class="nav-caption">工作空间</div>
      <button
        v-for="item in menuItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: activeMenu === item.key }"
        @click="navigate(item.key)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
        <span v-if="activeMenu === item.key" class="nav-arrow">›</span>
      </button>

      <div class="sidebar-bottom">
        <div class="online-dot"></div>
        <div>
          <div class="connection-title">运维工作台</div>
          <div class="connection-subtitle">智能诊断服务</div>
        </div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumb">工作空间 <span>/</span> {{ pageInfo.title }}</div>
        <div class="topbar-right">
          <span class="system-status"><i></i> 管理控制台</span>
          <div class="avatar">OP</div>
        </div>
      </header>

      <div class="page-content">
        <section class="page-heading">
          <div>
            <div class="eyebrow">{{ pageInfo.eyebrow }}</div>
            <h1>{{ pageInfo.title }}</h1>
            <p>{{ pageInfo.subtitle }}</p>
          </div>
          <button class="button button-primary" @click="openDiagnosis">
            ✳ 发起故障诊断
          </button>
        </section>

        <div
          v-if="notice"
          class="notice"
          :class="`notice-${notice.type}`"
          role="status"
        >
          <span>ⓘ</span>
          <span>{{ notice.message }}</span>
          <button type="button" @click="clearNotice">关闭</button>
        </div>

        <OverviewView
          v-if="activeMenu === 'overview'"
          @navigate="navigate"
          @diagnose-incident="diagnoseIncident"
        />

        <IncidentsView
          v-else-if="activeMenu === 'incidents'"
          @diagnose-incident="diagnoseIncident"
        />

        <DiagnosisView
          v-else-if="activeMenu === 'diagnosis'"
          :preset="diagnosisPreset"
          @preset-consumed="consumePreset"
        />

        <MonitorView
          v-else-if="activeMenu === 'monitor'"
          @diagnose="diagnoseMonitor"
        />

        <footer>
          AIOps Agent · 智能运维故障诊断平台
          <span>本机实时监控演示版本</span>
        </footer>
      </div>
    </main>
  </div>
</template>

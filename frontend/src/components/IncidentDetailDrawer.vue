<script setup>
import { reactive, watch } from 'vue'
import { formatDateTime, severityText, statusText } from '../utils/format'

const props = defineProps({
  incident: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'save', 'diagnose'])

const form = reactive({
  status: 'OPEN',
  rootCause: '',
  solution: ''
})

watch(
  () => props.incident,
  value => {
    if (!value) return
    form.status = value.status || 'OPEN'
    form.rootCause = value.rootCause || ''
    form.solution = value.solution || ''
  },
  { immediate: true }
)

function submit() {
  emit('save', {
    status: form.status,
    rootCause: form.rootCause.trim(),
    solution: form.solution.trim()
  })
}
</script>

<template>
  <div
    v-if="incident"
    class="drawer-overlay"
    role="presentation"
    @click.self="emit('close')"
  >
    <aside class="drawer" role="dialog" aria-modal="true" aria-label="工单详情">
      <header class="drawer-header">
        <div>
          <div class="eyebrow">INCIDENT #{{ incident.id }}</div>
          <h2>{{ incident.title }}</h2>
        </div>
        <button type="button" class="icon-button" aria-label="关闭" @click="emit('close')">
          ×
        </button>
      </header>

      <div class="drawer-body">
        <dl class="detail-grid">
          <div>
            <dt>设备编号</dt>
            <dd>{{ incident.deviceId || '—' }}</dd>
          </div>
          <div>
            <dt>严重程度</dt>
            <dd>
              <span class="severity" :class="'sev-' + incident.severity">
                {{ severityText(incident.severity) }}
              </span>
            </dd>
          </div>
          <div>
            <dt>当前状态</dt>
            <dd>{{ statusText(incident.status) }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(incident.createdAt) }}</dd>
          </div>
          <div>
            <dt>更新时间</dt>
            <dd>{{ formatDateTime(incident.updatedAt) }}</dd>
          </div>
        </dl>

        <section class="detail-section">
          <h3>故障描述</h3>
          <p class="pre-wrap">{{ incident.description }}</p>
        </section>

        <form class="resolution-form" @submit.prevent="submit">
          <h3>处理记录</h3>

          <label for="resolution-status">工单状态</label>
          <select id="resolution-status" v-model="form.status">
            <option value="OPEN">待处理</option>
            <option value="IN_PROGRESS">处理中</option>
            <option value="RESOLVED">已解决</option>
            <option value="CLOSED">已关闭</option>
          </select>

          <label for="resolution-root-cause">故障原因</label>
          <textarea
            id="resolution-root-cause"
            v-model="form.rootCause"
            rows="3"
            maxlength="5000"
            placeholder="记录排查确认的故障原因"
          />

          <label for="resolution-solution">解决方案</label>
          <textarea
            id="resolution-solution"
            v-model="form.solution"
            rows="3"
            maxlength="5000"
            placeholder="记录处理步骤和验证结果"
          />

          <div class="drawer-actions">
            <button
              type="button"
              class="button button-light"
              @click="emit('diagnose', incident)"
            >
              ✳ 对该工单发起诊断
            </button>
            <button type="submit" class="button button-primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存处理记录' }}
            </button>
          </div>
        </form>
      </div>
    </aside>
  </div>
</template>

export function statusText(status) {
  return ({
    OPEN: '待处理',
    IN_PROGRESS: '处理中',
    RESOLVED: '已解决',
    CLOSED: '已关闭'
  })[status] || status || '未知'
}

export function severityText(severity) {
  return ({
    CRITICAL: '严重',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  })[severity] || severity || '未知'
}

export function formatDateTime(value) {
  if (!value) return '—'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

export function truncate(value, maxLength = 80) {
  if (!value) return '—'
  return value.length > maxLength
    ? `${value.slice(0, maxLength)}…`
    : value
}

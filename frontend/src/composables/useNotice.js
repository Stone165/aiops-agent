import { ref } from 'vue'

const notice = ref(null)
let timer = null

function clearTimer() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

export function useNotice() {
  function notify(message, type = 'success') {
    clearTimer()
    notice.value = { message, type }
    timer = setTimeout(() => {
      notice.value = null
      timer = null
    }, 5000)
  }

  function clearNotice() {
    clearTimer()
    notice.value = null
  }

  return { notice, notify, clearNotice }
}

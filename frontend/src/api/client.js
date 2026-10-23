import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export const http = axios.create({
  baseURL,
  timeout: 15_000
})

http.interceptors.response.use(
  response => response,
  error => {
    const serverMessage = error.response?.data?.message
    const timeoutMessage = error.code === 'ECONNABORTED'
      ? '请求超时，请稍后重试。'
      : ''
    const networkMessage = error.response
      ? `请求失败（HTTP ${error.response.status}）。`
      : '无法连接后端服务，请确认 Java 后端已启动。'

    error.userMessage = serverMessage || timeoutMessage || networkMessage
    return Promise.reject(error)
  }
)

/**
 * 诊断请求可能等待模型与工具调用，单独使用更长超时。
 */
export function diagnosisRequest(config) {
  return http.request({ ...config, timeout: 90_000 })
}

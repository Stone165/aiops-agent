import { http } from './client'

export async function fetchLocalMonitor() {
  const { data } = await http.get('/monitor/local')
  return data
}

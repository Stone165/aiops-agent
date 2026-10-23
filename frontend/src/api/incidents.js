import { http } from './client'

export async function fetchIncidents() {
  const { data } = await http.get('/incidents')
  return data
}

export async function fetchIncident(id) {
  const { data } = await http.get(`/incidents/${id}`)
  return data
}

export async function fetchIncidentStats() {
  const { data } = await http.get('/incidents/stats')
  return data
}

export async function searchIncidents(params = {}) {
  const { data } = await http.get('/incidents/search', { params })
  return data
}

export async function createIncident(payload) {
  const { data } = await http.post('/incidents', payload)
  return data
}

export async function updateIncidentStatus(id, status) {
  const { data } = await http.put(`/incidents/${id}/status`, { status })
  return data
}

export async function updateIncidentResolution(id, payload) {
  const { data } = await http.put(`/incidents/${id}/resolution`, payload)
  return data
}

export async function deleteIncident(id) {
  await http.delete(`/incidents/${id}`)
}

import { diagnosisRequest, http } from './client'

export async function runAgentDiagnosis(payload) {
  const { data } = await diagnosisRequest({
    method: 'post',
    url: '/diagnosis/agent',
    data: payload
  })
  return data
}

export async function runRuleDiagnosis(payload) {
  const { data } = await diagnosisRequest({
    method: 'post',
    url: '/diagnosis/rule',
    data: payload
  })
  return data
}

export async function fetchDiagnosisRecords(params = {}) {
  const { data } = await http.get('/diagnosis/records', { params })
  return data
}

export async function fetchDiagnosisRecord(id) {
  const { data } = await http.get(`/diagnosis/records/${id}`)
  return data
}

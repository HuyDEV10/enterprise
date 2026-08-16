import apiClient from './apiClient'

export const riskMonitoringApi = {
  getSummary: () => apiClient.get('/risk-monitoring/summary').then((response) => response.data),
  getRules: () => apiClient.get('/risk-monitoring/rules').then((response) => response.data),
  run: () => apiClient.post('/risk-monitoring/run').then((response) => response.data),
}

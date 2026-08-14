import apiClient from './apiClient'
export const riskEventApi = {
  getAll: async () => (await apiClient.get('/risk-events')).data,
  getById: async (id) => (await apiClient.get(`/risk-events/${id}`)).data,
  create: async (payload) => (await apiClient.post('/risk-events', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/risk-events/${id}`, payload)).data,
  updateStatus: async (id, status) => (await apiClient.patch(`/risk-events/${id}/status`, { status })).data,
}

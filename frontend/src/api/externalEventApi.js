import apiClient from './apiClient'

export const externalEventApi = {
  getAll: async (from, to) => (await apiClient.get('/ai/external-events', { params: { from, to } })).data,
  getById: async (id) => (await apiClient.get(`/ai/external-events/${id}`)).data,
  ingestLatest: async (params = {}) => (await apiClient.post('/ai/external-events/gdelt/latest', null, { params })).data,
  analyzeSentiment: async (id, payload) => (await apiClient.post(`/ai/external-events/${id}/sentiment`, payload)).data,
  calculateImpacts: async (id) => (await apiClient.post(`/ai/external-events/${id}/impacts/calculate`)).data,
  getImpacts: async (id) => (await apiClient.get(`/ai/external-events/${id}/impacts`)).data,
}

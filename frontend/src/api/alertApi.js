import apiClient from './apiClient'

export const alertApi = {
  getAll: async () => (await apiClient.get('/alerts')).data,
  getById: async (id) => (await apiClient.get(`/alerts/${id}`)).data,
  create: async (payload) => (await apiClient.post('/alerts', payload)).data,
  updateStatus: async (id, status) => (await apiClient.patch(`/alerts/${id}/status`, { status })).data,
}

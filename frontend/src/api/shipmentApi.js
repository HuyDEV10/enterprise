import apiClient from './apiClient'
export const shipmentApi = {
  getAll: async () => (await apiClient.get('/shipments')).data,
  getById: async (id) => (await apiClient.get(`/shipments/${id}`)).data,
  create: async (payload) => (await apiClient.post('/shipments', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/shipments/${id}`, payload)).data,
  updateStatus: async (id, status) => (await apiClient.patch(`/shipments/${id}/status`, { status })).data,
}

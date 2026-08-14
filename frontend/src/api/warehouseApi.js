import apiClient from './apiClient'
export const warehouseApi = {
  getAll: async () => (await apiClient.get('/warehouses')).data,
  getById: async (id) => (await apiClient.get(`/warehouses/${id}`)).data,
  create: async (payload) => (await apiClient.post('/warehouses', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/warehouses/${id}`, payload)).data,
}

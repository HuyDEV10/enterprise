import apiClient from './apiClient'
export const inventoryApi = {
  getAll: async () => (await apiClient.get('/inventory')).data,
  getById: async (id) => (await apiClient.get(`/inventory/${id}`)).data,
  getLowStock: async () => (await apiClient.get('/inventory/low-stock')).data,
  create: async (payload) => (await apiClient.post('/inventory', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/inventory/${id}`, payload)).data,
}

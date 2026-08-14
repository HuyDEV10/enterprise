import apiClient from './apiClient'
export const productApi = {
  getAll: async () => (await apiClient.get('/products')).data,
  getById: async (id) => (await apiClient.get(`/products/${id}`)).data,
  create: async (payload) => (await apiClient.post('/products', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/products/${id}`, payload)).data,
  deactivate: async (id) => apiClient.delete(`/products/${id}`),
}

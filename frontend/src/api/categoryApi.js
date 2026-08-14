import apiClient from './apiClient'

export const categoryApi = {
  getAll: async () => (await apiClient.get('/product-categories')).data,
  create: async (payload) => (await apiClient.post('/product-categories', payload)).data,
  update: async (id, payload) => (await apiClient.put(`/product-categories/${id}`, payload)).data,
}

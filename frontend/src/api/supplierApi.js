import apiClient from './apiClient'
export const supplierApi = {
  getPage: async (params) =>
    (await apiClient.get('/suppliers/page', { params })).data,
  getAll: async (riskLevel) =>
    (
      await apiClient.get('/suppliers', {
        params: riskLevel ? { riskLevel } : {},
      })
    ).data,
  getById: async (id) => (await apiClient.get(`/suppliers/${id}`)).data,
  create: async (payload) => (await apiClient.post('/suppliers', payload)).data,
  update: async (id, payload) =>
    (await apiClient.put(`/suppliers/${id}`, payload)).data,
  deactivate: async (id) => apiClient.delete(`/suppliers/${id}`),
}

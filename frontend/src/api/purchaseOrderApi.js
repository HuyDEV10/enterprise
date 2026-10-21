import apiClient from './apiClient'
export const purchaseOrderApi = {
  getPage: async (params) =>
    (await apiClient.get('/purchase-orders/page', { params })).data,
  getAll: async () => (await apiClient.get('/purchase-orders')).data,
  getById: async (id) => (await apiClient.get(`/purchase-orders/${id}`)).data,
  create: async (payload) =>
    (await apiClient.post('/purchase-orders', payload)).data,
  update: async (id, payload) =>
    (await apiClient.put(`/purchase-orders/${id}`, payload)).data,
  updateStatus: async (id, status) =>
    (await apiClient.patch(`/purchase-orders/${id}/status`, { status })).data,
}

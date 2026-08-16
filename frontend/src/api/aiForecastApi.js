import apiClient from './apiClient'

export const aiForecastApi = {
  getForecast: async (productId) => (await apiClient.get(`/ai/inventory/products/${productId}/forecast`)).data,
  refreshRisk: async (productId) => (await apiClient.post(`/ai/inventory/products/${productId}/risk/refresh`)).data,
  importM5: async (productId, itemId, storeId = 'CA_1') => (await apiClient.post(
    `/ai/inventory/products/${productId}/demand/import-m5`,
    null,
    { params: { itemId, storeId } },
  )).data,
}

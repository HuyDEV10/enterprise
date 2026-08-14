import apiClient from './apiClient'
export const dashboardApi = { getSummary: async () => (await apiClient.get('/dashboard/summary')).data }

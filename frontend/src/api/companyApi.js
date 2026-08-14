import apiClient from './apiClient'
export const companyApi={
  getCompanies:async()=> (await apiClient.get('/companies')).data,
  createCompany:async payload=>(await apiClient.post('/companies',payload)).data,
  updateCompany:async(id,payload)=>(await apiClient.put(`/companies/${id}`,payload)).data,
  getDepartments:async()=> (await apiClient.get('/departments')).data,
  createDepartment:async payload=>(await apiClient.post('/departments',payload)).data,
  updateDepartment:async(id,payload)=>(await apiClient.put(`/departments/${id}`,payload)).data,
  getEmployees:async()=> (await apiClient.get('/employees')).data,
  createEmployee:async payload=>(await apiClient.post('/employees',payload)).data,
  updateEmployee:async(id,payload)=>(await apiClient.put(`/employees/${id}`,payload)).data,
}

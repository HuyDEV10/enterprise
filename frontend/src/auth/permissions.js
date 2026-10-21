// Mirror SecurityConfig. The API remains the authority for every operation.
const all = ['ADMIN', 'MANAGER', 'ANALYST', 'STAFF']
const analysts = ['ADMIN', 'MANAGER', 'ANALYST']
export const permissions = {
  readOperations: all,
  writeOperations: ['ADMIN', 'MANAGER', 'STAFF'],
  readRisks: analysts,
  writeRisks: analysts,
  readAlerts: analysts,
  createAlerts: ['ADMIN', 'MANAGER'],
  updateAlerts: analysts,
  readCompany: analysts,
  manageUsers: ['ADMIN'],
  runIntelligence: analysts,
}
export const can = (user, permission) =>
  (permissions[permission] || []).some((role) => user?.roles?.includes(role))

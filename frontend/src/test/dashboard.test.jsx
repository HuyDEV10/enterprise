import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../pages/DashboardPage'
import { can } from '../auth/permissions'
import { dashboardApi } from '../api/dashboardApi'
import { riskMonitoringApi } from '../api/riskMonitoringApi'
import { riskEventApi } from '../api/riskEventApi'
import { alertApi } from '../api/alertApi'
import { inventoryApi } from '../api/inventoryApi'
const { state } = vi.hoisted(() => ({ state: { role: 'STAFF' } }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { username: 'Thanh', roles: [state.role] },
    can: (permission) => can({ roles: [state.role] }, permission),
  }),
}))
vi.mock('../api/dashboardApi', () => ({
  dashboardApi: { getSummary: vi.fn() },
}))
vi.mock('../api/riskMonitoringApi', () => ({
  riskMonitoringApi: { getSummary: vi.fn() },
}))
vi.mock('../api/riskEventApi', () => ({ riskEventApi: { getAll: vi.fn() } }))
vi.mock('../api/alertApi', () => ({ alertApi: { getAll: vi.fn() } }))
vi.mock('../api/inventoryApi', () => ({
  inventoryApi: { getLowStock: vi.fn() },
}))
beforeEach(() => {
  dashboardApi.getSummary.mockResolvedValue({
    totalSuppliers: 12,
    totalPurchaseOrders: 8,
    lowStockItems: 1,
    openRiskEvents: 2,
    unresolvedAlerts: 1,
  })
  riskMonitoringApi.getSummary.mockResolvedValue({
    lowStockRisks: 1,
    delayedOrderRisks: 2,
  })
  inventoryApi.getLowStock.mockResolvedValue([
    {
      id: 1,
      productName: 'Gạo',
      warehouseName: 'Kho Bắc',
      quantity: 2,
      lowStockThreshold: 10,
    },
  ])
  riskEventApi.getAll.mockResolvedValue([])
  alertApi.getAll.mockResolvedValue([])
})
it.each(['ADMIN', 'MANAGER', 'ANALYST', 'STAFF'])(
  'renders dashboard for %s without forbidden calls',
  async (role) => {
    state.role = role
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    )
    expect(await screen.findByText('Gạo')).toBeInTheDocument()
    expect(riskEventApi.getAll).toHaveBeenCalledTimes(role === 'STAFF' ? 0 : 1)
    expect(alertApi.getAll).toHaveBeenCalledTimes(role === 'STAFF' ? 0 : 1)
  },
)
it('keeps stock and summary visible when monitoring is unavailable', async () => {
  state.role = 'STAFF'
  riskMonitoringApi.getSummary.mockRejectedValue({ response: { status: 503 } })
  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
  expect(await screen.findByText('Gạo')).toBeInTheDocument()
  expect(await screen.findByRole('alert')).toHaveTextContent('tạm gián đoạn')
  expect(screen.getByText('12')).toBeInTheDocument()
})

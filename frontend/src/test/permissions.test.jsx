import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { can } from '../auth/permissions'
import AppRoutes from '../routes/AppRoutes'
const { state } = vi.hoisted(() => ({ state: { role: 'STAFF' } }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { username: 'test', roles: [state.role] },
    loading: false,
    hasRole: (...roles) => roles.includes(state.role),
    can: (permission) => can({ roles: [state.role] }, permission),
  }),
}))
describe('Role boundaries', () => {
  it.each([
    ['STAFF', '/risks'],
    ['STAFF', '/alerts'],
    ['STAFF', '/company'],
    ['MANAGER', '/users'],
    ['ANALYST', '/suppliers/new'],
    ['ANALYST', '/products/new'],
    ['ANALYST', '/inventory/1/edit'],
    ['ANALYST', '/purchase-orders/new'],
    ['ANALYST', '/shipments/new'],
    ['ANALYST', '/alerts/new'],
  ])('%s cannot open %s directly', (role, path) => {
    state.role = role
    render(
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>,
    )
    expect(
      screen.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' }),
    ).toBeInTheDocument()
  })
  it('combines permissions for users holding multiple roles', () => {
    expect(can({ roles: ['STAFF', 'ANALYST'] }, 'writeOperations')).toBe(true)
    expect(can({ roles: ['STAFF', 'ANALYST'] }, 'readRisks')).toBe(true)
    expect(can({ roles: ['STAFF'] }, 'manageUsers')).toBe(false)
    expect(can(null, 'readOperations')).toBe(false)
  })
})

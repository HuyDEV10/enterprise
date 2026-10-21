import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from '../auth/AuthContext'
import { authApi } from '../api/authApi'
import { authStorage } from '../auth/authStorage'
vi.mock('../api/authApi', () => ({ authApi: { me: vi.fn(), login: vi.fn() } }))
function Probe() {
  const auth = useAuth()
  return (
    <>
      <span>{auth.user?.username || 'no-user'}</span>
      <span>{auth.sessionError}</span>
      <button onClick={auth.retrySession}>Retry</button>
      <button onClick={auth.logout}>Logout</button>
    </>
  )
}
it('preserves a valid token across a temporary failure and retries', async () => {
  authStorage.setToken('opaque-test-token')
  authApi.me
    .mockRejectedValueOnce({ response: { status: 503 } })
    .mockResolvedValueOnce({ username: 'Thanh', roles: ['STAFF'] })
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
  await screen.findByText(/tạm gián đoạn/)
  expect(authStorage.getToken()).toBe('opaque-test-token')
  fireEvent.click(screen.getByText('Retry'))
  expect(await screen.findByText('Thanh')).toBeInTheDocument()
})
it('invalidates a token when the server confirms 401', async () => {
  authStorage.setToken('opaque-test-token')
  authApi.me.mockRejectedValueOnce({ response: { status: 401 } })
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
  await waitFor(() => expect(authStorage.getToken()).toBeFalsy())
  expect(screen.getByText('no-user')).toBeInTheDocument()
})
it('does not restore a user after logout while /me is pending', async () => {
  let finish
  authStorage.setToken('opaque-test-token')
  authApi.me.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      }),
  )
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
  fireEvent.click(screen.getByText('Logout'))
  finish({ username: 'stale-user', roles: ['ADMIN'] })
  await waitFor(() => expect(authStorage.getToken()).toBeFalsy())
  expect(screen.queryByText('stale-user')).not.toBeInTheDocument()
})

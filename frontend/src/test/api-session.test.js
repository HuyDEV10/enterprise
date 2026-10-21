import { expect, it, vi } from 'vitest'
import apiClient from '../api/apiClient'
import { authStorage } from '../auth/authStorage'
it('does not erase a newer login when an older request returns 401', async () => {
  authStorage.setToken('old-test-session')
  let rejectRequest, start
  const started = new Promise((resolve) => {
    start = resolve
  })
  const request = apiClient.get('/inventory', {
    adapter: (config) => {
      start()
      return new Promise((resolve, reject) => {
        rejectRequest = () => reject({ config, response: { status: 401 } })
      })
    },
  })
  await started
  authStorage.setToken('new-test-session')
  rejectRequest()
  await expect(request).rejects.toMatchObject({ response: { status: 401 } })
  expect(authStorage.getToken()).toBe('new-test-session')
})
it('invalidates the current session and emits an expiry event on 401', async () => {
  authStorage.setToken('current-test-session')
  const expired = vi.fn()
  window.addEventListener('auth:expired', expired)
  try {
    await expect(
      apiClient.get('/inventory', {
        adapter: (config) =>
          Promise.reject({ config, response: { status: 401 } }),
      }),
    ).rejects.toBeDefined()
    expect(authStorage.getToken()).toBeNull()
    expect(expired).toHaveBeenCalledTimes(1)
  } finally {
    window.removeEventListener('auth:expired', expired)
  }
})
it('preserves the session when the API returns 503', async () => {
  authStorage.setToken('current-test-session')
  await expect(
    apiClient.get('/inventory', {
      adapter: (config) =>
        Promise.reject({ config, response: { status: 503 } }),
    }),
  ).rejects.toBeDefined()
  expect(authStorage.getToken()).toBe('current-test-session')
})

import { test, expect } from '@playwright/test'
import { mockApi } from './fixtures'
for (const role of ['ADMIN', 'MANAGER', 'ANALYST', 'STAFF'])
  test(`${role}: login and role-aware dashboard`, async ({ page }) => {
    const requests = await mockApi(page, role)
    await page.goto('/login')
    await expect(page.getByLabel('Tên đăng nhập')).toHaveValue('')
    await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveValue('')
    await page.getByLabel('Tên đăng nhập').fill('fixture-user')
    await page.getByLabel('Mật khẩu', { exact: true }).fill('fixture-password')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(
      page.getByRole('heading', { name: /Tổng quan chuỗi cung ứng/ }),
    ).toBeVisible()
    await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
    if (role === 'STAFF') {
      expect(requests).not.toContain('/risk-events')
      expect(requests).not.toContain('/alerts')
      await page.goto('/risks')
      await expect(
        page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' }),
      ).toBeVisible()
    }
    await expect(page.locator('.sidebar')).toBeVisible()
  })
test('analyst uses read-only lists; pagination and filters survive reload', async ({
  page,
}) => {
  await mockApi(page, 'ANALYST', { authenticated: true })
  await page.goto('/suppliers')
  await expect(
    page.getByRole('link', { name: 'Thêm nhà cung cấp' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('link', { name: 'Sửa', exact: true }),
  ).toHaveCount(0)
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page).toHaveURL(/page=1/)
  await expect(page.getByText('Đối tác trang hai')).toBeVisible()
  await page.reload()
  await expect(page.getByText('Đối tác trang hai')).toBeVisible()
  await page.getByLabel('Mức rủi ro').selectOption('HIGH')
  await expect(page).toHaveURL(/riskLevel=HIGH/)
  await expect(page.getByText('Công ty Nông sản Việt')).toBeVisible()
  await page.goto('/products/new')
  await expect(
    page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' }),
  ).toBeVisible()
})
test('AI outage is actionable and does not sign the user out', async ({
  page,
}) => {
  await mockApi(page, 'STAFF', { authenticated: true })
  await page.goto('/ai-forecast')
  await page.getByRole('button', { name: 'Chạy dự báo' }).click()
  await expect(
    page.getByText(/Dịch vụ dự báo đang tạm gián đoạn/),
  ).toBeVisible()
  await page.goto('/dashboard')
  await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
})
test('desktop dashboard and login render without page errors', async ({
  page,
}, testInfo) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await mockApi(page, 'ADMIN', { authenticated: true })
  await page.goto('/dashboard')
  await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
  await page.screenshot({
    path: testInfo.outputPath('dashboard-desktop.png'),
    fullPage: true,
  })
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(
    page.getByRole('heading', { name: /Đăng nhập vào/ }),
  ).toBeVisible()
  await page.screenshot({
    path: testInfo.outputPath('login-desktop.png'),
    fullPage: true,
  })
  expect(errors).toEqual([])
})
test('mobile navigation is accessible and content fits the viewport', async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page, 'STAFF', { authenticated: true })
  await page.goto('/dashboard')
  await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
  await page.screenshot({
    path: testInfo.outputPath('dashboard-mobile.png'),
    fullPage: true,
  })
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await expect(
    page.getByRole('navigation', { name: 'Điều hướng chính' }),
  ).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(
    page.getByRole('navigation', { name: 'Điều hướng chính' }),
  ).not.toBeVisible()
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await page.getByRole('link', { name: 'Nhà cung cấp', exact: true }).click()
  await expect(page.getByText('Công ty Nông sản Việt')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
  await page.screenshot({
    path: testInfo.outputPath('suppliers-mobile.png'),
    fullPage: true,
  })
})
test('temporary session outage offers retry without erasing the token', async ({
  page,
}) => {
  await mockApi(page, 'STAFF', { authenticated: true })
  let unavailable = true
  await page.route('**/api/auth/me', (route) =>
    unavailable
      ? route.fulfill({ status: 503, json: { message: 'Unavailable' } })
      : route.fulfill({ json: { username: 'thanh', roles: ['STAFF'] } }),
  )
  await page.goto('/inventory')
  await expect(page.getByRole('alert')).toContainText('tạm gián đoạn')
  expect(
    await page.evaluate(() => localStorage.getItem('enterprise_access_token')),
  ).toBe('e2e-fixture-token')
  unavailable = false
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(
    page.getByRole('heading', { name: 'Tồn kho', exact: true }),
  ).toBeVisible()
})
test('expired session returns to login and restores the requested route after login', async ({
  page,
}) => {
  await mockApi(page, 'STAFF', { authenticated: true })
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 401, json: { message: 'Expired' } }),
  )
  await page.goto('/inventory?filter=low')
  await expect(
    page.getByRole('button', { name: 'Đăng nhập', exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(() => localStorage.getItem('enterprise_access_token')),
  ).toBeNull()
  await page.getByLabel('Tên đăng nhập').fill('fixture-user')
  await page.getByLabel('Mật khẩu', { exact: true }).fill('fixture-password')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page).toHaveURL(/inventory\?filter=low/)
  await expect(
    page.getByRole('heading', { name: 'Tồn kho', exact: true }),
  ).toBeVisible()
})
test('forecast chart and daily table render returned data accessibly', async ({
  page,
}, testInfo) => {
  await mockApi(page, 'STAFF', { authenticated: true })
  const rows = Array.from({ length: 28 }, (_, index) => ({
    date: `2026-11-${String(index + 1).padStart(2, '0')}`,
    forecastDemand: 5,
    incomingSupply: 0,
    projectedInventory: 120 - (index + 1) * 5,
  }))
  await page.route('**/api/ai/inventory/products/*/forecast', (route) =>
    route.fulfill({
      json: {
        productCode: 'SP-001',
        productId: 'p1',
        modelVersion: 'fixture-model',
        historySource: 'DEMO_SYNTHETIC',
        historyDays: 56,
        currentInventory: 120,
        lowStockThreshold: 20,
        stockoutRisk: 'HIGH',
        forecastDemand: { next7Days: 35, next14Days: 70, next28Days: 140 },
        incomingSupply: { next7Days: 0, next14Days: 0, next28Days: 0 },
        projectedInventory: { next7Days: 85, next14Days: 50, next28Days: -20 },
        dailyProjection: rows,
      },
    }),
  )
  await page.goto('/ai-forecast')
  await page.getByRole('button', { name: 'Chạy dự báo' }).click()
  await expect(
    page.getByRole('img', { name: /Tồn kho dự kiến từ/ }),
  ).toBeVisible()
  await expect(page.getByRole('note')).toContainText('Dữ liệu nhu cầu mô phỏng')
  await expect(
    page.locator('.ai-dashboard-grid article').last().locator('tbody tr'),
  ).toHaveCount(28)
  await page.screenshot({
    path: testInfo.outputPath('forecast-desktop.png'),
    fullPage: true,
  })
})

import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mockApi } from './fixtures'
for (const path of ['/dashboard', '/suppliers', '/login'])
  test(`${path}: no automated WCAG A/AA violations`, async ({ page }) => {
    await mockApi(page, 'ADMIN', { authenticated: path !== '/login' })
    await page.goto(path)
    if (path === '/dashboard')
      await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
    if (path === '/suppliers')
      await expect(page.getByText('Công ty Nông sản Việt')).toBeVisible()
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze()
    expect(results.violations).toEqual([])
  })
test('small mobile screen fits at 320px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 })
  await mockApi(page, 'STAFF', { authenticated: true })
  await page.goto('/dashboard')
  await expect(page.getByText('Gạo thơm Jasmine')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
})

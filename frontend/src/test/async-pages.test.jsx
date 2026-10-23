import { expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import AiForecastPage from '../pages/AiForecastPage'
import ExternalEventsPage from '../pages/ExternalEventsPage'
import { aiForecastApi } from '../api/aiForecastApi'
import { productApi } from '../api/productApi'
import { externalEventApi } from '../api/externalEventApi'
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ can: () => false }),
}))
vi.mock('../api/aiForecastApi', () => ({
  aiForecastApi: { getForecast: vi.fn() },
}))
vi.mock('../api/productApi', () => ({ productApi: { getAll: vi.fn() } }))
vi.mock('../api/externalEventApi', () => ({
  externalEventApi: {
    getAll: vi.fn(),
    getImpacts: vi.fn(),
    getSentiments: vi.fn().mockResolvedValue([]),
  },
}))
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
it('ignores forecast A if it arrives after the selection and forecast B', async () => {
  productApi.getAll.mockResolvedValue([
    { id: 'a', productCode: 'A', name: 'A' },
    { id: 'b', productCode: 'B', name: 'B' },
  ])
  const a = deferred(),
    b = deferred()
  aiForecastApi.getForecast.mockImplementation((id) =>
    id === 'a' ? a.promise : b.promise,
  )
  render(<AiForecastPage />)
  const select = await screen.findByRole('combobox', { name: 'Sản phẩm' })
  fireEvent.click(screen.getByRole('button', { name: 'Chạy dự báo' }))
  fireEvent.change(select, { target: { value: 'b' } })
  fireEvent.click(screen.getByRole('button', { name: 'Chạy dự báo' }))
  await act(async () =>
    b.resolve({
      productCode: 'RESULT-B',
      modelVersion: 'model-b',
      historyDays: 60,
      dailyProjection: [],
    }),
  )
  expect(await screen.findByText('RESULT-B')).toBeInTheDocument()
  await act(async () =>
    a.resolve({
      productCode: 'RESULT-A',
      modelVersion: 'model-a',
      historyDays: 60,
      dailyProjection: [],
    }),
  )
  expect(screen.queryByText('RESULT-A')).not.toBeInTheDocument()
  expect(screen.getByText('RESULT-B')).toBeInTheDocument()
})
it('keeps exposure results keyed to the selected event and shows errors separately', async () => {
  externalEventApi.getAll.mockResolvedValue([
    { id: 'a', externalEventId: 'EVENT-A' },
    { id: 'b', externalEventId: 'EVENT-B' },
  ])
  const a = deferred(),
    b = deferred()
  externalEventApi.getImpacts.mockImplementation((id) =>
    id === 'a' ? a.promise : b.promise,
  )
  render(<ExternalEventsPage />)
  fireEvent.click(await screen.findByRole('button', { name: /EVENT-B/ }))
  await act(async () => b.reject({ response: { status: 503 } }))
  expect(await screen.findByRole('alert')).toHaveTextContent('tạm gián đoạn')
  await act(async () =>
    a.resolve([{ id: 'old', supplierCode: 'STALE-SUPPLIER' }]),
  )
  expect(screen.queryByText('STALE-SUPPLIER')).not.toBeInTheDocument()
  expect(
    screen.queryByText('Chưa có kết quả đánh giá ảnh hưởng'),
  ).not.toBeInTheDocument()
})

it.each(['DEMO_SYNTHETIC', 'DEMO_FIXTURE'])(
  'clearly labels %s events as fictional and does not link them as GDELT articles',
  async (externalSource) => {
    externalEventApi.getAll.mockResolvedValue([
      { id: 'demo', externalEventId: 'DEMO-EVT-01', externalSource, sourceUrl: 'https://example.invalid/demo/DEMO-EVT-01' },
    ])
    externalEventApi.getImpacts.mockResolvedValue([])
    externalEventApi.getSentiments.mockResolvedValue([])
    render(<ExternalEventsPage />)
    expect(await screen.findByText('Tình huống mô phỏng để thử chức năng; không phải tin tức thật.')).toBeInTheDocument()
    expect(screen.getByText('Dữ liệu demo mô phỏng')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Mở nguồn GDELT' })).not.toBeInTheDocument()
  },
)
it('keeps the article source for real GDELT events', async () => {
  externalEventApi.getAll.mockResolvedValue([
    { id: 'gdelt', externalEventId: 'GDELT-01', externalSource: 'GDELT_2_EVENT', sourceUrl: 'https://news.example/article' },
  ])
  externalEventApi.getImpacts.mockResolvedValue([])
  externalEventApi.getSentiments.mockResolvedValue([])
  render(<ExternalEventsPage />)
  expect(await screen.findByText('Thông tin sự kiện từ nguồn dữ liệu GDELT.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Mở nguồn GDELT' })).toHaveAttribute('href', 'https://news.example/article')
})
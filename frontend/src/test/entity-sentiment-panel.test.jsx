import { beforeEach, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import EntitySentimentPanel from '../components/EntitySentimentPanel'
import { externalEventApi } from '../api/externalEventApi'
import { supplierApi } from '../api/supplierApi'

vi.mock('../api/externalEventApi', () => ({
  externalEventApi: { getSentiments: vi.fn(), analyzeSentiment: vi.fn() },
}))
vi.mock('../api/supplierApi', () => ({ supplierApi: { getAll: vi.fn() } }))

beforeEach(() => {
  externalEventApi.getSentiments.mockReset().mockResolvedValue([])
  externalEventApi.analyzeSentiment.mockReset()
  supplierApi.getAll
    .mockReset()
    .mockResolvedValue([
      { id: 'supplier-a', supplierCode: 'SUP-A', name: 'Nhà cung cấp A' },
    ])
})

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

function row(entity, extra = {}) {
  return {
    id: entity,
    entity,
    language: 'en',
    modelVersion: 'bilingual-v2',
    sentiment: 'NEGATIVE',
    confidence: 0.93,
    needsReview: false,
    analyzedAt: '2026-10-23T00:00:00Z',
    ...extra,
  }
}

async function enterNews(entity = 'ACME', text = 'ACME suspended production.') {
  await screen.findByText('Chưa có kết quả theo thực thể')
  fireEvent.change(screen.getByLabelText('Tên thực thể xuất hiện trong tin'), {
    target: { value: entity },
  })
  fireEvent.change(screen.getByLabelText('Nội dung tin'), {
    target: { value: text },
  })
}

it.each([
  ['en', '  ACME  ', '  ACME suspended production.  '],
  ['vi', '  Công ty A  ', '  Công ty A tạm dừng sản xuất.  '],
])(
  'sends the selected %s language and supplier with trimmed text',
  async (language, entity, text) => {
    const updated = { id: 'event-a', sentiment: 'NEGATIVE' }
    const onAnalyzed = vi.fn(),
      onBusyChange = vi.fn()
    externalEventApi.analyzeSentiment.mockResolvedValue(updated)
    externalEventApi.getSentiments
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        row(entity.trim(), {
          language,
          supplierCode: 'SUP-A',
          supplierName: 'Nhà cung cấp A',
        }),
      ])
    render(
      <EntitySentimentPanel
        eventId="event-a"
        canAnalyze
        onAnalyzed={onAnalyzed}
        onBusyChange={onBusyChange}
      />,
    )
    await enterNews(entity, text)
    fireEvent.change(screen.getByLabelText('Nhà cung cấp liên kết'), {
      target: { value: 'supplier-a' },
    })
    fireEvent.change(screen.getByLabelText('Ngôn ngữ nội dung'), {
      target: { value: language },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Phân tích sentiment' }))
    await waitFor(() => expect(onAnalyzed).toHaveBeenCalledWith(updated))
    expect(externalEventApi.analyzeSentiment).toHaveBeenCalledExactlyOnceWith(
      'event-a',
      {
        entity: entity.trim(),
        text: text.trim(),
        supplierId: 'supplier-a',
        language,
      },
    )
    expect(onBusyChange.mock.calls).toEqual([[true], [false]])
    expect(screen.getByText('SUP-A')).toBeInTheDocument()
    expect(screen.getByText('Đạt ngưỡng tin cậy')).toBeInTheDocument()
  },
)

it('shows review and truncation status to staff without exposing analysis controls', async () => {
  externalEventApi.getSentiments.mockResolvedValue([
    row('Công ty A', {
      language: 'vi',
      confidence: 0.62,
      needsReview: true,
      reviewReason: 'DOMAIN_NOT_VALIDATED',
      inputTruncated: true,
    }),
  ])
  render(<EntitySentimentPanel eventId="event-a" canAnalyze={false} />)
  expect(await screen.findByText('Cần duyệt')).toBeInTheDocument()
  expect(
    screen.getByText('Tin doanh nghiệp tiếng Việt cần người kiểm tra'),
  ).toBeInTheDocument()
  expect(screen.getByText('62%')).toBeInTheDocument()
  expect(screen.getByText('Đã lấy đoạn quanh thực thể')).toBeInTheDocument()
  expect(
    screen.getByText(/Kết quả cần duyệt chưa được dùng/),
  ).toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: 'Phân tích sentiment' }),
  ).not.toBeInTheDocument()
  expect(supplierApi.getAll).not.toHaveBeenCalled()
})

it('ignores a previous event list that arrives after the selected event', async () => {
  const pending = deferred()
  externalEventApi.getSentiments.mockImplementation((id) =>
    id === 'event-a' ? pending.promise : Promise.resolve([row('CURRENT-B')]),
  )
  const { rerender } = render(
    <EntitySentimentPanel eventId="event-a" canAnalyze={false} />,
  )
  const firstSignal = externalEventApi.getSentiments.mock.calls[0][1].signal
  rerender(<EntitySentimentPanel eventId="event-b" canAnalyze={false} />)
  expect(await screen.findByText('CURRENT-B')).toBeInTheDocument()
  await act(async () => pending.resolve([row('STALE-A')]))
  expect(firstSignal.aborted).toBe(true)
  expect(screen.queryByText('STALE-A')).not.toBeInTheDocument()
  expect(screen.getByText('CURRENT-B')).toBeInTheDocument()
})

it('discards a late analysis when the event changes and releases its busy state', async () => {
  const pending = deferred(),
    onAnalyzed = vi.fn(),
    onBusyChange = vi.fn()
  externalEventApi.analyzeSentiment.mockReturnValue(pending.promise)
  const { rerender } = render(
    <EntitySentimentPanel
      eventId="event-a"
      canAnalyze
      onAnalyzed={onAnalyzed}
      onBusyChange={onBusyChange}
    />,
  )
  await enterNews()
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích sentiment' }))
  expect(onBusyChange).toHaveBeenLastCalledWith(true)
  rerender(
    <EntitySentimentPanel
      eventId="event-b"
      canAnalyze
      onAnalyzed={onAnalyzed}
      onBusyChange={onBusyChange}
    />,
  )
  await screen.findByText('Chưa có kết quả theo thực thể')
  expect(onBusyChange).toHaveBeenLastCalledWith(false)
  expect(screen.getByLabelText('Nội dung tin')).toHaveValue('')
  await act(async () => pending.resolve({ id: 'event-a' }))
  expect(onAnalyzed).not.toHaveBeenCalled()
  expect(
    externalEventApi.getSentiments.mock.calls.map((call) => call[0]),
  ).toEqual(['event-a', 'event-b'])
})

it('discards a late refresh after an analysis when the panel is replaced', async () => {
  const refresh = deferred(),
    onAnalyzed = vi.fn()
  externalEventApi.analyzeSentiment.mockResolvedValue({ id: 'event-a' })
  externalEventApi.getSentiments
    .mockResolvedValueOnce([])
    .mockReturnValueOnce(refresh.promise)
    .mockResolvedValueOnce([row('CURRENT-B')])
  const { rerender } = render(
    <EntitySentimentPanel
      eventId="event-a"
      canAnalyze
      onAnalyzed={onAnalyzed}
    />,
  )
  await enterNews()
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích sentiment' }))
  await waitFor(() =>
    expect(externalEventApi.getSentiments).toHaveBeenCalledTimes(2),
  )
  rerender(
    <EntitySentimentPanel
      eventId="event-b"
      canAnalyze
      onAnalyzed={onAnalyzed}
    />,
  )
  expect(await screen.findByText('CURRENT-B')).toBeInTheDocument()
  await act(async () => refresh.resolve([row('STALE-A')]))
  expect(screen.queryByText('STALE-A')).not.toBeInTheDocument()
  expect(onAnalyzed).not.toHaveBeenCalled()
})

it('shows a current analysis error and allows a retry without resubmitting the saved request', async () => {
  externalEventApi.analyzeSentiment.mockRejectedValue({
    response: { status: 503 },
  })
  render(<EntitySentimentPanel eventId="event-a" canAnalyze />)
  await enterNews()
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích sentiment' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('tạm gián đoạn')
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await waitFor(() =>
    expect(externalEventApi.getSentiments).toHaveBeenCalledTimes(2),
  )
  expect(externalEventApi.analyzeSentiment).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('releases busy state on unmount and ignores a late failure', async () => {
  const pending = deferred()
  const onAnalyzed = vi.fn(),
    onBusyChange = vi.fn()
  externalEventApi.analyzeSentiment.mockReturnValue(pending.promise)
  const { unmount } = render(
    <EntitySentimentPanel
      eventId="event-a"
      canAnalyze
      onAnalyzed={onAnalyzed}
      onBusyChange={onBusyChange}
    />,
  )
  await enterNews()
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích sentiment' }))
  expect(externalEventApi.analyzeSentiment).toHaveBeenCalledWith('event-a', {
    entity: 'ACME',
    text: 'ACME suspended production.',
    supplierId: null,
    language: 'auto',
  })
  unmount()
  await act(async () => pending.reject({ response: { status: 503 } }))
  expect(onBusyChange.mock.calls).toEqual([[true], [false]])
  expect(onAnalyzed).not.toHaveBeenCalled()
})

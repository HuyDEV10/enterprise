import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import StatusControl from '../components/StatusControl'
import { orderTransitions, shipmentTransitions } from '../utils/workflows'
import { getApiErrorMessage } from '../api/apiClient'
it('requires confirmation and never offers delivery from a draft order', async () => {
  const onChange = vi.fn().mockResolvedValue({})
  render(
    <StatusControl
      value="DRAFT"
      transitions={orderTransitions}
      onChange={onChange}
    />,
  )
  expect(
    screen.queryByRole('option', { name: 'Đã giao' }),
  ).not.toBeInTheDocument()
  fireEvent.change(screen.getByRole('combobox'), {
    target: { value: 'ORDERED' },
  })
  expect(onChange).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(onChange).toHaveBeenCalledWith('ORDERED')
})
it('shows a conflict inline and allows retry', async () => {
  render(
    <StatusControl
      value="SHIPPING"
      transitions={shipmentTransitions}
      onChange={() =>
        Promise.reject({ response: { status: 409, message: 'Conflict' } })
      }
    />,
  )
  fireEvent.change(screen.getByRole('combobox'), {
    target: { value: 'ARRIVED' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('tải lại')
})
it('does not offer changes on a finished shipment', () => {
  render(
    <StatusControl
      value="ARRIVED"
      transitions={shipmentTransitions}
      onChange={vi.fn()}
    />,
  )
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
})
it('explains throttling and keeps the support reference', () => {
  expect(
    getApiErrorMessage({
      response: {
        status: 429,
        headers: { 'retry-after': '60' },
        data: { requestId: 'test-ref' },
      },
    }),
  ).toMatch(/60 giây.*test-ref/)
})

export const orderTransitions = {
  DRAFT: ['ORDERED', 'CANCELLED'],
  ORDERED: ['IN_TRANSIT', 'DELAYED', 'CANCELLED'],
  IN_TRANSIT: ['DELIVERED', 'DELAYED', 'CANCELLED'],
  DELAYED: ['ORDERED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'],
  DELIVERED: [],
  CANCELLED: [],
}
export const shipmentTransitions = {
  PENDING: ['SHIPPING', 'DELAYED', 'CANCELLED'],
  SHIPPING: ['ARRIVED', 'DELAYED', 'CANCELLED'],
  DELAYED: ['SHIPPING', 'ARRIVED', 'CANCELLED'],
  ARRIVED: [],
  CANCELLED: [],
}
export const statusLabels = {
  DRAFT: 'Nháp',
  ORDERED: 'Đã đặt',
  IN_TRANSIT: 'Đang vận chuyển',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  DELAYED: 'Chậm tiến độ',
  PENDING: 'Chờ khởi hành',
  SHIPPING: 'Đang vận chuyển',
  ARRIVED: 'Đã đến',
}
export function localToday() {
  const date = new Date()
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10)
}

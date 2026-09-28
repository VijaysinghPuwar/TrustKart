/** Orders arrive seven days after they're placed. The server's tracking timeline uses the same rule. */
export const DELIVERY_DAYS = 7

export function estimatedDelivery(from = new Date()): string {
  const d = new Date(from)
  d.setDate(d.getDate() + DELIVERY_DAYS)
  return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

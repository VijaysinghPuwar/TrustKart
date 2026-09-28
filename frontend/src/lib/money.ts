/**
 * Money arrives from the API as decimal strings (e.g. "1799.99") so no precision is lost in JSON.
 * The frontend only formats amounts for display; it never computes totals the server relies on.
 */
const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const usdWhole = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
})

export type Money = string

export function formatMoney(amount: Money): string {
  return usd.format(Number(amount))
}

/** Compact form for tight UI such as the header balance chip: $84,550. */
export function formatMoneyWhole(amount: Money): string {
  return usdWhole.format(Math.floor(Number(amount)))
}

/** Percentage saved against a compare-at price, rounded down so it never overstates a discount. */
export function percentOff(price: Money, compareAt: Money): number {
  const p = Number(price)
  const c = Number(compareAt)
  if (!(c > p) || p <= 0) return 0
  return Math.floor(((c - p) / c) * 100)
}

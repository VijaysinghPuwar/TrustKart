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

const usdCompact = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
  roundingMode: 'trunc',
})

/** Very tight UI such as a stat tile: $100K, $84.5K, $1.2M. Truncates, so it never overstates a balance. */
export function formatMoneyCompact(amount: Money): string {
  return usdCompact.format(Number(amount))
}

/** Percentage saved against a compare-at price, rounded down so it never overstates a discount. */
export function percentOff(price: Money, compareAt: Money): number {
  const p = Number(price)
  const c = Number(compareAt)
  if (!(c > p) || p <= 0) return 0
  return Math.floor(((c - p) / c) * 100)
}

/** Exact decimal-string arithmetic in integer cents, for display-only differences (e.g. "remaining after"). */
export function toCents(amount: Money): bigint {
  const negative = amount.trim().startsWith('-')
  const [whole = '0', frac = ''] = amount.trim().replace('-', '').split('.')
  const cents = BigInt(whole) * 100n + BigInt((frac + '00').slice(0, 2))
  return negative ? -cents : cents
}

export function fromCents(cents: bigint): Money {
  const negative = cents < 0n
  const abs = negative ? -cents : cents
  const whole = abs / 100n
  const frac = (abs % 100n).toString().padStart(2, '0')
  return `${negative ? '-' : ''}${whole.toString()}.${frac}`
}

export function subtractMoney(a: Money, b: Money): Money {
  return fromCents(toCents(a) - toCents(b))
}

import { formatMoney, formatMoneyWhole, fromCents, percentOff, subtractMoney, toCents } from './money'

describe('formatMoney', () => {
  it('formats decimal strings as USD with cents', () => {
    expect(formatMoney('1799.99')).toBe('$1,799.99')
    expect(formatMoney('0.5')).toBe('$0.50')
    expect(formatMoney('1000000')).toBe('$1,000,000.00')
  })

  it('keeps cents on large virtual totals', () => {
    expect(formatMoney('26199.97')).toBe('$26,199.97')
  })
})

describe('formatMoneyWhole', () => {
  it('drops cents without rounding up', () => {
    expect(formatMoneyWhole('84550.99')).toBe('$84,550')
  })
})

describe('percentOff', () => {
  it('rounds down so a discount is never overstated', () => {
    expect(percentOff('129.00', '159.00')).toBe(18)
  })

  it('returns 0 when there is no real discount', () => {
    expect(percentOff('100.00', '100.00')).toBe(0)
    expect(percentOff('100.00', '90.00')).toBe(0)
  })
})

describe('exact cents arithmetic', () => {
  it('subtracts without floating point error', () => {
    expect(subtractMoney('100000.00', '99.99')).toBe('99900.01')
    expect(subtractMoney('0.30', '0.10')).toBe('0.20')
    expect(subtractMoney('10.00', '12000.00')).toBe('-11990.00')
  })

  it('round-trips large amounts', () => {
    expect(fromCents(toCents('1000000000000.00'))).toBe('1000000000000.00')
    expect(fromCents(toCents('1799.9'))).toBe('1799.90')
  })
})

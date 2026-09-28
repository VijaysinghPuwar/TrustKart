import { describe, expect, it } from 'vitest'
import type { ProductOptionGroup } from '@/lib/types'
import { defaultSelection, selectionPrice } from './OptionPicker'

const groups: ProductOptionGroup[] = [
  {
    name: 'Storage',
    values: [
      { label: '256 GB', price: '1199.00', default: true },
      { label: '512 GB', price: '1399.00' },
    ],
  },
  { name: 'Color', values: [{ label: 'Silver' }, { label: 'Cosmic Orange', default: true }] },
]

describe('product options', () => {
  it('starts from each group’s default', () => {
    expect(defaultSelection(groups)).toEqual({ Storage: '256 GB', Color: 'Cosmic Orange' })
  })

  it('falls back to the first value when no default is marked', () => {
    expect(defaultSelection([{ name: 'Pack', values: [{ label: '1' }, { label: '2' }] }])).toEqual({
      Pack: '1',
    })
  })

  it('prices a configuration from its priced group', () => {
    expect(selectionPrice(groups, { Storage: '512 GB', Color: 'Silver' }, '1199.00')).toBe('1399.00')
  })

  it('uses the base price when nothing is priced or there are no options', () => {
    const colorOnly = groups.filter((g) => g.name === 'Color')
    expect(selectionPrice(colorOnly, { Color: 'Silver' }, '999.00')).toBe('999.00')
    expect(selectionPrice(undefined, {}, '49.99')).toBe('49.99')
  })
})

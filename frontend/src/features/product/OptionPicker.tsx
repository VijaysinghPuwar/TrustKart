import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import type { Money } from '@/lib/money'
import type { OptionSelection, ProductOptionGroup } from '@/lib/types'

/** Every group's default choice (or first value), the configuration a product page opens with. */
export function defaultSelection(groups: ProductOptionGroup[] | undefined): OptionSelection {
  const out: OptionSelection = {}
  for (const g of groups ?? []) {
    const v = g.values.find((x) => x.default) ?? g.values[0]
    if (v) out[g.name] = v.label
  }
  return out
}

/** The unit price of a configuration: the chosen priced value, or the base price when no group carries prices. */
export function selectionPrice(
  groups: ProductOptionGroup[] | undefined,
  selection: OptionSelection,
  base: Money,
) {
  for (const g of groups ?? []) {
    const v = g.values.find((x) => x.label === selection[g.name])
    if (v?.price) return v.price
  }
  return base
}

/**
 * Storage / colour / configuration chips. Priced groups show each choice's price so shoppers can compare
 * without clicking through. The server re-resolves every price, so this is display only.
 */
export function OptionPicker({
  groups,
  value,
  onChange,
}: {
  groups: ProductOptionGroup[]
  value: OptionSelection
  onChange: (next: OptionSelection) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => (
        <fieldset key={g.name} className="flex flex-col gap-2">
          <legend className="mb-2 text-sm">
            <span className="font-semibold">{g.name}:</span> {value[g.name]}
          </legend>
          <div className="flex flex-wrap gap-2">
            {g.values.map((v) => {
              const selected = value[g.name] === v.label
              return (
                <label
                  key={v.label}
                  className={cn(
                    'flex min-h-11 cursor-pointer flex-col justify-center rounded-control border px-3.5 py-1.5 text-sm transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary',
                    selected
                      ? 'border-primary bg-primary-subtle font-semibold text-ink'
                      : 'border-border bg-surface text-ink hover:border-ink-muted',
                  )}
                >
                  <input
                    type="radio"
                    name={`option-${g.name}`}
                    value={v.label}
                    checked={selected}
                    onChange={() => onChange({ ...value, [g.name]: v.label })}
                    className="sr-only"
                  />
                  {v.label}
                  {v.price && (
                    <span className="text-xs font-normal text-ink-muted">{formatMoney(v.price)}</span>
                  )}
                </label>
              )
            })}
          </div>
        </fieldset>
      ))}
    </div>
  )
}

import { useId, useState, type SyntheticEvent } from 'react'
import { useFacets } from '@/data/catalog'
import type { Facets, SpecFacet } from '@/lib/types'
import { Skeleton } from '@/components/ui/Skeleton'

interface FilterPanelProps {
  category?: string
  params: URLSearchParams
  onChange: (mutate: (next: URLSearchParams) => void) => void
}

/** Filters live entirely in the URL. Category pages add specification facets from the server's registry. */
export function FilterPanel({ category, params, onChange }: FilterPanelProps) {
  const facets = useFacets(category)
  return (
    <div className="flex flex-col gap-6">
      <Toggle
        label="In stock only"
        checked={params.get('inStock') === 'true'}
        onChange={(on) => onChange((p) => (on ? p.set('inStock', 'true') : p.delete('inStock')))}
      />
      <Toggle
        label="On sale"
        checked={params.get('onSale') === 'true'}
        onChange={(on) => onChange((p) => (on ? p.set('onSale', 'true') : p.delete('onSale')))}
      />
      <PriceFilter params={params} onChange={onChange} facets={facets.data} />
      {category && facets.isPending && <Skeleton className="h-40" />}
      {facets.data && facets.data.brands.length > 1 && (
        <CheckboxGroup
          legend="Brand"
          name="brand"
          options={facets.data.brands}
          selected={params.getAll('brand')}
          onToggle={(value, on) =>
            onChange((p) => {
              const next = p.getAll('brand').filter((b) => b !== value)
              p.delete('brand')
              ;(on ? [...next, value] : next).forEach((b) => p.append('brand', b))
            })
          }
        />
      )}
      {facets.data?.specs.map((spec) => (
        <SpecFilter key={spec.key} spec={spec} params={params} onChange={onChange} />
      ))}
    </div>
  )
}

function SpecFilter({
  spec,
  params,
  onChange,
}: {
  spec: SpecFacet
  params: URLSearchParams
  onChange: FilterPanelProps['onChange']
}) {
  const name = `spec.${spec.key}`
  if (spec.type === 'BOOLEAN') {
    return (
      <Toggle
        label={spec.label}
        checked={params.get(name) === 'true'}
        onChange={(on) => onChange((p) => (on ? p.set(name, 'true') : p.delete(name)))}
      />
    )
  }
  return (
    <CheckboxGroup
      legend={spec.unit ? `${spec.label} (${spec.unit})` : spec.label}
      name={name}
      options={spec.options}
      selected={params.getAll(name)}
      onToggle={(value, on) =>
        onChange((p) => {
          const next = p.getAll(name).filter((v) => v !== value)
          p.delete(name)
          ;(on ? [...next, value] : next).forEach((v) => p.append(name, v))
        })
      }
    />
  )
}

function CheckboxGroup({
  legend,
  name,
  options,
  selected,
  onToggle,
}: {
  legend: string
  name: string
  options: { value: string; label: string; count: number }[]
  selected: string[]
  onToggle: (value: string, on: boolean) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const visible = expanded ? options : options.slice(0, 6)
  return (
    <fieldset className="flex flex-col gap-1">
      <legend className="mb-1.5 text-sm font-semibold">{legend}</legend>
      {visible.map((o) => (
        <label key={o.value} className="flex min-h-8 cursor-pointer items-center gap-2.5 text-sm">
          <input
            type="checkbox"
            name={name}
            className="size-[18px] cursor-pointer accent-primary"
            checked={selected.includes(o.value)}
            onChange={(e) => onToggle(o.value, e.target.checked)}
          />
          <span className="flex-1">{o.label}</span>
          <span className="text-xs text-ink-muted tabular">{o.count}</span>
        </label>
      ))}
      {options.length > 6 && (
        <button
          type="button"
          onClick={() => setExpanded((e) => !e)}
          className="mt-1 self-start text-[13px] font-semibold text-primary"
        >
          {expanded ? 'Show fewer' : `Show all ${String(options.length)}`}
        </button>
      )}
    </fieldset>
  )
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (on: boolean) => void
}) {
  return (
    <label className="flex min-h-8 cursor-pointer items-center gap-2.5 text-sm font-medium">
      <input
        type="checkbox"
        className="size-[18px] cursor-pointer accent-primary"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  )
}

function PriceFilter({
  params,
  onChange,
  facets,
}: {
  params: URLSearchParams
  onChange: FilterPanelProps['onChange']
  facets?: Facets
}) {
  const id = useId()
  const [min, setMin] = useState(params.get('minPrice') ?? '')
  const [max, setMax] = useState(params.get('maxPrice') ?? '')
  function apply(e: SyntheticEvent) {
    e.preventDefault()
    onChange((p) => {
      if (min) p.set('minPrice', min)
      else p.delete('minPrice')
      if (max) p.set('maxPrice', max)
      else p.delete('maxPrice')
    })
  }
  return (
    <form onSubmit={apply} className="flex flex-col gap-2" aria-labelledby={`${id}-legend`}>
      <span id={`${id}-legend`} className="text-sm font-semibold">
        Price
      </span>
      <div className="flex items-center gap-2">
        <label className="sr-only" htmlFor={`${id}-min`}>
          Minimum price
        </label>
        <input
          id={`${id}-min`}
          inputMode="decimal"
          placeholder={facets ? `$${facets.price.min}` : 'Min'}
          value={min}
          onChange={(e) => setMin(e.target.value.replace(/[^\d.]/g, ''))}
          className="h-10 w-full min-w-0 rounded-control border border-border-strong bg-surface px-2.5 text-sm"
        />
        <span aria-hidden="true">–</span>
        <label className="sr-only" htmlFor={`${id}-max`}>
          Maximum price
        </label>
        <input
          id={`${id}-max`}
          inputMode="decimal"
          placeholder={facets ? `$${facets.price.max}` : 'Max'}
          value={max}
          onChange={(e) => setMax(e.target.value.replace(/[^\d.]/g, ''))}
          className="h-10 w-full min-w-0 rounded-control border border-border-strong bg-surface px-2.5 text-sm"
        />
      </div>
      <button
        type="submit"
        className="h-9 self-start rounded-control border border-border-strong px-3 text-sm font-semibold hover:bg-surface-2"
      >
        Apply price
      </button>
    </form>
  )
}

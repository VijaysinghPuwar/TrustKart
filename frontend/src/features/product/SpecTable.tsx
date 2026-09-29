import type { ProductDetail } from '@/lib/types'

/** One table per spec group, each captioned with its group name, inside a single bordered card. */
export function SpecTable({ groups }: { groups: ProductDetail['specGroups'] }) {
  if (groups.length === 0) return null
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-[22px] font-bold">Specifications</h2>
      <div className="overflow-x-auto rounded-card border border-border bg-surface">
        {groups.map((g, i) => (
          <table
            key={g.group}
            className={
              i > 0
                ? 'w-full border-collapse border-t border-border text-sm'
                : 'w-full border-collapse text-sm'
            }
          >
            <caption className="bg-surface-2 px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-ink-muted">
              {g.group}
            </caption>
            <tbody>
              {g.specs.map((s) => (
                <tr key={s.key} className="border-t border-border">
                  <th scope="row" className="w-2/5 px-4 py-2.5 text-left font-medium text-ink-muted">
                    {s.label}
                  </th>
                  <td className="px-4 py-2.5">{s.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
      </div>
    </div>
  )
}

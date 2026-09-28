import type { ProductDetail } from '@/lib/types'

export function SpecTable({ groups }: { groups: ProductDetail['specGroups'] }) {
  if (groups.length === 0) return null
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-[22px] font-bold">Specifications</h2>
      <div className="overflow-hidden rounded-card border border-border bg-surface">
        <table className="w-full border-collapse text-sm">
          {groups.map((g) => (
            <tbody key={g.group}>
              <tr>
                <th
                  colSpan={2}
                  scope="colgroup"
                  className="bg-surface-2 px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-ink-muted"
                >
                  {g.group}
                </th>
              </tr>
              {g.specs.map((s) => (
                <tr key={s.key} className="border-t border-border">
                  <th scope="row" className="w-2/5 px-4 py-2.5 text-left font-medium text-ink-muted">
                    {s.label}
                  </th>
                  <td className="px-4 py-2.5">{s.value}</td>
                </tr>
              ))}
            </tbody>
          ))}
        </table>
      </div>
    </div>
  )
}

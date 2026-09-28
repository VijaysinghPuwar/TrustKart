import { useQuery } from '@tanstack/react-query'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { usePageTitle } from '@/lib/usePageTitle'
import { Prose } from './Prose'

interface Credit {
  file: string
  title: string
  source: string
  author: string
  license: string
  licenseUrl: string
  match: string
}

export default function CreditsPage() {
  usePageTitle('Image credits')
  const credits = useQuery({
    queryKey: ['credits'],
    queryFn: async () => (await fetch('/credits.json')).json() as Promise<Credit[]>,
    staleTime: Infinity,
  })
  return (
    <Prose
      title="Image credits"
      lead="Product and category photos come from Wikimedia Commons under public domain, CC0, CC BY and CC BY-SA licenses. Many show a similar or representative model, and product pages say so."
    >
      {credits.isPending ? (
        <PageSpinner />
      ) : (
        <div
          role="region"
          aria-label="Image credits"
          tabIndex={0}
          className="relative overflow-x-auto rounded-card border border-border bg-surface"
        >
          <table className="w-full min-w-[640px] text-sm leading-5">
            <thead>
              <tr className="border-b border-border text-left text-ink-muted">
                <th scope="col" className="px-3 py-2 font-medium">
                  Used for
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Author
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  License
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Match
                </th>
              </tr>
            </thead>
            <tbody>
              {credits.data?.map((c) => (
                <tr key={c.file} className="border-b border-border last:border-0">
                  <td className="px-3 py-2">
                    <a href={c.source} target="_blank" rel="noopener noreferrer">
                      {c.file}
                    </a>
                  </td>
                  <td className="px-3 py-2">{c.author}</td>
                  <td className="px-3 py-2">
                    {c.licenseUrl ? (
                      <a href={c.licenseUrl} target="_blank" rel="noopener noreferrer">
                        {c.license}
                      </a>
                    ) : (
                      c.license
                    )}
                  </td>
                  <td className="px-3 py-2 text-ink-muted">{c.match}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Prose>
  )
}

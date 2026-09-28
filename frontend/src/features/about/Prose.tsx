import type { ReactNode } from 'react'

/** Readable long-form layout for the About pages. */
export function Prose({ title, lead, children }: { title: string; lead?: string; children: ReactNode }) {
  return (
    <article className="mx-auto flex w-full max-w-3xl flex-col gap-5 py-4 leading-7 [&_h2]:mt-4 [&_h2]:text-[22px] [&_h2]:font-bold [&_h2]:leading-8 [&_li]:ml-5 [&_li]:list-disc [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-1.5">
      <header className="flex flex-col gap-2">
        <h1 className="text-[32px] font-bold leading-10">{title}</h1>
        {lead && <p className="text-lg text-ink-muted">{lead}</p>}
      </header>
      {children}
    </article>
  )
}

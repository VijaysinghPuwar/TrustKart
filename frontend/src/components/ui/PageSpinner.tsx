export function PageSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex flex-1 items-center justify-center py-24">
      <span
        className="size-8 animate-spin rounded-full border-[3px] border-primary border-r-transparent"
        aria-hidden="true"
      />
      <span className="sr-only">{label}</span>
    </div>
  )
}

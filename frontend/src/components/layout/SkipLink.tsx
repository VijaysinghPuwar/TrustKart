/** Visible only when focused, as the first tab stop on every page. */
export function SkipLink() {
  return (
    <a
      href="#main"
      className="fixed left-3 top-3 z-50 -translate-y-24 rounded-control bg-primary px-4 py-2.5 font-semibold text-on-primary shadow-lg focus:translate-y-0"
    >
      Skip to content
    </a>
  )
}

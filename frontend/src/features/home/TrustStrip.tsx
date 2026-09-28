import { Check } from 'lucide-react'

const promises = [
  'Secure checkout',
  'Free delivery on every order',
  'Easy returns',
  'Pay with TrustKart Wallet',
]

/** The design's trust strip: TrustKart's store promises. Green is kept for trust meaning. */
export function TrustStrip() {
  return (
    <section aria-label="Why shop with TrustKart">
      <ul className="grid grid-cols-[repeat(auto-fit,minmax(min(220px,100%),1fr))] rounded-card border border-border bg-surface">
        {promises.map((p) => (
          <li
            key={p}
            className="flex items-center justify-center gap-1.5 px-[18px] py-3 text-sm font-semibold text-trust"
          >
            <Check className="size-4" aria-hidden="true" />
            {p}
          </li>
        ))}
      </ul>
    </section>
  )
}

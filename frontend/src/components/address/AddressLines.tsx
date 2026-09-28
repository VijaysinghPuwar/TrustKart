import type { SimulationAddress } from '@/lib/types'
import { countryName } from './countries'

/** Renders an address in the usual postal order. */
export function AddressLines({ address, className }: { address: SimulationAddress; className?: string }) {
  const cityLine = [address.city, [address.region, address.postalCode].filter(Boolean).join(' ')]
    .filter(Boolean)
    .join(', ')
  return (
    <address className={className ?? 'text-sm not-italic leading-6'}>
      {address.fullName && <span className="block font-medium">{address.fullName}</span>}
      {address.line1 && <span className="block">{address.line1}</span>}
      {address.line2 && <span className="block">{address.line2}</span>}
      {cityLine && <span className="block">{cityLine}</span>}
      {address.country && <span className="block">{countryName(address.country)}</span>}
    </address>
  )
}

import type { AddressInput, DeliveryPreset, SavedAddress } from '@/lib/types'

/** Where the virtual order goes: a saved address, a one-off address, or a no-address preset. */
export type Destination =
  | { kind: 'saved'; address: SavedAddress }
  | { kind: 'inline'; address: AddressInput }
  | { kind: 'preset'; preset: Exclude<DeliveryPreset, 'ADDRESS' | 'HOME' | 'OFFICE'>; label?: string }

export function destinationLabel(d: Destination): string {
  switch (d.kind) {
    case 'saved':
    case 'inline':
      return d.address.label
    case 'preset':
      return (
        d.label ??
        {
          DREAM_SETUP: 'Dream setup',
          HOMELAB: 'Homelab',
          COLLECTION: 'My collection',
          CUSTOM: 'Custom place',
        }[d.preset]
      )
  }
}

/** The request fields for POST /purchases. Saved addresses go by id and are resolved (and ownership-checked) on the server. */
export function destinationPayload(d: Destination) {
  switch (d.kind) {
    case 'saved':
      return { deliveryPreset: 'ADDRESS' as const, addressId: d.address.id }
    case 'inline':
      return {
        deliveryPreset: 'ADDRESS' as const,
        simulationAddress: {
          ...d.address,
          line2: d.address.line2 || undefined,
          region: d.address.region || undefined,
        },
      }
    case 'preset':
      return { deliveryPreset: d.preset, simulationAddress: d.label ? { label: d.label } : undefined }
  }
}

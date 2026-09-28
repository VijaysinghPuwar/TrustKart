import type { DeliveryPreset } from '@/lib/types'

const LABELS: Record<DeliveryPreset, string> = {
  ADDRESS: 'Address',
  HOME: 'Home',
  OFFICE: 'Office',
  DREAM_SETUP: 'Dream setup',
  HOMELAB: 'Homelab',
  COLLECTION: 'My collection',
  CUSTOM: 'Custom place',
}

export function presetLabel(p: DeliveryPreset): string {
  return LABELS[p]
}

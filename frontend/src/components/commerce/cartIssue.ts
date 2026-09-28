import type { CartIssue } from '@/lib/types'

export function cartIssueText(issue: CartIssue): string {
  switch (issue) {
    case 'OUT_OF_STOCK':
      return 'Out of stock. Remove it or save it for later.'
    case 'DISCONTINUED':
      return 'This product was discontinued.'
    case 'QUANTITY_EXCEEDS_STOCK':
      return 'Fewer are available now. Lower the quantity to continue.'
  }
}

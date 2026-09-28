import type { Money } from './money'

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER' | 'DISCONTINUED'
export type ImageMatch = 'EXACT' | 'PRODUCT_LINE' | 'REPRESENTATIVE'

export interface Ref {
  slug: string
  name: string
}

export interface ProductImage {
  small: string
  large: string
  width: number
  height: number
  alt: string
  match: ImageMatch
}

export interface ProductCard {
  id: number
  slug: string
  sku: string
  name: string
  brand: Ref
  category: Ref
  summary: string
  price: Money
  compareAtPrice?: Money
  percentOff: number
  stockStatus: StockStatus
  stockLeft?: number
  maxQuantity: number
  featured: boolean
  image?: ProductImage
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Category {
  slug: string
  name: string
  description: string | null
  productCount: number
  children: Category[]
}

export interface Shelf {
  key: string
  title: string
  subtitle: string
  link: string
  items: ProductCard[]
}

export interface Home {
  hero: ProductCard | null
  tiles: Shelf[]
  deals: ProductCard[]
  featured: ProductCard[]
  categories: Category[]
}

export interface ImageCredit extends ProductImage {
  credit: string
  sourceUrl: string
}

export interface ProductDetail {
  product: ProductCard
  description: string
  warrantyMonths: number
  breadcrumbs: Ref[]
  images: ImageCredit[]
  specGroups: { group: string; specs: { key: string; label: string; value: string }[] }[]
  collections: string[]
  related: ProductCard[]
}

export interface FacetOption {
  value: string
  label: string
  count: number
}

export interface SpecFacet {
  key: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'BOOLEAN'
  unit?: string
  options: FacetOption[]
}

export interface Facets {
  category: Ref
  brands: FacetOption[]
  price: { min: Money; max: Money }
  specs: SpecFacet[]
}

export interface InterpretationChip {
  kind: 'CATEGORY' | 'BUDGET' | 'TERM'
  label: string
  value: string
  ignoreKey: string
}

export interface SearchResult {
  query: string
  mode: 'exact' | 'smart'
  smartAvailable: boolean
  relaxed: boolean
  interpretation: InterpretationChip[]
  results: Page<ProductCard>
}

export interface Suggestions {
  queries: string[]
  categories: Ref[]
  products: ProductCard[]
  interpretation: { label: string; value: string }[]
}

export interface Compare {
  products: ProductCard[]
  rows: { key: string; label: string; group: string; values: (string | null)[]; differs: boolean }[]
}

export interface Profile {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
  avatarUrl?: string | null
  linkedProviders: string[]
  memberSince: string
  passwordChangedAt: string
  roles: string[]
  permissions: string[]
}

export interface Me {
  authenticated: boolean
  profile?: Profile
}

export interface SessionInfo {
  id: string
  device: string
  ipAddress: string
  createdAt: string
  lastUsedAt: string
  current: boolean
}

export interface LoginEvent {
  id: number
  outcome: string
  method: 'PASSWORD' | 'GOOGLE' | 'REFRESH'
  ipAddress: string | null
  device: string
  at: string
}

export type CartIssue = 'OUT_OF_STOCK' | 'DISCONTINUED' | 'QUANTITY_EXCEEDS_STOCK'

export interface CartLine {
  id: string
  product: ProductCard
  quantity: number
  unitPrice: Money
  lineTotal: Money
  issue?: CartIssue
}

export interface Cart {
  items: CartLine[]
  savedForLater: CartLine[]
  subtotal: Money
  itemCount: number
}

export type WalletMode = 'BUDGET' | 'UNLIMITED'

export interface Wallet {
  balance: Money
  mode: WalletMode
  exists: boolean
  startingBalance: Money
}

export interface WalletTransaction {
  id: string
  type: 'CREDIT' | 'PURCHASE' | 'REFUND' | 'RESET'
  amount: Money
  balanceBefore: Money
  balanceAfter: Money
  reference: string | null
  description: string
  createdAt: string
}

export interface Quote {
  lines: {
    productId: number
    slug: string
    name: string
    imageUrl?: string
    unitPrice: Money
    quantity: number
    lineTotal: Money
    issue?: string
  }[]
  itemCount: number
  subtotal: Money
  shipping: Money
  total: Money
  walletMode: WalletMode
  balance: Money
  balanceAfter?: Money
  shortfall?: Money
  canPlace: boolean
  simulation: true
}

export type DeliveryPreset =
  'ADDRESS' | 'HOME' | 'OFFICE' | 'DREAM_SETUP' | 'HOMELAB' | 'COLLECTION' | 'CUSTOM'

export interface SimulationAddress {
  label: string
  fullName?: string
  line1?: string
  line2?: string
  city?: string
  region?: string
  postalCode?: string
  country?: string
}

export interface AddressInput {
  label: string
  fullName: string
  line1: string
  line2?: string
  city: string
  region?: string
  postalCode: string
  country: string
}

export interface SavedAddress extends AddressInput {
  id: string
  isDefault: boolean
}

export interface Purchase {
  id: string
  orderNumber: string
  status: 'COMPLETED' | 'REFUNDED'
  createdAt: string
  refundedAt?: string
  itemCount: number
  subtotal: Money
  shipping: Money
  total: Money
  walletMode: WalletMode
  balanceBefore?: Money
  balanceAfter?: Money
  deliveryPreset: DeliveryPreset
  simulationAddress?: SimulationAddress
  items: {
    productId: number
    slug: string
    name: string
    categoryName: string
    imageUrl: string | null
    unitPrice: Money
    quantity: number
    lineTotal: Money
  }[]
  simulation: true
}

export interface PurchaseSummary {
  id: string
  orderNumber: string
  status: 'COMPLETED' | 'REFUNDED'
  createdAt: string
  itemCount: number
  total: Money
  walletMode: WalletMode
  thumbnails: string[]
}

export interface WishlistList {
  id: string
  name: string
  isDefault: boolean
  items: ProductCard[]
}

export interface CollectionView {
  items: {
    productId: number
    slug: string
    name: string
    categoryName: string
    imageUrl: string | null
    quantity: number
    virtualSpend: Money
    currentValue: Money
    firstAcquired: string
    lastAcquired: string
  }[]
  stats: {
    productsOwned: number
    distinctProducts: number
    purchases: number
    totalVirtualSpend: Money
    collectionValue: Money
    mostExpensive?: { label: string; amount: Money }
    largestPurchase?: { label: string; amount: Money }
    favoriteCategory?: string
  }
  achievements: { code: string; title: string; description: string; unlocked: boolean; progress: string }[]
  simulation: true
}

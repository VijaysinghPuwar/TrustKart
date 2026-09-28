import type { Money } from './money'

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER' | 'DISCONTINUED'
export type ImageMatch = 'EXACT' | 'PRODUCT_LINE' | 'REPRESENTATIVE' | 'RENDER'

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
  heroSlides: ProductCard[]
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
  /** Configurable choices (storage, colour…); empty for simple products. */
  options?: ProductOptionGroup[]
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

export type CartIssue = 'OUT_OF_STOCK' | 'DISCONTINUED' | 'QUANTITY_EXCEEDS_STOCK' | 'OPTION_UNAVAILABLE'

/** A product's configurable choices. At most one group carries prices (absolute unit prices). */
/** Photo of one option choice, e.g. the product in that colour. */
export interface OptionImage {
  small: string
  large: string
  width: number
  height: number
  alt: string
}

export interface ProductOptionGroup {
  name: string
  values: { label: string; price?: Money; default?: boolean; image?: OptionImage }[]
}

/** Group name to chosen label, e.g. { Storage: '512 GB', Color: 'Silver' }. */
export type OptionSelection = Record<string, string>

export interface CartLine {
  id: string
  product: ProductCard
  options: OptionSelection
  optionsLabel?: string
  /** The chosen configuration's own photo (e.g. the colour), when it has one. */
  optionImage?: string
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
    optionsLabel?: string
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

export type PurchaseStatus = 'COMPLETED' | 'CANCELLED' | 'REFUNDED'
export type TrackingStage =
  | 'PLACED'
  | 'PROCESSING'
  | 'PACKED'
  | 'SHIPPED'
  | 'IN_TRANSIT'
  | 'LOCAL_FACILITY'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'

export interface TrackingEvent {
  stage: TrackingStage
  label: string
  description: string
  location?: string
  at: string
  done: boolean
}

export interface Tracking {
  stage: TrackingStage
  stageLabel: string
  trackingNumber: string
  carrier: string
  estimatedDelivery: string
  deliveredAt?: string
  canCancel: boolean
  canReturn: boolean
  returnBy?: string
  progress: number
  events: TrackingEvent[]
}

export interface Purchase {
  id: string
  orderNumber: string
  status: PurchaseStatus
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
    optionsLabel?: string
    categoryName: string
    imageUrl: string | null
    unitPrice: Money
    quantity: number
    lineTotal: Money
  }[]
  tracking: Tracking
  simulation: true
}

export interface PurchaseSummary {
  id: string
  orderNumber: string
  status: PurchaseStatus
  createdAt: string
  itemCount: number
  total: Money
  walletMode: WalletMode
  thumbnails: string[]
  stage: TrackingStage
  stageLabel: string
  estimatedDelivery: string
}

export interface AppNotification {
  id: string
  type: string
  title: string
  body: string
  link?: string
  imageUrl?: string
  createdAt: string
  read: boolean
}

export interface NotificationPage {
  items: AppNotification[]
  unreadCount: number
  page: number
  size: number
  totalItems: number
}

export interface NotificationPreferences {
  orderUpdates: boolean
  deliveryUpdates: boolean
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
  achievements: Achievement[]
  simulation: true
}

export type AchievementTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'LEGENDARY'

export interface Achievement {
  code: string
  title: string
  description: string
  group: string
  tier: AchievementTier
  unlocked: boolean
  /** Human progress, e.g. "$1.2M / $1B" or "3 / 5". */
  progress: string
  percent: number
}

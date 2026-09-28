import { Shelf } from '@/components/commerce/Shelf'
import { ErrorState } from '@/components/ui/ErrorState'
import { useHome, useLookup } from '@/data/catalog'
import { useWallet } from '@/data/shopping'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import { useRecentlyViewed } from '@/state/recentlyViewed'
import { CategoryGrid } from './CategoryGrid'
import { CollectionTile } from './CollectionTile'
import { productTileItems } from './tileItems'
import { HeroFeature } from './HeroFeature'
import { TrustStrip } from './TrustStrip'

export function HomePage() {
  usePageTitle('Shop everything. Spend nothing.')
  const home = useHome()
  const recent = useRecentlyViewed()
  const recentProducts = useLookup(recent.ids)
  const { data: wallet } = useWallet()
  const tiles = home.data?.tiles ?? []

  if (home.isError)
    return <ErrorState error={home.error} title="The store didn’t load" onRetry={() => void home.refetch()} />

  // The first tile is personal: recently viewed if there's history, otherwise the shopper's wallet.
  const firstTile =
    recentProducts.data && recentProducts.data.length > 0 ? (
      <CollectionTile
        title="Keep shopping for"
        link={{ label: 'View browsing history', to: '#recently-viewed' }}
        items={productTileItems(recentProducts.data)}
      />
    ) : (
      <CollectionTile
        title="Your TrustKart Wallet"
        link={{ label: 'Open wallet', to: '/wallet' }}
        items={
          wallet && [
            {
              key: 'balance',
              label: 'Virtual balance',
              meta: wallet.mode === 'UNLIMITED' ? '∞' : formatMoney(wallet.balance),
              to: '/wallet',
            },
            {
              key: 'mode',
              label: 'Mode',
              meta: wallet.mode === 'UNLIMITED' ? 'Unlimited' : 'Budget',
              to: '/wallet',
            },
            { key: 'cards', label: 'Payment details needed', meta: 'None', metaTone: 'trust', to: '/about' },
            { key: 'ship', label: 'Real money spent', meta: '$0.00', metaTone: 'trust', to: '/about' },
          ]
        }
      />
    )

  return (
    <div className="flex flex-col gap-9">
      <section aria-label="Featured" className="grid grid-cols-2 gap-3.5 sm:grid-cols-3 lg:grid-cols-4">
        <HeroFeature product={home.isPending ? undefined : (home.data.hero ?? null)} />
        {firstTile}
        {(home.isPending ? Array.from({ length: 5 }, () => null) : tiles.slice(0, 5)).map((t, i) =>
          t ? (
            <CollectionTile
              key={t.key}
              title={t.title}
              link={{ label: 'See the collection', to: t.link }}
              items={productTileItems(t.items)}
            />
          ) : (
            <CollectionTile key={i} title="Loading" link={{ label: '', to: '/' }} />
          ),
        )}
      </section>

      <TrustStrip />

      <Shelf
        id="deals"
        layout="row"
        title="Today’s deals"
        action={{ label: 'See all deals', to: '/deals' }}
        items={home.data?.deals}
        loading={home.isPending}
      />
      <Shelf
        id="featured"
        layout="grid"
        title="Featured technology"
        subtitle="Hand-picked from the catalog"
        items={home.data?.featured}
        loading={home.isPending}
      />
      {recent.ids.length > 0 && (
        <Shelf
          id="recently-viewed"
          layout="row"
          title="Recently viewed"
          subtitle="Stored only in this browser"
          action={{ label: 'Clear history', onClick: recent.clear }}
          items={recentProducts.data}
          loading={recentProducts.isPending}
        />
      )}
      {home.data && <CategoryGrid categories={home.data.categories} />}
    </div>
  )
}

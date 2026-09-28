import { Shelf } from '@/components/commerce/Shelf'
import { ErrorState } from '@/components/ui/ErrorState'
import { useHome, useLookup } from '@/data/catalog'
import { usePageTitle } from '@/lib/usePageTitle'
import { useRecentlyViewed } from '@/state/recentlyViewed'
import { CategoryGrid } from './CategoryGrid'
import { CollectionTile } from './CollectionTile'
import { productTileItems } from './tileItems'
import { HeroCarousel } from './HeroCarousel'
import { TrustStrip } from './TrustStrip'

export function HomePage() {
  usePageTitle('Shop everything. Spend nothing.')
  const home = useHome()
  const recent = useRecentlyViewed()
  const recentProducts = useLookup(recent.ids)
  const tiles = home.data?.tiles ?? []

  if (home.isError)
    return <ErrorState error={home.error} title="The store didn’t load" onRetry={() => void home.refetch()} />

  // Recently viewed leads the grid once there's history; otherwise it's all curated collections.
  const hasRecent = !!recentProducts.data && recentProducts.data.length > 0
  const collectionTiles = home.isPending
    ? Array.from({ length: 6 }, () => null)
    : tiles.slice(0, hasRecent ? 5 : 6)

  return (
    <div className="flex flex-col gap-9">
      <HeroCarousel slides={home.isPending ? undefined : home.data.heroSlides} />

      <section aria-label="Shop by collection" className="grid grid-cols-2 gap-3.5 sm:grid-cols-3">
        {hasRecent && (
          <CollectionTile
            title="Pick up where you left off"
            link={{ label: 'View browsing history', to: '#recently-viewed' }}
            items={productTileItems(recentProducts.data)}
          />
        )}
        {collectionTiles.map((t, i) =>
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

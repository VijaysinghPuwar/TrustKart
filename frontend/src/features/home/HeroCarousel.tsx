import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Skeleton'
import { useAddToCartAction } from '@/components/commerce/useAddToCart'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import type { ProductCard } from '@/lib/types'

const INTERVAL_MS = 7000

/** One backdrop per slide position, so consecutive slides always look different. */
const BACKDROPS = [
  'from-[#0b1f4d] via-[#123a8a] to-[#1d5fd6]',
  'from-[#07261f] via-[#0d4a3a] to-[#16805f]',
  'from-[#1f0b3d] via-[#3b1476] to-[#6d28d9]',
  'from-[#2b1206] via-[#6b2a0a] to-[#c2570c]',
  'from-[#0f172a] via-[#1e293b] to-[#475569]',
]

function eyebrow(p: ProductCard) {
  if (p.percentOff > 0) return `${p.category.name} deal · Save ${String(p.percentOff)}%`
  return `Featured in ${p.category.name}`
}

/** "$1,299.99" as a large dollar figure with superscript cents, the way retail banners set prices. */
function BigPrice({ amount }: { amount: string }) {
  const [whole, cents = '00'] = formatMoney(amount).split('.')
  return (
    <span className="flex items-start font-extrabold leading-none tracking-tight tabular">
      <span className="text-[clamp(40px,5vw,64px)]">{whole}</span>
      <span className="mt-1 text-[clamp(18px,2vw,26px)]">{cents}</span>
    </span>
  )
}

/**
 * Full-width promotional banner built from live catalog deals (never from hard-coded marketing copy):
 * rotates every 7 s, pauses on hover, focus and via the pause button, and doesn't auto-rotate at all for
 * reduced-motion users. Follows the WAI-ARIA carousel pattern.
 */
export function HeroCarousel({ slides }: { slides: ProductCard[] | undefined }) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const [hovered, setHovered] = useState(false)
  const root = useRef<HTMLElement>(null)
  const [reducedMotion] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  )
  const { addToCart, justAdded, pendingId } = useAddToCartAction()
  const count = slides?.length ?? 0
  const go = useCallback((to: number) => setIndex(((to % count) + count) % count), [count])

  // Pause while the pointer or keyboard focus is inside the banner (WCAG 2.2.2).
  useEffect(() => {
    const el = root.current
    if (!el) return
    const on = () => setHovered(true)
    const off = () => setHovered(false)
    const blur = (e: FocusEvent) => {
      if (!el.contains(e.relatedTarget as Node | null)) off()
    }
    el.addEventListener('pointerenter', on)
    el.addEventListener('pointerleave', off)
    el.addEventListener('focusin', on)
    el.addEventListener('focusout', blur)
    return () => {
      el.removeEventListener('pointerenter', on)
      el.removeEventListener('pointerleave', off)
      el.removeEventListener('focusin', on)
      el.removeEventListener('focusout', blur)
    }
  }, [count])

  const rotating = count > 1 && !paused && !hovered && !reducedMotion
  useEffect(() => {
    if (!rotating) return
    const t = window.setTimeout(() => go(index + 1), INTERVAL_MS)
    return () => window.clearTimeout(t)
  }, [rotating, index, go])

  if (!slides) return <Skeleton className="h-[420px] rounded-tile sm:h-[360px]" />
  if (count === 0) return null
  const p = slides[Math.min(index, count - 1)]

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Today’s top deals"
      ref={root}
      className="relative overflow-hidden rounded-tile"
    >
      <div
        key={p.id}
        role="group"
        aria-roledescription="slide"
        aria-label={`${String(index + 1)} of ${String(count)}: ${p.name}`}
        className={cn(
          'tk-banner-slide grid min-h-[360px] grid-cols-1 bg-gradient-to-br text-white md:grid-cols-[1fr_auto]',
          BACKDROPS[index % BACKDROPS.length],
        )}
      >
        <Link
          to={`/p/${p.slug}`}
          tabIndex={-1}
          aria-hidden="true"
          className="relative order-first flex items-center justify-center p-5 md:order-last md:justify-start md:py-8 md:pl-2 md:pr-16"
        >
          <span className="absolute inset-6 rounded-full bg-white/25 blur-3xl" />
          <span className="tk-img-well relative flex aspect-[4/3] w-full max-w-[520px] items-center justify-center rounded-card bg-white p-5 shadow-2xl md:h-[300px] md:w-auto">
            {p.image && (
              <img
                src={p.image.small}
                srcSet={`${p.image.small} 400w, ${p.image.large} 800w`}
                sizes="(max-width: 768px) 90vw, 400px"
                width={p.image.width}
                height={p.image.height}
                alt=""
                fetchPriority={index === 0 ? 'high' : 'auto'}
                className="tk-product-img size-full object-contain"
              />
            )}
          </span>
        </Link>

        <div className="flex flex-col justify-center gap-3 px-6 pb-14 pt-1 md:py-10 md:pl-12 md:pr-4">
          <p className="w-fit rounded-full bg-white/15 px-3 py-1 text-xs font-semibold uppercase tracking-wide">
            {eyebrow(p)}
          </p>
          <h2 className="text-balance text-[clamp(24px,3vw,38px)] font-bold leading-tight tracking-[-0.02em]">
            <Link to={`/p/${p.slug}`} className="text-white no-underline hover:text-white hover:underline">
              {p.name}
            </Link>
          </h2>
          {p.summary && <p className="line-clamp-2 max-w-[52ch] text-[15px] text-white/85">{p.summary}</p>}
          <div className="flex flex-wrap items-end gap-x-3 gap-y-1">
            <BigPrice amount={p.price} />
            {p.compareAtPrice && (
              <span className="pb-1 text-sm text-white/75">
                List price <span className="line-through">{formatMoney(p.compareAtPrice)}</span>
              </span>
            )}
          </div>
          <div className="mt-1 flex flex-wrap gap-2">
            <Button
              variant="accent"
              size="lg"
              className="h-11"
              loading={pendingId === p.id}
              onClick={() => addToCart(p)}
            >
              {justAdded === p.id ? 'Added ✓' : 'Add to cart'}
            </Button>
            <ButtonLink variant="inverse" size="lg" className="h-11" to={`/p/${p.slug}`}>
              See details
            </ButtonLink>
          </div>
        </div>
      </div>

      {count > 1 && (
        <>
          <button
            type="button"
            onClick={() => go(index - 1)}
            aria-label="Previous deal"
            className="absolute left-2 top-1/2 hidden size-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/35 text-white hover:bg-black/55 md:flex"
          >
            <ChevronLeft className="size-5" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => go(index + 1)}
            aria-label="Next deal"
            className="absolute right-2 top-1/2 hidden size-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/35 text-white hover:bg-black/55 md:flex"
          >
            <ChevronRight className="size-5" aria-hidden="true" />
          </button>
          <div className="absolute bottom-4 left-6 flex items-center gap-2 md:left-12">
            {!reducedMotion && (
              <button
                type="button"
                onClick={() => setPaused((v) => !v)}
                aria-label={paused ? 'Resume rotating deals' : 'Pause rotating deals'}
                className="flex size-7 items-center justify-center rounded-full bg-black/35 text-white hover:bg-black/55"
              >
                {paused ? (
                  <Play className="size-3.5" aria-hidden="true" />
                ) : (
                  <Pause className="size-3.5" aria-hidden="true" />
                )}
              </button>
            )}
            {slides.map((s, i) => (
              <button
                key={s.id}
                type="button"
                onClick={() => go(i)}
                aria-label={`Show deal ${String(i + 1)}: ${s.name}`}
                aria-current={i === index}
                className="flex size-6 items-center justify-center"
              >
                <span
                  className={cn(
                    'block h-2 rounded-full transition-all',
                    i === index ? 'w-6 bg-white' : 'w-2 bg-white/45',
                  )}
                />
              </button>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

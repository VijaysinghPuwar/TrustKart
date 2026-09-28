import { Suspense, useEffect } from 'react'
import { Outlet, ScrollRestoration, useLocation } from 'react-router'
import { CartDrawer } from '@/components/commerce/CartDrawer'
import { CompareTray } from '@/components/commerce/CompareTray'
import { SiteFooter } from '@/components/layout/SiteFooter'
import { SiteHeader } from '@/components/layout/SiteHeader'
import { SkipLink } from '@/components/layout/SkipLink'
import { VirtualBar } from '@/components/layout/VirtualBar'
import { PageSpinner } from '@/components/ui/PageSpinner'

export function RootLayout() {
  const { pathname } = useLocation()
  // Move focus to main content on navigation so screen-reader users hear the new page.
  useEffect(() => {
    document.getElementById('main')?.focus({ preventScroll: true })
  }, [pathname])

  return (
    <div className="flex min-h-dvh flex-col">
      <SkipLink />
      <VirtualBar />
      <SiteHeader />
      <main
        id="main"
        tabIndex={-1}
        className="page-width page-gutter flex flex-1 flex-col pb-14 pt-4 outline-none"
      >
        <Suspense fallback={<PageSpinner />}>
          <Outlet />
        </Suspense>
      </main>
      <SiteFooter />
      <CompareTray />
      <CartDrawer />
      <ScrollRestoration />
    </div>
  )
}

import { AxeBuilder } from '@axe-core/playwright'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

// Overlay geometry, truncation and keyboard behaviour that a root-overflow check can't see. Each test sets its own
// viewport, so they run once (in the desktop project).
test.skip(({ isMobile }) => isMobile, 'viewports are set explicitly')

/** Calls the API through the page's cookie jar, echoing the CSRF cookie the way the app does. */
async function api(request: APIRequestContext, method: string, path: string, data?: unknown) {
  let state = await request.storageState()
  if (!state.cookies.some((c) => c.name === 'XSRF-TOKEN')) {
    await request.get('/api/v1/auth/csrf')
    state = await request.storageState()
  }
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const res = await request.fetch(path, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': xsrf, 'Idempotency-Key': crypto.randomUUID() },
  })
  expect(res.status(), `${method} ${path}`).toBeLessThan(300)
  return res.status() === 204 ? null : ((await res.json()) as Record<string, unknown>)
}

async function register(request: APIRequestContext, tag: string) {
  await api(request, 'POST', '/api/v1/auth/register', {
    email: `e2e-${tag}-${crypto.randomUUID().slice(0, 8)}@example.test`,
    password: 'correct-horse-battery-staple-42',
    displayName: `E2E ${tag}`,
  })
}

const viewportWidth = (page: Page) => page.evaluate<number>('window.innerWidth')

test('the notification panel stays inside the viewport and Escape returns focus to the bell', async ({
  page,
}) => {
  for (const width of [320, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 800 })
    await page.goto('/')
    const bell = page.getByRole('button', { name: /^Notifications/ })
    await bell.click()
    const panel = page.getByRole('region', { name: 'Notifications' })
    await expect(panel).toBeVisible()
    const box = await panel.boundingBox()
    expect(box?.x ?? -1, `panel left edge at ${String(width)}px`).toBeGreaterThanOrEqual(0)
    expect((box?.x ?? 0) + (box?.width ?? 0), `panel right edge at ${String(width)}px`).toBeLessThanOrEqual(
      await viewportWidth(page),
    )
    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
    await expect(bell).toBeFocused()
  }
})

test('the compare tray sits above the phone purchase bar, and both stay usable', async ({ page }) => {
  for (const width of [320, 390, 430]) {
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/p/apple-iphone-18-pro')
    await page.getByRole('button', { name: 'Compare', exact: true }).click()
    const tray = page.getByRole('region', { name: 'Compare products' })
    await expect(tray).toBeVisible()
    const bar = page.getByRole('button', { name: 'Add to cart' }).last()
    const trayBox = await tray.boundingBox()
    const barTop = (await bar.locator('..').boundingBox())?.y ?? 0
    expect(
      (trayBox?.y ?? 0) + (trayBox?.height ?? 0),
      `tray bottom vs bar top at ${String(width)}px`,
    ).toBeLessThanOrEqual(barTop)
    await bar.click() // would be intercepted by the tray if they overlapped
    await expect(page.getByRole('status').filter({ hasText: 'Added to cart' })).toBeVisible()
    await tray.getByRole('button', { name: 'Clear' }).click()
    await expect(tray).toBeHidden()
  }
})

test('account summary cards show the whole wallet mode and amount on a tablet', async ({ page }) => {
  // A signed-in account (which gets the side menu) with Unlimited mode and a six-figure collection.
  await page.goto('/')
  await page.waitForLoadState('networkidle')
  await register(page.request, 'account')
  await api(page.request, 'PUT', '/api/v1/wallet/mode', { mode: 'UNLIMITED' })
  const priciest = (await api(
    page.request,
    'GET',
    '/api/v1/catalog/products?size=10&sort=price_desc&inStock=true',
  )) as {
    items: { id: number }[]
  }
  await api(page.request, 'POST', '/api/v1/cart/items', { productId: priciest.items[0]?.id, quantity: 1 })
  const quote = (await api(page.request, 'GET', '/api/v1/checkout/quote')) as { total: string }
  await api(page.request, 'POST', '/api/v1/purchases', { deliveryPreset: 'HOME', expectedTotal: quote.total })

  for (const width of [600, 768, 820, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1024 })
    await page.goto('/account')
    await expect(page.getByRole('link', { name: /Wallet balance/ })).toContainText('∞ Unlimited')
    await expect(page.getByRole('link', { name: /Collection value/ })).toContainText('$')
    // The e2e project has no DOM types, so the check runs as an expression in the page.
    const clipped = await page.evaluate<string[]>(
      `[...document.querySelectorAll('main a')]
        .filter((a) => a.children.length === 2 && /^(Wallet balance|Collection value|Orders)$/.test(a.firstElementChild.textContent))
        .filter((a) => a.lastElementChild.scrollWidth > a.lastElementChild.clientWidth)
        .map((a) => a.firstElementChild.textContent)`,
    )
    expect(clipped, `clipped summary values at ${String(width)}px`).toEqual([])
  }
})

test('every retained notification is reachable through pages', async ({ page }) => {
  // The UI contract is what's tested here; the server's paging is covered by its own tests.
  const total = 65
  const requested: string[] = []
  await page.route('**/api/v1/notifications?page=*', async (route) => {
    const url = new URL(route.request().url())
    requested.push(url.search)
    const p = Number(url.searchParams.get('page'))
    const size = Number(url.searchParams.get('size'))
    const items = Array.from({ length: Math.max(0, Math.min(size, total - p * size)) }, (_, i) => ({
      id: `00000000-0000-4000-8000-${String(p * size + i).padStart(12, '0')}`,
      type: 'DELIVERED',
      title: `Update ${String(p * size + i + 1)}`,
      body: 'Delivered.',
      createdAt: new Date(Date.UTC(2026, 8, 1) - (p * size + i) * 3_600_000).toISOString(),
      read: true,
    }))
    await route.fulfill({ json: { items, unreadCount: 0, page: p, size, totalItems: total } })
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/account/notifications')
  await expect(page.getByText('Update 1', { exact: true })).toBeVisible()
  await page.getByRole('navigation', { name: 'Pagination' }).getByRole('button', { name: '3' }).click()
  await expect(page.getByText('Update 65', { exact: true })).toBeVisible()
  expect(requested.at(-1)).toContain('page=2')
})

test('leaderboard period tabs follow the arrow-key tabs pattern', async ({ page }) => {
  await page.goto('/rankings')
  const month = page.getByRole('tab', { name: 'This Month' })
  const allTime = page.getByRole('tab', { name: 'All Time' })
  await expect(month).toHaveAttribute('tabindex', '0')
  await expect(allTime).toHaveAttribute('tabindex', '-1')
  await month.focus()
  await page.keyboard.press('ArrowRight')
  await expect(allTime).toBeFocused()
  await expect(allTime).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('heading', { name: /All-time Top 100/ })).toBeVisible()
  await page.keyboard.press('ArrowRight') // wraps
  await expect(month).toBeFocused()
  await page.keyboard.press('End')
  await expect(allTime).toHaveAttribute('aria-selected', 'true')
  await page.keyboard.press('Home')
  await expect(month).toHaveAttribute('aria-selected', 'true')
})

test('a recent search can be removed with the keyboard', async ({ page }) => {
  await page.goto('/')
  const search = page.getByRole('combobox', { name: "Describe what you're looking for" })
  await search.fill('macbook air')
  await search.press('Enter')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('macbook air')

  await search.fill('')
  await search.focus()
  const recent = page.getByRole('option', { name: /macbook air/ })
  await expect(recent).toBeVisible()
  for (let i = 0; i < 12 && (await recent.getAttribute('aria-selected')) !== 'true'; i++)
    await search.press('ArrowDown')
  await expect(recent).toHaveAttribute('aria-selected', 'true')
  await search.press('Delete')
  await expect(recent).toHaveCount(0)
  await expect(
    page.getByRole('status').filter({ hasText: 'Removed macbook air from recent searches' }),
  ).toBeAttached()
  await expect(search).toBeFocused()
})

test('inline links in running text are underlined, not told apart by colour alone', async ({ page }) => {
  await page.goto('/')
  await page.waitForLoadState('networkidle')
  await register(page.request, 'links')
  await page.goto('/rankings')
  const change = page.getByRole('link', { name: 'Change' })
  await expect(change).toBeVisible()
  await expect(change).toHaveCSS('text-decoration-line', 'underline')
})

test('small text on tinted chips keeps AA contrast in light and dark themes', async ({ page }) => {
  for (const colorScheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme })
    for (const path of ['/rankings', '/collection']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      const { violations } = await new AxeBuilder({ page }).withRules(['color-contrast']).analyze()
      expect(
        violations.flatMap((v) => v.nodes.map((n) => n.target.join(' '))),
        `${path} (${colorScheme})`,
      ).toEqual([])
    }
  }
})

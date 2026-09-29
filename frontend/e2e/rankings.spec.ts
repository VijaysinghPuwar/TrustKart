import { expect, test, type APIRequestContext } from '@playwright/test'

/** Calls the API through the page's own cookie jar, echoing the CSRF cookie the way the app does. */
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

test('guests can browse both boards and are invited to sign in', async ({ page }) => {
  await page.goto('/rankings')
  await expect(page.getByRole('heading', { name: 'Top Virtual Spenders' })).toBeVisible()
  await expect(page.getByText('No real money is involved')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Sign in' }).first()).toBeVisible()

  await page.getByRole('tab', { name: 'All Time' }).click()
  await expect(page.getByRole('tab', { name: 'All Time' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('heading', { name: /All-time Top 100/ })).toBeVisible()
  await page.getByRole('tab', { name: 'This Month' }).click()
  await expect(page.getByRole('heading', { name: /Top 50/ })).toBeVisible()

  // Nothing that looks like an email address is ever rendered.
  await expect(page.getByRole('main')).not.toContainText('@')
})

test('a purchase puts a signed-in shopper on the board, under the name they choose', async ({ page }) => {
  // Let the app finish its own start-up requests so the CSRF token is not rotated mid-registration.
  await page.goto('/')
  await page.waitForLoadState('networkidle')
  const id = crypto.randomUUID().slice(0, 8)
  await api(page.request, 'POST', '/api/v1/auth/register', {
    email: `e2e-rank-${id}@example.test`,
    password: 'correct-horse-battery-staple-42',
    displayName: 'E2E Ranker',
  })

  await page.goto('/rankings')
  await expect(page.getByRole('heading', { name: 'Your position' })).toBeVisible()
  await expect(page.getByText('Not ranked yet').first()).toBeVisible()

  // Buy something through the real checkout API with the unlimited wallet.
  await api(page.request, 'PUT', '/api/v1/wallet/mode', { mode: 'UNLIMITED' })
  const products = (await api(page.request, 'GET', '/api/v1/catalog/products?size=48')) as {
    items: { id: number; stockStatus: string }[]
  }
  const product = products.items.find((p) => p.stockStatus === 'IN_STOCK')
  await api(page.request, 'POST', '/api/v1/cart/items', { productId: product?.id, quantity: 1 })
  const quote = (await api(page.request, 'GET', '/api/v1/checkout/quote')) as { total: string }
  await api(page.request, 'POST', '/api/v1/purchases', {
    deliveryPreset: 'ADDRESS',
    simulationAddress: {
      label: 'Home',
      fullName: 'E2E Ranker',
      line1: '1 Test Lane',
      city: 'Springfield',
      region: 'IL',
      postalCode: '62701',
      country: 'US',
    },
    expectedTotal: quote.total,
  })

  // Anonymous by default, then public under a chosen name.
  const name = `Ranker_${id.replace(/-/g, '')}`.slice(0, 20)
  await page.goto('/account/rankings')
  await page.getByLabel('Public name').fill(name)
  await page.getByRole('switch', { name: /Show me on public leaderboards/ }).check()
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Leaderboard profile saved')).toBeVisible()

  await page.goto('/rankings')
  const position = page.getByRole('region', { name: 'Your position' })
  await expect(position).toContainText(name)
  await expect(position.getByText(/^#\d/).first()).toBeVisible()
  await expect(page.getByText('You', { exact: true }).locator('visible=true').first()).toBeVisible()
})

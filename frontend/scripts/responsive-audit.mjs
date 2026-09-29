// Responsive audit: loads every customer route at 13 widths as a signed-in shopper with orders and a cart, and
// reports horizontal page overflow, elements wider than the viewport, and tap targets under 40px on phones.
//
//   node scripts/responsive-audit.mjs http://localhost:4173 [screenshot-dir]
//
// With a screenshot directory it also saves full-page captures of the main routes at 320/390/768/1024/1440/1920.
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const base = process.argv[2] ?? 'http://localhost:4173'
const shots = process.argv[3]
const WIDTHS = [320, 360, 375, 390, 430, 600, 768, 820, 1024, 1280, 1440, 1728, 1920]
const SHOT_WIDTHS = new Set([320, 390, 768, 1024, 1440, 1920])

async function api(page, method, path, data) {
  let state = await page.request.storageState()
  if (!state.cookies.some((c) => c.name === 'XSRF-TOKEN')) {
    await page.request.get(base + '/api/v1/auth/csrf')
    state = await page.request.storageState()
  }
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const res = await page.request.fetch(base + path, { method, data,
    headers: { 'X-XSRF-TOKEN': xsrf, 'Idempotency-Key': crypto.randomUUID() } })
  if (res.status() >= 300) throw new Error(`${method} ${path} -> ${res.status()}`)
  return res.status() === 204 ? null : res.json()
}

const browser = await chromium.launch()
const context = await browser.newContext()
const page = await context.newPage()
await page.goto(base + '/')
await page.waitForLoadState('networkidle')
await api(page, 'POST', '/api/v1/auth/register', { email: `audit-${crypto.randomUUID().slice(0, 8)}@example.test`,
  password: 'correct-horse-battery-staple-42', displayName: 'Audit Shopper' })
await api(page, 'PUT', '/api/v1/wallet/mode', { mode: 'UNLIMITED' })
const list = await api(page, 'GET', '/api/v1/catalog/products?size=48&sort=price_desc')
const ids = list.items.filter((p) => p.stockStatus === 'IN_STOCK').map((p) => p.id)
const address = { label: 'Home', fullName: 'Audit Shopper', line1: '1 Test Lane', city: 'Springfield', region: 'IL',
  postalCode: '62701', country: 'US' }
let orderId
for (let i = 0; i < 2; i++) {
  await api(page, 'POST', '/api/v1/cart/items', { productId: ids[i], quantity: 1 })
  const quote = await api(page, 'GET', '/api/v1/checkout/quote')
  const order = await api(page, 'POST', '/api/v1/purchases', { deliveryPreset: 'ADDRESS', simulationAddress: address,
    expectedTotal: quote.total })
  orderId = order.id ?? order.publicId
}
for (let i = 2; i < 5; i++) await api(page, 'POST', '/api/v1/cart/items', { productId: ids[i], quantity: 2 })
await api(page, 'POST', '/api/v1/wishlist/items', { productId: ids[5] }).catch(() => {})
const product = list.items[0].slug

const ROUTES = [
  ['home', '/'], ['search', '/search?q=laptop'], ['category', '/c/laptops'], ['product', `/p/${product}`],
  ['compare', `/compare?p=${list.items[0].slug},${list.items[1].slug}`], ['cart', '/cart'], ['checkout', '/checkout'],
  ['wallet', '/wallet'], ['collection', '/collection'], ['wishlist', '/wishlist'], ['rankings', '/rankings'],
  ['account', '/account'], ['purchases', '/account/purchases'], ['receipt', `/account/purchases/${orderId}`],
  ['notifications', '/account/notifications'], ['security', '/account/security'], ['addresses', '/account/addresses'],
  ['lb-settings', '/account/rankings'], ['about', '/about/project'], ['notfound', '/nope'],
]

if (shots) mkdirSync(shots, { recursive: true })
const problems = []
for (const [name, path] of ROUTES) {
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: width < 768 ? 844 : 900 })
    await page.goto(base + path, { waitUntil: 'networkidle' })
    await page.waitForTimeout(300)
    const r = await page.evaluate((w) => {
      const overflow = document.documentElement.scrollWidth - window.innerWidth
      const wide = []
      for (const el of document.querySelectorAll('body *')) {
        const b = el.getBoundingClientRect()
        if (b.width === 0 || b.height === 0) continue
        // Only report the outermost offender; skip content inside a horizontal scroller.
        let scroller = false
        for (let p = el.parentElement; p; p = p.parentElement) {
          const s = getComputedStyle(p)
          if (['auto', 'scroll', 'hidden', 'clip'].includes(s.overflowX) && p !== document.body) { scroller = true; break }
        }
        if (!scroller && (b.right > w + 1 || b.left < -1)) {
          const id = el.tagName.toLowerCase() + (el.className && typeof el.className === 'string' ? '.' + el.className.split(' ').slice(0, 3).join('.') : '')
          if (!wide.some((x) => x.startsWith(id))) wide.push(`${id} [${Math.round(b.left)}..${Math.round(b.right)}]`)
        }
      }
      const small = []
      if (w < 768) {
        for (const el of document.querySelectorAll('a, button, input[type=checkbox], input[type=radio], [role=tab], select')) {
          const b = el.getBoundingClientRect()
          const s = getComputedStyle(el)
          if (b.width === 0 || s.visibility === 'hidden' || el.closest('[aria-hidden=true],dialog:not([open])')) continue
          if ((b.width < 40 || b.height < 40) && !(el.tagName === 'A' && getComputedStyle(el).display === 'inline')) {
            const label = (el.getAttribute('aria-label') || el.textContent || el.tagName).trim().slice(0, 30)
            small.push(`${label} ${Math.round(b.width)}x${Math.round(b.height)}`)
          }
        }
      }
      return { overflow, wide: wide.slice(0, 4), small: [...new Set(small)].slice(0, 8) }
    }, width)
    if (r.overflow > 0 || r.wide.length) problems.push(`${name} @${width}: overflow ${r.overflow}px ${r.wide.join(' | ')}`)
    if (width === 390 && r.small.length) problems.push(`${name} @390 small targets: ${r.small.join('; ')}`)
    if (shots && SHOT_WIDTHS.has(width)) await page.screenshot({ path: `${shots}/${name}-${width}.png`, fullPage: true })
  }
  console.log(`checked ${name}`)
}
await browser.close()
console.log(problems.length ? problems.join('\n') : 'no problems found')

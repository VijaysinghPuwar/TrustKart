// Page-weight and Web Vitals measurement for TrustKart.
//
//   npm run build && npx vite preview --port 4173 &   (proxies /api to the local backend on :8080)
//   node scripts/measure.mjs http://localhost:4173 out.json
//
// Every route is loaded in a fresh Chromium context with a cold cache, as a phone (390x844, 4x CPU slowdown,
// ~9 Mbps / 60 ms network) and as a desktop (1440x900, unthrottled). For each load it records the number of
// requests, bytes transferred (compressed, as sent over the wire), API calls and duplicate API calls, and LCP, CLS,
// FCP and TTFB from the browser's own PerformanceObserver entries. Numbers vary between runs; compare medians.
import { chromium } from '@playwright/test'
import { writeFileSync } from 'node:fs'

const base = process.argv[2] ?? 'http://localhost:4173'
const out = process.argv[3]
const runs = Number(process.env.RUNS ?? 3)

const ROUTES = [
  ['home', '/'],
  ['search', '/search?q=gaming%20laptop'],
  ['category', '/c/laptops'],
  ['deals', '/deals'],
  ['product', '/p/apple-iphone-18-pro'],
  ['compare', '/compare'],
  ['rankings', '/rankings'],
  ['cart', '/cart'],
  ['wallet', '/wallet'],
  ['signin', '/signin'],
]

const PROFILES = {
  mobile: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 3, cpu: 4,
    net: { offline: false, latency: 60, downloadThroughput: (9 * 1024 * 1024) / 8, uploadThroughput: (2 * 1024 * 1024) / 8 } },
  desktop: { viewport: { width: 1440, height: 900 }, isMobile: false, hasTouch: false, deviceScaleFactor: 1, cpu: 1, net: null },
}

const VITALS = () => {
  window.__vitals = { lcp: 0, cls: 0, fcp: 0, lcpElement: '' }
  new PerformanceObserver((l) => {
    for (const e of l.getEntries()) {
      window.__vitals.lcp = e.startTime
      window.__vitals.lcpElement = e.element ? e.element.tagName + (e.url ? ' ' + e.url.split('/').pop() : '') : ''
    }
  }).observe({ type: 'largest-contentful-paint', buffered: true })
  new PerformanceObserver((l) => {
    for (const e of l.getEntries()) if (!e.hadRecentInput) window.__vitals.cls += e.value
  }).observe({ type: 'layout-shift', buffered: true })
  new PerformanceObserver((l) => {
    for (const e of l.getEntries()) if (e.name === 'first-contentful-paint') window.__vitals.fcp = e.startTime
  }).observe({ type: 'paint', buffered: true })
}

async function measure(browser, profileName, path) {
  const p = PROFILES[profileName]
  const context = await browser.newContext({ viewport: p.viewport, isMobile: p.isMobile, hasTouch: p.hasTouch,
    deviceScaleFactor: p.deviceScaleFactor })
  const page = await context.newPage()
  const cdp = await context.newCDPSession(page)
  await cdp.send('Network.enable')
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
  if (p.net) await cdp.send('Network.emulateNetworkConditions', p.net)
  if (p.cpu > 1) await cdp.send('Emulation.setCPUThrottlingRate', { rate: p.cpu })
  const requests = new Map()
  let bytes = 0
  const byType = {}
  cdp.on('Network.responseReceived', (e) => requests.set(e.requestId, { url: e.response.url, type: e.type }))
  cdp.on('Network.loadingFinished', (e) => {
    bytes += e.encodedDataLength
    const r = requests.get(e.requestId)
    if (r) byType[r.type] = (byType[r.type] ?? 0) + e.encodedDataLength
  })
  const consoleErrors = []
  page.on('console', (m) => m.type() === 'error' && consoleErrors.push(m.text().slice(0, 160)))
  await page.addInitScript(VITALS)
  await page.goto(base + path, { waitUntil: 'networkidle', timeout: 120_000 })
  await page.waitForTimeout(1500)
  // Scroll to the bottom and back to count what lazy content costs.
  const loadedBytes = bytes
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 600) {
      window.scrollTo(0, y)
      await new Promise((r) => setTimeout(r, 120))
    }
  })
  await page.waitForLoadState('networkidle')
  const vitals = await page.evaluate(() => window.__vitals)
  const nav = await page.evaluate(() => {
    const n = performance.getEntriesByType('navigation')[0]
    return { ttfb: n.responseStart, dcl: n.domContentLoadedEventEnd }
  })
  const dom = await page.evaluate(() => document.getElementsByTagName('*').length)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  const all = [...requests.values()]
  const api = all.filter((r) => r.url.includes('/api/')).map((r) => new URL(r.url).pathname + new URL(r.url).search)
  const dupes = [...new Set(api.filter((u, i) => api.indexOf(u) !== i))]
  await context.close()
  return { requests: all.length, kb: +(loadedBytes / 1024).toFixed(1), kbAfterScroll: +(bytes / 1024).toFixed(1),
    kbByType: Object.fromEntries(Object.entries(byType).map(([k, v]) => [k, +(v / 1024).toFixed(1)])),
    images: all.filter((r) => r.type === 'Image').length, apiCalls: api.length, duplicateApi: dupes,
    lcp: Math.round(vitals.lcp), lcpElement: vitals.lcpElement, cls: +vitals.cls.toFixed(3), fcp: Math.round(vitals.fcp),
    ttfb: Math.round(nav.ttfb), domNodes: dom, horizontalOverflow: overflow, consoleErrors }
}

const median = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)]

const browser = await chromium.launch()
const results = {}
for (const [name, path] of ROUTES) {
  for (const profile of Object.keys(PROFILES)) {
    const samples = []
    for (let i = 0; i < runs; i++) samples.push(await measure(browser, profile, path))
    const last = samples[samples.length - 1]
    const r = { ...last }
    for (const k of ['lcp', 'fcp', 'ttfb', 'cls', 'kb', 'kbAfterScroll', 'requests']) r[k] = median(samples.map((s) => s[k]))
    results[`${name}:${profile}`] = r
    console.log(`${name.padEnd(9)} ${profile.padEnd(7)} req ${String(r.requests).padStart(3)}  ${String(r.kb).padStart(7)} KB` +
      ` (+scroll ${String(r.kbAfterScroll).padStart(7)} KB)  img ${String(r.images).padStart(3)}  api ${r.apiCalls}` +
      `${r.duplicateApi.length ? ' dup ' + r.duplicateApi.join(',') : ''}  LCP ${r.lcp} ms (${r.lcpElement})` +
      `  CLS ${r.cls}  FCP ${r.fcp}  DOM ${r.domNodes}  overflow ${r.horizontalOverflow}` +
      `${r.consoleErrors.length ? '  console errors ' + r.consoleErrors.length : ''}`)
  }
}
await browser.close()
if (out) writeFileSync(out, JSON.stringify(results, null, 2))

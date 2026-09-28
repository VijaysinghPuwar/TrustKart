// Local visual audit: screenshots, overflow and axe checks across widths and themes. Not part of the app build.
import { chromium } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

const OUT = process.env.OUT ?? 'visual-audit-output'
const BASE = process.env.BASE ?? 'http://localhost:5173'
const pages = (process.env.PAGES ?? '/').split(',')
const widths = (process.env.WIDTHS ?? '1440').split(',').map(Number)
const themes = (process.env.THEMES ?? 'light').split(',')
const full = process.env.FULL !== '0'

await import('node:fs').then((fs) => fs.mkdirSync(OUT, { recursive: true }))
const browser = await chromium.launch()
const summary = []
for (const theme of themes) {
  const context = await browser.newContext({ colorScheme: theme, deviceScaleFactor: 1 })
  for (const path of pages) {
    for (const width of widths) {
      const page = await context.newPage()
      await page.setViewportSize({ width, height: 900 })
      const errors = []
      page.on('console', (m) => m.type() === 'error' && errors.push(m.text().slice(0, 200)))
      page.on('pageerror', (e) => errors.push('pageerror: ' + e.message.slice(0, 200)))
      await page.goto(BASE + path, { waitUntil: 'networkidle' })
      await page.waitForTimeout(400)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      const name = (path.replace(/[^a-z0-9]+/gi, '_') || 'home') + '-' + width + '-' + theme
      await page.screenshot({ path: OUT + '/' + name + '.png', fullPage: full })
      let axe = []
      if (process.env.AXE !== '0') {
        const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
        axe = r.violations.map((v) => v.id + ' (' + v.impact + ') x' + v.nodes.length + ': ' + v.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' | '))
      }
      summary.push({ name, overflow, errors, axe })
      await page.close()
    }
  }
  await context.close()
}
await browser.close()
for (const s of summary) {
  const bad = s.overflow > 0 || s.errors.length || s.axe.length
  console.log((bad ? 'FAIL ' : 'ok   ') + s.name + (s.overflow > 0 ? ' overflow=' + s.overflow : ''))
  s.errors.forEach((e) => console.log('   console: ' + e))
  s.axe.forEach((a) => console.log('   axe: ' + a))
}

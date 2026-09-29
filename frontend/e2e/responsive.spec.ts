import { expect, test } from '@playwright/test'

// Layout guards across phone, tablet and desktop widths. They run once (in the desktop project) and set their own
// viewport, so each width is checked exactly one time.
test.skip(({ isMobile }) => isMobile, 'viewports are set explicitly')

const WIDTHS = [320, 390, 768, 1024, 1440, 1920]
const PAGES = ['/', '/search?q=laptop', '/c/laptops', '/rankings', '/cart']

for (const width of WIDTHS) {
  test(`no horizontal page scroll at ${String(width)}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    for (const path of PAGES) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      // The e2e project has no DOM types, so the check runs as an expression in the page.
      const overflow = await page.evaluate<number>('document.documentElement.scrollWidth - window.innerWidth')
      expect(overflow, `${path} overflows by ${String(overflow)}px`).toBeLessThanOrEqual(0)
    }
  })
}

test('the home banner keeps one height as it rotates', async ({ page }) => {
  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    const banner = page.locator('[aria-roledescription=carousel]')
    await expect(banner).toBeVisible()
    const heights = new Set<number>()
    const dots = page.getByRole('button', { name: /^Show deal/ })
    for (let i = 0; i < (await dots.count()); i++) {
      await dots.nth(i).click()
      heights.add(Math.round((await banner.boundingBox())?.height ?? 0))
    }
    expect([...heights], `banner heights at ${String(width)}px`).toHaveLength(1)
  }
})

test('the phone header fits on one row above the search box', async ({ page }) => {
  for (const width of [320, 360, 390]) {
    await page.setViewportSize({ width, height: 800 })
    await page.goto('/')
    const header = await page.locator('header').boundingBox()
    expect(header?.height ?? 0, `header height at ${String(width)}px`).toBeLessThan(180)
  }
})

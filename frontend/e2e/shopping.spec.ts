import { expect, test, type Page } from '@playwright/test'

async function openFirstResult(page: Page, query: string) {
  const search = page.getByRole('combobox', { name: "Describe what you're looking for" })
  await search.fill(query)
  await search.press('Enter')
  await expect(page.getByRole('heading', { level: 1 })).toContainText(query)
  await page.getByRole('main').getByRole('article').first().getByRole('link').first().click()
  await expect(page.getByRole('button', { name: 'Instant Virtual Buy' })).toBeVisible()
}

test('natural-language search is interpreted into chips', async ({ page }) => {
  await page.goto('/search?q=quiet%20mechanical%20keyboard%20for%20programming%20under%20%24100')
  const chips = page.getByText('Understood as').locator('..')
  await expect(chips).toContainText('Keyboards')
  await expect(chips).toContainText('Under $100')
  await expect(page.getByRole('main').getByRole('article').first()).toContainText('Keychron V3 Max')
})

test('search, add to cart, virtual checkout with an address, receipt and collection', async ({ page }) => {
  await page.goto('/')
  await openFirstResult(page, 'YubiKey')
  await page.getByRole('button', { name: /^Add to cart$/ }).first().click()
  await expect(page.getByRole('status').filter({ hasText: 'Added to cart' })).toBeVisible()

  await page.goto('/cart')
  await expect(page.getByRole('heading', { name: 'Cart' })).toBeVisible()
  await page.getByRole('link', { name: 'Proceed to Virtual Checkout' }).click()

  // Step 1: review
  await expect(page.getByRole('heading', { name: 'Review cart' })).toBeVisible()
  await page.getByRole('button', { name: 'Continue' }).click()

  // Step 2: a fictional address
  await expect(page.getByText('No physical product will be shipped.')).toBeVisible()
  await page.getByLabel('Full name').fill('Maya Chen')
  await page.getByLabel('Street address').fill('1 Test Lane')
  await page.getByLabel('City').fill('Springfield')
  await page.getByLabel('Postal code').fill('62701')
  await page.getByRole('button', { name: 'Use this address' }).click()
  await page.getByRole('button', { name: 'Continue' }).click()

  // Step 3: virtual payment, no card fields anywhere
  await expect(page.getByText('TrustKart Virtual Balance')).toBeVisible()
  await expect(page.getByText('No real payment information is required.')).toBeVisible()
  await expect(page.getByLabel(/card number|cvv|cvc/i)).toHaveCount(0)
  await page.getByRole('button', { name: 'Continue' }).click()

  // Step 4: review and place
  await expect(page.getByText('No real money will be charged')).toBeVisible()
  await page.getByRole('button', { name: 'Place Virtual Order' }).click()

  const dialog = page.getByRole('dialog', { name: 'Virtual purchase complete!' })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('This was a simulated purchase')
  await dialog.getByRole('link', { name: 'View my collection' }).click()
  await expect(page.getByRole('heading', { name: 'My collection' })).toBeVisible()
  await expect(page.getByRole('main')).toContainText('YubiKey')
})

test('wallet: add virtual funds', async ({ page }) => {
  await page.goto('/wallet')
  await page.getByRole('button', { name: 'Add virtual funds' }).click()
  const dialog = page.getByRole('dialog', { name: 'Add virtual funds' })
  await dialog.getByText('+$1,000,000').click()
  await dialog.getByRole('button', { name: 'Add to Wallet' }).click()
  await expect(page.getByRole('dialog', { name: '$1,000,000 added' })).toBeVisible()
  await expect(page.getByText('No real money was deposited.')).toBeVisible()
})

test('compare two CPUs and show differences only', async ({ page }) => {
  await page.goto('/compare?slugs=amd-ryzen-9-9950x&slugs=intel-core-ultra-9-285k')
  await expect(page.getByRole('table', { name: 'Specification comparison' })).toContainText('LGA1851')
  await page.getByLabel('Show differences only').check()
  await expect(page.getByRole('rowheader', { name: /Socket/ })).toBeVisible()
})

test('unknown pages show the 404 page', async ({ page }) => {
  await page.goto('/this/does/not/exist')
  await expect(page.getByRole('heading', { name: 'We couldn’t find that page' })).toBeVisible()
})

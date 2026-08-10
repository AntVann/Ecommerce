import { expect, Page, test } from '@playwright/test';

const productId =
  process.env.DEMO_PRODUCT_ID ?? '33333333-3333-3333-3333-333333333333';
const customerEmail =
  process.env.DEMO_CUSTOMER_EMAIL ?? 'demo.customer@example.test';
const customerPassword =
  process.env.DEMO_CUSTOMER_PASSWORD ?? 'MarketFlowDemo!123';

test.describe.configure({ mode: 'serial' });

async function clearCart(page: Page) {
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/cart/api/v1/cart') &&
        response.request().method() === 'GET' &&
        response.ok(),
    ),
    page.getByRole('link', { name: 'Cart' }).click(),
  ]);
  await expect(page.getByRole('heading', { name: 'Your cart', exact: true })).toBeVisible();
  while (await page.getByRole('button', { name: 'Remove' }).count()) {
    await page.getByRole('button', { name: 'Remove' }).first().click();
    await page.waitForTimeout(100);
  }
}

test('customer completes a seeded checkout with simulated payment', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(customerEmail);
  await page.getByLabel('Password').fill(customerPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL('/');
  await clearCart(page);

  await page.getByRole('link', { name: 'Browse' }).click();
  await expect(page.getByRole('link', { name: 'MarketFlow Demo Laptop' })).toBeVisible();
  await page.getByRole('link', { name: 'MarketFlow Demo Laptop' }).click();
  await expect(page).toHaveURL(new RegExp(`/products/${productId}$`));

  await expect(page.getByRole('button', { name: 'Add to cart' }).first()).toBeEnabled();
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/cart/api/v1/cart/items') &&
        response.request().method() === 'POST' &&
        response.ok(),
    ),
    page.getByRole('button', { name: 'Add to cart' }).first().click(),
  ]);
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/cart/api/v1/cart') &&
        response.request().method() === 'GET' &&
        response.ok(),
    ),
    page.getByRole('link', { name: 'Cart' }).click(),
  ]);
  await expect(page.getByRole('heading', { name: 'Your cart', exact: true })).toBeVisible();
  await expect(page.getByText('Variant 44444444')).toBeVisible();
  await page.getByRole('link', { name: 'Continue to checkout' }).click();
  await expect(page).toHaveURL('/checkout');
  await page.getByLabel('Recipient').fill('Demo Customer');
  await page.getByLabel('Address line 1').fill('1 MarketFlow Way');
  await page.getByLabel('City').fill('Portland');
  await page.getByLabel('Region').fill('OR');
  await page.getByLabel('Postal code').fill('97205');
  await page.getByLabel('Country code').fill('US');
  await page.getByRole('button', { name: 'Place order' }).click();

  await expect(page).toHaveURL(/\/account\/orders\/[^/]+$/);
  await expect(page.getByRole('heading', { name: /[0-9a-f-]{36}/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Simulated payment' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve payment' }).click();

  await expect(page.getByText('Payment: AUTHORIZED')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('CONFIRMED')).toBeVisible();
});

test('customer sees payment decline compensation without confirming the order', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(customerEmail);
  await page.getByLabel('Password').fill(customerPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL('/');
  await clearCart(page);

  await page.getByRole('link', { name: 'Browse' }).click();
  await page.getByRole('link', { name: 'MarketFlow Demo Laptop' }).click();
  await expect(page).toHaveURL(new RegExp(`/products/${productId}$`));
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/cart/api/v1/cart/items') &&
        response.request().method() === 'POST' &&
        response.ok(),
    ),
    page.getByRole('button', { name: 'Add to cart' }).first().click(),
  ]);
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/cart/api/v1/cart') &&
        response.request().method() === 'GET' &&
        response.ok(),
    ),
    page.getByRole('link', { name: 'Cart' }).click(),
  ]);
  await page.getByRole('link', { name: 'Continue to checkout' }).click();
  await page.getByLabel('Recipient').fill('Demo Customer');
  await page.getByLabel('Address line 1').fill('1 MarketFlow Way');
  await page.getByLabel('City').fill('Portland');
  await page.getByLabel('Region').fill('OR');
  await page.getByLabel('Postal code').fill('97205');
  await page.getByLabel('Country code').fill('US');
  await page.getByRole('button', { name: 'Place order' }).click();

  await expect(page).toHaveURL(/\/account\/orders\/[^/]+$/);
  await page.getByRole('button', { name: 'Decline' }).click();
  await expect(page.getByText('Payment: DECLINED')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('PAYMENT_FAILED')).toBeVisible();
});

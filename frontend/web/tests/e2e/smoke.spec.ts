import { test, expect } from '@playwright/test';
test('storefront is navigable', async ({ page }) => { await page.goto('/'); await expect(page.getByRole('link', { name: 'Explore products' })).toBeVisible(); await page.getByRole('link', { name: 'Browse' }).click(); await expect(page.getByRole('heading', { name: /find your next favorite/i })).toBeVisible(); });

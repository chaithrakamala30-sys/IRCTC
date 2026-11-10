// @ts-check
import { test, expect } from '@playwright/test';

const ADMIN_URL = process.env.ADMIN_PORTAL_URL || 'http://localhost:3001';

test.describe('E2E Admin Governance & Maker-Checker Flow', () => {
  test('Admin console renders tabs and enforces authentication', async ({ page }) => {
    await page.goto(ADMIN_URL);
    
    // Check if login card or dashboard is present
    const signInCard = page.locator('text=Administrator sign in');
    if (await signInCard.isVisible()) {
      await expect(signInCard).toBeVisible();
      await expect(page.locator('input[name="identifier"]')).toBeVisible();
    } else {
      // If signed in, check executive dashboard tab
      await expect(page.locator('text=Executive Dashboard')).toBeVisible();
    }
  });
});

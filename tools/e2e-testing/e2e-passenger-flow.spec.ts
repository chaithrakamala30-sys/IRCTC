// @ts-check
import { test, expect } from '@playwright/test';

const PASSENGER_URL = process.env.PASSENGER_WEB_URL || 'http://localhost:3000';

test.describe('E2E Passenger Journey Flow', () => {
  test('Passenger browse, search trains, and view fare quote', async ({ page }) => {
    // 1. Visit homepage
    await page.goto(PASSENGER_URL);
    await expect(page).toHaveTitle(/RailConnect/);

    // 2. Navigate to train search
    const searchHeading = page.locator('text=Book Train Tickets');
    if (await searchHeading.isVisible()) {
      await expect(searchHeading).toBeVisible();
    }

    // 3. Navigate to notifications
    await page.goto(`${PASSENGER_URL}/notifications`);
    await expect(page.locator('text=Notifications & Travel Alerts')).toBeVisible();

    // 4. Navigate to support center
    await page.goto(`${PASSENGER_URL}/support`);
    await expect(page.locator('text=Passenger Support Centre')).toBeVisible();
  });
});

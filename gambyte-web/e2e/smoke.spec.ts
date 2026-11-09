import { expect, test } from '@playwright/test';

/** Register → start a game against the engine → make a move → see the engine reply → resign. */
test('a new player can play the engine', async ({ page }) => {
  const name = `e2e${Date.now().toString(36)}`;
  await page.goto('/register');
  await page.getByLabel('Username').fill(name);
  await page.getByLabel('Email').fill(`${name}@example.test`);
  await page.getByLabel('Password').fill('e2e-password-1');
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByRole('link', { name })).toBeVisible();

  // Play as White against level 1.
  await page.getByRole('button', { name: 'White', exact: true }).click();
  await page.getByRole('button', { name: 'Play', exact: true }).click();
  await expect(page).toHaveURL(/\/game\//);

  // e2-e4 by clicking the two squares (react-chessboard squares carry data-square attributes).
  await page.locator('[data-square="e2"]').click();
  await page.locator('[data-square="e4"]').click();
  await expect(page.getByRole('button', { name: 'e4' })).toBeVisible();
  // The engine answers: a second move appears in the list.
  await expect(page.locator('[data-current]')).toHaveCount(2, { timeout: 20_000 });

  await page.getByRole('button', { name: 'Resign' }).click();
  await page.getByRole('button', { name: 'Confirm resign' }).click();
  await expect(page.getByText('White resigned')).toBeVisible();
});

import { test, expect } from './fixtures.js';

test('protected routes require login; login survives refresh and logout ends access', async ({ browser }) => {
  const context = await browser.newContext({ baseURL: 'http://frontend:8080' });
  await context.clearCookies();
  const page = await context.newPage();

  await page.goto('/printers');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'Printer Inventory' })).toBeVisible();

  await page.getByLabel('Username').fill(process.env.TEST_ADMIN_USERNAME);
  await page.getByLabel('Password').fill('invalid-password');
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.');

  await page.getByLabel('Password').fill(process.env.TEST_ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page).toHaveURL(/\/printers$/);
  await expect(page.getByText('E2E Administrator')).toBeVisible();

  await page.reload();
  await expect(page).toHaveURL(/\/printers$/);
  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await page.request.get('/api/printers')).status()).toBe(401);

  await context.close();
});

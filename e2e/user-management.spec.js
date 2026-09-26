import { test, expect } from './fixtures.js';

test('ADMIN manages users while USER cannot see or open User Management', async ({ page, browser }) => {
  const suffix = Date.now();
  const username = `user.${suffix}`;
  const password = 'E2eUserPassword12!';

  await page.goto('/users');
  await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();
  await page.getByRole('button', { name: '+ Add User' }).click();
  await page.getByLabel('Full Name *').fill('E2E Regular User');
  await page.getByLabel('Username *').fill(username);
  await page.getByLabel('Password *', { exact: true }).fill(password);
  await page.getByLabel('Confirm Password *').fill(password);
  await page.getByRole('button', { name: 'Save User' }).click();
  await expect(page.getByRole('status')).toHaveText('User created.');
  await expect(page.getByRole('cell', { name: username, exact: true })).toBeVisible();

  const context = await browser.newContext({ baseURL: 'http://frontend:8080' });
  await context.clearCookies();
  const userPage = await context.newPage();
  await userPage.goto('/login');
  await userPage.getByLabel('Username').fill(username);
  await userPage.getByLabel('Password').fill(password);
  await userPage.getByRole('button', { name: 'Login' }).click();
  await expect(userPage).toHaveURL(/\/printers$/);
  await expect(userPage.getByRole('link', { name: 'User Management' })).toHaveCount(0);
  await userPage.goto('/users');
  await expect(userPage).toHaveURL(/\/printers$/);
  expect((await userPage.request.get('/api/admin/users')).status()).toBe(403);
  await context.close();
});

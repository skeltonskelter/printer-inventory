import { test, expect } from './fixtures.js';

test('ADMIN reviews and filters audit entries while USER is denied', async ({ page, request, browser, playwright }) => {
  const suffix = Date.now();
  const username = `audit.user.${suffix}`;
  const password = 'E2eAuditPassword12!';
  const location = `Audit Location ${suffix}`;

  expect((await request.post('/api/admin/users', { data: {
    fullName: 'Audit Trail User', username, password, confirmPassword: password, role: 'USER', enabled: true,
  } })).ok()).toBeTruthy();
  expect((await request.post('/api/locations', { data: { department: location } })).ok()).toBeTruthy();

  await page.goto('/audit-trail');
  await expect(page.getByRole('heading', { name: 'Audit Trail' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Audit Trail' })).toBeVisible();
  await page.getByLabel('Action').selectOption('CREATE');
  await page.getByLabel('Entity Type').selectOption('LOCATION');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText(location, { exact: true }).first()).toBeVisible();
  await page.getByText('View details').first().click();
  await expect(page.getByText('New Values').first()).toBeVisible();
  await page.getByLabel('Rows per page').selectOption('50');
  await expect(page).toHaveURL(/size=50/);

  await page.setViewportSize({ width: 375, height: 800 });
  await expect(page.getByRole('heading', { name: 'Audit Trail' })).toBeVisible();
  await expect(page.locator('.audit-card').first()).toBeVisible();

  const userApi = await playwright.request.newContext({ baseURL: 'http://frontend:8080' });
  const csrf = await (await userApi.get('/api/auth/csrf')).json();
  const login = await userApi.post('/api/auth/login', { form: { username, password }, headers: { [csrf.headerName]: csrf.token } });
  expect(login.status()).toBe(204);
  const context = await browser.newContext({ baseURL: 'http://frontend:8080', storageState: await userApi.storageState() });
  await userApi.dispose();
  const userPage = await context.newPage();
  await userPage.goto('/printers');
  await expect(userPage.getByRole('link', { name: 'Audit Trail' })).toHaveCount(0);
  await userPage.goto('/audit-trail');
  await expect(userPage).toHaveURL(/\/printers$/);
  expect((await userPage.request.get('/api/admin/audit-logs')).status()).toBe(403);
  await context.close();
});

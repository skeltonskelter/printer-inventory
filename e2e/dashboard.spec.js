import { test, expect } from '@playwright/test';

test('empty dashboard is the home page and failures have a retry', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your inventory starts here' })).toBeVisible();
  for (const value of await page.locator('.metric-value').allTextContents()) expect(value).toBe('0');
  await page.route('**/api/dashboard', route => route.abort());
  await page.getByRole('button', { name: 'Refresh dashboard' }).click();
  await expect(page.getByRole('alert')).toContainText('could not be reached');
  await expect(page.locator('.metric-value')).toHaveCount(0);
  await page.unroute('**/api/dashboard');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.locator('.metric-value')).toHaveCount(6);
  await page.getByRole('link', { name: 'Add your first printer' }).click();
  await expect(page).toHaveURL(/\/printers\/new$/);
});

test('dashboard reflects transfers, status changes, deletion, and works on desktop tablet and phone', async ({ page, request }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const a = await (await request.post('/api/locations', { data: { department: 'Information Technology', building: 'Main Building', room: '201' } })).json();
  const b = await (await request.post('/api/locations', { data: { department: 'Accounting Department', building: 'Annex', room: '102' } })).json();
  const printers = [];
  for (const [index, status] of ['ACTIVE', 'UNDER_REPAIR', 'STORAGE', 'RETIRED', 'FOR_REPAIR', 'DISPOSED'].entries()) {
    const response = await request.post('/api/printers', { data: { brand: 'Epson', model: 'L5290', stickerNumber: `DASH-${index}`, locationId: a.id, status } });
    expect(response.status()).toBe(201);
    printers.push(await response.json());
  }
  let printer = printers[0];
  for (const destination of [b.id, a.id]) {
    const response = await request.post(`/api/printers/${printer.id}/relocate`, { data: { newLocationId: destination, relocationDate: new Date().toISOString().slice(0, 10), version: printer.version } });
    expect(response.status()).toBe(200);
    printer = await response.json();
  }
  await page.goto('/dashboard');
  const metric = label => page.locator('.metric-card').filter({ has: page.getByRole('heading', { name: label, exact: false }) }).locator('.metric-value');
  await expect(metric('Total printers')).toHaveText('6');
  await expect(metric('Relocated printers')).toHaveText('1');
  await expect(page.getByRole('region', { name: 'Recent transfers' }).locator('li')).toHaveCount(2);
  await expect(page.getByRole('region', { name: 'Recently added' }).locator('li')).toHaveCount(5);
  for (const width of [1440, 1024, 768, 375, 320]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Dashboard' })).toBeVisible();
    await page.screenshot({ path: `/reports/dashboard-${width}.png`, fullPage: true });
  }
  await page.getByRole('link', { name: 'Under repair', exact: false }).click();
  await expect(page).toHaveURL(/status=UNDER_REPAIR/);
  await expect(page.getByLabel('Status', { exact: true })).toHaveValue('UNDER_REPAIR');
  await page.getByRole('navigation').getByRole('link', { name: 'Dashboard' }).click();
  const response = await request.put(`/api/printers/${printer.id}`, { data: { ...printer, locationId: printer.location.id, status: 'STORAGE' } });
  expect(response.status()).toBe(200);
  await page.getByRole('button', { name: 'Refresh dashboard' }).click();
  await expect(metric('Active printers')).toHaveText('0');
  await expect(metric('In storage')).toHaveText('2');
  for (const item of printers) expect((await request.delete(`/api/printers/${item.id}`)).status()).toBe(204);
  await page.getByRole('button', { name: 'Refresh dashboard' }).click();
  await expect(metric('Total printers')).toHaveText('0');
  await expect(metric('Relocated printers')).toHaveText('0');
  await expect(page.getByRole('region', { name: 'Recent transfers' }).locator('li')).toHaveCount(0);
  expect(errors).toEqual([]);
});

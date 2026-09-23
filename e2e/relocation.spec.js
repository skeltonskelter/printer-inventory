import { test, expect } from '@playwright/test';

async function setup(request) {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 6)}`;
  const source = await (await request.post('/api/locations', { data: { department: `ICT ${suffix}`, building: 'Main', room: '101' } })).json();
  const destination = await (await request.post('/api/locations', { data: { department: `Accounting ${suffix}`, building: 'Annex', room: '202' } })).json();
  const response = await request.post('/api/printers', { data: { brand: 'Epson', model: 'L5290', serialNumber: `SN-MOVE-${suffix}`, stickerNumber: `MOVE-${suffix}`, locationId: source.id, status: 'ACTIVE' } });
  expect(response.status()).toBe(201);
  return { source, destination, printer: await response.json() };
}

test('relocate, return transfer, ordered history, and retained history after deletion', async ({ page, request }) => {
  const { source, destination, printer } = await setup(request);
  await page.goto(`/printers/${printer.id}`);
  await expect(page.getByText('No relocations recorded.', { exact: false })).toBeVisible();
  await page.getByRole('link', { name: 'Relocate', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Relocate printer', exact: true })).toBeVisible();
  await expect(page.locator('#new-location option', { hasText: source.department })).toHaveCount(0);
  await page.getByLabel('New location *').selectOption(String(destination.id));
  await page.getByLabel('Remarks', { exact: true }).fill('Transferred upon department request');
  await page.screenshot({ path: '/reports/relocate-desktop.png', fullPage: true });
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Printer relocated.');
  await expect(page.locator('.location-details')).toContainText(destination.department);
  const history = page.locator('.relocation-history');
  await expect(history.locator('li')).toHaveCount(1);
  await expect(history).toContainText(source.department);
  await expect(history).toContainText(destination.department);
  await expect(history).toContainText('Transferred upon department request');
  await page.reload();
  await expect(history.locator('li')).toHaveCount(1);
  await page.getByRole('link', { name: 'Relocate', exact: true }).click();
  await page.getByLabel('New location *').selectOption(String(source.id));
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(history.locator('li')).toHaveCount(2);
  await expect(history.locator('li').first().locator('.relocation-route > div').first()).toContainText(destination.department);
  await expect(history.locator('li').first().locator('.relocation-route > div').last()).toContainText(source.department);
  await page.screenshot({ path: '/reports/history-desktop.png', fullPage: true });
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.getByRole('button', { name: 'Delete printer', exact: true }).click();
  await expect(page).toHaveURL(/\/printers$/);
  const records = await request.get(`/api/printers/${printer.id}/relocations`);
  expect(records.status()).toBe(200);
  expect((await records.json()).length).toBe(2);
});

test('stale relocation cannot create a second transfer; input stays intact', async ({ page, request }) => {
  const { printer, destination } = await setup(request);
  await page.goto(`/printers/${printer.id}/relocate`);
  await page.getByLabel('New location *').selectOption(String(destination.id));
  await page.getByLabel('Remarks', { exact: true }).fill('Do not lose these remarks');
  const date = await page.getByLabel('Relocation date *').inputValue();
  const moved = await request.post(`/api/printers/${printer.id}/relocate`, { data: { newLocationId: destination.id, relocationDate: date, version: printer.version } });
  expect(moved.status()).toBe(200);
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('record has changed');
  await expect(page.getByLabel('Remarks', { exact: true })).toHaveValue('Do not lose these remarks');
  expect((await (await request.get(`/api/printers/${printer.id}/relocations`)).json()).length).toBe(1);
});

test('phone relocation supports new locations, validation, cancel, and readable history', async ({ page, request }) => {
  const { printer } = await setup(request);
  await page.setViewportSize({ width: 375, height: 900 });
  await page.goto(`/printers?search=${printer.stickerNumber}`);
  await page.getByRole('link', { name: `Relocate ${printer.stickerNumber}`, exact: true }).click();
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(page.locator('#new-location:invalid')).toBeVisible();
  await page.getByLabel('Remarks', { exact: true }).fill('Moved from the mobile inventory screen');
  await page.getByRole('button', { name: '+ New location', exact: true }).click();
  await page.getByLabel('Department *').fill('Mobile test department');
  await page.getByRole('button', { name: 'Save location', exact: true }).click();
  await expect(page.getByLabel('New location *')).not.toHaveValue('');
  await expect(page.getByLabel('Remarks', { exact: true })).toHaveValue('Moved from the mobile inventory screen');
  await page.getByLabel('Relocation date *').fill('2999-01-01');
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('cannot be in the future');
  expect((await (await request.get(`/api/printers/${printer.id}/relocations`)).json()).length).toBe(0);
  const date = new Date().toISOString().slice(0, 10);
  await page.getByLabel('Relocation date *').fill(date);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '/reports/relocate-phone.png', fullPage: true });
  await page.getByRole('button', { name: 'Relocate printer', exact: true }).click();
  await expect(page.locator('.relocation-history li')).toHaveCount(1);
  await expect(page.locator('.location-details')).toContainText('Mobile test department');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '/reports/history-phone.png', fullPage: true });
  await page.getByRole('link', { name: 'Relocate', exact: true }).click();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/printers/${printer.id}$`));
  expect((await (await request.get(`/api/printers/${printer.id}/relocations`)).json()).length).toBe(1);
});

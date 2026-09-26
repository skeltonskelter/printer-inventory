import { test, expect } from './fixtures.js';

const unique = () => `TEST-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`.toUpperCase();
async function seed(request, overrides = {}) {
  const suffix = unique();
  const locationResponse = await request.post('/api/locations', { data: { department: `ICT ${suffix}`, building: 'Main Building', floor: '2', room: '201' } });
  expect(locationResponse.status()).toBe(201);
  const location = await locationResponse.json();
  const data = { brand: 'Epson', model: 'L5290', stickerNumber: suffix, serialNumber: `SN-${suffix}`, locationId: location.id, status: 'ACTIVE', ...overrides };
  const response = await request.post('/api/printers', { data });
  expect(response.status()).toBe(201);
  return { printer: await response.json(), data, location };
}

test('empty installation: create location, add, view, edit, search, cancel and confirm deletion', async ({ page, request }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/printers');
  await expect(page.getByRole('heading', { name: 'No printers found' })).toBeVisible();
  await page.getByRole('link', { name: '+ Add printer', exact: true }).click();
  await page.getByRole('button', { name: 'Add printer', exact: true }).click();
  await expect(page.locator('#brand:invalid')).toBeVisible();
  const sticker = unique();
  await page.getByLabel('Brand *', { exact: true }).fill('Epson');
  await page.getByLabel('Model *', { exact: true }).fill('L5290');
  await page.getByLabel('Sticker number', { exact: true }).fill(sticker);
  await page.getByLabel('Serial number *', { exact: true }).fill(`SN-${sticker}`);
  await page.getByRole('button', { name: '+ New location' }).click();
  await page.getByLabel('Department *').fill('ICT Department');
  await page.getByLabel('Section', { exact: true }).fill('Operations');
  await page.getByLabel('Building', { exact: true }).fill('Main');
  await page.getByLabel('Room', { exact: true }).fill('101');
  await page.getByRole('button', { name: 'Save location' }).click();
  await expect(page.getByRole('status')).toContainText('added and selected');
  await page.getByRole('button', { name: 'Edit selected location', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Edit location', exact: true })).toBeVisible();
  await page.getByLabel('Section', { exact: true }).fill('Operations Updated');
  await page.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('updated');
  await expect(page.getByLabel('Brand *', { exact: true })).toHaveValue('Epson');
  await page.getByRole('button', { name: 'Add printer', exact: true }).click();
  await expect(page.getByRole('heading', { name: sticker, exact: true })).toBeVisible();
  const id = page.url().split('/').at(-1);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Current location' })).toBeVisible();
  await page.getByRole('link', { name: 'Edit printer', exact: true }).click();
  await expect(page.getByLabel('Location *', { exact: true })).toBeDisabled();
  await page.getByLabel('Model *', { exact: true }).fill('L6270');
  await page.getByLabel('Status *', { exact: true }).selectOption('UNDER_REPAIR');
  await page.getByLabel('Remarks', { exact: true }).fill('Paper feed issue');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Printer updated.', { exact: true })).toBeVisible();
  await expect(page.getByText('Under Repair', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'All printers' }).click();
  await page.getByLabel('Search printers', { exact: true }).fill(`SN-${sticker}`);
  await page.getByLabel('Brand', { exact: true }).fill('epson');
  await page.getByLabel('Status', { exact: true }).selectOption('UNDER_REPAIR');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByRole('heading', { name: '1 printer', exact: true })).toBeVisible();
  await page.getByRole('button', { name: `Delete ${sticker}`, exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText(sticker);
  await expect(page.getByRole('button', { name: 'Cancel', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect((await request.get(`/api/printers/${id}`)).status()).toBe(200);
  await page.getByRole('button', { name: `Delete ${sticker}`, exact: true }).click();
  await page.getByRole('button', { name: 'Delete printer', exact: true }).click();
  await expect(page.getByText(`Printer ${sticker} was deleted.`, { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'No printers found' })).toBeVisible();
  expect((await request.get(`/api/printers/${id}`)).status()).toBe(404);
  expect(errors).toEqual([]);
});

test('duplicate identifiers and stale edits preserve input and explain conflicts', async ({ page, request }) => {
  const { printer, data } = await seed(request);
  await page.goto('/printers/new');
  await page.getByLabel('Brand *', { exact: true }).fill('Brother');
  await page.getByLabel('Model *', { exact: true }).fill('DCP-T720DW');
  await page.getByLabel('Sticker number', { exact: true }).fill(printer.stickerNumber);
  await page.getByLabel('Serial number *', { exact: true }).fill(`SN-DUP-${Date.now()}`);
  await page.getByLabel('Location *', { exact: true }).selectOption(String(data.locationId));
  await page.getByRole('button', { name: 'Add printer', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Sticker number is already in use');
  await expect(page.getByLabel('Model *', { exact: true })).toHaveValue('DCP-T720DW');
  await page.goto(`/printers/${printer.id}/edit`);
  await expect(page.getByLabel('Brand *', { exact: true })).toHaveValue('Epson');
  expect((await request.put(`/api/printers/${printer.id}`, { data: { ...data, model: 'Newer edit', version: printer.version } })).status()).toBe(200);
  await page.getByLabel('Model *', { exact: true }).fill('Old form edit');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('alert')).toContainText('record has changed');
  await expect(page.getByLabel('Model *', { exact: true })).toHaveValue('Old form edit');
  expect((await (await request.get(`/api/printers/${printer.id}`)).json()).model).toBe('Newer edit');
});

test('filters survive refresh and history; pagination and delete-last-row recover', async ({ page, request }) => {
  const { printer, location, data } = await seed(request);
  for (let index = 2; index <= 26; index++) {
    const extra = await request.post('/api/printers', { data: { ...data, stickerNumber: unique(), serialNumber: `${data.serialNumber}-${index}`, status: 'STORAGE' } });
    expect(extra.status()).toBe(201);
  }
  await page.goto(`/printers?locationId=${location.id}&size=25`);
  await expect(page.getByLabel('Location', { exact: true })).toHaveValue(String(location.id));
  await expect(page.getByText('Page 1 of 2', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Page 2 of 2', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: `Delete ${printer.stickerNumber}`, exact: true }).click();
  await page.getByRole('button', { name: 'Delete printer', exact: true }).click();
  await expect(page.getByRole('heading', { name: '25 printers', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/page=0/);
  await page.getByLabel('Status', { exact: true }).selectOption('ACTIVE');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByRole('heading', { name: 'No printers found' })).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('heading', { name: '25 printers', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Location', { exact: true })).toHaveValue(String(location.id));
});

test('request failures, missing records and unknown routes have recovery paths', async ({ page }) => {
  await page.route('**/api/printers?*', route => route.abort());
  await page.goto('/printers?search=anything');
  await expect(page.getByRole('alert')).toContainText('server could not be reached');
  await page.unroute('**/api/printers?*');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('heading', { name: 'No printers found' })).toBeVisible();
  await page.goto('/printers/9223372036854775807');
  await expect(page.getByRole('alert')).toContainText('Printer not found');
  await page.getByRole('link', { name: 'All printers' }).click();
  await expect(page.getByRole('heading', { name: 'Printers', exact: true })).toBeVisible();
  await page.goto('/not-a-page');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});

test('desktop, tablet and phone layouts fit; mobile actions and forms work', async ({ page, request }) => {
  const { printer } = await seed(request, { model: 'LaserJet Enterprise M507dn', brand: 'HP', remarks: 'Near the service counter.\nCheck paper tray weekly.' });
  for (const width of [1440, 768, 375, 320]) {
    await page.setViewportSize({ width, height: 950 });
    await page.goto(`/printers?search=${printer.stickerNumber}`);
    await expect(page.getByRole('heading', { name: '1 printer', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width >= 1200) await expect(page.getByRole('table')).toBeVisible();
    else await expect(page.getByRole('table')).not.toBeVisible();
    await page.screenshot({ path: `/reports/list-${width}.png`, fullPage: true });
  }
  await page.getByRole('link', { name: `View ${printer.stickerNumber}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Current location' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '/reports/details-phone.png', fullPage: true });
  await page.getByRole('link', { name: 'Edit printer', exact: true }).click();
  await page.getByLabel('Remarks', { exact: true }).fill('Updated on a phone');
  await page.screenshot({ path: '/reports/form-phone.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Updated on a phone', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.screenshot({ path: '/reports/delete-phone.png', fullPage: true });
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
});

import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  request: async ({ playwright, baseURL }, use) => {
    const context = await playwright.request.newContext({
      baseURL,
      storageState: '/tmp/auth-state.json',
    });
    const csrfResponse = await context.get('/api/auth/csrf');
    if (!csrfResponse.ok()) throw new Error(`Could not obtain CSRF token: ${csrfResponse.status()}`);
    const csrf = await csrfResponse.json();
    const secured = new Proxy(context, {
      get(target, property) {
        const value = target[property];
        if (['post', 'put', 'patch', 'delete'].includes(String(property))) {
          return (url, options = {}) => value.call(target, url, {
            ...options,
            headers: { ...options.headers, [csrf.headerName]: csrf.token },
          });
        }
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
    await use(secured);
    await context.dispose();
  },
});

export { expect };

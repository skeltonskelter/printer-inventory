import { request } from '@playwright/test';

export default async function globalSetup(config) {
  const baseURL = config.projects[0].use.baseURL;
  const api = await request.newContext({ baseURL });
  const csrfResponse = await api.get('/api/auth/csrf');
  if (!csrfResponse.ok()) throw new Error(`Could not obtain CSRF token: ${csrfResponse.status()}`);
  const csrf = await csrfResponse.json();
  const login = await api.post('/api/auth/login', {
    form: {
      username: process.env.TEST_ADMIN_USERNAME,
      password: process.env.TEST_ADMIN_PASSWORD,
    },
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!login.ok()) throw new Error(`E2E administrator login failed: ${login.status()}`);
  await api.storageState({ path: '/tmp/auth-state.json' });
  await api.dispose();
}

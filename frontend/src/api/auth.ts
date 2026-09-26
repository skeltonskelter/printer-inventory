import { api, resetCsrfToken } from './client';
import type { CurrentUser } from '../types/auth';

export const authentication = {
  current: async () => (await api.get<CurrentUser>('/auth/me')).data,
  login: async (username: string, password: string) => {
    const form = new URLSearchParams({ username, password });
    await api.post('/auth/login', form, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    resetCsrfToken();
    return (await api.get<CurrentUser>('/auth/me')).data;
  },
  logout: async () => {
    await api.post('/auth/logout');
    resetCsrfToken();
  },
};

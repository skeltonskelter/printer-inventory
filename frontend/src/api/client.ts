import axios from 'axios';

export const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

const csrfApi = axios.create({ baseURL: '/api', timeout: 10000, withCredentials: true });
let csrfRequest: Promise<{ headerName: string; token: string }> | null = null;

export function resetCsrfToken() { csrfRequest = null; }

async function csrfToken() {
  if (!csrfRequest) {
    csrfRequest = csrfApi.get<{ headerName: string; token: string }>('/auth/csrf')
      .then((response) => response.data)
      .catch((error) => {
        csrfRequest = null;
        throw error;
      });
  }
  return csrfRequest;
}

api.interceptors.request.use(async (config) => {
  const method = config.method?.toUpperCase() ?? 'GET';
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await csrfToken();
    config.headers.set(csrf.headerName, csrf.token);
  }
  return config;
});

api.interceptors.response.use(undefined, (error) => {
  const url = String(error.config?.url ?? '');
  if (error.response?.status === 401 && !url.startsWith('/auth/')) {
    window.dispatchEvent(new Event('authentication-expired'));
  }
  return Promise.reject(error);
});

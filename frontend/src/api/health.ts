import { api } from './client';
import type { HealthResponse } from '../types/health';

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/health', {
    signal,
    validateStatus: (status) => status === 200 || status === 503,
  });
  // A proxy error page must never be mistaken for a successful API response.
  if (!data || !['UP', 'DOWN'].includes(data.status)
      || !['UP', 'DOWN'].includes(data.api) || !['UP', 'DOWN'].includes(data.database)
      || typeof data.message !== 'string' || typeof data.checkedAt !== 'string'
      || Number.isNaN(Date.parse(data.checkedAt))) {
    throw new Error('Unexpected health response');
  }
  return data;
}

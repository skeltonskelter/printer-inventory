import { api } from './client';
import type { AuditPage } from '../types/audit';

export const auditApi = {
  list: async (params: URLSearchParams, signal?: AbortSignal) =>
    (await api.get<AuditPage>('/admin/audit-logs', { params, signal })).data,
};

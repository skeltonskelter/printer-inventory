import { api } from './client';
import type { CreateUserInput, ManagedUser, UserProfileInput } from '../types/auth';

export const usersApi = {
  list: async (signal?: AbortSignal) =>
    (await api.get<ManagedUser[]>('/admin/users', { signal })).data,
  create: async (input: CreateUserInput) =>
    (await api.post<ManagedUser>('/admin/users', input)).data,
  update: async (id: number, input: UserProfileInput) =>
    (await api.put<ManagedUser>(`/admin/users/${id}`, input)).data,
  setStatus: async (id: number, enabled: boolean) =>
    (await api.put<ManagedUser>(`/admin/users/${id}/status`, { enabled })).data,
  resetPassword: async (id: number, password: string, confirmPassword: string) => {
    await api.put(`/admin/users/${id}/password`, { password, confirmPassword });
  },
};

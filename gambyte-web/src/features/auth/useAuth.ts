/**
 * Who is logged in. Backed by TanStack Query, so every component that calls {@link useMe} shares
 * one cached request to /api/auth/me, and login/logout just refresh that cache.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api';
import type { Me } from '@/lib/types';
import { reconnect } from '@/lib/ws';

export const meKey = ['me'] as const;

/** The logged-in account, or null for visitors. */
export function useMe() {
  return useQuery({
    queryKey: meKey,
    queryFn: async () => {
      try {
        return await api.get<Me>('/api/auth/me');
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 60_000,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { login: string; password: string }) => api.post<Me>('/api/auth/login', body),
    onSuccess: (me) => {
      qc.setQueryData(meKey, me);
      reconnect();
    },
  });
}

export function useRegister() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { username: string; email: string; password: string }) =>
      api.post<Me>('/api/auth/register', body),
    onSuccess: (me) => {
      qc.setQueryData(meKey, me);
      reconnect();
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/api/auth/logout'),
    onSuccess: () => {
      qc.setQueryData(meKey, null);
      qc.invalidateQueries();
      reconnect();
    },
  });
}

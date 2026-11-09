/**
 * The one place that talks HTTP to the server.
 *
 * - Cookies: the session cookie is sent automatically (same origin).
 * - CSRF: the server puts a token in the readable XSRF-TOKEN cookie; we copy it into the
 *   X-XSRF-TOKEN header on every request that changes something (see SecurityConfig.java).
 * - Errors: non-2xx responses throw an {@link ApiError} carrying the server's `code`, so the UI
 *   can show a precise message ("username_taken") without parsing text.
 */
import type { ApiProblem } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(problem: ApiProblem) {
    super(problem.detail ?? `Request failed (${problem.status})`);
    this.status = problem.status;
    this.code = problem.code;
  }
}

function csrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

/** Makes sure the CSRF cookie exists before the first POST (e.g. on the login page). */
let csrfReady: Promise<void> | null = null;
function ensureCsrf(): Promise<void> {
  if (csrfToken()) return Promise.resolve();
  csrfReady ??= fetch('/api/auth/csrf', { credentials: 'same-origin' }).then(() => undefined);
  return csrfReady;
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    await ensureCsrf();
    const token = csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    let problem: ApiProblem = { status: res.status };
    try {
      problem = { ...(await res.json()), status: res.status };
    } catch {
      // not JSON (e.g. a proxy error page)
    }
    throw new ApiError(problem);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('Content-Type') ?? '';
  return (type.includes('json') ? res.json() : res.text()) as Promise<T>;
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body ?? {}),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body ?? {}),
  del: <T>(url: string) => request<T>('DELETE', url),
};

/** A friendly message for any error thrown by {@link api}. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 429) return 'Too many attempts - wait a minute and try again.';
    return e.message;
  }
  return 'Network problem - check your connection.';
}

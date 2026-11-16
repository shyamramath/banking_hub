'use client';

import type { ApiErrorBody, BatchItem, FieldIssue, Session } from './types';

/**
 * The browser's only API client. It calls same-origin /bff/* routes; the session cookie is
 * HttpOnly and sent automatically. No tokens are ever handled here.
 */

let csrfToken: string | null = null;
export const setCsrfToken = (token: string | null | undefined) => {
  csrfToken = token ?? null;
};

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: FieldIssue[] = [],
    public correlationId?: string,
  ) {
    super(message);
  }
}

const ACTIVITY_EVENT = 'nb:activity';
export const onActivity = (fn: () => void) => {
  window.addEventListener(ACTIVITY_EVENT, fn);
  return () => window.removeEventListener(ACTIVITY_EVENT, fn);
};

export interface RawResult {
  status: number;
  body: unknown;
  correlationId: string | null;
}

/** Low-level POST. Exposed for the Security Center so it can show raw responses. */
export async function rawPost(path: string, body: unknown, opts: { csrf?: boolean } = {}): Promise<RawResult> {
  const headers: Record<string, string> = { 'content-type': 'application/json' };
  if (opts.csrf !== false && csrfToken) headers['x-csrf-token'] = csrfToken;
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    headers,
    body: JSON.stringify(body),
  });
  window.dispatchEvent(new Event(ACTIVITY_EVENT));
  const json = await res.json().catch(() => ({}));
  return { status: res.status, body: json, correlationId: res.headers.get('x-correlation-id') };
}

function toError(status: number, body: { error?: ApiErrorBody; correlationId?: string } | undefined): ApiError {
  const e = body?.error;
  return new ApiError(status, e?.code ?? 'ERROR', e?.message ?? 'Request failed', e?.details ?? [], body?.correlationId);
}

function redirectToLogin() {
  const returnTo = window.location.pathname + window.location.search;
  window.location.href = `/auth/login?returnTo=${encodeURIComponent(returnTo)}`;
}

/** Run one operation through the single endpoint. */
export async function execute<T>(operation: string, payload?: Record<string, unknown>): Promise<T> {
  const { status, body } = await rawPost('/bff/execute', { operation, payload });
  const b = body as { success?: boolean; data?: T; error?: ApiErrorBody; correlationId?: string };
  if (status === 401) {
    redirectToLogin();
    throw toError(status, b);
  }
  if (status >= 400 || !b.success) throw toError(status, b);
  return b.data as T;
}

/** Several read operations in one round trip. Each item succeeds or fails on its own. */
export async function batch(requests: { operation: string; payload?: Record<string, unknown> }[]): Promise<BatchItem[]> {
  const { status, body } = await rawPost('/bff/execute/batch', { requests });
  if (status === 401) {
    redirectToLogin();
    throw toError(status, body as never);
  }
  if (status >= 400 || !Array.isArray(body)) throw toError(status, body as never);
  return body as BatchItem[];
}

export async function getSession(): Promise<Session> {
  const res = await fetch('/bff/session', { credentials: 'same-origin', cache: 'no-store' });
  if (!res.ok) return { authenticated: false };
  const s = (await res.json()) as Session;
  setCsrfToken(s.csrfToken);
  return s;
}

export async function logout() {
  const { body } = await rawPost('/auth/logout', {});
  setCsrfToken(null);
  const url = (body as { logoutUrl?: string }).logoutUrl;
  window.location.href = url ?? '/?signedOut=1';
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

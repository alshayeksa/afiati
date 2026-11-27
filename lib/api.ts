// Client for the Afiati Cloudflare Worker (worker/).
import { syncStateRepository } from '@/lib/repositories/syncStateRepository';

const apiUrl = (process.env.EXPO_PUBLIC_API_URL || '').replace(/\/+$/, '');

export const isApiConfigured = Boolean(apiUrl);

const REQUEST_TIMEOUT_MS = 20000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message?: string,
  ) {
    super(message ?? code);
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT';
  body?: unknown;
  /** false for the one call that creates the device token. */
  auth?: boolean;
};

export async function apiRequest<T>(path: string, { method = 'GET', body, auth = true }: RequestOptions = {}): Promise<T> {
  if (!isApiConfigured) throw new ApiError(0, 'not_configured', 'خادم المزامنة غير مُعد.');

  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = await syncStateRepository.get('device_token');
    if (!token) throw new ApiError(401, 'unauthorized', 'الجهاز غير مسجل في خادم المزامنة.');
    headers.Authorization = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(apiUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch {
    throw new ApiError(0, 'network_error', 'تعذر الوصول إلى خادم المزامنة.');
  } finally {
    clearTimeout(timeout);
  }

  const data = (await response.json().catch(() => null)) as (T & { error?: string; message?: string }) | null;
  if (!response.ok) {
    throw new ApiError(response.status, data?.error ?? 'http_error', data?.message ?? `HTTP ${response.status}`);
  }
  return data as T;
}

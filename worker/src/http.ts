export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message?: string,
  ) {
    super(message ?? code);
  }
}

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
  });
}

export function errorResponse(error: unknown) {
  if (error instanceof HttpError) {
    return json({ error: error.code, message: error.message }, error.status);
  }
  console.error(error);
  return json({ error: 'internal_error' }, 500);
}

export async function readJson<T = Record<string, unknown>>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    throw new HttpError(400, 'invalid_json');
  }
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function requireUuid(value: unknown, field: string): string {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    throw new HttpError(400, 'invalid_field', `${field} must be a UUID`);
  }
  return value.toLowerCase();
}

export function requireText(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== 'string' || !value.trim() || value.length > maxLength) {
    throw new HttpError(400, 'invalid_field', `${field} is required (max ${maxLength})`);
  }
  return value.trim();
}

export function optionalText(value: unknown, field: string, maxLength: number): string | null {
  if (value === undefined || value === null || value === '') return null;
  return requireText(value, field, maxLength);
}

export const nowIso = () => new Date().toISOString();

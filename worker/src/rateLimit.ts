import { HttpError } from './http';

/** Counts one hit for `key`; throws 429 once more than `limit` hits land in the current window. */
export async function enforceRateLimit(env: Env, key: string, limit: number, windowSeconds: number, message: string) {
  const now = Math.floor(Date.now() / 1000);
  const expired = now - windowSeconds;
  // In the UPDATE, column references are the old row values, so both CASEs see the old window.
  const row = await env.DB.prepare(
    `INSERT INTO rate_limits (key, window_start, count) VALUES (?, ?, 1)
     ON CONFLICT(key) DO UPDATE SET
       count = CASE WHEN rate_limits.window_start <= ? THEN 1 ELSE rate_limits.count + 1 END,
       window_start = CASE WHEN rate_limits.window_start <= ? THEN ? ELSE rate_limits.window_start END
     RETURNING count`,
  )
    .bind(key, now, expired, expired, now)
    .first<{ count: number }>();
  if (row && row.count > limit) throw new HttpError(429, 'rate_limited', message);
}

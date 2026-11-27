// Device-based identity, replacing Supabase anonymous auth.
//
// On first registration the Worker creates the profile (whose id the phone generated)
// and a device, then returns a token "<deviceId>.<secret>" exactly once. Only the
// SHA-256 of the secret is stored. Every later request sends:
//   Authorization: Bearer <deviceId>.<secret>
import { HttpError, nowIso, optionalText, readJson, requireText, requireUuid } from './http';

export type AuthContext = { deviceId: string; profileId: string };

const PLATFORMS = new Set(['android', 'ios', 'web']);

export async function register(request: Request, env: Env) {
  const body = await readJson(request);
  const profileId = requireUuid(body.profile_id, 'profile_id');
  const fullName = requireText(body.full_name, 'full_name', 100);
  const phone = requireText(body.phone, 'phone', 20);
  const email = optionalText(body.email, 'email', 200);
  const platform = typeof body.platform === 'string' && PLATFORMS.has(body.platform) ? body.platform : null;

  // A profile id that already exists cannot be claimed by registering again;
  // otherwise anyone who learned the id could take over that patient's data.
  const existing = await env.DB.prepare('SELECT 1 FROM profiles WHERE id = ?').bind(profileId).first();
  if (existing) throw new HttpError(409, 'profile_exists');

  const deviceId = crypto.randomUUID();
  const secret = randomSecret();
  const secretHash = await sha256Hex(secret);
  const now = nowIso();

  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO profiles (id, full_name, phone, email, phone_verified, created_at, updated_at)
       VALUES (?, ?, ?, ?, 0, ?, ?)`,
    ).bind(profileId, fullName, phone, email, now, now),
    env.DB.prepare(
      `INSERT INTO devices (id, profile_id, secret_hash, platform, created_at, last_seen_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).bind(deviceId, profileId, secretHash, platform, now, now),
  ]);

  return { device_id: deviceId, token: `${deviceId}.${secret}` };
}

export async function authenticate(request: Request, env: Env): Promise<AuthContext> {
  const header = request.headers.get('Authorization') ?? '';
  const match = /^Bearer ([0-9a-f-]{36})\.([A-Za-z0-9_-]{43})$/.exec(header);
  if (!match) throw new HttpError(401, 'unauthorized');
  const [, deviceId, secret] = match;

  const device = await env.DB.prepare(
    'SELECT profile_id, secret_hash, last_seen_at FROM devices WHERE id = ? AND revoked_at IS NULL',
  )
    .bind(deviceId)
    .first<{ profile_id: string; secret_hash: string; last_seen_at: string }>();
  if (!device || !(await hashesEqual(await sha256Hex(secret), device.secret_hash))) {
    throw new HttpError(401, 'unauthorized');
  }

  // Avoid a write on every request: refresh last_seen_at at most once an hour.
  if (Date.now() - Date.parse(device.last_seen_at) > 60 * 60 * 1000) {
    await env.DB.prepare('UPDATE devices SET last_seen_at = ? WHERE id = ?').bind(nowIso(), deviceId).run();
  }

  return { deviceId, profileId: device.profile_id };
}

function randomSecret() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256Hex(value: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function hashesEqual(a: string, b: string) {
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  return left.byteLength === right.byteLength && crypto.subtle.timingSafeEqual(left, right);
}

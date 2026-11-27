// Family following. Replaces the Supabase Edge Functions generate-invite-code,
// join-family, approve-family-link and send-family-reminder, plus the RLS read rules:
//   full        -> caregiver sees medications and dose logs
//   status_only -> caregiver sees dose logs only (no medication names)
//   alerts_only -> caregiver sees neither (alerts arrive as notifications)
import type { AuthContext } from './auth';
import { HttpError, nowIso, readJson } from './http';
import { notifyUser, type Waiter } from './push';
import { enforceRateLimit } from './rateLimit';
import { isUuid } from './validate';

const INVITE_TTL_MS = 24 * 60 * 60 * 1000;
const PRIVACY_LEVELS = new Set(['full', 'status_only', 'alerts_only']);


type LinkRow = {
  id: string;
  patient_id: string;
  caregiver_id: string;
  privacy_level: string;
  status: string;
  full_name: string | null;
};

export async function overview(env: Env, auth: AuthContext) {
  // Only the other person's name is exposed, never their phone or email.
  const [followers, following, pending] = await env.DB.batch<LinkRow>([
    env.DB.prepare(
      `SELECT l.id, l.patient_id, l.caregiver_id, l.privacy_level, l.status, p.full_name
       FROM family_links l JOIN profiles p ON p.id = l.caregiver_id
       WHERE l.patient_id = ? AND l.status = 'active' ORDER BY l.approved_at`,
    ).bind(auth.profileId),
    env.DB.prepare(
      `SELECT l.id, l.patient_id, l.caregiver_id, l.privacy_level, l.status, p.full_name
       FROM family_links l JOIN profiles p ON p.id = l.patient_id
       WHERE l.caregiver_id = ? AND l.status = 'active' ORDER BY l.approved_at`,
    ).bind(auth.profileId),
    env.DB.prepare(
      `SELECT l.id, l.patient_id, l.caregiver_id, l.privacy_level, l.status, p.full_name
       FROM family_links l JOIN profiles p ON p.id = l.caregiver_id
       WHERE l.patient_id = ? AND l.status = 'pending' ORDER BY l.invited_at`,
    ).bind(auth.profileId),
  ]);
  const toApi = ({ full_name, ...link }: LinkRow) => ({ ...link, profiles: { full_name: full_name ?? '' } });
  return {
    followers: followers.results.map(toApi),
    following: following.results.map(toApi),
    pending: pending.results.map(toApi),
  };
}

export async function createInvitation(env: Env, auth: AuthContext) {
  const now = nowIso();
  const expiresAt = new Date(Date.now() + INVITE_TTL_MS).toISOString();
  // Expired codes are deleted so the 6-digit space is never exhausted; the old code of
  // this patient stops working as soon as a new one is made.
  await env.DB.batch([
    env.DB.prepare('DELETE FROM invitations WHERE expires_at <= ?').bind(now),
    env.DB.prepare('UPDATE invitations SET revoked_at = ? WHERE patient_id = ? AND revoked_at IS NULL').bind(now, auth.profileId),
  ]);

  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomCode();
    const inserted = await env.DB.prepare(
      `INSERT INTO invitations (id, patient_id, code, expires_at, created_at) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(code) DO NOTHING`,
    )
      .bind(crypto.randomUUID(), auth.profileId, code, expiresAt, now)
      .run();
    if (inserted.meta.changes === 1) return { code, expires_at: expiresAt };
  }
  throw new HttpError(503, 'code_unavailable', 'could not allocate an invite code');
}

export async function joinFamily(request: Request, env: Env, auth: AuthContext, waitUntil: Waiter) {
  const body = await readJson(request);
  const code = typeof body.code === 'string' ? body.code.trim() : '';
  if (!/^\d{6}$/.test(code)) throw new HttpError(400, 'invalid_field', 'code must be 6 digits');

  // Guessing codes: at most 10 attempts per device per hour.
  await enforceRateLimit(env, `join:${auth.deviceId}`, 10, 60 * 60, 'too many invite code attempts');

  const invite = await env.DB.prepare(
    `SELECT i.patient_id, p.full_name FROM invitations i JOIN profiles p ON p.id = i.patient_id
     WHERE i.code = ? AND i.revoked_at IS NULL AND i.expires_at > ?`,
  )
    .bind(code, nowIso())
    .first<{ patient_id: string; full_name: string }>();
  if (!invite) throw new HttpError(400, 'invalid_code', 'الرمز غير صحيح أو منتهي الصلاحية');
  if (invite.patient_id === auth.profileId) throw new HttpError(400, 'self_follow', 'لا يمكنك متابعة نفسك');

  const now = nowIso();
  // New request, or a fresh request after a rejection/removal. An active link stays active.
  const result = await env.DB.prepare(
    `INSERT INTO family_links (id, patient_id, caregiver_id, privacy_level, status, invited_at, updated_at)
     VALUES (?, ?, ?, 'full', 'pending', ?, ?)
     ON CONFLICT(patient_id, caregiver_id) DO UPDATE SET
       status = 'pending', invited_at = excluded.invited_at, approved_at = NULL, updated_at = excluded.updated_at
     WHERE family_links.status IN ('rejected', 'removed')`,
  )
    .bind(crypto.randomUUID(), invite.patient_id, auth.profileId, now, now)
    .run();

  const existing = await env.DB.prepare('SELECT status FROM family_links WHERE patient_id = ? AND caregiver_id = ?')
    .bind(invite.patient_id, auth.profileId)
    .first<{ status: string }>();

  if (result.meta.changes === 1) {
    const caregiver = await profileName(env, auth.profileId);
    await notifyUser(env, invite.patient_id, {
      type: 'invite_request',
      title: 'طلب متابعة جديد',
      body: `${caregiver || 'أحد أفراد العائلة'} يريد متابعة أدويتك`,
      data: { caregiver_id: auth.profileId },
    }, waitUntil);
  }

  return { status: existing?.status ?? 'pending', patient_name: invite.full_name };
}

export async function respondToRequest(request: Request, env: Env, auth: AuthContext, waitUntil: Waiter) {
  const body = await readJson(request);
  if (!isUuid(body.caregiver_id)) throw new HttpError(400, 'invalid_field', 'caregiver_id must be a UUID');
  const caregiverId = body.caregiver_id.toLowerCase();
  const action = body.action;
  if (action !== 'approve' && action !== 'reject') throw new HttpError(400, 'invalid_field', 'action must be approve or reject');
  const privacyLevel = body.privacy_level ?? 'full';
  if (typeof privacyLevel !== 'string' || !PRIVACY_LEVELS.has(privacyLevel)) {
    throw new HttpError(400, 'invalid_field', 'invalid privacy_level');
  }

  const now = nowIso();
  const status = action === 'approve' ? 'active' : 'rejected';
  // Only the patient can answer, and only a pending request.
  const result = await env.DB.prepare(
    `UPDATE family_links SET status = ?, privacy_level = ?, approved_at = ?, updated_at = ?
     WHERE patient_id = ? AND caregiver_id = ? AND status = 'pending'`,
  )
    .bind(status, privacyLevel, action === 'approve' ? now : null, now, auth.profileId, caregiverId)
    .run();
  if (result.meta.changes !== 1) throw new HttpError(404, 'request_not_found');

  await notifyUser(env, caregiverId, {
    type: 'invite_response',
    title: action === 'approve' ? 'تم القبول' : 'تم الرفض',
    body: action === 'approve' ? 'تم قبول طلب المتابعة' : 'لم يتم قبول طلب المتابعة',
    data: { status, patient_id: auth.profileId },
  }, waitUntil);
  return { status };
}

export async function patientDay(request: Request, env: Env, auth: AuthContext, patientId: string) {
  const link = await activeLink(env, patientId, auth.profileId);
  const url = new URL(request.url);
  const from = url.searchParams.get('from') ?? '';
  const to = url.searchParams.get('to') ?? '';
  const fromMs = Date.parse(from);
  const toMs = Date.parse(to);
  if (Number.isNaN(fromMs) || Number.isNaN(toMs) || toMs <= fromMs || toMs - fromMs > 48 * 60 * 60 * 1000) {
    throw new HttpError(400, 'invalid_field', 'from/to must be ISO dates at most 48 hours apart');
  }

  const patient = await profileName(env, patientId);
  const response = { patient_name: patient, privacy_level: link.privacy_level, medications: [] as unknown[], logs: [] as unknown[] };
  if (link.privacy_level === 'alerts_only') return response;

  const { results: logs } = await env.DB.prepare(
    `SELECT id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at
     FROM medication_logs WHERE patient_id = ? AND scheduled_at >= ? AND scheduled_at < ? ORDER BY scheduled_at`,
  )
    .bind(patientId, new Date(fromMs).toISOString(), new Date(toMs).toISOString())
    .all();
  response.logs = logs;

  if (link.privacy_level === 'full') {
    const { results: medications } = await env.DB.prepare(
      `SELECT id, name, dose_amount, dose_unit, times_json FROM medications
       WHERE patient_id = ? AND is_active = 1 AND deleted_at IS NULL ORDER BY created_at`,
    )
      .bind(patientId)
      .all<{ times_json: string } & Record<string, unknown>>();
    response.medications = medications.map(({ times_json, ...rest }) => ({ ...rest, times: JSON.parse(times_json) }));
  }
  return response;
}

export async function remindPatient(env: Env, auth: AuthContext, patientId: string, waitUntil: Waiter) {
  await activeLink(env, patientId, auth.profileId);
  // A gentle nudge, not spam: one reminder per caregiver per patient every 10 minutes.
  await enforceRateLimit(env, `remind:${auth.profileId}:${patientId}`, 1, 10 * 60, 'reminder already sent recently');
  const caregiver = await profileName(env, auth.profileId);
  await notifyUser(env, patientId, {
    type: 'family_reminder',
    title: 'تذكير من العائلة',
    body: `${caregiver || 'أحد أفراد عائلتك'} يذكّرك بلطف بمراجعة جرعاتك اليوم`,
    data: { caregiver_id: auth.profileId },
    channelId: 'medication-reminders',
  }, waitUntil);
  return { ok: true };
}

export async function listNotifications(env: Env, auth: AuthContext) {
  const { results } = await env.DB.prepare(
    `SELECT id, type, title, body, data_json, read, created_at FROM notifications_log
     WHERE user_id = ? ORDER BY created_at DESC LIMIT 100`,
  )
    .bind(auth.profileId)
    .all<{ data_json: string; read: number } & Record<string, unknown>>();
  return {
    notifications: results.map(({ data_json, read, ...rest }) => ({ ...rest, data: JSON.parse(data_json), read: read === 1 })),
  };
}

export async function markAllNotificationsRead(env: Env, auth: AuthContext) {
  await env.DB.prepare('UPDATE notifications_log SET read = 1 WHERE user_id = ? AND read = 0').bind(auth.profileId).run();
  return { ok: true };
}

async function activeLink(env: Env, patientId: string, caregiverId: string) {
  const link = await env.DB.prepare(
    `SELECT privacy_level FROM family_links WHERE patient_id = ? AND caregiver_id = ? AND status = 'active'`,
  )
    .bind(patientId, caregiverId)
    .first<{ privacy_level: string }>();
  // Same answer whether the patient exists or not, so ids cannot be probed.
  if (!link) throw new HttpError(403, 'forbidden');
  return link;
}

async function profileName(env: Env, profileId: string) {
  const row = await env.DB.prepare('SELECT full_name FROM profiles WHERE id = ?').bind(profileId).first<{ full_name: string }>();
  return row?.full_name ?? null;
}

function randomCode() {
  // Rejection sampling keeps every code equally likely.
  const buffer = new Uint32Array(1);
  let value: number;
  do {
    crypto.getRandomValues(buffer);
    value = buffer[0];
  } while (value >= 4294000000);
  return String(value % 1000000).padStart(6, '0');
}

// Help button: the elder asks for help, and every approved family member gets an urgent push,
// whatever privacy level they were given. One relative answers "I'm coming", and the elder and
// the rest of the family are told. Unanswered requests are pushed again every 2 minutes, 5 times
// at most (the scheduled handler).
import type { AuthContext } from './auth';
import { HttpError, nowIso, optionalText, readJson } from './http';
import { notifyUser, type Waiter } from './push';
import { enforceRateLimit } from './rateLimit';

/** A request older than this is no longer shown as active. */
const ACTIVE_FOR_MS = 12 * 60 * 60 * 1000;
const RENOTIFY_EVERY_MS = 2 * 60 * 1000;
const MAX_NOTIFY = 5;
/** Re-alerts stop for requests older than this, even if nobody answered. */
const ESCALATE_FOR_MS = 60 * 60 * 1000;

export const HELP_CHANNEL = 'help';

type HelpRow = {
  id: string;
  patient_id: string;
  status: 'open' | 'acknowledged' | 'resolved' | 'cancelled';
  message: string | null;
  latitude: number | null;
  longitude: number | null;
  acknowledged_by: string | null;
  acknowledged_at: string | null;
  resolved_at: string | null;
  notify_count: number;
  last_notified_at: string;
  created_at: string;
};

const activeSince = () => new Date(Date.now() - ACTIVE_FOR_MS).toISOString();

async function followers(env: Env, patientId: string) {
  const { results } = await env.DB.prepare(
    `SELECT l.caregiver_id AS id, p.full_name AS name FROM family_links l JOIN profiles p ON p.id = l.caregiver_id
     WHERE l.patient_id = ? AND l.status = 'active'`,
  )
    .bind(patientId)
    .all<{ id: string; name: string }>();
  return results;
}

async function profile(env: Env, id: string) {
  return env.DB.prepare('SELECT full_name, phone FROM profiles WHERE id = ?').bind(id).first<{ full_name: string; phone: string }>();
}

/** What the app shows. The phone number goes only to approved family, so they can call. */
async function toApi(env: Env, row: HelpRow, forCaregiver: boolean) {
  const patient = await profile(env, row.patient_id);
  const responder = row.acknowledged_by ? await profile(env, row.acknowledged_by) : null;
  return {
    id: row.id,
    patient_id: row.patient_id,
    patient_name: patient?.full_name ?? '',
    patient_phone: forCaregiver ? patient?.phone ?? null : null,
    status: row.status,
    message: row.message,
    latitude: forCaregiver ? row.latitude : null,
    longitude: forCaregiver ? row.longitude : null,
    acknowledged_by: row.acknowledged_by,
    acknowledged_by_name: responder?.full_name ?? null,
    acknowledged_at: row.acknowledged_at,
    resolved_at: row.resolved_at,
    created_at: row.created_at,
  };
}

function alert(patientName: string, row: HelpRow, phone: string | null, repeat: boolean) {
  return {
    type: 'help_request',
    title: repeat ? `تذكير عاجل: ${patientName} ما زال يحتاج مساعدة` : `${patientName} يحتاج مساعدة`,
    body: row.message || 'ضغط زر المساعدة في عافيتي. اتصل به أو اضغط «أنا قادم».',
    data: { help_id: row.id, patient_id: row.patient_id, patient_name: patientName, phone: phone ?? '' },
    channelId: HELP_CHANNEL,
    priority: 'high' as const,
  };
}

async function pushToFamily(env: Env, row: HelpRow, waitUntil: Waiter, repeat: boolean) {
  const patient = await profile(env, row.patient_id);
  const family = await followers(env, row.patient_id);
  const message = alert(patient?.full_name ?? 'أحد أفراد عائلتك', row, patient?.phone ?? null, repeat);
  // Repeated alerts are pushed again but not stored again, so the notifications list stays readable.
  for (const member of family) await notifyUser(env, member.id, message, waitUntil, { log: !repeat });
  return family.length;
}

export async function requestHelp(request: Request, env: Env, auth: AuthContext, waitUntil: Waiter) {
  const body = await readJson(request).catch(() => ({}) as Record<string, unknown>);
  const message = optionalText(body.message, 'message', 300);
  const latitude = typeof body.latitude === 'number' && Math.abs(body.latitude) <= 90 ? body.latitude : null;
  const longitude = typeof body.longitude === 'number' && Math.abs(body.longitude) <= 180 ? body.longitude : null;
  await enforceRateLimit(env, `help:${auth.profileId}`, 10, 60 * 60, 'too many help requests');

  // Pressing again while a request is still open re-alerts the family instead of making a second one.
  const existing = await env.DB.prepare(
    `SELECT * FROM help_requests WHERE patient_id = ? AND status IN ('open', 'acknowledged') AND created_at > ?
     ORDER BY created_at DESC LIMIT 1`,
  )
    .bind(auth.profileId, activeSince())
    .first<HelpRow>();

  const now = nowIso();
  let row: HelpRow;
  if (existing) {
    await env.DB.prepare(
      `UPDATE help_requests SET status = 'open', acknowledged_by = NULL, acknowledged_at = NULL,
         message = COALESCE(?, message), latitude = COALESCE(?, latitude), longitude = COALESCE(?, longitude),
         notify_count = 1, last_notified_at = ? WHERE id = ?`,
    )
      .bind(message, latitude, longitude, now, existing.id)
      .run();
    row = (await env.DB.prepare('SELECT * FROM help_requests WHERE id = ?').bind(existing.id).first<HelpRow>())!;
  } else {
    row = {
      id: crypto.randomUUID(), patient_id: auth.profileId, status: 'open', message, latitude, longitude,
      acknowledged_by: null, acknowledged_at: null, resolved_at: null, notify_count: 1, last_notified_at: now, created_at: now,
    };
    await env.DB.prepare(
      `INSERT INTO help_requests (id, patient_id, status, message, latitude, longitude, notify_count, last_notified_at, created_at)
       VALUES (?, ?, 'open', ?, ?, ?, 1, ?, ?)`,
    )
      .bind(row.id, row.patient_id, message, latitude, longitude, now, now)
      .run();
  }
  const notified = await pushToFamily(env, row, waitUntil, false);
  return { help: await toApi(env, row, false), notified };
}

async function load(env: Env, id: string) {
  const row = await env.DB.prepare('SELECT * FROM help_requests WHERE id = ?').bind(id).first<HelpRow>();
  if (!row) throw new HttpError(404, 'help_not_found');
  return row;
}

async function isFollower(env: Env, patientId: string, caregiverId: string) {
  const link = await env.DB.prepare(
    `SELECT 1 AS ok FROM family_links WHERE patient_id = ? AND caregiver_id = ? AND status = 'active'`,
  )
    .bind(patientId, caregiverId)
    .first<{ ok: number }>();
  return Boolean(link);
}

export async function acknowledgeHelp(env: Env, auth: AuthContext, id: string, waitUntil: Waiter) {
  const row = await load(env, id);
  // Same answer as a missing request, so ids cannot be probed.
  if (!(await isFollower(env, row.patient_id, auth.profileId))) throw new HttpError(404, 'help_not_found');
  if (row.status === 'resolved' || row.status === 'cancelled') return { help: await toApi(env, row, true) };
  if (row.status === 'acknowledged') return { help: await toApi(env, row, true) };

  const now = nowIso();
  const updated = await env.DB.prepare(
    `UPDATE help_requests SET status = 'acknowledged', acknowledged_by = ?, acknowledged_at = ? WHERE id = ? AND status = 'open'`,
  )
    .bind(auth.profileId, now, id)
    .run();
  const current = await load(env, id);
  if (updated.meta.changes === 1) {
    const responder = (await profile(env, auth.profileId))?.full_name ?? 'أحد أفراد عائلتك';
    const patientName = (await profile(env, row.patient_id))?.full_name ?? '';
    await notifyUser(env, row.patient_id, {
      type: 'help_ack',
      title: `${responder} قادم إليك`,
      body: 'وصل طلبك. ابق في مكانك واطمئن.',
      data: { help_id: id },
      channelId: HELP_CHANNEL,
      priority: 'high',
    }, waitUntil);
    for (const member of await followers(env, row.patient_id)) {
      if (member.id === auth.profileId) continue;
      await notifyUser(env, member.id, {
        type: 'help_update',
        title: `${responder} استجاب`,
        body: `${responder} في طريقه إلى ${patientName}.`,
        data: { help_id: id, patient_id: row.patient_id },
        channelId: 'family',
      }, waitUntil);
    }
  }
  return { help: await toApi(env, current, true) };
}

export async function resolveHelp(env: Env, auth: AuthContext, id: string, waitUntil: Waiter) {
  const row = await load(env, id);
  if (row.patient_id !== auth.profileId) throw new HttpError(404, 'help_not_found');
  if (row.status === 'resolved' || row.status === 'cancelled') return { help: await toApi(env, row, false) };
  const now = nowIso();
  // Answered before anyone responded: a cancel (pressed by mistake). Otherwise resolved.
  const status = row.status === 'open' ? 'cancelled' : 'resolved';
  await env.DB.prepare('UPDATE help_requests SET status = ?, resolved_at = ? WHERE id = ?').bind(status, now, id).run();
  const patientName = (await profile(env, row.patient_id))?.full_name ?? '';
  for (const member of await followers(env, row.patient_id)) {
    await notifyUser(env, member.id, {
      type: 'help_resolved',
      title: `${patientName} بخير الآن`,
      body: status === 'cancelled' ? 'ألغى طلب المساعدة وطمأنكم أنه بخير.' : 'أكّد أنه بخير. شكرًا لاستجابتكم.',
      data: { help_id: id, patient_id: row.patient_id },
      channelId: 'family',
    }, waitUntil);
  }
  return { help: await toApi(env, await load(env, id), false) };
}

/** The elder's own active request, and active requests of people this user follows. */
export async function activeHelp(env: Env, auth: AuthContext) {
  const since = activeSince();
  const mine = await env.DB.prepare(
    `SELECT * FROM help_requests WHERE patient_id = ? AND status IN ('open', 'acknowledged') AND created_at > ?
     ORDER BY created_at DESC LIMIT 1`,
  )
    .bind(auth.profileId, since)
    .first<HelpRow>();
  const { results: family } = await env.DB.prepare(
    `SELECT h.* FROM help_requests h JOIN family_links l ON l.patient_id = h.patient_id
     WHERE l.caregiver_id = ? AND l.status = 'active' AND h.status IN ('open', 'acknowledged') AND h.created_at > ?
     ORDER BY h.created_at DESC LIMIT 20`,
  )
    .bind(auth.profileId, since)
    .all<HelpRow>();
  return {
    mine: mine ? await toApi(env, mine, false) : null,
    family: await Promise.all(family.map((row) => toApi(env, row, true))),
  };
}

/** Scheduled every minute: push unanswered requests again. */
export async function escalateHelp(env: Env, waitUntil: Waiter) {
  const now = Date.now();
  const { results } = await env.DB.prepare(
    `SELECT * FROM help_requests WHERE status = 'open' AND notify_count < ? AND last_notified_at <= ? AND created_at > ? LIMIT 20`,
  )
    .bind(MAX_NOTIFY, new Date(now - RENOTIFY_EVERY_MS).toISOString(), new Date(now - ESCALATE_FOR_MS).toISOString())
    .all<HelpRow>();
  for (const row of results) {
    // Claim the row first, so two overlapping runs cannot push the same round twice.
    const claimed = await env.DB.prepare(
      `UPDATE help_requests SET notify_count = notify_count + 1, last_notified_at = ? WHERE id = ? AND last_notified_at = ? AND status = 'open'`,
    )
      .bind(nowIso(), row.id, row.last_notified_at)
      .run();
    if (claimed.meta.changes === 1) await pushToFamily(env, row, waitUntil, true);
  }
  return results.length;
}

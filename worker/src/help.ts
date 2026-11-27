// Help button: the elder asks for help, and every approved family member gets an urgent push,
// whatever privacy level they were given. One relative answers "I'm coming", and the elder and
// the rest of the family are told. Unanswered requests are pushed again every 2 minutes, 5 times
// at most (the scheduled handler).
//
// The message says when the button was pressed (in the elder's time zone), the reason the elder
// picked, and the elder's location on Google Maps when the phone could get it. Verbs follow the
// elder's gender ("يحتاج" / "تحتاج"). The location may arrive a few seconds after the alert
// (POST /v1/help/:id/location); the family is then alerted again with the map.
import type { AuthContext } from './auth';
import { clock, g, mapUrl, parseGender, type Gender } from './arabic';
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
  location_accuracy: number | null;
  location_at: string | null;
  acknowledged_by: string | null;
  acknowledged_at: string | null;
  resolved_at: string | null;
  notify_count: number;
  last_notified_at: string;
  created_at: string;
};

type Person = { full_name: string; phone: string; gender: Gender };

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

async function profile(env: Env, id: string): Promise<Person | null> {
  const row = await env.DB.prepare('SELECT full_name, phone, gender FROM profiles WHERE id = ?')
    .bind(id)
    .first<{ full_name: string; phone: string; gender: string | null }>();
  return row ? { ...row, gender: parseGender(row.gender) } : null;
}

async function zoneOf(env: Env, profileId: string) {
  const row = await env.DB.prepare(
    'SELECT timezone FROM devices WHERE profile_id = ? AND revoked_at IS NULL AND timezone IS NOT NULL ORDER BY last_seen_at DESC LIMIT 1',
  )
    .bind(profileId)
    .first<{ timezone: string }>();
  return row?.timezone ?? null;
}

const hasLocation = (row: HelpRow) => row.latitude !== null && row.longitude !== null;

/** What the app shows. The phone number and location go only to approved family. */
async function toApi(env: Env, row: HelpRow, forCaregiver: boolean) {
  const patient = await profile(env, row.patient_id);
  const responder = row.acknowledged_by ? await profile(env, row.acknowledged_by) : null;
  const located = forCaregiver && hasLocation(row);
  return {
    id: row.id,
    patient_id: row.patient_id,
    patient_name: patient?.full_name ?? '',
    patient_gender: patient?.gender ?? null,
    patient_phone: forCaregiver ? patient?.phone ?? null : null,
    status: row.status,
    message: row.message,
    latitude: located ? row.latitude : null,
    longitude: located ? row.longitude : null,
    location_accuracy: located ? row.location_accuracy : null,
    map_url: located ? mapUrl(row.latitude!, row.longitude!) : null,
    // The elder only needs to know the location was shared.
    location_shared: hasLocation(row),
    acknowledged_by: row.acknowledged_by,
    acknowledged_by_name: responder?.full_name ?? null,
    acknowledged_by_gender: responder?.gender ?? null,
    acknowledged_at: row.acknowledged_at,
    resolved_at: row.resolved_at,
    created_at: row.created_at,
  };
}

/** The urgent alert to relatives, in Arabic and English. Exported for tests of the wording. */
export function helpAlert(patient: { name: string; gender: Gender; phone: string | null }, row: HelpRow, zone: string | null, repeat: boolean) {
  const { name, gender } = patient;
  const needs = g(gender, 'يحتاج', 'تحتاج');
  const located = hasLocation(row);
  const ar: string[] = [];
  const en: string[] = [];
  if (row.message) {
    ar.push(`«${row.message}».`);
    en.push(`“${row.message}”.`);
  }
  ar.push(`${g(gender, 'ضغط', 'ضغطت')} زر المساعدة الساعة ${clock(row.created_at, zone)}.`);
  en.push(`Pressed the help button at ${clock(row.created_at, zone, 'en')}.`);
  if (located) {
    ar.push(`${g(gender, 'موقعه', 'موقعها')} مرفق، اضغط «الخريطة».`);
    en.push('Location attached — tap “Map”.');
  }
  ar.push(`${g(gender, 'اتصل به', 'اتصل بها')} أو اضغط «أنا قادم».`);
  en.push("Call them or tap “I'm coming”.");
  const data: Record<string, unknown> = {
    help_id: row.id,
    patient_id: row.patient_id,
    patient_name: name,
    patient_gender: gender ?? '',
    phone: patient.phone ?? '',
  };
  if (located) {
    data.latitude = row.latitude;
    data.longitude = row.longitude;
    data.map_url = mapUrl(row.latitude!, row.longitude!);
  }
  return {
    type: 'help_request',
    title: repeat
      ? { ar: `تذكير عاجل: ${name} ${g(gender, 'ما زال', 'ما زالت')} ${needs} مساعدة`, en: `Urgent reminder: ${name} still needs help` }
      : { ar: `${name} ${needs} مساعدة`, en: `${name} needs help` },
    body: { ar: ar.join(' '), en: en.join(' ') },
    data,
    channelId: HELP_CHANNEL,
    priority: 'high' as const,
  };
}

async function pushToFamily(env: Env, row: HelpRow, waitUntil: Waiter, repeat: boolean, log = !repeat) {
  const patient = await profile(env, row.patient_id);
  const family = await followers(env, row.patient_id);
  const message = helpAlert(
    { name: patient?.full_name ?? 'أحد أفراد عائلتك', gender: patient?.gender ?? null, phone: patient?.phone ?? null },
    row, await zoneOf(env, row.patient_id), repeat,
  );
  // Repeated alerts are pushed again but not stored again, so the notifications list stays readable.
  for (const member of family) await notifyUser(env, member.id, message, waitUntil, { log });
  return family.length;
}

type Location = { latitude: number; longitude: number; accuracy: number | null; at: string } | null;

function readLocation(body: Record<string, unknown>): Location {
  const { latitude, longitude } = body;
  if (typeof latitude !== 'number' || typeof longitude !== 'number') return null;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
  const accuracy = typeof body.accuracy === 'number' && Number.isFinite(body.accuracy) && body.accuracy >= 0 ? Math.min(body.accuracy, 100000) : null;
  return { latitude, longitude, accuracy, at: nowIso() };
}

export async function requestHelp(request: Request, env: Env, auth: AuthContext, waitUntil: Waiter) {
  const body = await readJson(request).catch(() => ({}) as Record<string, unknown>);
  const message = optionalText(body.message, 'message', 300);
  const location = readLocation(body);
  await enforceRateLimit(env, `help:${auth.profileId}`, 10, 60 * 60, 'too many help requests');

  // Pressing again while a request is still open re-alerts the family instead of making a second one.
  const existing = await env.DB.prepare(
    `SELECT * FROM help_requests WHERE patient_id = ? AND status IN ('open', 'acknowledged') AND created_at > ?
     ORDER BY created_at DESC LIMIT 1`,
  )
    .bind(auth.profileId, activeSince())
    .first<HelpRow>();

  const now = nowIso();
  let id: string;
  if (existing) {
    id = existing.id;
    await env.DB.prepare(
      `UPDATE help_requests SET status = 'open', acknowledged_by = NULL, acknowledged_at = NULL,
         message = COALESCE(?, message), latitude = COALESCE(?, latitude), longitude = COALESCE(?, longitude),
         location_accuracy = CASE WHEN ? IS NULL THEN location_accuracy ELSE ? END,
         location_at = COALESCE(?, location_at), notify_count = 1, last_notified_at = ? WHERE id = ?`,
    )
      .bind(message, location?.latitude ?? null, location?.longitude ?? null, location?.latitude ?? null, location?.accuracy ?? null,
        location?.at ?? null, now, id)
      .run();
  } else {
    id = crypto.randomUUID();
    await env.DB.prepare(
      `INSERT INTO help_requests (id, patient_id, status, message, latitude, longitude, location_accuracy, location_at, notify_count, last_notified_at, created_at)
       VALUES (?, ?, 'open', ?, ?, ?, ?, ?, 1, ?, ?)`,
    )
      .bind(id, auth.profileId, message, location?.latitude ?? null, location?.longitude ?? null, location?.accuracy ?? null, location?.at ?? null, now, now)
      .run();
  }
  const row = await load(env, id);
  const notified = await pushToFamily(env, row, waitUntil, false);
  return { help: await toApi(env, row, false), notified };
}

/**
 * The elder's location, sent after the alert when the phone needed a few seconds to find it.
 * Unanswered: the family is alerted again, now with the map. Answered: they get the map quietly.
 */
export async function updateHelpLocation(request: Request, env: Env, auth: AuthContext, id: string, waitUntil: Waiter) {
  const row = await load(env, id);
  if (row.patient_id !== auth.profileId) throw new HttpError(404, 'help_not_found');
  const location = readLocation(await readJson(request));
  if (!location) throw new HttpError(400, 'invalid_field', 'latitude and longitude are required');
  if (row.status === 'resolved' || row.status === 'cancelled') return { help: await toApi(env, row, false) };

  await env.DB.prepare('UPDATE help_requests SET latitude = ?, longitude = ?, location_accuracy = ?, location_at = ? WHERE id = ?')
    .bind(location.latitude, location.longitude, location.accuracy, location.at, id)
    .run();
  const current = await load(env, id);
  if (current.status === 'open') {
    await pushToFamily(env, current, waitUntil, false, false);
  } else {
    const patient = await profile(env, row.patient_id);
    const name = patient?.full_name ?? '';
    for (const member of await followers(env, row.patient_id)) {
      await notifyUser(env, member.id, {
        type: 'help_location',
        title: { ar: `موقع ${name}`, en: `${name}'s location` },
        body: { ar: `وصل موقع ${name} الآن. اضغط «الخريطة».`, en: `${name}'s location just arrived. Tap “Map”.` },
        data: { help_id: id, patient_id: row.patient_id, latitude: location.latitude, longitude: location.longitude, map_url: mapUrl(location.latitude, location.longitude) },
        channelId: 'family',
      }, waitUntil, { log: false });
    }
  }
  return { help: await toApi(env, current, false) };
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
    const responder = await profile(env, auth.profileId);
    const responderName = responder?.full_name ?? 'أحد أفراد عائلتك';
    const rg = responder?.gender ?? null;
    const patient = await profile(env, row.patient_id);
    const pg = patient?.gender ?? null;
    await notifyUser(env, row.patient_id, {
      type: 'help_ack',
      title: { ar: `${responderName} ${g(rg, 'قادم', 'قادمة')} إليك`, en: `${responderName} is coming to you` },
      body: { ar: `وصل طلبك. ${g(pg, 'ابق في مكانك واطمئن.', 'ابقي في مكانك واطمئني.')}`, en: 'Your request arrived. Stay where you are and relax.' },
      data: { help_id: id },
      channelId: HELP_CHANNEL,
      priority: 'high',
    }, waitUntil);
    for (const member of await followers(env, row.patient_id)) {
      if (member.id === auth.profileId) continue;
      await notifyUser(env, member.id, {
        type: 'help_update',
        title: { ar: `${responderName} ${g(rg, 'استجاب', 'استجابت')}`, en: `${responderName} responded` },
        body: {
          ar: `${responderName} ${g(rg, 'في طريقه', 'في طريقها')} إلى ${patient?.full_name ?? ''}.`,
          en: `${responderName} is on the way to ${patient?.full_name ?? ''}.`,
        },
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
  const patient = await profile(env, row.patient_id);
  const pg = patient?.gender ?? null;
  for (const member of await followers(env, row.patient_id)) {
    await notifyUser(env, member.id, {
      type: 'help_resolved',
      title: { ar: `${patient?.full_name ?? ''} بخير الآن`, en: `${patient?.full_name ?? ''} is okay now` },
      body: status === 'cancelled'
        ? { ar: g(pg, 'ألغى طلب المساعدة وطمأنكم أنه بخير.', 'ألغت طلب المساعدة وطمأنتكم أنها بخير.'), en: "Cancelled the help request and says they're okay." }
        : { ar: `${g(pg, 'أكّد أنه بخير.', 'أكّدت أنها بخير.')} شكرًا لاستجابتكم.`, en: "Confirmed they're okay. Thank you for responding." },
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

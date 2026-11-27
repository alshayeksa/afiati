// Scheduled family alerts, run by the cron every minute (see index.ts):
//
//   Missed doses: a dose still not taken an hour after its time is reported once to every approved
//   relative. Names of medicines go only to relatives with full privacy. The server only knows what
//   the phone synced, so when the phone has been offline since the dose the message says so.
//
//   Low stock: a medicine at or below its alert level is reported once to relatives with full privacy
//   (they are the ones who can buy it), only during the day in the elder's time zone. It is reported
//   again only after a refill above the level. The elder is told on the phone itself, offline.
import { nowIso } from './http';
import { notifyUser, type Waiter } from './push';

/** A dose is reported this long after its time if it is still not taken. */
export const MISSED_AFTER_MS = 60 * 60 * 1000;
/** Doses older than this are not reported (e.g. a phone that comes back online the next day). */
export const MISSED_WINDOW_MS = 3 * 60 * 60 * 1000;
/** Stock alerts wait for this local time window in the elder's zone. */
const DAY_FROM_HOUR = 9;
const DAY_TO_HOUR = 21;
/** Most users are in Saudi Arabia; used until the phone reports its zone. */
const DEFAULT_ZONE = 'Asia/Riyadh';

type DueLog = { id: string; patient_id: string; scheduled_at: string; name: string };
type Follower = { id: string; privacy_level: 'full' | 'status_only' | 'alerts_only' };

async function followers(env: Env, patientId: string) {
  const { results } = await env.DB.prepare(
    `SELECT caregiver_id AS id, privacy_level FROM family_links WHERE patient_id = ? AND status = 'active'`,
  )
    .bind(patientId)
    .all<Follower>();
  return results;
}

async function patientName(env: Env, patientId: string) {
  const row = await env.DB.prepare('SELECT full_name FROM profiles WHERE id = ?').bind(patientId).first<{ full_name: string }>();
  return row?.full_name ?? 'أحد أفراد عائلتك';
}

/** Arabic count of doses: جرعة، جرعتان، 3 جرعات، 11 جرعة */
export function dosesPhrase(count: number) {
  if (count === 1) return 'جرعة';
  if (count === 2) return 'جرعتان';
  if (count <= 10) return `${count} جرعات`;
  return `${count} جرعة`;
}

export function missedMessage(name: string, medicines: string[], doses: number, privacy: Follower['privacy_level'], phoneOffline: boolean) {
  const what = privacy === 'full' && medicines.length ? `${[...new Set(medicines)].join('، ')}: ` : '';
  const offline = phoneOffline ? ' الجوّال غير متصل بالإنترنت منذ موعدها، فربما أُخذت ولم يصلنا التسجيل.' : '';
  return {
    type: 'missed_dose',
    title: `فاتت ${name} ${dosesPhrase(doses)}`,
    body: `${what}مضت ساعة على الموعد ولم تُسجَّل.${offline} اضغط للتفاصيل وإرسال تذكير.`,
  };
}

/** Pending doses an hour late: one message per relative per elder, then the doses are marked as reported. */
export async function alertMissedDoses(env: Env, waitUntil: Waiter, now = Date.now()) {
  const { results } = await env.DB.prepare(
    `SELECT l.id, l.patient_id, l.scheduled_at, m.name
     FROM medication_logs l JOIN medications m ON m.id = l.medication_id
     WHERE l.status IN ('pending', 'missed') AND l.family_notified_at IS NULL
       AND l.scheduled_at <= ? AND l.scheduled_at > ?
       AND m.is_active = 1 AND m.deleted_at IS NULL
     ORDER BY l.patient_id, l.scheduled_at LIMIT 200`,
  )
    .bind(new Date(now - MISSED_AFTER_MS).toISOString(), new Date(now - MISSED_WINDOW_MS).toISOString())
    .all<DueLog>();
  if (!results.length) return 0;

  // Claim each dose first, so two overlapping runs never report the same dose twice.
  const stamp = nowIso();
  const claims = await env.DB.batch(
    results.map((log) =>
      env.DB.prepare('UPDATE medication_logs SET family_notified_at = ? WHERE id = ? AND family_notified_at IS NULL').bind(stamp, log.id),
    ),
  );
  const claimed = results.filter((_, index) => claims[index].meta.changes === 1);

  const byPatient = new Map<string, DueLog[]>();
  for (const log of claimed) byPatient.set(log.patient_id, [...(byPatient.get(log.patient_id) ?? []), log]);

  let sent = 0;
  for (const [patientId, logs] of byPatient) {
    const family = await followers(env, patientId);
    if (!family.length) continue;
    const name = await patientName(env, patientId);
    // last_seen_at is refreshed at most hourly, so "no request since the dose" is certain only below this line.
    const seen = await env.DB.prepare('SELECT MAX(last_seen_at) AS at FROM devices WHERE profile_id = ? AND revoked_at IS NULL')
      .bind(patientId)
      .first<{ at: string | null }>();
    const earliest = logs[0].scheduled_at;
    const phoneOffline = !seen?.at || Date.parse(seen.at) < Date.parse(earliest) - 60 * 60 * 1000;
    for (const member of family) {
      const message = missedMessage(name, logs.map((log) => log.name), logs.length, member.privacy_level, phoneOffline);
      await notifyUser(env, member.id, { ...message, data: { patient_id: patientId }, channelId: 'family' }, waitUntil);
      sent++;
    }
  }
  return sent;
}

/** The hour (0-23) now in a time zone. */
export function localHour(zone: string | null, now = Date.now()) {
  const format = (timeZone: string) =>
    Number(new Intl.DateTimeFormat('en-US', { timeZone, hour: 'numeric', hourCycle: 'h23' }).format(new Date(now)));
  try {
    return format(zone || DEFAULT_ZONE);
  } catch {
    return format(DEFAULT_ZONE);
  }
}

export function stockMessage(name: string, medicine: string, count: number) {
  return count <= 0
    ? { type: 'low_stock', title: `نفد ${medicine} عند ${name}`, body: 'انتهت الكمية المسجلة. يحتاج علبة جديدة.' }
    : { type: 'low_stock', title: `${medicine} عند ${name} قارب على النفاد`, body: `الكمية الباقية: ${count}. يحتاج علبة جديدة قريبًا.` };
}

type LowStock = { id: string; patient_id: string; name: string; stock_count: number };

export async function alertLowStock(env: Env, waitUntil: Waiter, now = Date.now()) {
  // Refilled, stopped or no longer counted: the next time it runs low is a new alert.
  await env.DB.prepare(
    `UPDATE medications SET stock_alerted_at = NULL
     WHERE stock_alerted_at IS NOT NULL AND (stock_count IS NULL OR stock_count > stock_alert OR is_active = 0 OR deleted_at IS NOT NULL)`,
  ).run();

  const { results } = await env.DB.prepare(
    `SELECT id, patient_id, name, stock_count FROM medications
     WHERE stock_alerted_at IS NULL AND stock_count IS NOT NULL AND stock_count <= stock_alert
       AND is_active = 1 AND deleted_at IS NULL
     ORDER BY patient_id LIMIT 100`,
  ).all<LowStock>();

  let sent = 0;
  const zones = new Map<string, string | null>();
  for (const medication of results) {
    if (!zones.has(medication.patient_id)) {
      const device = await env.DB.prepare(
        `SELECT timezone FROM devices WHERE profile_id = ? AND revoked_at IS NULL AND timezone IS NOT NULL ORDER BY last_seen_at DESC LIMIT 1`,
      )
        .bind(medication.patient_id)
        .first<{ timezone: string }>();
      zones.set(medication.patient_id, device?.timezone ?? null);
    }
    // At night it stays unreported and goes out in the morning.
    const hour = localHour(zones.get(medication.patient_id) ?? null, now);
    if (hour < DAY_FROM_HOUR || hour > DAY_TO_HOUR) continue;

    const claim = await env.DB.prepare('UPDATE medications SET stock_alerted_at = ? WHERE id = ? AND stock_alerted_at IS NULL')
      .bind(nowIso(), medication.id)
      .run();
    if (claim.meta.changes !== 1) continue;
    const relatives = (await followers(env, medication.patient_id)).filter((member) => member.privacy_level === 'full');
    if (!relatives.length) continue;
    const message = stockMessage(await patientName(env, medication.patient_id), medication.name, medication.stock_count);
    for (const member of relatives) {
      await notifyUser(
        env, member.id,
        { ...message, data: { patient_id: medication.patient_id, medication_id: medication.id }, channelId: 'family' },
        waitUntil,
      );
      sent++;
    }
  }
  return sent;
}

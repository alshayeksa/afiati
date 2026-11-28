// Medical partners (ambulance services, hospitals): ready for when an organisation asks to be linked.
//
// The owner adds a partner on the control page and gives it two secrets, shown once:
//   - an API key, to call /v1/partner/* (Authorization: Bearer afp_...)
//   - a webhook secret, to check our signature on the requests we send to its webhook
//
// When an elder who turned on «share my help requests with medical services» presses the help
// button, every active partner serving the elder's country gets the request on its webhook
// (event help.created), then help.location when the location arrives and help.resolved when the
// elder is fine. The partner answers with POST /v1/partner/help/:id/status (acknowledged, arrived,
// closed), and the elder and the family are told. Failed deliveries are retried by the cron.
// Full contract: docs/partner-api.md.
import { g, mapUrl, parseGender } from './arabic';
import { HttpError, nowIso, readJson } from './http';
import { notifyUser, type Waiter } from './push';
import { enforceRateLimit } from './rateLimit';

export type Partner = {
  id: string;
  name: string;
  webhook_url: string | null;
  webhook_secret: string;
  countries_json: string;
  active: number;
  created_at: string;
};

export type HelpEvent = 'help.created' | 'help.location' | 'help.resolved';

const MAX_ATTEMPTS = 6;
const RETRY_AFTER_MS = 60 * 1000;
/** Deliveries are not retried for requests older than this. */
const RETRY_FOR_MS = 60 * 60 * 1000;

const hex = (bytes: ArrayBuffer) => [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, '0')).join('');
const sha256Hex = async (text: string) => hex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)));

function randomToken(bytes = 32) {
  const raw = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...raw)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function sign(secret: string, timestamp: string, body: string) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return hex(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${timestamp}.${body}`)));
}

export const countriesOf = (partner: Pick<Partner, 'countries_json'>): string[] => {
  try {
    const list = JSON.parse(partner.countries_json);
    return Array.isArray(list) ? list.filter((c) => typeof c === 'string') : [];
  } catch {
    return [];
  }
};

// --- Owner (control page) ---

export async function listPartners(env: Env) {
  const { results } = await env.DB.prepare(
    `SELECT p.*, (SELECT COUNT(*) FROM help_dispatches d WHERE d.partner_id = p.id) AS dispatched,
            (SELECT COUNT(*) FROM help_dispatches d WHERE d.partner_id = p.id AND d.status = 'failed') AS failed,
            (SELECT MAX(updated_at) FROM help_dispatches d WHERE d.partner_id = p.id) AS last_dispatch
     FROM partners p ORDER BY p.created_at`,
  ).all<Partner & { dispatched: number; failed: number; last_dispatch: string | null }>();
  return results;
}

/** Parses "SA, ae kw" into ["SA","AE","KW"]; null when a code is not two letters. */
export function parseCountries(text: string): string[] | null {
  const codes = text.toUpperCase().split(/[\s,،]+/).filter(Boolean);
  if (!codes.every((c) => /^[A-Z]{2}$/.test(c))) return null;
  return [...new Set(codes)];
}

export async function createPartner(env: Env, name: string, webhookUrl: string | null, countries: string[]) {
  const key = `afp_${randomToken()}`;
  const secret = `whsec_${randomToken()}`;
  const now = nowIso();
  const id = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO partners (id, name, key_hash, webhook_url, webhook_secret, countries_json, active, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)`,
  )
    .bind(id, name, await sha256Hex(key), webhookUrl, secret, JSON.stringify(countries), now, now)
    .run();
  return { id, key, secret };
}

export async function setPartnerActive(env: Env, id: string, active: boolean) {
  await env.DB.prepare('UPDATE partners SET active = ?, updated_at = ? WHERE id = ?').bind(active ? 1 : 0, nowIso(), id).run();
}

export async function deletePartner(env: Env, id: string) {
  await env.DB.prepare('DELETE FROM partners WHERE id = ?').bind(id).run();
}

/** New API key and webhook secret; the old ones stop working at once. */
export async function rotatePartner(env: Env, id: string) {
  const key = `afp_${randomToken()}`;
  const secret = `whsec_${randomToken()}`;
  const result = await env.DB.prepare('UPDATE partners SET key_hash = ?, webhook_secret = ?, updated_at = ? WHERE id = ?')
    .bind(await sha256Hex(key), secret, nowIso(), id)
    .run();
  return result.meta.changes === 1 ? { key, secret } : null;
}

// --- What a partner receives ---

type HelpRow = {
  id: string;
  patient_id: string;
  status: string;
  message: string | null;
  latitude: number | null;
  longitude: number | null;
  location_accuracy: number | null;
  location_at: string | null;
  acknowledged_by: string | null;
  acknowledged_at: string | null;
  resolved_at: string | null;
  created_at: string;
};

async function helpPayload(env: Env, help: HelpRow) {
  const patient = await env.DB.prepare('SELECT full_name, phone, gender FROM profiles WHERE id = ?')
    .bind(help.patient_id)
    .first<{ full_name: string; phone: string; gender: string | null }>();
  const responder = help.acknowledged_by
    ? await env.DB.prepare('SELECT full_name FROM profiles WHERE id = ?').bind(help.acknowledged_by).first<{ full_name: string }>()
    : null;
  const located = help.latitude !== null && help.longitude !== null;
  return {
    id: help.id,
    status: help.status,
    created_at: help.created_at,
    resolved_at: help.resolved_at,
    message: help.message,
    patient: { name: patient?.full_name ?? '', phone: patient?.phone ?? '', gender: parseGender(patient?.gender ?? null) },
    location: located
      ? { latitude: help.latitude, longitude: help.longitude, accuracy_m: help.location_accuracy, at: help.location_at, map_url: mapUrl(help.latitude!, help.longitude!) }
      : null,
    family_responder: responder ? { name: responder.full_name, at: help.acknowledged_at } : null,
  };
}

async function deliver(env: Env, partner: Partner, event: HelpEvent, help: HelpRow) {
  const now = nowIso();
  if (!partner.webhook_url) {
    // No webhook: the partner polls GET /v1/partner/help/active instead
    await env.DB.prepare(`UPDATE help_dispatches SET status = CASE WHEN status IN ('pending', 'failed') THEN 'sent' ELSE status END,
      sent_at = COALESCE(sent_at, ?), updated_at = ? WHERE help_id = ? AND partner_id = ?`)
      .bind(now, now, help.id, partner.id).run();
    return true;
  }
  const body = JSON.stringify({ event, sent_at: now, help: await helpPayload(env, help) });
  const timestamp = String(Math.floor(Date.now() / 1000));
  let error: string | null = null;
  try {
    const response = await fetch(partner.webhook_url, {
      method: 'POST',
      body,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'Afiati-Webhook/1',
        'X-Afiati-Event': event,
        'X-Afiati-Timestamp': timestamp,
        'X-Afiati-Signature': `sha256=${await sign(partner.webhook_secret, timestamp, body)}`,
      },
      signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) error = `HTTP ${response.status}`;
  } catch (e) {
    error = e instanceof Error ? e.message.slice(0, 200) : 'network error';
  }
  // Only the first event decides whether the request reached the partner; later events are best effort.
  if (event === 'help.created') {
    await env.DB.prepare(
      `UPDATE help_dispatches SET attempts = attempts + 1, last_error = ?, updated_at = ?,
         status = CASE WHEN status IN ('pending', 'failed') THEN ? ELSE status END,
         sent_at = CASE WHEN ? IS NULL THEN COALESCE(sent_at, ?) ELSE sent_at END
       WHERE help_id = ? AND partner_id = ?`,
    )
      .bind(error, now, error ? 'failed' : 'sent', error, now, help.id, partner.id)
      .run();
  } else if (error) {
    console.error(`partner ${partner.id} ${event} failed: ${error}`);
  }
  return !error;
}

/**
 * After a help request changes. help.created picks the partners (the elder agreed, the partner is
 * active and serves the elder's country); later events go to partners that already have it.
 */
export async function dispatchHelp(env: Env, helpId: string, event: HelpEvent) {
  const help = await env.DB.prepare('SELECT * FROM help_requests WHERE id = ?').bind(helpId).first<HelpRow>();
  if (!help) return 0;
  if (event === 'help.created') {
    const consent = await env.DB.prepare('SELECT partner_consent FROM profiles WHERE id = ?').bind(help.patient_id).first<{ partner_consent: number }>();
    if (consent?.partner_consent !== 1) return 0;
    const device = await env.DB.prepare(
      'SELECT country FROM devices WHERE profile_id = ? AND revoked_at IS NULL AND country IS NOT NULL ORDER BY last_seen_at DESC LIMIT 1',
    )
      .bind(help.patient_id)
      .first<{ country: string }>();
    const { results } = await env.DB.prepare('SELECT * FROM partners WHERE active = 1').all<Partner>();
    const chosen = results.filter((p) => {
      const countries = countriesOf(p);
      return countries.length === 0 || (device?.country ? countries.includes(device.country) : false);
    });
    const now = nowIso();
    for (const partner of chosen) {
      await env.DB.prepare(`INSERT OR IGNORE INTO help_dispatches (help_id, partner_id, status, updated_at) VALUES (?, ?, 'pending', ?)`)
        .bind(help.id, partner.id, now)
        .run();
    }
  }
  const { results: targets } = await env.DB.prepare(
    `SELECT p.* FROM help_dispatches d JOIN partners p ON p.id = d.partner_id
     WHERE d.help_id = ? AND p.active = 1 AND d.status <> 'closed'`,
  )
    .bind(help.id)
    .all<Partner>();
  let delivered = 0;
  for (const partner of targets) if (await deliver(env, partner, event, help)) delivered++;
  return delivered;
}

/** Cron: deliveries that failed are tried again every minute, 6 times at most, for an hour. */
export async function retryDispatches(env: Env) {
  const now = Date.now();
  const { results } = await env.DB.prepare(
    `SELECT d.help_id, d.partner_id FROM help_dispatches d JOIN help_requests h ON h.id = d.help_id JOIN partners p ON p.id = d.partner_id
     WHERE d.status IN ('pending', 'failed') AND d.attempts < ? AND d.updated_at <= ? AND h.created_at > ?
       AND h.status IN ('open', 'acknowledged') AND p.active = 1 LIMIT 20`,
  )
    .bind(MAX_ATTEMPTS, new Date(now - RETRY_AFTER_MS).toISOString(), new Date(now - RETRY_FOR_MS).toISOString())
    .all<{ help_id: string; partner_id: string }>();
  for (const row of results) {
    const partner = await env.DB.prepare('SELECT * FROM partners WHERE id = ?').bind(row.partner_id).first<Partner>();
    const help = await env.DB.prepare('SELECT * FROM help_requests WHERE id = ?').bind(row.help_id).first<HelpRow>();
    if (partner && help) await deliver(env, partner, 'help.created', help);
  }
  return results.length;
}

/** What the elder's app shows: which medical services have the request and what they answered. */
export async function dispatchesFor(env: Env, helpId: string) {
  const { results } = await env.DB.prepare(
    `SELECT p.name, d.status, d.eta_minutes, d.acknowledged_at FROM help_dispatches d JOIN partners p ON p.id = d.partner_id
     WHERE d.help_id = ? AND d.status IN ('sent', 'acknowledged', 'arrived') ORDER BY d.updated_at`,
  )
    .bind(helpId)
    .all<{ name: string; status: string; eta_minutes: number | null; acknowledged_at: string | null }>();
  return results;
}

// --- Partner API (/v1/partner/*) ---

async function authenticatePartner(request: Request, env: Env): Promise<Partner> {
  const match = /^Bearer (afp_[A-Za-z0-9_-]{43})$/.exec(request.headers.get('Authorization') ?? '');
  if (!match) throw new HttpError(401, 'unauthorized');
  const partner = await env.DB.prepare('SELECT * FROM partners WHERE key_hash = ? AND active = 1').bind(await sha256Hex(match[1])).first<Partner>();
  if (!partner) throw new HttpError(401, 'unauthorized');
  await enforceRateLimit(env, `partner:${partner.id}`, 600, 60, 'too many requests');
  return partner;
}

async function dispatchedHelp(env: Env, partner: Partner, helpId: string) {
  const help = await env.DB.prepare(
    `SELECT h.* FROM help_requests h JOIN help_dispatches d ON d.help_id = h.id WHERE h.id = ? AND d.partner_id = ?`,
  )
    .bind(helpId, partner.id)
    .first<HelpRow>();
  // Same answer for a request that exists but was not sent to this partner
  if (!help) throw new HttpError(404, 'help_not_found');
  return help;
}

const PARTNER_STATUSES = new Set(['acknowledged', 'arrived', 'closed']);

async function updateStatus(request: Request, env: Env, partner: Partner, helpId: string, waitUntil: Waiter) {
  const help = await dispatchedHelp(env, partner, helpId);
  const body = await readJson(request);
  const status = body.status;
  if (typeof status !== 'string' || !PARTNER_STATUSES.has(status)) {
    throw new HttpError(400, 'invalid_field', 'status must be acknowledged, arrived or closed');
  }
  const eta = Number.isInteger(body.eta_minutes) && Number(body.eta_minutes) >= 0 && Number(body.eta_minutes) <= 600 ? Number(body.eta_minutes) : null;
  const note = typeof body.note === 'string' ? body.note.trim().slice(0, 300) || null : null;
  const before = await env.DB.prepare('SELECT status FROM help_dispatches WHERE help_id = ? AND partner_id = ?')
    .bind(helpId, partner.id)
    .first<{ status: string }>();
  const now = nowIso();
  await env.DB.prepare(
    `UPDATE help_dispatches SET status = ?, eta_minutes = COALESCE(?, eta_minutes), note = COALESCE(?, note),
       acknowledged_at = CASE WHEN ? = 'acknowledged' THEN COALESCE(acknowledged_at, ?) ELSE acknowledged_at END, updated_at = ?
     WHERE help_id = ? AND partner_id = ?`,
  )
    .bind(status, eta, note, status, now, now, helpId, partner.id)
    .run();

  // Tell the elder and the family, once per change
  if (before?.status !== status && status !== 'closed') {
    const patient = await env.DB.prepare('SELECT full_name, gender FROM profiles WHERE id = ?')
      .bind(help.patient_id)
      .first<{ full_name: string; gender: string | null }>();
    const pg = parseGender(patient?.gender ?? null);
    const name = patient?.full_name ?? '';
    const etaAr = eta ? ` خلال ${eta} دقيقة تقريبًا` : '';
    const etaEn = eta ? ` in about ${eta} minutes` : '';
    if (status === 'acknowledged') {
      await notifyUser(env, help.patient_id, {
        type: 'help_partner',
        title: { ar: `${partner.name} في الطريق إليك`, en: `${partner.name} is on the way` },
        body: { ar: `وصل طلبك إلى ${partner.name}${etaAr}. ${g(pg, 'ابق في مكانك واطمئن.', 'ابقي في مكانك واطمئني.')}`, en: `${partner.name} received your request${etaEn}. Stay where you are.` },
        data: { help_id: helpId },
        channelId: 'help-coming',
        priority: 'high',
      }, waitUntil);
    }
    const { results: family } = await env.DB.prepare(`SELECT caregiver_id AS id FROM family_links WHERE patient_id = ? AND status = 'active'`)
      .bind(help.patient_id)
      .all<{ id: string }>();
    for (const member of family) {
      await notifyUser(env, member.id, {
        type: 'help_partner',
        title: status === 'arrived'
          ? { ar: `وصل ${partner.name} إلى ${name}`, en: `${partner.name} reached ${name}` }
          : { ar: `${partner.name} في الطريق إلى ${name}`, en: `${partner.name} is on the way to ${name}` },
        body: status === 'arrived'
          ? { ar: `وصل فريق ${partner.name} إلى ${name}.`, en: `The ${partner.name} team reached ${name}.` }
          : { ar: `استلم ${partner.name} طلب المساعدة${etaAr}.`, en: `${partner.name} took the help request${etaEn}.` },
        data: { help_id: helpId, patient_id: help.patient_id },
        channelId: 'family',
      }, waitUntil);
    }
  }
  return { ok: true, help: await helpPayload(env, await dispatchedHelp(env, partner, helpId)) };
}

export async function partnerRoute(request: Request, env: Env, pathname: string, waitUntil: Waiter): Promise<unknown> {
  const partner = await authenticatePartner(request, env);
  const method = request.method;
  if (method === 'GET' && pathname === '/v1/partner/ping') return { ok: true, partner: { id: partner.id, name: partner.name, countries: countriesOf(partner) } };
  if (method === 'GET' && pathname === '/v1/partner/help/active') {
    const { results } = await env.DB.prepare(
      `SELECT h.* FROM help_requests h JOIN help_dispatches d ON d.help_id = h.id
       WHERE d.partner_id = ? AND d.status <> 'closed' AND h.status IN ('open', 'acknowledged') AND h.created_at > ?
       ORDER BY h.created_at DESC LIMIT 50`,
    )
      .bind(partner.id, new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString())
      .all<HelpRow>();
    return { help: await Promise.all(results.map((row) => helpPayload(env, row))) };
  }
  const match = /^\/v1\/partner\/help\/([0-9a-f-]{36})(\/status)?$/.exec(pathname);
  if (match && method === 'GET' && !match[2]) return { help: await helpPayload(env, await dispatchedHelp(env, partner, match[1])) };
  if (match && method === 'POST' && match[2]) return updateStatus(request, env, partner, match[1], waitUntil);
  throw new HttpError(404, 'not_found');
}

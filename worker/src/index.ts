import { alertLowStock, alertMissedDoses } from './alerts';
import { authenticate, register } from './auth';
import { isFcmToken } from './fcm';
import { acknowledgeHelp, activeHelp, escalateHelp, requestHelp, resolveHelp } from './help';
import {
  createInvitation,
  joinFamily,
  listNotifications,
  markAllNotificationsRead,
  overview,
  patientDay,
  remindPatient,
  respondToRequest,
} from './family';
import { HttpError, errorResponse, json, nowIso, optionalText, readJson, requireText, requireUuid } from './http';
import { pull, push } from './sync';

export default {
  async fetch(request, env, ctx): Promise<Response> {
    try {
      return await route(request, env, (promise) => ctx.waitUntil(promise));
    } catch (error) {
      return errorResponse(error);
    }
  },

  // Cron (every minute, see wrangler.jsonc): unanswered help, missed doses, low stock.
  // Each job runs even if another one failed.
  async scheduled(_controller, env, ctx) {
    const waitUntil = (promise: Promise<unknown>) => ctx.waitUntil(promise);
    const jobs = { help: escalateHelp, missed: alertMissedDoses, stock: alertLowStock };
    for (const [name, job] of Object.entries(jobs)) {
      try {
        await job(env, waitUntil);
      } catch (error) {
        console.error(`scheduled ${name} failed`, error);
      }
    }
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env, waitUntil: (promise: Promise<unknown>) => void): Promise<Response> {
  const { pathname } = new URL(request.url);
  const method = request.method;

  if (method === 'GET' && pathname === '/health') return json({ ok: true });

  if (method === 'POST' && pathname === '/v1/register') return json(await register(request, env), 201);

  // Everything below requires a registered device.
  const auth = await authenticate(request, env);

  if (method === 'GET' && pathname === '/v1/me') {
    const profile = await env.DB.prepare(
      'SELECT id, full_name, phone, email, phone_verified, created_at, updated_at FROM profiles WHERE id = ?',
    )
      .bind(auth.profileId)
      .first();
    return json({ profile, device_id: auth.deviceId });
  }

  if (method === 'PUT' && pathname === '/v1/me') {
    const body = await readJson(request);
    const fullName = requireText(body.full_name, 'full_name', 100);
    const phone = requireText(body.phone, 'phone', 20);
    const email = optionalText(body.email, 'email', 200);
    await env.DB.prepare('UPDATE profiles SET full_name = ?, phone = ?, email = ?, updated_at = ? WHERE id = ?')
      .bind(fullName, phone, email, nowIso(), auth.profileId)
      .run();
    return json({ ok: true });
  }

  if (method === 'PUT' && pathname === '/v1/me/push-token') {
    const body = await readJson(request);
    const token = body.push_token;
    // Native Android sends an FCM token; the Expo app sends an Expo push token.
    if (typeof token !== 'string' || !(/^(Exponent|Expo)PushToken\[.+\]$/.test(token) || isFcmToken(token))) {
      throw new HttpError(400, 'invalid_field', 'push_token must be an FCM or Expo push token');
    }
    // A token belongs to one physical phone; move it if it was registered under another device.
    await env.DB.batch([
      env.DB.prepare('UPDATE devices SET push_token = NULL WHERE push_token = ? AND id <> ?').bind(token, auth.deviceId),
      env.DB.prepare('UPDATE devices SET push_token = ? WHERE id = ?').bind(token, auth.deviceId),
    ]);
    return json({ ok: true });
  }

  if (method === 'POST' && pathname === '/v1/sync/push') return json(await push(request, env, auth));
  if (method === 'GET' && pathname === '/v1/sync/pull') return json(await pull(request, env, auth));

  if (method === 'GET' && pathname === '/v1/family') return json(await overview(env, auth));
  if (method === 'POST' && pathname === '/v1/family/invitations') return json(await createInvitation(env, auth), 201);
  if (method === 'POST' && pathname === '/v1/family/join') return json(await joinFamily(request, env, auth, waitUntil));
  if (method === 'POST' && pathname === '/v1/family/respond') return json(await respondToRequest(request, env, auth, waitUntil));

  const patientRoute = /^\/v1\/family\/patients\/([^/]+)\/(today|remind)$/.exec(pathname);
  if (patientRoute) {
    const patientId = requireUuid(patientRoute[1], 'patient id');
    if (method === 'GET' && patientRoute[2] === 'today') return json(await patientDay(request, env, auth, patientId));
    if (method === 'POST' && patientRoute[2] === 'remind') return json(await remindPatient(env, auth, patientId, waitUntil));
  }

  if (method === 'POST' && pathname === '/v1/help') return json(await requestHelp(request, env, auth, waitUntil), 201);
  if (method === 'GET' && pathname === '/v1/help/active') return json(await activeHelp(env, auth));
  const helpRoute = /^\/v1\/help\/([^/]+)\/(acknowledge|resolve)$/.exec(pathname);
  if (helpRoute && method === 'POST') {
    const helpId = requireUuid(helpRoute[1], 'help id');
    if (helpRoute[2] === 'acknowledge') return json(await acknowledgeHelp(env, auth, helpId, waitUntil));
    return json(await resolveHelp(env, auth, helpId, waitUntil));
  }

  if (method === 'GET' && pathname === '/v1/notifications') return json(await listNotifications(env, auth));
  if (method === 'POST' && pathname === '/v1/notifications/read-all') return json(await markAllNotificationsRead(env, auth));

  throw new HttpError(404, 'not_found');
}

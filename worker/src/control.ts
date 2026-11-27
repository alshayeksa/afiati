// Owner controls: forced update (per platform) and the service switch, changed from /forceupdate.
// The page is a plain HTML form protected by Cloudflare Access (the JWT is verified here too).
// The apps read /v1/app-config on every open; requests from old builds or while the service is off are refused.
import { HttpError, json, nowIso } from './http';
import { enforceRateLimit } from './rateLimit';

type ControlEnv = Env & { ACCESS_AUD?: string; ACCESS_TEAM_DOMAIN?: string; ACCESS_CERTS_URL?: string; CONTROL_PASSWORD?: string };

export type PlatformRule = { enabled: boolean; build: number; enforce: boolean; url: string };
export type Settings = { serviceOn: boolean; ios: PlatformRule; android: PlatformRule };

const DEFAULT_URLS = {
  ios: 'https://testflight.apple.com/',
  android: '',
};

export const defaults = (): Settings => ({
  serviceOn: true,
  ios: { enabled: false, build: 0, enforce: false, url: DEFAULT_URLS.ios },
  android: { enabled: false, build: 0, enforce: false, url: DEFAULT_URLS.android },
});

function normalize(raw: unknown): Settings {
  const value = (raw && typeof raw === 'object' ? raw : {}) as Partial<Settings>;
  const rule = (r: Partial<PlatformRule> | undefined, url: string): PlatformRule => ({
    enabled: Boolean(r?.enabled),
    build: Number.isInteger(r?.build) ? Number(r?.build) : 0,
    enforce: Boolean(r?.enforce),
    url: typeof r?.url === 'string' ? r.url : url,
  });
  return { serviceOn: value.serviceOn !== false, ios: rule(value.ios, DEFAULT_URLS.ios), android: rule(value.android, DEFAULT_URLS.android) };
}

// Cached briefly per isolate; a save updates this isolate at once and the others within seconds.
const TTL_MS = 10_000;
let cache: { at: number; value: Settings } | null = null;

export async function readSettings(env: Env, fresh = false): Promise<Settings> {
  if (!fresh && cache && Date.now() - cache.at < TTL_MS) return cache.value;
  const row = await env.DB.prepare("SELECT value FROM app_settings WHERE key = 'control'").first<{ value: string }>();
  let value = defaults();
  if (row) {
    try {
      value = normalize(JSON.parse(row.value));
    } catch {
      value = defaults();
    }
  }
  cache = { at: Date.now(), value };
  return value;
}

export async function writeSettings(env: Env, value: Settings, by: string) {
  await env.DB.prepare(
    `INSERT INTO app_settings (key, value, updated_at, updated_by) VALUES ('control', ?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at, updated_by = excluded.updated_by`,
  )
    .bind(JSON.stringify(value), nowIso(), by)
    .run();
  cache = { at: Date.now(), value };
}

/** What the apps read on every open. Public, no personal data. */
export function appConfig(settings: Settings) {
  return {
    service_on: settings.serviceOn,
    ios: { min_build: settings.ios.enabled ? settings.ios.build : 0, url: settings.ios.url },
    android: { min_build: settings.android.enabled ? settings.android.build : 0, url: settings.android.url },
  };
}

/**
 * Refuses a request while the service is off, or from a build older than the enforced minimum.
 * The apps send X-Platform and X-App-Build. A build that does not identify itself counts as old
 * once enforcement is on for any platform, since we cannot tell which one it is.
 */
export function gate(request: Request, settings: Settings) {
  if (!settings.serviceOn) throw new HttpError(503, 'service_off', 'The service is paused');
  const platform = request.headers.get('X-Platform');
  const build = Number(request.headers.get('X-App-Build'));
  const known = Number.isInteger(build) && build > 0;
  const rule = platform === 'ios' ? settings.ios : platform === 'android' ? settings.android : null;
  const old = rule
    ? rule.enabled && rule.enforce && (!known || build < rule.build)
    : (settings.ios.enabled && settings.ios.enforce) || (settings.android.enabled && settings.android.enforce);
  if (old) throw new HttpError(426, 'update_required', 'Please update the app');
}

// --- Cloudflare Access ---

let accessKeys: { at: number; keys: (JsonWebKey & { kid?: string })[] } = { at: 0, keys: [] };

/** The signed-in email when the request came through Cloudflare Access, otherwise null. */
export async function verifyAccess(request: Request, env: Env): Promise<string | null> {
  const e = env as ControlEnv;
  if (!e.ACCESS_AUD || !e.ACCESS_TEAM_DOMAIN) return null;
  const token = request.headers.get('Cf-Access-Jwt-Assertion');
  const [h, p, s] = (token || '').split('.');
  if (!h || !p || !s) return null;
  const bytes = (part: string) => Uint8Array.from(atob(part.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
  let header: { alg?: string; kid?: string };
  let payload: { aud?: string | string[]; exp?: number; iss?: string; email?: string };
  try {
    header = JSON.parse(new TextDecoder().decode(bytes(h)));
    payload = JSON.parse(new TextDecoder().decode(bytes(p)));
  } catch {
    return null;
  }
  if (header.alg !== 'RS256') return null;
  if (!([] as string[]).concat(payload.aud ?? []).includes(e.ACCESS_AUD)) return null;
  if (!(Number(payload.exp) > Date.now() / 1000)) return null;
  if (payload.iss !== `https://${e.ACCESS_TEAM_DOMAIN}`) return null;
  if (Date.now() - accessKeys.at > 3_600_000 || !accessKeys.keys.some((k) => k.kid === header.kid)) {
    try {
      const url = e.ACCESS_CERTS_URL || `https://${e.ACCESS_TEAM_DOMAIN}/cdn-cgi/access/certs`;
      accessKeys = { at: Date.now(), keys: ((await (await fetch(url)).json()) as { keys?: [] }).keys ?? [] };
    } catch {
      return null;
    }
  }
  const jwk = accessKeys.keys.find((k) => k.kid === header.kid);
  if (!jwk) return null;
  try {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, bytes(s), new TextEncoder().encode(`${h}.${p}`));
    return valid ? payload.email || 'Access user' : null;
  } catch {
    return null;
  }
}

// --- Pages ---

const escapeHtml = (text: string) =>
  text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string);

const CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

function html(body: string, status = 200, cache = 'no-store') {
  return new Response(
    `<!doctype html>
<html lang="ar" dir="rtl">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>عافيتي</title>
<style>
  body { margin: 0; font: 17px/1.7 -apple-system, system-ui, sans-serif; background: #f2f6fa; color: #10233a; }
  main { max-width: 30rem; margin: 6vh auto; padding: 0 20px; }
  h1 { font-size: 1.6rem; margin: 0 0 .4rem; }
  h2 { font-size: 1.15rem; margin: 2rem 0 .6rem; }
  .card { background: #fff; border: 1px solid #d7e2ee; border-radius: 18px; padding: 1rem 1.1rem; margin: .6rem 0; }
  .state { border-radius: 14px; padding: .7rem .9rem; background: #e8f3ee; margin: .5rem 0; }
  .state.on { background: #fff1e0; }
  .state.off { background: #fde8e8; }
  label { display: block; margin: .6rem 0 .2rem; }
  .switch { display: flex; gap: .5rem; align-items: center; }
  input[type=number], input[type=url] { width: 100%; box-sizing: border-box; padding: .7rem; border-radius: 12px; border: 1px solid #c9d6e3; font: inherit; }
  .btn { display: inline-block; margin-top: .8rem; padding: .75rem 1.4rem; border: 0; border-radius: 14px; background: #1677c9; color: #fff; font: inherit; font-weight: 600; text-decoration: none; cursor: pointer; }
  .btn.danger { background: #d83a3f; }
  .msg { border-radius: 14px; padding: .7rem .9rem; margin: 1rem 0; }
  .msg.ok { background: #e3f6ea; } .msg.err { background: #fde8e8; }
  small { color: #5b6d80; }
  @media (prefers-color-scheme: dark) {
    body { background: #07121c; color: #e6eef6; }
    .card { background: #0d1d2b; border-color: #1d3448; }
    .state { background: #11301f; } .state.on { background: #3a2a10; } .state.off { background: #3a1518; }
    input[type=number], input[type=url] { background: #0a1724; color: inherit; border-color: #24405a; }
    .msg.ok { background: #11301f; } .msg.err { background: #3a1518; }
    small { color: #8ea3b8; }
  }
</style>
<main>${body}</main>
</html>`,
    {
      status,
      headers: {
        'Content-Type': 'text/html; charset=utf-8',
        'Content-Security-Policy': CSP,
        // same-origin (not no-referrer) so the browser sends a real Origin when the form is saved
        'Referrer-Policy': 'same-origin',
        'X-Content-Type-Options': 'nosniff',
        'Cache-Control': cache,
      },
    },
  );
}

function platformSection(title: string, section: 'ios' | 'android', rule: PlatformRule, hint: string) {
  const status = rule.enabled
    ? `<div class="state on">التحديث الإلزامي <b>مفعّل</b>: كل نسخة رقمها أقل من <b>${rule.build}</b> تظهر لها شاشة التحديث.${rule.enforce ? ' <b>والخادم يرفض اتصالها أيضًا.</b>' : ''}</div>`
    : `<div class="state">التحديث الإلزامي <b>متوقف</b>.</div>`;
  return `<h2>${title}</h2>
  <div class="card">
  ${status}
  <form method="post" action="/forceupdate">
    <input type="hidden" name="section" value="${section}">
    <label class="switch"><input type="checkbox" name="enabled" value="1"${rule.enabled ? ' checked' : ''}> تفعيل التحديث الإلزامي</label>
    <label for="build-${section}">${hint}</label>
    <input id="build-${section}" name="build" type="number" inputmode="numeric" min="1" max="100000" value="${rule.build || ''}" required>
    <label for="url-${section}">رابط التحديث (المتجر أو TestFlight)</label>
    <input id="url-${section}" name="url" type="url" dir="ltr" maxlength="300" value="${escapeHtml(rule.url)}">
    <label class="switch"><input type="checkbox" name="enforce" value="1"${rule.enforce ? ' checked' : ''}> ارفض النسخ القديمة من الخادم أيضًا</label>
    <small>مع هذا الخيار لا تتصل النسخة الأقدم بالخادم إطلاقًا، حتى لو تجاوزت شاشة التحديث. النسخ التي لا ترسل رقمها (المنصّبة قبل إضافة هذه الصفحة) تُعامل كقديمة، فلا تفعّله حتى يحدّث الناس.</small>
    <br><button class="btn" type="submit">حفظ</button>
  </form>
  </div>`;
}

function controlPage(settings: Settings, user: string, message = '', ok = true) {
  const service = settings.serviceOn
    ? `<div class="state">الخدمة <b>تعمل</b>.</div>
    <form method="post" action="/forceupdate">
      <input type="hidden" name="section" value="service"><input type="hidden" name="serviceOn" value="0">
      <button class="btn danger" type="submit">إيقاف الخدمة</button>
    </form>`
    : `<div class="state off">الخدمة <b>متوقفة</b>: التطبيقات تعرض «الخدمة متوقفة مؤقتًا»، والخادم يرفض الطلبات، وتنبيهات العائلة المجدولة متوقفة. تذكيرات الأدوية المحلية في الجوالات تبقى تعمل.</div>
    <form method="post" action="/forceupdate">
      <input type="hidden" name="section" value="service"><input type="hidden" name="serviceOn" value="1">
      <button class="btn" type="submit">تشغيل الخدمة</button>
    </form>`;
  return html(`<h1>التحكم في عافيتي</h1>
  <small>مسجّل الدخول: <span dir="ltr">${escapeHtml(user)}</span></small>
  ${message ? `<div class="msg ${ok ? 'ok' : 'err'}">${escapeHtml(message)}</div>` : ''}
  <h2>حالة الخدمة</h2>
  <div class="card">${service}</div>
  ${platformSection('التحديث الإلزامي: الآيفون', 'ios', settings.ios, 'أقل رقم بناء مسموح (الرقم بين القوسين في TestFlight أو «عن التطبيق»)')}
  ${platformSection('التحديث الإلزامي: أندرويد', 'android', settings.android, 'أقل رقم بناء مسموح (الرقم بين القوسين في «عن التطبيق»)')}`);
}

const sha256 = async (text: string) => new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)));

type Login = { user: string } | { status: 401 | 429 } | null;

/**
 * The owner password (secret CONTROL_PASSWORD), asked by the browser (HTTP Basic over HTTPS).
 * Wrong attempts are limited per IP. null when no password is set.
 */
async function passwordLogin(request: Request, env: Env): Promise<Login> {
  const secret = (env as ControlEnv).CONTROL_PASSWORD;
  if (!secret || secret.length < 16) return null;
  const header = request.headers.get('Authorization') || '';
  if (!header.startsWith('Basic ')) return { status: 401 };
  let given = '';
  try {
    const decoded = new TextDecoder().decode(Uint8Array.from(atob(header.slice(6)), (c) => c.charCodeAt(0)));
    given = decoded.slice(decoded.indexOf(':') + 1);
  } catch {
    given = '';
  }
  const [a, b] = await Promise.all([sha256(given), sha256(secret)]);
  if (crypto.subtle.timingSafeEqual(a, b)) return { user: 'owner (password)' };
  try {
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    await enforceRateLimit(env, `control:${ip}`, 10, 3600, 'Too many attempts');
  } catch {
    return { status: 429 };
  }
  return { status: 401 };
}

/** /forceupdate: GET shows the controls, POST saves them. Cloudflare Access, or the owner password. */
export async function controlRoute(request: Request, env: Env): Promise<Response> {
  let user = await verifyAccess(request, env);
  if (!user) {
    const login = await passwordLogin(request, env);
    if (login && 'user' in login) {
      user = login.user;
    } else if (login?.status === 429) {
      return html(`<h1>محاولات كثيرة</h1><p>انتظر ساعة ثم حاول مرة أخرى.</p>`, 429);
    } else if (login?.status === 401) {
      const response = html(`<h1>صفحة محمية</h1><p>أدخل كلمة مرور صفحة التحكم.</p>`, 401);
      response.headers.set('WWW-Authenticate', 'Basic realm="Afiati control", charset="UTF-8"');
      return response;
    } else {
      return html(`<h1>صفحة محمية</h1><p>هذه الصفحة محمية بـ Cloudflare Access. سجّل الدخول بحساب مصرّح له.</p>`, 403);
    }
  }
  const url = new URL(request.url);
  const current = await readSettings(env, true);
  if (request.method === 'GET') return controlPage(current, user);
  // The save must come from this page (a form on another site cannot use your sign-in)
  if (request.headers.get('Origin') !== url.origin) {
    return controlPage(current, user, 'رُفض الحفظ: الطلب لم يأتِ من هذه الصفحة. حدّث الصفحة وحاول مرة أخرى.', false);
  }
  const form = await request.formData();
  const section = form.get('section');
  if (section === 'service') {
    const next = { ...current, serviceOn: form.get('serviceOn') === '1' };
    await writeSettings(env, next, user);
    return controlPage(next, user, next.serviceOn ? 'الخدمة تعمل الآن.' : 'أُوقفت الخدمة. المستخدمون يرون شاشة التوقف.');
  }
  if (section === 'ios' || section === 'android') {
    const build = Number(form.get('build'));
    if (!Number.isInteger(build) || build < 1 || build > 100_000) return controlPage(current, user, 'رقم البناء غير صالح.', false);
    const link = String(form.get('url') ?? '').trim();
    if (link && !/^https:\/\/[^\s"'<>]{3,300}$/.test(link)) return controlPage(current, user, 'الرابط يجب أن يبدأ بـ https://', false);
    const rule: PlatformRule = { enabled: form.get('enabled') === '1', build, enforce: form.get('enforce') === '1', url: link };
    const next = { ...current, [section]: rule };
    await writeSettings(env, next, user);
    return controlPage(next, user, 'تم الحفظ، والتغيير يسري الآن.');
  }
  return controlPage(current, user, 'طلب غير معروف.', false);
}

/** /update: JSON for the apps (Accept: application/json), or a small page with the store link. */
export async function updateRoute(request: Request, env: Env): Promise<Response> {
  const settings = await readSettings(env);
  if ((request.headers.get('Accept') || '').includes('application/json')) return json(appConfig(settings));
  const android = /Android/i.test(request.headers.get('User-Agent') || '');
  const link = (android ? settings.android.url : settings.ios.url) || '';
  return html(
    `<h1>يتوفر تحديث لتطبيق عافيتي</h1><p>حدّث التطبيق ليستمر في العمل.</p>${link ? `<a class="btn" href="${escapeHtml(link)}">تحديث</a>` : ''}`,
    200,
    'no-store',
  );
}

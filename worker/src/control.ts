// Owner controls on /forceupdate: usage statistics, the service switch, forced update (per platform),
// the server address the apps use, and medical partners.
// The page is a plain HTML form protected by Cloudflare Access (the JWT is verified here too).
// The apps read /v1/app-config on every open; requests from old builds or while the service is off are refused.
import { HttpError, json, nowIso } from './http';
import { enforceRateLimit } from './rateLimit';
import { createPartner, deletePartner, listPartners, parseCountries, rotatePartner, setPartnerActive } from './partners';
import { readStats } from './stats';
import { controlPage, escapeHtml, simplePage, type Reveal } from './controlPage';

type ControlEnv = Env & { ACCESS_AUD?: string; ACCESS_TEAM_DOMAIN?: string; ACCESS_CERTS_URL?: string; CONTROL_PASSWORD?: string };

export type PlatformRule = { enabled: boolean; build: number; enforce: boolean; url: string };
/** apiUrl: the server the apps talk to ("" = the address built into the app). */
export type Settings = { serviceOn: boolean; ios: PlatformRule; android: PlatformRule; apiUrl: string };

const DEFAULT_URLS = {
  ios: 'https://testflight.apple.com/',
  android: '',
};

export const defaults = (): Settings => ({
  serviceOn: true,
  ios: { enabled: false, build: 0, enforce: false, url: DEFAULT_URLS.ios },
  android: { enabled: false, build: 0, enforce: false, url: DEFAULT_URLS.android },
  apiUrl: '',
});

/** An https origin without a path ("https://api.example.com"), or null. */
export function validApiUrl(text: string): string | null {
  try {
    const url = new URL(text.trim());
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) return null;
    if (url.pathname !== '/' && url.pathname !== '') return null;
    return url.origin;
  } catch {
    return null;
  }
}

function normalize(raw: unknown): Settings {
  const value = (raw && typeof raw === 'object' ? raw : {}) as Partial<Settings>;
  const rule = (r: Partial<PlatformRule> | undefined, url: string): PlatformRule => ({
    enabled: Boolean(r?.enabled),
    build: Number.isInteger(r?.build) ? Number(r?.build) : 0,
    enforce: Boolean(r?.enforce),
    url: typeof r?.url === 'string' ? r.url : url,
  });
  return {
    serviceOn: value.serviceOn !== false,
    ios: rule(value.ios, DEFAULT_URLS.ios),
    android: rule(value.android, DEFAULT_URLS.android),
    apiUrl: typeof value.apiUrl === 'string' ? validApiUrl(value.apiUrl) ?? '' : '',
  };
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

/**
 * What the apps read on every open, from https://afiati.alshaye.ai/v1/app-config (fixed in the apps),
 * so the server can move without a new app build. Public, no personal data.
 */
export function appConfig(settings: Settings) {
  return {
    service_on: settings.serviceOn,
    // null: keep the address built into the app
    api_url: settings.apiUrl || null,
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

// --- Owner password ---

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

/** Where the apps read their settings. Fixed in the apps; served by this worker on the owner's domain. */
export const CONFIG_URL = 'https://afiati.alshaye.ai/v1/app-config';

/** The address the apps fall back to (built into them). Saving it needs no health check. */
const BUILT_IN_API = 'https://afiati-api.alshayeksa.workers.dev';

async function answersHealth(origin: string) {
  try {
    const response = await fetch(`${origin}/health`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(6000) });
    return response.ok && ((await response.json()) as { ok?: unknown }).ok === true;
  } catch {
    return false;
  }
}

/** /forceupdate: GET shows the page, POST saves one section. Cloudflare Access, or the owner password. */
export async function controlRoute(request: Request, env: Env): Promise<Response> {
  let user = await verifyAccess(request, env);
  if (!user) {
    const login = await passwordLogin(request, env);
    if (login && 'user' in login) {
      user = login.user;
    } else if (login?.status === 429) {
      return simplePage(`<h1>محاولات كثيرة</h1><p>انتظر ساعة ثم حاول مرة أخرى.</p>`, 429);
    } else if (login?.status === 401) {
      const response = simplePage(`<h1>صفحة محمية</h1><p>أدخل كلمة مرور صفحة التحكم.</p>`, 401);
      response.headers.set('WWW-Authenticate', 'Basic realm="Afiati control", charset="UTF-8"');
      return response;
    } else {
      return simplePage(`<h1>صفحة محمية</h1><p>هذه الصفحة محمية بـ Cloudflare Access. سجّل الدخول بحساب مصرّح له.</p>`, 403);
    }
  }
  const signedIn = user;
  const url = new URL(request.url);
  let settings = await readSettings(env, true);
  const page = async (message = '', ok = true, reveal: Reveal | null = null) =>
    controlPage({ settings, stats: await readStats(env), partners: await listPartners(env), user: signedIn, configUrl: CONFIG_URL, message, ok, reveal });

  if (request.method === 'GET') return page();
  // The save must come from this page (a form on another site cannot use your sign-in)
  if (request.headers.get('Origin') !== url.origin) {
    return page('رُفض الحفظ: الطلب لم يأتِ من هذه الصفحة. حدّث الصفحة وحاول مرة أخرى.', false);
  }
  const form = await request.formData();
  const field = (name: string) => String(form.get(name) ?? '').trim();
  const section = field('section');

  if (section === 'service') {
    const on = field('serviceOn') === '1';
    if (!on && field('confirm') !== '1') return page('لم يُوقف شيء: ضع علامة التأكيد أولًا.', false);
    settings = { ...settings, serviceOn: on };
    await writeSettings(env, settings, signedIn);
    return page(on ? 'الخدمة تعمل الآن.' : 'أُوقفت الخدمة. المستخدمون يرون شاشة التوقف.');
  }
  if (section === 'ios' || section === 'android') {
    const build = Number(field('build'));
    if (!Number.isInteger(build) || build < 1 || build > 100_000) return page('رقم البناء غير صالح.', false);
    const link = field('url');
    if (link && !/^https:\/\/[^\s"'<>]{3,300}$/.test(link)) return page('الرابط يجب أن يبدأ بـ https://', false);
    const rule: PlatformRule = { enabled: field('enabled') === '1', build, enforce: field('enforce') === '1', url: link };
    settings = { ...settings, [section]: rule };
    await writeSettings(env, settings, signedIn);
    return page('تم الحفظ، والتغيير يسري الآن.');
  }
  if (section === 'server') {
    const text = field('apiUrl');
    let apiUrl = '';
    if (text) {
      const origin = validApiUrl(text);
      if (!origin) return page('عنوان الخادم يجب أن يبدأ بـ https:// وبلا مسار، مثل https://api.example.com', false);
      // This worker's own addresses are known to work (a worker cannot always fetch itself)
      const own = origin === url.origin || origin === BUILT_IN_API;
      if (!own && !(await answersHealth(origin))) {
        return page(`لم يُحفظ: ${origin}/health لم يُجب بـ {"ok":true}. تأكد أن الخادم الجديد منشور ويعمل.`, false);
      }
      apiUrl = origin === BUILT_IN_API ? '' : origin;
    }
    settings = { ...settings, apiUrl };
    await writeSettings(env, settings, signedIn);
    return page(apiUrl ? `تم الحفظ. التطبيقات تنتقل إلى ${apiUrl} عند فتحها التالي.` : 'تم الحفظ. التطبيقات تستخدم العنوان الافتراضي.');
  }
  if (section === 'partner-add') {
    const name = field('name');
    if (!name || name.length > 80) return page('اكتب اسم الجهة (80 حرفًا على الأكثر).', false);
    const webhook = field('webhook');
    // Local tests only (ALLOW_LOCAL_WEBHOOKS=1 in wrangler dev) may use a plain-http receiver on this machine
    const localTest = (env as ControlEnv & { ALLOW_LOCAL_WEBHOOKS?: string }).ALLOW_LOCAL_WEBHOOKS === '1' && /^http:\/\/127\.0\.0\.1:\d+\//.test(webhook);
    if (webhook && !localTest && !/^https:\/\/[^\s"'<>]{3,300}$/.test(webhook)) return page('رابط الاستقبال يجب أن يبدأ بـ https://', false);
    const countries = parseCountries(field('countries'));
    if (!countries) return page('رموز الدول من حرفين، مثل SA, AE', false);
    const created = await createPartner(env, name, webhook || null, countries);
    return page(`أُضيفت «${name}».`, true, { name, key: created.key, secret: created.secret });
  }
  const id = field('id');
  if (section === 'partner-toggle' && id) {
    const active = field('active') === '1';
    await setPartnerActive(env, id, active);
    return page(active ? 'فُعّلت الجهة.' : 'أُوقفت الجهة: لن تصلها طلبات ولا يعمل مفتاحها.');
  }
  if (section === 'partner-rotate' && id) {
    const partner = (await listPartners(env)).find((p) => p.id === id);
    const keys = partner ? await rotatePartner(env, id) : null;
    if (!partner || !keys) return page('الجهة غير موجودة.', false);
    return page('أُنشئت مفاتيح جديدة، والقديمة لم تعد تعمل.', true, { name: partner.name, ...keys });
  }
  if (section === 'partner-delete' && id) {
    if (field('confirm') !== '1') return page('لم يُحذف شيء: ضع علامة التأكيد أولًا.', false);
    await deletePartner(env, id);
    return page('حُذفت الجهة.');
  }
  return page('طلب غير معروف.', false);
}

/** /update: JSON for the apps (Accept: application/json), or a small page with the store link. */
export async function updateRoute(request: Request, env: Env): Promise<Response> {
  const settings = await readSettings(env);
  if ((request.headers.get('Accept') || '').includes('application/json')) return json(appConfig(settings));
  const android = /Android/i.test(request.headers.get('User-Agent') || '');
  const link = (android ? settings.android.url : settings.ios.url) || '';
  return simplePage(`<h1>يتوفر تحديث لتطبيق عافيتي</h1><p>حدّث التطبيق ليستمر في العمل.</p>${link ? `<a class="btn" href="${escapeHtml(link)}">تحديث</a>` : ''}`);
}

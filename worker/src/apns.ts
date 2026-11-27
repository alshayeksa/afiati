// Apple Push Notification service (APNs) for the native iOS app.
// The iPhone sends its device token as "apns:<hex>" (App Store / TestFlight) or "apns-dev:<hex>" (Xcode builds).
// Secrets: APNS_KEY (the .p8 auth key text), APNS_KEY_ID, APNS_TEAM_ID. Optional: APNS_BUNDLE_ID, APNS_URL (tests).
// Without them, notifications are still stored in notifications_log; only the phone push is skipped.

type ApnsEnv = Env & { APNS_KEY?: string; APNS_KEY_ID?: string; APNS_TEAM_ID?: string; APNS_BUNDLE_ID?: string; APNS_URL?: string };

export const DEFAULT_BUNDLE_ID = 'ai.alshaye.afiati';

/** "apns:" + hex token (production), or "apns-dev:" for the sandbox */
export const isApnsToken = (token: string) => /^apns(-dev)?:[0-9a-f]{64,200}$/.test(token);

export const apnsConfigured = (env: Env) => {
  const e = env as ApnsEnv;
  return Boolean(e.APNS_KEY && e.APNS_KEY_ID && e.APNS_TEAM_ID);
};

function base64Url(data: string | ArrayBuffer) {
  const bytes = typeof data === 'string' ? new TextEncoder().encode(data) : new Uint8Array(data);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

let cached: { token: string; at: number } | null = null;

/** Provider token (ES256 JWT). Apple accepts one for an hour; renew after 40 minutes. */
async function providerToken(env: ApnsEnv) {
  if (cached && Date.now() - cached.at < 40 * 60 * 1000) return cached.token;
  const der = Uint8Array.from(atob(env.APNS_KEY!.replace(/-----(BEGIN|END) PRIVATE KEY-----|\s/g, '')), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']);
  const unsigned = `${base64Url(JSON.stringify({ alg: 'ES256', kid: env.APNS_KEY_ID }))}.${base64Url(
    JSON.stringify({ iss: env.APNS_TEAM_ID, iat: Math.floor(Date.now() / 1000) }),
  )}`;
  const signature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key, new TextEncoder().encode(unsigned));
  cached = { token: `${unsigned}.${base64Url(signature)}`, at: Date.now() };
  return cached.token;
}

export type ApnsMessage = {
  title: string;
  body: string;
  /** Extra values for the app (help_id, phone, map_url, patient_id...). */
  data: Record<string, string>;
  type: string;
  urgent: boolean;
};

/** Notification category registered by the app: decides the buttons under the notification. */
export function categoryFor(type: string, data: Record<string, string>) {
  if (type === 'help_request') return 'HELP_REQUEST';
  if (type.startsWith('help') && data.map_url) return 'HELP_MAP';
  return undefined;
}

/** The JSON body sent to Apple. Exported for tests. */
export function apnsPayload(message: ApnsMessage) {
  const category = categoryFor(message.type, message.data);
  const aps: Record<string, unknown> = {
    alert: { title: message.title, body: message.body },
    // «X is coming to you» plays the crystal chime bundled in the app (coming.wav); iOS uses the default if it is missing
    sound: message.type === 'help_ack' ? 'coming.wav' : 'default',
    // Messages about one help request replace each other on the lock screen
    'thread-id': message.data.help_id ? `help-${message.data.help_id}` : message.type,
  };
  if (category) aps.category = category;
  // A relative asking for help breaks through Focus (Time Sensitive)
  if (message.urgent) aps['interruption-level'] = 'time-sensitive';
  return { aps, ...message.data, type: message.type };
}

/** Sends to one device. Returns 'sent', 'gone' (token no longer valid) or 'failed'. */
export async function sendApns(env: Env, token: string, message: ApnsMessage): Promise<'sent' | 'gone' | 'failed'> {
  const e = env as ApnsEnv;
  if (!apnsConfigured(env)) return 'failed';
  const sandbox = token.startsWith('apns-dev:');
  const deviceToken = token.slice(token.indexOf(':') + 1);
  const host = e.APNS_URL || (sandbox ? 'https://api.sandbox.push.apple.com' : 'https://api.push.apple.com');
  try {
    const response = await fetch(`${host}/3/device/${deviceToken}`, {
      method: 'POST',
      headers: {
        authorization: `bearer ${await providerToken(e)}`,
        'apns-topic': e.APNS_BUNDLE_ID || DEFAULT_BUNDLE_ID,
        'apns-push-type': 'alert',
        'apns-priority': '10',
        'apns-expiration': String(Math.floor(Date.now() / 1000) + (message.urgent ? 3600 : 86400)),
        ...(message.data.help_id ? { 'apns-collapse-id': `help-${message.data.help_id}`.slice(0, 64) } : {}),
        'content-type': 'application/json',
      },
      body: JSON.stringify(apnsPayload(message)),
    });
    if (response.ok) return 'sent';
    const reason = ((await response.json().catch(() => ({}))) as { reason?: string }).reason ?? '';
    if (response.status === 410 || reason === 'BadDeviceToken' || reason === 'Unregistered' || reason === 'DeviceTokenNotForTopic') return 'gone';
    if (reason === 'ExpiredProviderToken' || reason === 'InvalidProviderToken') cached = null;
    console.error('apns send failed', response.status, reason);
    return 'failed';
  } catch (error) {
    console.error('apns error', error);
    return 'failed';
  }
}

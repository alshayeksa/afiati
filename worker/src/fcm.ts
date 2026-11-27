// Firebase Cloud Messaging (HTTP v1) for the native Android app.
// Secret FCM_SERVICE_ACCOUNT: the Firebase service account JSON (as is, or base64).
// Without it, notifications are still stored in notifications_log; only the phone push is skipped.

type Account = { client_email: string; private_key: string; project_id: string };
type Access = { token: string; project: string; expiresAt: number };

let cached: Access | null = null;

/** A device token from FCM (Expo tokens look like ExponentPushToken[...]). */
export const isFcmToken = (token: string) => /^[\w:-]{100,4096}$/.test(token);

export const fcmConfigured = (env: Env) => Boolean((env as FcmEnv).FCM_SERVICE_ACCOUNT);

type FcmEnv = Env & { FCM_SERVICE_ACCOUNT?: string; FCM_URL?: string };

function base64Url(data: string | ArrayBuffer) {
  const bytes = typeof data === 'string' ? new TextEncoder().encode(data) : new Uint8Array(data);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function parseAccount(raw: string): Account {
  const text = raw.trim().startsWith('{') ? raw.trim() : new TextDecoder().decode(Uint8Array.from(atob(raw.trim()), (c) => c.charCodeAt(0)));
  const account = JSON.parse(text) as Account;
  if (!account.client_email || !account.private_key || !account.project_id) throw new Error('invalid service account');
  return account;
}

/** Signs the OAuth assertion with the service account key (RS256). */
export async function signedAssertion(account: Account, now = Math.floor(Date.now() / 1000)) {
  const der = Uint8Array.from(atob(account.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----|\s/g, '')), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const claims = {
    iss: account.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${base64Url(JSON.stringify(claims))}`;
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64Url(signature)}`;
}

async function access(env: FcmEnv): Promise<Access> {
  // Google access tokens last an hour; renew a little early.
  if (cached && Date.now() < cached.expiresAt) return cached;
  const account = parseAccount(env.FCM_SERVICE_ACCOUNT!);
  const response = await fetch(env.FCM_URL ? `${env.FCM_URL}/token` : 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: await signedAssertion(account) }),
  });
  const body = (await response.json().catch(() => ({}))) as { access_token?: string };
  if (!body.access_token) throw new Error(`fcm oauth failed (${response.status})`);
  cached = { token: body.access_token, project: account.project_id, expiresAt: Date.now() + 45 * 60 * 1000 };
  return cached;
}

export type FcmMessage = {
  title: string;
  body: string;
  /** Values must be strings in FCM. */
  data: Record<string, string>;
  channelId: string;
  urgent: boolean;
};

/** Sends to one device. Returns 'sent', 'gone' (token no longer valid) or 'failed'. */
export async function sendFcm(env: Env, token: string, message: FcmMessage): Promise<'sent' | 'gone' | 'failed'> {
  const fcmEnv = env as FcmEnv;
  if (!fcmEnv.FCM_SERVICE_ACCOUNT) return 'failed';
  try {
    const auth = await access(fcmEnv);
    const response = await fetch(`${fcmEnv.FCM_URL || 'https://fcm.googleapis.com'}/v1/projects/${auth.project}/messages:send`, {
      method: 'POST',
      headers: { authorization: `Bearer ${auth.token}`, 'content-type': 'application/json' },
      body: JSON.stringify({
        message: {
          token,
          // Data for the app; the app shows the notification itself so it can use its own channels.
          data: { ...message.data, title: message.title, body: message.body, channel: message.channelId },
          android: { priority: 'HIGH', ttl: message.urgent ? '3600s' : '86400s' },
        },
      }),
    });
    if (response.ok) return 'sent';
    const status = ((await response.json().catch(() => ({}))) as { error?: { status?: string } }).error?.status ?? '';
    if (response.status === 404 || status === 'UNREGISTERED' || status === 'NOT_FOUND') return 'gone';
    if (response.status === 401) cached = null;
    console.error('fcm send failed', response.status, status);
    return 'failed';
  } catch (error) {
    console.error('fcm error', error);
    return 'failed';
  }
}

// Notifications: every message is stored in notifications_log (shown in the app's
// notifications screen) and pushed to the user's devices: Firebase (FCM) for the native
// Android app, and the Expo Push service for the older Expo app.
import { isFcmToken, sendFcm } from './fcm';
import { nowIso } from './http';

/** Text for a reader: one string, or Arabic and English (chosen by the reader's interface language). */
export type Text = string | { ar: string; en: string };

export type Notification = {
  type: string;
  title: Text;
  body: Text;
  data?: Record<string, unknown>;
  /** Android notification channel created by the app. */
  channelId?: string;
  priority?: 'default' | 'high';
};

export type Language = 'ar' | 'en';

/** A notification with its text already in the reader's language */
type Resolved = Omit<Notification, 'title' | 'body'> & { title: string; body: string };

export const pick = (text: Text, language: Language) => (typeof text === 'string' ? text : text[language]);

export async function languageOf(env: Env, userId: string): Promise<Language> {
  const row = await env.DB.prepare(
    'SELECT language FROM devices WHERE profile_id = ? AND revoked_at IS NULL AND language IS NOT NULL ORDER BY last_seen_at DESC LIMIT 1',
  )
    .bind(userId)
    .first<{ language: string }>();
  return row?.language === 'en' ? 'en' : 'ar';
}

const EXPO_PUSH_URL = 'https://exp.host/--/api/v2/push/send';

type ExpoTicket = { status: 'ok' | 'error'; details?: { error?: string } };

export type Waiter = (promise: Promise<unknown>) => void;

/**
 * Logs the notification now; the network push to Expo is handed to `waitUntil`
 * (ctx.waitUntil) so the HTTP response is not held up by it.
 */
export async function notifyUser(
  env: Env,
  userId: string,
  message: Notification,
  waitUntil: Waiter,
  { log = true }: { log?: boolean } = {},
): Promise<void> {
  const data = { ...message.data, type: message.type };
  // The reader's language: the interface language of their most recently seen phone (Arabic by default).
  const language = await languageOf(env, userId);
  const notification: Resolved = { ...message, title: pick(message.title, language), body: pick(message.body, language) };
  if (log) await env.DB.prepare(
    `INSERT INTO notifications_log (id, user_id, type, title, body, data_json, read, created_at)
     VALUES (?, ?, ?, ?, ?, ?, 0, ?)`,
  )
    .bind(crypto.randomUUID(), userId, notification.type, notification.title, notification.body, JSON.stringify(data), nowIso())
    .run();

  const { results: devices } = await env.DB.prepare(
    'SELECT id, push_token FROM devices WHERE profile_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL',
  )
    .bind(userId)
    .all<{ id: string; push_token: string }>();
  const fcm = devices.filter((device) => isFcmToken(device.push_token));
  const expo = devices.filter((device) => !isFcmToken(device.push_token));
  if (expo.length) waitUntil(sendExpoPush(env, expo, { ...notification, data }));
  if (fcm.length) waitUntil(sendFcmPush(env, fcm, { ...notification, data }));
}

async function sendFcmPush(env: Env, devices: { id: string; push_token: string }[], notification: Resolved) {
  // FCM data values must be strings.
  const data = Object.fromEntries(
    Object.entries(notification.data ?? {}).map(([key, value]) => [key, typeof value === 'string' ? value : JSON.stringify(value)]),
  );
  const results = await Promise.all(
    devices.map((device) =>
      sendFcm(env, device.push_token, {
        title: notification.title,
        body: notification.body,
        data,
        channelId: notification.channelId ?? 'family',
        urgent: notification.priority === 'high',
      }),
    ),
  );
  // Forget tokens of uninstalled apps.
  const stale = devices.filter((_, index) => results[index] === 'gone').map((device) => device.id);
  if (stale.length) {
    await env.DB.batch(stale.map((id) => env.DB.prepare('UPDATE devices SET push_token = NULL WHERE id = ?').bind(id)));
  }
}

async function sendExpoPush(env: Env, devices: { id: string; push_token: string }[], notification: Resolved) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' };
  const accessToken = (env as Env & { EXPO_ACCESS_TOKEN?: string }).EXPO_ACCESS_TOKEN;
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const messages = devices.map((device) => ({
    to: device.push_token,
    title: notification.title,
    body: notification.body,
    data: notification.data,
    sound: 'default',
    priority: notification.priority ?? 'default',
    channelId: notification.channelId,
  }));

  try {
    const response = await fetch(EXPO_PUSH_URL, { method: 'POST', headers, body: JSON.stringify(messages) });
    if (!response.ok) {
      console.error('expo push failed', response.status, await response.text());
      return;
    }
    const { data: tickets } = (await response.json()) as { data: ExpoTicket[] };
    // Tickets come back in message order. Forget tokens of uninstalled apps.
    const stale = tickets
      .map((ticket, index) => (ticket.details?.error === 'DeviceNotRegistered' ? devices[index].id : null))
      .filter((id): id is string => id !== null);
    if (stale.length) {
      await env.DB.batch(stale.map((id) => env.DB.prepare('UPDATE devices SET push_token = NULL WHERE id = ?').bind(id)));
    }
  } catch (error) {
    console.error('expo push error', error);
  }
}

// Usage statistics for the owner's control page. Counts only: no names, phones or IP addresses.
// A "user" is a profile; "active" means a device used the app that day (device_days, written by auth.ts).

export type Stats = {
  users: number;
  newUsers7: number;
  newUsers30: number;
  devices: number;
  platforms: { ios: number; android: number; other: number };
  active1: number;
  active7: number;
  active30: number;
  daily: { day: string; active: number; joined: number }[];
  countries: { code: string | null; devices: number }[];
  medicines: number;
  dosesTaken7: number;
  help30: number;
  familyLinks: number;
};

const dayIso = (offsetDays: number) => new Date(Date.now() - offsetDays * 86_400_000).toISOString().slice(0, 10);

export async function readStats(env: Env, days = 30): Promise<Stats> {
  const today = dayIso(0);
  const from = dayIso(days - 1);
  const since7 = new Date(Date.now() - 7 * 86_400_000).toISOString();
  const since30 = new Date(Date.now() - 30 * 86_400_000).toISOString();
  const first = <T>(sql: string, ...bind: unknown[]) => env.DB.prepare(sql).bind(...bind).first<T>();

  const [totals, platforms, active, daily, joined, countries, usage] = await Promise.all([
    first<{ users: number; new7: number; new30: number }>(
      'SELECT COUNT(*) AS users, SUM(created_at >= ?) AS new7, SUM(created_at >= ?) AS new30 FROM profiles', since7, since30),
    env.DB.prepare(`SELECT COALESCE(platform, 'other') AS platform, COUNT(*) AS n FROM devices WHERE revoked_at IS NULL GROUP BY 1`)
      .all<{ platform: string; n: number }>(),
    first<{ a1: number; a7: number; a30: number }>(
      `SELECT COUNT(DISTINCT CASE WHEN day = ? THEN device_id END) AS a1,
              COUNT(DISTINCT CASE WHEN day >= ? THEN device_id END) AS a7,
              COUNT(DISTINCT device_id) AS a30
       FROM device_days WHERE day >= ?`, today, dayIso(6), dayIso(29)),
    env.DB.prepare('SELECT day, COUNT(*) AS n FROM device_days WHERE day >= ? GROUP BY day').bind(from).all<{ day: string; n: number }>(),
    env.DB.prepare('SELECT substr(created_at, 1, 10) AS day, COUNT(*) AS n FROM profiles WHERE created_at >= ? GROUP BY 1').bind(from).all<{ day: string; n: number }>(),
    env.DB.prepare('SELECT country AS code, COUNT(*) AS devices FROM devices WHERE revoked_at IS NULL GROUP BY country ORDER BY devices DESC, country')
      .all<{ code: string | null; devices: number }>(),
    first<{ medicines: number; doses: number; help: number; links: number }>(
      `SELECT (SELECT COUNT(*) FROM medications WHERE is_active = 1 AND deleted_at IS NULL) AS medicines,
              (SELECT COUNT(*) FROM medication_logs WHERE status = 'taken' AND taken_at >= ?) AS doses,
              (SELECT COUNT(*) FROM help_requests WHERE created_at >= ?) AS help,
              (SELECT COUNT(*) FROM family_links WHERE status = 'active') AS links`, since7, since30),
  ]);

  const byPlatform = { ios: 0, android: 0, other: 0 };
  for (const row of platforms.results) {
    if (row.platform === 'ios' || row.platform === 'android') byPlatform[row.platform] += row.n;
    else byPlatform.other += row.n;
  }
  const activeByDay = new Map(daily.results.map((r) => [r.day, r.n]));
  const joinedByDay = new Map(joined.results.map((r) => [r.day, r.n]));
  const series: Stats['daily'] = [];
  for (let i = days - 1; i >= 0; i--) {
    const day = dayIso(i);
    series.push({ day, active: activeByDay.get(day) ?? 0, joined: joinedByDay.get(day) ?? 0 });
  }

  return {
    users: totals?.users ?? 0,
    newUsers7: totals?.new7 ?? 0,
    newUsers30: totals?.new30 ?? 0,
    devices: byPlatform.ios + byPlatform.android + byPlatform.other,
    platforms: byPlatform,
    active1: active?.a1 ?? 0,
    active7: active?.a7 ?? 0,
    active30: active?.a30 ?? 0,
    daily: series,
    countries: countries.results,
    medicines: usage?.medicines ?? 0,
    dosesTaken7: usage?.doses ?? 0,
    help30: usage?.help ?? 0,
    familyLinks: usage?.links ?? 0,
  };
}

let regionNames: Intl.DisplayNames | null | undefined;

/** Arabic country name ("السعودية") for an ISO code, or the code itself. */
export function countryName(code: string | null) {
  if (!code) return 'غير معروف';
  if (regionNames === undefined) {
    try {
      regionNames = new Intl.DisplayNames(['ar'], { type: 'region' });
    } catch {
      regionNames = null;
    }
  }
  try {
    return regionNames?.of(code) ?? code;
  } catch {
    return code;
  }
}

/** Flag emoji from the two letters (regional indicator symbols). */
export const flag = (code: string | null) =>
  code && /^[A-Z]{2}$/.test(code) ? String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65)) : '🌐';

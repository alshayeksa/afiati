// Arabic wording for messages about a person: verbs and pronouns follow the person's gender
// (chosen in the app). Unknown gender uses the masculine form, as before.

export type Gender = 'male' | 'female' | null;

export function parseGender(value: unknown): Gender {
  return value === 'male' || value === 'female' ? value : null;
}

/** Picks the masculine or feminine form. */
export const g = (gender: Gender, male: string, female: string) => (gender === 'female' ? female : male);

/** Most users are in Saudi Arabia; used until the phone reports its zone. */
export const DEFAULT_ZONE = 'Asia/Riyadh';

/** "2:15 م" in the given IANA zone (Latin digits, like the app). */
export function clock(iso: string, zone: string | null) {
  const date = new Date(iso);
  const parts = (timeZone: string) =>
    new Intl.DateTimeFormat('en-US', { timeZone, hour: 'numeric', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date);
  let list: Intl.DateTimeFormatPart[];
  try {
    list = parts(zone || DEFAULT_ZONE);
  } catch {
    list = parts(DEFAULT_ZONE);
  }
  const hour = Number(list.find((p) => p.type === 'hour')?.value ?? 0) % 24;
  const minute = list.find((p) => p.type === 'minute')?.value ?? '00';
  const h12 = hour % 12 === 0 ? 12 : hour % 12;
  return `${h12}:${minute} ${hour < 12 ? 'ص' : 'م'}`;
}

/** Opens Google Maps (the app if installed, otherwise the website) at a point. */
export function mapUrl(latitude: number, longitude: number) {
  return `https://www.google.com/maps/search/?api=1&query=${latitude.toFixed(6)},${longitude.toFixed(6)}`;
}

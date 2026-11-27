/**
 * Formats the first 128 bits of a hex digest as a UUID (version 8, RFC 9562 "custom").
 * Used to give a dose log the same id on every device: hash(medication_id | scheduled_at).
 */
export function uuidFromHex(hex: string) {
  if (!/^[0-9a-f]{32,}$/i.test(hex)) throw new Error('uuidFromHex needs at least 32 hex characters');
  const h = hex.slice(0, 32).toLowerCase();
  const variant = ((parseInt(h[16], 16) & 0x3) | 0x8).toString(16);
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-8${h.slice(13, 16)}-${variant}${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

export const doseLogKey = (medicationId: string, scheduledAt: string) => `${medicationId}|${scheduledAt}`;

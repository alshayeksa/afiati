// Strict checks for rows the app uploads. Anything that fails here is rejected
// before it reaches D1, so one bad operation cannot abort a whole batch.

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Date#toISOString() output. Fixed format means plain string comparison orders correctly.
const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

const DOSE_UNITS = new Set(['حبة', 'مل', 'قطرة', 'بخة', 'other']);
const REPEAT_TYPES = new Set(['daily', 'weekly', 'monthly']);
const LOG_STATUSES = new Set(['pending', 'taken', 'missed', 'skipped']);

export class InvalidRow extends Error {}

type Raw = Record<string, unknown>;

export type MedicationRow = {
  id: string;
  patient_id: string;
  name: string;
  dose_amount: number;
  dose_unit: string;
  repeat_type: string;
  repeat_days_json: string;
  frequency: number;
  times_json: string;
  stock_count: number | null;
  stock_alert: number;
  is_active: 0 | 1;
  notes: string | null;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
};

export type LogRow = {
  id: string;
  medication_id: string;
  patient_id: string;
  scheduled_at: string;
  taken_at: string | null;
  status: string;
  created_at: string;
  updated_at: string;
};

export function parseMedication(raw: Raw, patientId: string): MedicationRow {
  const repeatDays = raw.repeat_days ?? [];
  if (!Array.isArray(repeatDays) || repeatDays.length > 31 || !repeatDays.every((d) => isInt(d, 1, 31))) {
    fail('repeat_days');
  }
  const times = raw.times;
  if (!Array.isArray(times) || times.length < 1 || times.length > 4 || !times.every((t) => typeof t === 'string' && TIME.test(t))) {
    fail('times');
  }
  return {
    id: uuid(raw.id, 'id'),
    patient_id: owner(raw.patient_id, patientId),
    name: text(raw.name, 'name', 200),
    dose_amount: positiveNumber(raw.dose_amount, 'dose_amount'),
    dose_unit: oneOf(raw.dose_unit, DOSE_UNITS, 'dose_unit'),
    repeat_type: oneOf(raw.repeat_type ?? 'daily', REPEAT_TYPES, 'repeat_type'),
    repeat_days_json: JSON.stringify(repeatDays),
    frequency: int(raw.frequency, 1, 4, 'frequency'),
    times_json: JSON.stringify(times),
    stock_count: raw.stock_count === null || raw.stock_count === undefined ? null : int(raw.stock_count, 0, 100000, 'stock_count'),
    stock_alert: raw.stock_alert === undefined ? 7 : int(raw.stock_alert, 0, 100000, 'stock_alert'),
    is_active: bool(raw.is_active, 'is_active'),
    notes: raw.notes === null || raw.notes === undefined || raw.notes === '' ? null : text(raw.notes, 'notes', 1000),
    created_at: iso(raw.created_at, 'created_at'),
    updated_at: iso(raw.updated_at, 'updated_at'),
    deleted_at: raw.deleted_at === null || raw.deleted_at === undefined ? null : iso(raw.deleted_at, 'deleted_at'),
  };
}

export function parseLog(raw: Raw, patientId: string): LogRow {
  const status = oneOf(raw.status, LOG_STATUSES, 'status');
  const takenAt = raw.taken_at === null || raw.taken_at === undefined ? null : iso(raw.taken_at, 'taken_at');
  if (status === 'taken' && !takenAt) fail('taken_at');
  return {
    id: uuid(raw.id, 'id'),
    medication_id: uuid(raw.medication_id, 'medication_id'),
    patient_id: owner(raw.patient_id, patientId),
    scheduled_at: iso(raw.scheduled_at, 'scheduled_at'),
    taken_at: takenAt,
    status,
    created_at: iso(raw.created_at, 'created_at'),
    updated_at: iso(raw.updated_at, 'updated_at'),
  };
}

export const isUuid = (value: unknown): value is string => typeof value === 'string' && UUID.test(value);

function fail(field: string): never {
  throw new InvalidRow(`invalid ${field}`);
}

function uuid(value: unknown, field: string) {
  if (!isUuid(value)) fail(field);
  return value.toLowerCase();
}

// A device may only write rows that belong to its own profile.
function owner(value: unknown, patientId: string) {
  if (typeof value !== 'string' || value.toLowerCase() !== patientId) throw new InvalidRow('forbidden patient_id');
  return patientId;
}

function text(value: unknown, field: string, max: number) {
  if (typeof value !== 'string' || !value.trim() || value.length > max) fail(field);
  return value.trim();
}

function iso(value: unknown, field: string) {
  if (typeof value !== 'string' || !ISO.test(value) || Number.isNaN(Date.parse(value))) fail(field);
  return value;
}

function isInt(value: unknown, min: number, max: number) {
  return typeof value === 'number' && Number.isInteger(value) && value >= min && value <= max;
}

function int(value: unknown, min: number, max: number, field: string) {
  if (!isInt(value, min, max)) fail(field);
  return value as number;
}

function positiveNumber(value: unknown, field: string) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0 || value > 10000) fail(field);
  return value;
}

function oneOf(value: unknown, allowed: Set<string>, field: string) {
  if (typeof value !== 'string' || !allowed.has(value)) fail(field);
  return value;
}

function bool(value: unknown, field: string): 0 | 1 {
  if (value === true || value === 1) return 1;
  if (value === false || value === 0) return 0;
  fail(field);
}

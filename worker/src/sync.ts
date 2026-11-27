// Offline-first sync for the patient's own medications and dose logs.
//
// push: the app uploads its Outbox in order. Conflict rules match lib/sync/conflictPolicy.ts:
//   - a medication is replaced only by a version whose updated_at is the same or newer;
//   - a dose that is 'taken' never goes back; a 'taken' upload always wins otherwise.
// pull: the app downloads rows whose server_seq is past its cursor.
import type { AuthContext } from './auth';
import { HttpError, readJson } from './http';
import { InvalidRow, isUuid, LogRow, MedicationRow, parseLog, parseMedication } from './validate';

// D1 on the free plan allows 50 queries per request; a push uses at most ~3 + one per operation.
export const MAX_PUSH_OPERATIONS = 25;
const DEFAULT_PULL_LIMIT = 200;
const MAX_PULL_LIMIT = 500;

type PushOperation = {
  operation_id?: unknown;
  entity_type?: unknown;
  operation_type?: unknown;
  payload?: unknown;
};

type PushResult = { operation_id: string; status: 'applied' | 'rejected'; error?: string };

type Prepared =
  | { operationId: string; kind: 'medication'; row: MedicationRow }
  | { operationId: string; kind: 'medication_log'; row: LogRow };

export async function push(request: Request, env: Env, auth: AuthContext) {
  const body = await readJson<{ operations?: unknown }>(request);
  if (!Array.isArray(body.operations)) throw new HttpError(400, 'invalid_field', 'operations must be an array');
  if (body.operations.length > MAX_PUSH_OPERATIONS) {
    throw new HttpError(413, 'too_many_operations', `send at most ${MAX_PUSH_OPERATIONS} operations per request`);
  }

  const results: PushResult[] = [];
  const prepared: Prepared[] = [];

  for (const operation of body.operations as PushOperation[]) {
    const operationId = isUuid(operation?.operation_id) ? operation.operation_id : null;
    if (!operationId) throw new HttpError(400, 'invalid_field', 'operation_id must be a UUID');
    try {
      if (operation.operation_type !== 'upsert') throw new InvalidRow('unsupported operation_type');
      const payload = operation.payload;
      if (!payload || typeof payload !== 'object') throw new InvalidRow('invalid payload');
      if (operation.entity_type === 'medication') {
        prepared.push({ operationId, kind: 'medication', row: parseMedication(payload as Record<string, unknown>, auth.profileId) });
      } else if (operation.entity_type === 'medication_log') {
        prepared.push({ operationId, kind: 'medication_log', row: parseLog(payload as Record<string, unknown>, auth.profileId) });
      } else {
        throw new InvalidRow('unsupported entity_type');
      }
    } catch (error) {
      if (!(error instanceof InvalidRow)) throw error;
      results.push({ operation_id: operationId, status: 'rejected', error: error.message });
    }
  }

  // A dose log must point at one of this patient's medications, either already stored
  // or uploaded earlier in this same request.
  const knownMedications = await existingMedicationIds(env, auth.profileId, prepared);
  const statements: { operationId: string; statement: D1PreparedStatement }[] = [];
  for (const item of prepared) {
    if (item.kind === 'medication') {
      knownMedications.add(item.row.id);
      statements.push({ operationId: item.operationId, statement: medicationUpsert(env, item.row) });
    } else if (knownMedications.has(item.row.medication_id)) {
      statements.push({ operationId: item.operationId, statement: logUpsert(env, item.row) });
    } else {
      results.push({ operation_id: item.operationId, status: 'rejected', error: 'unknown medication_id' });
    }
  }

  if (statements.length) {
    try {
      // One transaction for the common case.
      await env.DB.batch(statements.map((item) => item.statement));
      for (const item of statements) results.push({ operation_id: item.operationId, status: 'applied' });
    } catch {
      // Something slipped past validation; apply one by one so only the bad row is rejected.
      for (const item of statements) {
        try {
          await item.statement.run();
          results.push({ operation_id: item.operationId, status: 'applied' });
        } catch (error) {
          console.error('push operation failed', item.operationId, error);
          results.push({ operation_id: item.operationId, status: 'rejected', error: 'database_error' });
        }
      }
    }
  }

  return { results };
}

export async function pull(request: Request, env: Env, auth: AuthContext) {
  const url = new URL(request.url);
  const medicationsAfter = cursorParam(url, 'medications_after');
  const logsAfter = cursorParam(url, 'logs_after');
  const limit = Math.min(Number(url.searchParams.get('limit')) || DEFAULT_PULL_LIMIT, MAX_PULL_LIMIT);

  const [medications, logs] = await env.DB.batch<Record<string, unknown>>([
    env.DB.prepare('SELECT * FROM medications WHERE patient_id = ? AND server_seq > ? ORDER BY server_seq LIMIT ?').bind(
      auth.profileId,
      medicationsAfter,
      limit,
    ),
    env.DB.prepare('SELECT * FROM medication_logs WHERE patient_id = ? AND server_seq > ? ORDER BY server_seq LIMIT ?').bind(
      auth.profileId,
      logsAfter,
      limit,
    ),
  ]);
  const medicationRows = medications.results;
  // Logs are sent only once every medication has been sent (both queries ran in one
  // batch, so they see the same snapshot). The phone's medication_logs_local has a
  // foreign key to medications_local, so a log must never arrive before its medication.
  const logRows = medicationRows.length === limit ? [] : logs.results;

  return {
    medications: medicationRows.map(medicationToApi),
    logs: logRows.map(logToApi),
    cursor: {
      medications_after: lastSeq(medicationRows, medicationsAfter),
      logs_after: lastSeq(logRows, logsAfter),
    },
    has_more: medicationRows.length === limit || logRows.length === limit,
  };
}

async function existingMedicationIds(env: Env, patientId: string, prepared: Prepared[]) {
  const ids = [...new Set(prepared.filter((item) => item.kind === 'medication_log').map((item) => item.row.medication_id))];
  if (!ids.length) return new Set<string>();
  const { results } = await env.DB.prepare(
    `SELECT id FROM medications WHERE patient_id = ? AND id IN (${ids.map(() => '?').join(', ')})`,
  )
    .bind(patientId, ...ids)
    .all<{ id: string }>();
  return new Set(results.map((row) => row.id));
}

function medicationUpsert(env: Env, row: MedicationRow) {
  return env.DB.prepare(
    `INSERT INTO medications
       (id, patient_id, name, dose_amount, dose_unit, repeat_type, repeat_days_json, frequency, times_json,
        stock_count, stock_alert, is_active, notes, created_at, updated_at, deleted_at, server_seq)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM medications))
     ON CONFLICT(id) DO UPDATE SET
       name = excluded.name,
       dose_amount = excluded.dose_amount,
       dose_unit = excluded.dose_unit,
       repeat_type = excluded.repeat_type,
       repeat_days_json = excluded.repeat_days_json,
       frequency = excluded.frequency,
       times_json = excluded.times_json,
       stock_count = excluded.stock_count,
       stock_alert = excluded.stock_alert,
       is_active = excluded.is_active,
       notes = excluded.notes,
       updated_at = excluded.updated_at,
       deleted_at = excluded.deleted_at,
       server_seq = excluded.server_seq
     WHERE medications.patient_id = excluded.patient_id
       AND excluded.updated_at >= medications.updated_at`,
  ).bind(
    row.id,
    row.patient_id,
    row.name,
    row.dose_amount,
    row.dose_unit,
    row.repeat_type,
    row.repeat_days_json,
    row.frequency,
    row.times_json,
    row.stock_count,
    row.stock_alert,
    row.is_active,
    row.notes,
    row.created_at,
    row.updated_at,
    row.deleted_at,
  );
}

const LOG_UPDATE = `DO UPDATE SET
       taken_at = excluded.taken_at,
       status = excluded.status,
       updated_at = excluded.updated_at,
       server_seq = excluded.server_seq
     WHERE medication_logs.patient_id = excluded.patient_id
       AND medication_logs.status <> 'taken'
       AND (excluded.status = 'taken' OR excluded.updated_at >= medication_logs.updated_at)`;

function logUpsert(env: Env, row: LogRow) {
  // The second conflict target covers the same dose arriving under a different id
  // (older app builds generated random log ids).
  return env.DB.prepare(
    `INSERT INTO medication_logs
       (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at, server_seq)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM medication_logs))
     ON CONFLICT(id) ${LOG_UPDATE}
     ON CONFLICT(medication_id, scheduled_at) ${LOG_UPDATE}`,
  ).bind(row.id, row.medication_id, row.patient_id, row.scheduled_at, row.taken_at, row.status, row.created_at, row.updated_at);
}

function medicationToApi(row: Record<string, unknown>) {
  const { repeat_days_json, times_json, server_seq, is_active, ...rest } = row;
  return {
    ...rest,
    repeat_days: JSON.parse(String(repeat_days_json)),
    times: JSON.parse(String(times_json)),
    is_active: is_active === 1,
  };
}

function logToApi(row: Record<string, unknown>) {
  const { server_seq, ...rest } = row;
  return rest;
}

function cursorParam(url: URL, name: string) {
  const value = Number(url.searchParams.get(name) ?? 0);
  if (!Number.isSafeInteger(value) || value < 0) throw new HttpError(400, 'invalid_field', `${name} must be a non-negative integer`);
  return value;
}

function lastSeq(rows: Record<string, unknown>[], fallback: number) {
  return rows.length ? Number(rows[rows.length - 1].server_seq) : fallback;
}

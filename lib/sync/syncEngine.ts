import { Platform } from 'react-native';

import { ApiError, apiRequest, isApiConfigured } from '@/lib/api';
import { getDatabase } from '@/lib/database';
import { isInternetAvailable } from '@/lib/connectivity';
import { outboxRepository } from '@/lib/repositories/outboxRepository';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { syncStateRepository } from '@/lib/repositories/syncStateRepository';
import { getSyncPrecondition, mergeDoseState, shouldApplyRemoteVersion } from '@/lib/sync/conflictPolicy';
import { Medication, MedicationLog, Patient } from '@/store/types';

// Must not exceed MAX_PUSH_OPERATIONS in worker/src/sync.ts.
const PUSH_BATCH_SIZE = 25;
const PULL_PAGE_SIZE = 200;

let activeSync: Promise<SyncResult> | null = null;

export type SyncResult = {
  status: 'synced' | 'offline' | 'not_configured' | 'no_patient' | 'failed';
  pushed: number;
  pulled: number;
  error?: string;
};

type PushResponse = { results: { operation_id: string; status: 'applied' | 'rejected'; error?: string }[] };
type PullResponse = {
  medications: Medication[];
  logs: MedicationLog[];
  cursor: { medications_after: number; logs_after: number };
  has_more: boolean;
};

export function syncNow(): Promise<SyncResult> {
  if (activeSync) return activeSync;
  activeSync = runSync().finally(() => {
    activeSync = null;
  });
  return activeSync;
}

async function runSync(): Promise<SyncResult> {
  const online = await isInternetAvailable();
  const patient = await patientRepository.get();
  const precondition = getSyncPrecondition({ configured: isApiConfigured, online, hasPatient: Boolean(patient) });
  if (precondition !== 'ready') return { status: precondition, pushed: 0, pulled: 0 };

  try {
    const registered = await ensureCloudIdentity(patient!);
    await pushProfile(registered);
    const pushed = await pushOutbox();
    const pulled = await pullRemoteChanges();
    await syncStateRepository.set('last_successful_sync', new Date().toISOString());
    return { status: 'synced', pushed, pulled };
  } catch (error) {
    return {
      status: 'failed',
      pushed: 0,
      pulled: 0,
      error: error instanceof Error ? error.message : 'تعذرت المزامنة.',
    };
  }
}

/**
 * Registers this phone with the Worker once and keeps the device token in sync_state.
 * patients_local.auth_user_id now holds the Worker device id.
 */
export async function ensureCloudIdentity(patient?: Patient): Promise<Patient> {
  const localPatient = patient ?? (await patientRepository.get());
  if (!localPatient) throw new Error('لا يوجد ملف مريض محلي.');
  if (await syncStateRepository.get('device_token')) return localPatient;
  if (!(await isInternetAvailable())) throw new Error('لا يوجد اتصال بالإنترنت.');

  let registration: { device_id: string; token: string };
  try {
    registration = await apiRequest('/v1/register', {
      method: 'POST',
      auth: false,
      body: {
        profile_id: localPatient.id,
        full_name: localPatient.full_name,
        phone: localPatient.phone,
        email: localPatient.email,
        platform: Platform.OS,
      },
    });
  } catch (error) {
    if (error instanceof ApiError && error.code === 'profile_exists') {
      throw new Error('هذا الملف مسجل مسبقًا على جهاز آخر. استعادة الحساب غير متاحة بعد.');
    }
    throw error;
  }

  await syncStateRepository.set('device_token', registration.token);
  // The server already has this exact profile version.
  await syncStateRepository.set('profile_synced_version', localPatient.updated_at);
  await patientRepository.setCloudIdentity(registration.device_id);
  return { ...localPatient, auth_user_id: registration.device_id };
}

async function pushProfile(patient: Patient) {
  if ((await syncStateRepository.get('profile_synced_version')) === patient.updated_at) return;
  await apiRequest('/v1/me', {
    method: 'PUT',
    body: { full_name: patient.full_name, phone: patient.phone, email: patient.email },
  });
  await syncStateRepository.set('profile_synced_version', patient.updated_at);
}

async function pushOutbox() {
  let pushed = 0;
  // Each round either removes operations or marks them rejected (which eventually
  // excludes them from list()), so this loop always ends.
  for (;;) {
    const operations = await outboxRepository.list(PUSH_BATCH_SIZE);
    if (!operations.length) return pushed;

    // Network and server errors throw here and leave the Outbox untouched for the next attempt.
    const response = await apiRequest<PushResponse>('/v1/sync/push', {
      method: 'POST',
      body: {
        operations: operations.map((operation) => ({
          operation_id: operation.operation_id,
          entity_type: operation.entity_type,
          entity_id: operation.entity_id,
          operation_type: operation.operation_type,
          payload: JSON.parse(operation.payload_json),
        })),
      },
    });

    for (const result of response.results) {
      if (result.status === 'applied') {
        await outboxRepository.remove(result.operation_id);
        pushed += 1;
      } else {
        await outboxRepository.fail(result.operation_id, result.error ?? 'rejected');
      }
    }
    if (operations.length < PUSH_BATCH_SIZE) return pushed;
  }
}

async function pullRemoteChanges() {
  let medicationsAfter = Number((await syncStateRepository.get('pull_medications_after')) ?? 0);
  let logsAfter = Number((await syncStateRepository.get('pull_logs_after')) ?? 0);
  let pulled = 0;

  for (;;) {
    const page = await apiRequest<PullResponse>(
      `/v1/sync/pull?medications_after=${medicationsAfter}&logs_after=${logsAfter}&limit=${PULL_PAGE_SIZE}`,
    );
    await applyRemotePage(page.medications, page.logs);
    // Save the cursor only after the page is committed locally.
    medicationsAfter = page.cursor.medications_after;
    logsAfter = page.cursor.logs_after;
    await syncStateRepository.set('pull_medications_after', String(medicationsAfter));
    await syncStateRepository.set('pull_logs_after', String(logsAfter));
    pulled += page.medications.length + page.logs.length;
    if (!page.has_more) return pulled;
  }
}

async function applyRemotePage(medications: Medication[], logs: MedicationLog[]) {
  if (!medications.length && !logs.length) return;
  const database = await getDatabase();
  await database.withExclusiveTransactionAsync(async (transaction) => {
    for (const medication of medications) {
      const localMedication = await transaction.getFirstAsync<{ updated_at: string }>(
        'SELECT updated_at FROM medications_local WHERE id = ?',
        medication.id,
      );
      if (!shouldApplyRemoteVersion(localMedication, medication)) continue;
      await transaction.runAsync(
        `INSERT INTO medications_local
          (id, patient_id, name, dose_amount, dose_unit, repeat_type, repeat_days_json,
           frequency, times_json, stock_count, stock_alert, is_active, notes, created_at, updated_at, deleted_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
           deleted_at = excluded.deleted_at
         WHERE excluded.updated_at >= medications_local.updated_at`,
        medication.id,
        medication.patient_id,
        medication.name,
        medication.dose_amount,
        medication.dose_unit,
        medication.repeat_type ?? 'daily',
        JSON.stringify(medication.repeat_days ?? []),
        medication.frequency,
        JSON.stringify(medication.times),
        medication.stock_count,
        medication.stock_alert,
        medication.is_active ? 1 : 0,
        medication.notes ?? null,
        medication.created_at ?? new Date().toISOString(),
        medication.updated_at ?? new Date().toISOString(),
        medication.deleted_at ?? null,
      );
    }

    for (const log of logs) {
      const now = new Date().toISOString();
      const localLog = await transaction.getFirstAsync<Pick<MedicationLog, 'status' | 'taken_at' | 'updated_at'>>(
        'SELECT status, taken_at, updated_at FROM medication_logs_local WHERE id = ?',
        log.id,
      );
      const merged = mergeDoseState(localLog, log);
      // The second conflict target keeps a local copy of the same dose stored under an
      // older random id instead of failing the whole page.
      await transaction.runAsync(
        `INSERT INTO medication_logs_local
          (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(id) DO UPDATE SET
           taken_at = CASE
             WHEN medication_logs_local.status = 'taken' THEN medication_logs_local.taken_at
             ELSE excluded.taken_at
           END,
           status = CASE
             WHEN medication_logs_local.status = 'taken' THEN 'taken'
             ELSE excluded.status
           END,
           updated_at = MAX(medication_logs_local.updated_at, excluded.updated_at)
         ON CONFLICT(medication_id, scheduled_at) DO NOTHING`,
        log.id,
        log.medication_id,
        log.patient_id,
        log.scheduled_at,
        merged.taken_at,
        merged.status,
        log.created_at ?? now,
        merged.updated_at ?? now,
      );
    }
  });
}

import * as Crypto from 'expo-crypto';

import { getDatabase } from '@/lib/database';
import { scheduledAtForDate } from '@/lib/helpers';
import { enqueueOperation } from '@/lib/repositories/outboxRepository';
import { doseLogKey, uuidFromHex } from '@/lib/sync/ids';
import { Medication, MedicationLog } from '@/store/types';

const doseLogId = async (medicationId: string, scheduledAt: string) =>
  uuidFromHex(await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, doseLogKey(medicationId, scheduledAt)));

export type MedicationDraft = Omit<Medication, 'id' | 'patient_id' | 'is_active' | 'stock_alert'>;

type MedicationRow = Omit<Medication, 'times' | 'repeat_days' | 'is_active'> & {
  times_json: string;
  repeat_days_json: string;
  is_active: number;
};
type LogJoinRow = MedicationLog & {
  medication_name: string;
  medication_dose_amount: number;
  medication_dose_unit: Medication['dose_unit'];
};

const medicationFromRow = (row: MedicationRow): Medication => {
  const { times_json, repeat_days_json, ...rest } = row;
  return {
    ...rest,
    repeat_type: row.repeat_type ?? 'daily',
    repeat_days: JSON.parse(repeat_days_json || '[]') as number[],
    times: JSON.parse(times_json) as string[],
    is_active: row.is_active === 1,
  };
};

const medicationPayload = (medication: Medication) => ({
  ...medication,
  deleted_at: medication.deleted_at ?? null,
});

export const medicationRepository = {
  async list(patientId: string, includeInactive = false): Promise<Medication[]> {
    const database = await getDatabase();
    const rows = await database.getAllAsync<MedicationRow>(
      `SELECT * FROM medications_local WHERE patient_id = ? ${includeInactive ? '' : 'AND is_active = 1'} ORDER BY created_at DESC`,
      patientId,
    );
    return rows.map(medicationFromRow);
  },

  async listLogs(patientId: string, from: Date, to: Date): Promise<MedicationLog[]> {
    const database = await getDatabase();
    const rows = await database.getAllAsync<LogJoinRow>(
      `SELECT logs.*, medications.name AS medication_name,
              medications.dose_amount AS medication_dose_amount,
              medications.dose_unit AS medication_dose_unit
       FROM medication_logs_local logs
       JOIN medications_local medications ON medications.id = logs.medication_id
       WHERE logs.patient_id = ? AND logs.scheduled_at >= ? AND logs.scheduled_at < ?
       ORDER BY logs.scheduled_at ASC`,
      patientId,
      from.toISOString(),
      to.toISOString(),
    );
    return rows.map((row) => ({
      id: row.id,
      medication_id: row.medication_id,
      patient_id: row.patient_id,
      scheduled_at: row.scheduled_at,
      taken_at: row.taken_at,
      status: row.status,
      created_at: row.created_at,
      updated_at: row.updated_at,
      medications: {
        name: row.medication_name,
        dose_amount: row.medication_dose_amount,
        dose_unit: row.medication_dose_unit,
      },
    }));
  },

  async ensureLogsForDate(patientId: string, date = new Date()) {
    const database = await getDatabase();
    const medications = await this.list(patientId);
    await database.withExclusiveTransactionAsync(async (transaction) => {
      for (const medication of medications) {
        const scheduleDay = medication.repeat_type === 'weekly' ? date.getDay() + 1 : date.getDate();
        if (medication.repeat_type !== 'daily' && !medication.repeat_days.includes(scheduleDay)) continue;
        for (const time of medication.times) {
          const now = new Date().toISOString();
          const scheduledAt = scheduledAtForDate(time, date);
          const log: MedicationLog = {
            // Same dose -> same id on every device, so the server never sees two rows for one dose.
            id: await doseLogId(medication.id, scheduledAt),
            medication_id: medication.id,
            patient_id: patientId,
            scheduled_at: scheduledAt,
            taken_at: null,
            status: 'pending',
            created_at: now,
            updated_at: now,
          };
          const result = await transaction.runAsync(
            `INSERT OR IGNORE INTO medication_logs_local
              (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            log.id,
            log.medication_id,
            log.patient_id,
            log.scheduled_at,
            log.taken_at,
            log.status,
            now,
            now,
          );
          if (result.changes > 0) {
            await enqueueOperation(
              { patientId, entityType: 'medication_log', entityId: log.id, operationType: 'upsert', payload: log },
              transaction,
            );
          }
        }
      }
    });
  },

  async add(patientId: string, draft: MedicationDraft): Promise<Medication> {
    const database = await getDatabase();
    const now = new Date().toISOString();
    const medication: Medication = {
      ...draft,
      id: Crypto.randomUUID(),
      patient_id: patientId,
      stock_alert: 7,
      is_active: true,
      created_at: draft.created_at ?? now,
      updated_at: now,
      deleted_at: null,
    };
    await database.withExclusiveTransactionAsync(async (transaction) => {
      await transaction.runAsync(
        `INSERT INTO medications_local
          (id, patient_id, name, dose_amount, dose_unit, repeat_type, repeat_days_json,
           frequency, times_json, stock_count, stock_alert, is_active, notes, created_at, updated_at, deleted_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        medication.id,
        medication.patient_id,
        medication.name,
        medication.dose_amount,
        medication.dose_unit,
        medication.repeat_type,
        JSON.stringify(medication.repeat_days),
        medication.frequency,
        JSON.stringify(medication.times),
        medication.stock_count,
        medication.stock_alert,
        1,
        medication.notes ?? null,
        medication.created_at!,
        medication.updated_at!,
        medication.deleted_at ?? null,
      );
      await enqueueOperation(
        { patientId, entityType: 'medication', entityId: medication.id, operationType: 'upsert', payload: medicationPayload(medication) },
        transaction,
      );
    });
    await this.ensureLogsForDate(patientId);
    return medication;
  },

  async findExactDuplicate(patientId: string, draft: MedicationDraft) {
    const medications = await this.list(patientId);
    const normalize = (value: string) => value.trim().toLocaleLowerCase('ar');
    return medications.find(
      (item) =>
        normalize(item.name) === normalize(draft.name) &&
        item.dose_amount === draft.dose_amount &&
        item.dose_unit === draft.dose_unit &&
        item.repeat_type === draft.repeat_type &&
        JSON.stringify([...item.repeat_days].sort()) === JSON.stringify([...draft.repeat_days].sort()) &&
        JSON.stringify([...item.times].sort()) === JSON.stringify([...draft.times].sort()),
    );
  },

  async markTaken(patientId: string, logId: string, medicationId: string) {
    const database = await getDatabase();
    await database.withExclusiveTransactionAsync(async (transaction) => {
      const now = new Date().toISOString();
      await transaction.runAsync(
        `UPDATE medication_logs_local SET status = 'taken', taken_at = ?, updated_at = ?
         WHERE id = ? AND status != 'taken'`,
        now,
        now,
        logId,
      );
      await transaction.runAsync(
        `UPDATE medications_local
         SET stock_count = CASE WHEN stock_count IS NULL THEN NULL ELSE MAX(stock_count - 1, 0) END,
             updated_at = ? WHERE id = ?`,
        now,
        medicationId,
      );
      const log = await transaction.getFirstAsync<MedicationLog>('SELECT * FROM medication_logs_local WHERE id = ?', logId);
      const medRow = await transaction.getFirstAsync<MedicationRow>('SELECT * FROM medications_local WHERE id = ?', medicationId);
      if (log) {
        await enqueueOperation(
          { patientId, entityType: 'medication_log', entityId: log.id, operationType: 'upsert', payload: log },
          transaction,
        );
      }
      if (medRow) {
        const medication = medicationFromRow(medRow);
        await enqueueOperation(
          { patientId, entityType: 'medication', entityId: medication.id, operationType: 'upsert', payload: medicationPayload(medication) },
          transaction,
        );
      }
    });
  },

  async deactivate(patientId: string, medicationId: string) {
    const database = await getDatabase();
    const now = new Date().toISOString();
    await database.withExclusiveTransactionAsync(async (transaction) => {
      await transaction.runAsync(
        'UPDATE medications_local SET is_active = 0, deleted_at = ?, updated_at = ? WHERE id = ?',
        now,
        now,
        medicationId,
      );
      const row = await transaction.getFirstAsync<MedicationRow>('SELECT * FROM medications_local WHERE id = ?', medicationId);
      if (row) {
        const medication = medicationFromRow(row);
        await enqueueOperation(
          { patientId, entityType: 'medication', entityId: medication.id, operationType: 'upsert', payload: medicationPayload(medication) },
          transaction,
        );
      }
    });
  },
};

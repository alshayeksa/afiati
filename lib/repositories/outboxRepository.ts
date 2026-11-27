import * as Crypto from 'expo-crypto';
import type { SQLiteDatabase } from 'expo-sqlite';

import { getDatabase } from '@/lib/database';
import { OutboxOperation } from '@/store/types';

type EnqueueInput = {
  patientId: string;
  entityType: OutboxOperation['entity_type'];
  entityId: string;
  operationType: OutboxOperation['operation_type'];
  payload: unknown;
};

export async function enqueueOperation(input: EnqueueInput, transaction?: SQLiteDatabase) {
  const database = transaction ?? (await getDatabase());
  await database.runAsync(
    `INSERT INTO outbox
      (operation_id, patient_id, entity_type, entity_id, operation_type, payload_json, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    Crypto.randomUUID(),
    input.patientId,
    input.entityType,
    input.entityId,
    input.operationType,
    JSON.stringify(input.payload),
    new Date().toISOString(),
  );
}

// An operation the server rejected this many times stays in the Outbox for inspection
// but is no longer sent, so it cannot hold back newer operations forever.
export const MAX_OUTBOX_ATTEMPTS = 5;

export const outboxRepository = {
  async list(limit = 100) {
    const database = await getDatabase();
    return database.getAllAsync<OutboxOperation>(
      'SELECT * FROM outbox WHERE attempts < ? ORDER BY created_at ASC, rowid ASC LIMIT ?',
      MAX_OUTBOX_ATTEMPTS,
      limit,
    );
  },

  async remove(operationId: string) {
    const database = await getDatabase();
    await database.runAsync('DELETE FROM outbox WHERE operation_id = ?', operationId);
  },

  async fail(operationId: string, message: string) {
    const database = await getDatabase();
    await database.runAsync(
      'UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE operation_id = ?',
      message.slice(0, 500),
      operationId,
    );
  },
};


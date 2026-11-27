import * as Crypto from 'expo-crypto';

import { getDatabase } from '@/lib/database';
import { Patient } from '@/store/types';

type PatientInput = {
  full_name: string;
  phone: string;
  email?: string | null;
};

type PatientRow = Omit<Patient, 'phone_verified'> & { phone_verified: number };

const fromRow = (row: PatientRow): Patient => ({ ...row, phone_verified: row.phone_verified === 1 });

export const patientRepository = {
  async get(): Promise<Patient | null> {
    const database = await getDatabase();
    const row = await database.getFirstAsync<PatientRow>('SELECT * FROM patients_local LIMIT 1');
    return row ? fromRow(row) : null;
  },

  async create(input: PatientInput): Promise<Patient> {
    const database = await getDatabase();
    const existing = await this.get();
    if (existing) return existing;

    const now = new Date().toISOString();
    const patient: Patient = {
      id: Crypto.randomUUID(),
      auth_user_id: null,
      full_name: input.full_name.trim(),
      phone: normalizePhone(input.phone),
      email: input.email?.trim().toLowerCase() || null,
      phone_verified: false,
      created_at: now,
      updated_at: now,
    };

    await database.runAsync(
      `INSERT INTO patients_local
        (id, auth_user_id, full_name, phone, email, phone_verified, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      patient.id,
      patient.auth_user_id,
      patient.full_name,
      patient.phone,
      patient.email,
      0,
      patient.created_at,
      patient.updated_at,
    );
    return patient;
  },

  async setCloudIdentity(authUserId: string) {
    const database = await getDatabase();
    await database.runAsync(
      'UPDATE patients_local SET auth_user_id = ?, updated_at = ? WHERE id = (SELECT id FROM patients_local LIMIT 1)',
      authUserId,
      new Date().toISOString(),
    );
  },

  async update(input: PatientInput): Promise<Patient> {
    const database = await getDatabase();
    const existing = await this.get();
    if (!existing) throw new Error('لا يوجد ملف شخصي لتحديثه.');

    const updated: Patient = {
      ...existing,
      full_name: input.full_name.trim(),
      phone: normalizePhone(input.phone),
      email: input.email?.trim().toLowerCase() || null,
      updated_at: new Date().toISOString(),
    };

    await database.runAsync(
      `UPDATE patients_local
       SET full_name = ?, phone = ?, email = ?, updated_at = ?
       WHERE id = ?`,
      updated.full_name,
      updated.phone,
      updated.email,
      updated.updated_at,
      updated.id,
    );
    return updated;
  },
};

export const normalizePhone = (phone: string) => {
  return phone.trim();
};

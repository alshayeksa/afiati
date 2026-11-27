export type DoseUnit = 'حبة' | 'مل' | 'قطرة' | 'بخة' | 'other';
export type RepeatType = 'daily' | 'weekly' | 'monthly';

export type Medication = {
  id: string;
  patient_id: string;
  name: string;
  dose_amount: number;
  dose_unit: DoseUnit;
  repeat_type: RepeatType;
  repeat_days: number[];
  frequency: number;
  times: string[];
  stock_count: number | null;
  stock_alert: number;
  is_active: boolean;
  notes?: string | null;
  created_at?: string;
  updated_at?: string;
  deleted_at?: string | null;
};

export type MedicationLog = {
  id: string;
  medication_id: string;
  patient_id: string;
  scheduled_at: string;
  taken_at: string | null;
  status: 'pending' | 'taken' | 'missed' | 'skipped';
  created_at?: string;
  updated_at?: string;
  medications?: Pick<Medication, 'name' | 'dose_amount' | 'dose_unit'>;
};

export type Patient = {
  id: string;
  auth_user_id: string | null;
  full_name: string;
  phone: string;
  email: string | null;
  phone_verified: boolean;
  created_at: string;
  updated_at: string;
};

export type OutboxOperation = {
  operation_id: string;
  patient_id: string;
  entity_type: 'medication' | 'medication_log';
  entity_id: string;
  operation_type: 'upsert' | 'delete';
  payload_json: string;
  created_at: string;
  attempts: number;
  last_error: string | null;
};

export type FamilyLink = {
  id: string;
  patient_id: string;
  caregiver_id: string;
  privacy_level: 'full' | 'status_only' | 'alerts_only';
  status: 'pending' | 'active' | 'rejected' | 'removed';
  profiles?: { full_name: string };
};

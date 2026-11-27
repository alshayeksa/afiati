import { create } from 'zustand';

import { medicationRepository, MedicationDraft } from '@/lib/repositories/medicationRepository';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { refreshMedicationReminders } from '@/lib/localNotifications';
import { syncNow } from '@/lib/sync/syncEngine';
import { Medication, MedicationLog } from '@/store/types';

type MedicationState = {
  medications: Medication[];
  logs: MedicationLog[];
  loading: boolean;
  load: (date?: Date) => Promise<void>;
  addMedication: (draft: MedicationDraft) => Promise<void>;
  findDuplicateMedication: (draft: MedicationDraft) => Promise<Medication | undefined>;
  markTaken: (logId: string, medicationId: string) => Promise<void>;
  deleteMedication: (id: string) => Promise<void>;
};

export const useMedicationStore = create<MedicationState>((set, get) => ({
  medications: [],
  logs: [],
  loading: false,
  load: async (date = new Date()) => {
    set({ loading: true });
    try {
      const patient = await patientRepository.get();
      if (!patient) {
        set({ medications: [], logs: [] });
        return;
      }
      await medicationRepository.ensureLogsForDate(patient.id, date);
      const dayStart = new Date(date);
      dayStart.setHours(0, 0, 0, 0);
      const dayEnd = new Date(dayStart);
      dayEnd.setDate(dayEnd.getDate() + 1);
      const [medications, logs] = await Promise.all([
        medicationRepository.list(patient.id),
        medicationRepository.listLogs(patient.id, dayStart, dayEnd),
      ]);
      set({ medications, logs });
    } finally {
      set({ loading: false });
    }
  },
  addMedication: async (draft) => {
    const patient = await patientRepository.get();
    if (!patient) throw new Error('أكمل إعداد ملفك أولًا.');
    await medicationRepository.add(patient.id, draft);
    await refreshMedicationReminders(patient.id, true);
    void syncNow();
    await get().load();
  },
  findDuplicateMedication: async (draft) => {
    const patient = await patientRepository.get();
    if (!patient) return undefined;
    return medicationRepository.findExactDuplicate(patient.id, draft);
  },
  markTaken: async (logId, medicationId) => {
    const patient = await patientRepository.get();
    if (!patient) throw new Error('أكمل إعداد ملفك أولًا.');
    await medicationRepository.markTaken(patient.id, logId, medicationId);
    void syncNow();
    await get().load();
  },
  deleteMedication: async (id) => {
    const patient = await patientRepository.get();
    if (!patient) throw new Error('أكمل إعداد ملفك أولًا.');
    await medicationRepository.deactivate(patient.id, id);
    await refreshMedicationReminders(patient.id);
    void syncNow();
    await get().load();
  },
}));

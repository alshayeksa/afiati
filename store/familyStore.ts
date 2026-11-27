import { create } from 'zustand';

import { isInternetAvailable } from '@/lib/connectivity';
import { FAMILY_OFFLINE_MESSAGE, familyApi } from '@/lib/family';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { FamilyLink } from '@/store/types';

type FamilyState = {
  followers: FamilyLink[];
  following: FamilyLink[];
  pending: FamilyLink[];
  online: boolean;
  error: string | null;
  load: () => Promise<void>;
  approve: (caregiverId: string, privacyLevel: FamilyLink['privacy_level']) => Promise<void>;
  reject: (caregiverId: string) => Promise<void>;
};

export const useFamilyStore = create<FamilyState>((set, get) => ({
  followers: [],
  following: [],
  pending: [],
  online: true,
  error: null,
  load: async () => {
    if (!(await isInternetAvailable())) {
      set({ followers: [], following: [], pending: [], online: false, error: FAMILY_OFFLINE_MESSAGE });
      return;
    }
    try {
      if (!(await patientRepository.get())) return;
      const { followers, following, pending } = await familyApi.overview();
      set({ followers, following, pending, online: true, error: null });
    } catch (error) {
      set({
        followers: [],
        following: [],
        pending: [],
        online: false,
        error: error instanceof Error ? error.message : 'تعذر الاتصال بخدمة العائلة.',
      });
    }
  },
  approve: async (caregiverId, privacyLevel) => {
    await familyApi.respond(caregiverId, 'approve', privacyLevel);
    await get().load();
  },
  reject: async (caregiverId) => {
    await familyApi.respond(caregiverId, 'reject');
    await get().load();
  },
}));

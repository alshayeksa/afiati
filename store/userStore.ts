import { create } from 'zustand';

import { patientRepository } from '@/lib/repositories/patientRepository';
import { Patient } from '@/store/types';

type UserState = {
  user: Patient | null;
  loading: boolean;
  hydrate: () => Promise<Patient | null>;
  setUser: (user: Patient) => void;
};

export const useUserStore = create<UserState>((set) => ({
  user: null,
  loading: true,
  hydrate: async () => {
    const user = await patientRepository.get();
    set({ user, loading: false });
    return user;
  },
  setUser: (user) => set({ user, loading: false }),
}));

import { MedicationLog } from '@/store/types';

type Versioned = { updated_at?: string | null };

export function shouldApplyRemoteVersion(local: Versioned | null, remote: Versioned) {
  if (!local?.updated_at) return true;
  if (!remote.updated_at) return false;
  return new Date(remote.updated_at).getTime() >= new Date(local.updated_at).getTime();
}

export function mergeDoseState(
  local: Pick<MedicationLog, 'status' | 'taken_at' | 'updated_at'> | null,
  remote: Pick<MedicationLog, 'status' | 'taken_at' | 'updated_at'>,
) {
  if (local?.status === 'taken') return local;
  if (remote.status === 'taken') return remote;
  return shouldApplyRemoteVersion(local, remote) ? remote : local ?? remote;
}

export function getSyncPrecondition(input: { configured: boolean; online: boolean; hasPatient: boolean }) {
  if (!input.configured) return 'not_configured' as const;
  if (!input.online) return 'offline' as const;
  if (!input.hasPatient) return 'no_patient' as const;
  return 'ready' as const;
}


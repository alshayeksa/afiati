// Online-only family features, served by the Cloudflare Worker (worker/src/family.ts).
import { ApiError, apiRequest } from '@/lib/api';
import { isInternetAvailable } from '@/lib/connectivity';
import { ensureCloudIdentity } from '@/lib/sync/syncEngine';
import { FamilyLink, Medication, MedicationLog } from '@/store/types';

export const FAMILY_OFFLINE_MESSAGE = 'خاصية العائلة تحتاج اتصالًا بالإنترنت.';

const MESSAGES: Record<string, string> = {
  not_configured: 'خدمة العائلة غير مُعدّة بعد.',
  network_error: 'تعذر الاتصال بخدمة العائلة. تحقق من الإنترنت وحاول مرة أخرى.',
  invalid_code: 'الرمز غير صحيح أو منتهي الصلاحية.',
  self_follow: 'لا يمكنك متابعة نفسك.',
  forbidden: 'لا تملك صلاحية متابعة هذا الشخص.',
  request_not_found: 'هذا الطلب لم يعد بانتظار الموافقة.',
};

type Options = Parameters<typeof apiRequest>[1] & {
  /** Message for HTTP 429 on this particular call. */
  rateLimitMessage?: string;
};

/** Calls the Worker after making sure the phone is online and registered. Errors carry Arabic messages. */
async function familyRequest<T>(path: string, { rateLimitMessage, ...options }: Options = {}): Promise<T> {
  if (!(await isInternetAvailable())) throw new Error(FAMILY_OFFLINE_MESSAGE);
  await ensureCloudIdentity();
  try {
    return await apiRequest<T>(path, options);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 429 && rateLimitMessage) throw new Error(rateLimitMessage);
      throw new Error(MESSAGES[error.code] ?? 'تعذر إكمال الطلب. حاول مرة أخرى.');
    }
    throw error;
  }
}

export type FamilyOverview = { followers: FamilyLink[]; following: FamilyLink[]; pending: FamilyLink[] };

export type PatientDay = {
  patient_name: string | null;
  privacy_level: FamilyLink['privacy_level'];
  medications: Pick<Medication, 'id' | 'name' | 'dose_amount' | 'dose_unit' | 'times'>[];
  logs: MedicationLog[];
};

export type NotificationItem = {
  id: string;
  type: string;
  title: string;
  body: string;
  data: Record<string, unknown>;
  read: boolean;
  created_at: string;
};

export const familyApi = {
  overview: () => familyRequest<FamilyOverview>('/v1/family'),

  createInvitation: () =>
    familyRequest<{ code: string; expires_at: string }>('/v1/family/invitations', { method: 'POST' }),

  join: (code: string) =>
    familyRequest<{ status: FamilyLink['status']; patient_name: string }>('/v1/family/join', {
      method: 'POST',
      body: { code },
      rateLimitMessage: 'محاولات كثيرة. انتظر ساعة ثم حاول مرة أخرى.',
    }),

  respond: (caregiverId: string, action: 'approve' | 'reject', privacyLevel?: FamilyLink['privacy_level']) =>
    familyRequest<{ status: FamilyLink['status'] }>('/v1/family/respond', {
      method: 'POST',
      body: { caregiver_id: caregiverId, action, privacy_level: privacyLevel },
    }),

  patientDay: (patientId: string, from: Date, to: Date) =>
    familyRequest<PatientDay>(
      `/v1/family/patients/${encodeURIComponent(patientId)}/today?from=${from.toISOString()}&to=${to.toISOString()}`,
    ),

  remind: (patientId: string) =>
    familyRequest<{ ok: true }>(`/v1/family/patients/${encodeURIComponent(patientId)}/remind`, {
      method: 'POST',
      rateLimitMessage: 'أرسلت تذكيرًا قبل قليل. يمكنك الإرسال مرة أخرى بعد 10 دقائق.',
    }),

  notifications: () => familyRequest<{ notifications: NotificationItem[] }>('/v1/notifications'),

  markAllRead: () => familyRequest<{ ok: true }>('/v1/notifications/read-all', { method: 'POST' }),
};

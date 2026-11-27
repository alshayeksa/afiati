import { Linking } from 'react-native';

import { MedicationLog } from '@/store/types';

export const todayArabic = () =>
  new Intl.DateTimeFormat('ar-SA-u-ca-islamic', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date()) +
  ' • ' +
  new Intl.DateTimeFormat('ar-SA', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());

export const timeGreeting = (date = new Date()) => {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) return 'صباح الخير';
  if (hour >= 12 && hour < 17) return 'مساء الخير';
  if (hour >= 17 && hour < 22) return 'مساء الخير';
  return 'أهلاً بك';
};

export const formatTime = (time: string) => {
  const [h, m] = time.split(':').map(Number);
  const date = new Date();
  date.setHours(h, m, 0, 0);
  return new Intl.DateTimeFormat('ar-SA', { hour: 'numeric', minute: '2-digit' }).format(date);
};

export const getDoseStatus = (log?: MedicationLog) => {
  if (!log) return { label: 'لم تُؤخذ بعد', tone: 'gray' as const };
  if (log.status === 'taken') return { label: 'تم', tone: 'green' as const };
  if (log.status === 'missed') return { label: 'فاتت', tone: 'red' as const };
  const scheduled = new Date(log.scheduled_at).getTime();
  if (Math.abs(Date.now() - scheduled) <= 30 * 60 * 1000) return { label: 'موعدك الآن', tone: 'gold' as const };
  return { label: 'لم تُؤخذ بعد', tone: 'gray' as const };
};

export const scheduledAtToday = (time: string) => {
  return scheduledAtForDate(time, new Date());
};

export const scheduledAtForDate = (time: string, sourceDate: Date) => {
  const [hours, minutes] = time.split(':').map(Number);
  const date = new Date(sourceDate);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};

export const openWhatsAppInvite = (code: string) => {
  const body = `أدعوك لمتابعة أدويتي في تطبيق عافيتي. رمز الدعوة: ${code}`;
  return Linking.openURL(`https://wa.me/?text=${encodeURIComponent(body)}`);
};

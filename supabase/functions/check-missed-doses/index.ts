import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async () => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const now = Date.now();
  const t30 = new Date(now - 30 * 60 * 1000).toISOString();
  const t60 = new Date(now - 60 * 60 * 1000).toISOString();

  const { data: recent } = await supabase
    .from('medication_logs')
    .select('*, medications(name)')
    .eq('status', 'pending')
    .lt('scheduled_at', t30)
    .gt('scheduled_at', t60);

  for (const log of recent || []) {
    await supabase.from('medication_logs').update({ status: 'missed' }).eq('id', log.id);
    await sendPushToUser(supabase, log.patient_id, {
      title: 'لم تأخذ دواءك',
      body: `لم تأخذ ${log.medications.name} بعد`,
      data: { type: 'missed_reminder', medication_id: log.medication_id },
    });
  }

  const { data: old } = await supabase
    .from('medication_logs')
    .select('*, medications(name), profiles!patient_id(full_name)')
    .eq('status', 'missed')
    .lt('scheduled_at', t60);

  for (const log of old || []) {
    const { data: caregivers } = await supabase
      .from('family_links')
      .select('caregiver_id')
      .eq('patient_id', log.patient_id)
      .eq('status', 'active');

    for (const caregiver of caregivers || []) {
      await sendPushToUser(supabase, caregiver.caregiver_id, {
        title: 'تنبيه عائلي',
        body: `${log.profiles.full_name} لم يأخذ ${log.medications.name}`,
        data: { type: 'family_alert', patient_id: log.patient_id },
      });
    }
  }

  return new Response('done');
});

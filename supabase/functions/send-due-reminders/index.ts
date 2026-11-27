import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async () => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const now = new Date();
  const in5 = new Date(now.getTime() + 5 * 60 * 1000);

  const { data: dueLogs } = await supabase
    .from('medication_logs')
    .select('*, medications(name)')
    .eq('status', 'pending')
    .gte('scheduled_at', now.toISOString())
    .lte('scheduled_at', in5.toISOString());

  for (const log of dueLogs || []) {
    await sendPushToUser(supabase, log.patient_id, {
      title: 'موعد دوائك',
      body: `حان موعد ${log.medications.name}`,
      data: { type: 'reminder', log_id: log.id, medication_id: log.medication_id },
    });
  }

  return new Response('done');
});

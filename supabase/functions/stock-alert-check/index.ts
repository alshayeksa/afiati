import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async () => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const { data: meds } = await supabase.from('medications').select('*').eq('is_active', true).not('stock_count', 'is', null);

  for (const med of meds || []) {
    if (med.stock_count <= med.stock_alert) {
      await sendPushToUser(supabase, med.patient_id, {
        title: 'كمية الدواء تنفد',
        body: `باقي ${med.stock_count} ${med.dose_unit} من ${med.name}`,
        data: { type: 'stock_alert', medication_id: med.id },
      });
    }
  }

  return new Response('done');
});

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async (req) => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });

  const { data: authData } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''));
  if (!authData.user) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: caregiver } = await supabase.from('profiles').select('id, full_name').eq('auth_user_id', authData.user.id).single();
  if (!caregiver) return new Response(JSON.stringify({ error: 'Profile not found' }), { status: 404 });

  const { patient_id } = await req.json();
  const { data: link } = await supabase
    .from('family_links')
    .select('id')
    .eq('patient_id', patient_id)
    .eq('caregiver_id', caregiver.id)
    .eq('status', 'active')
    .single();
  if (!link) return new Response(JSON.stringify({ error: 'Forbidden' }), { status: 403 });

  await sendPushToUser(supabase, patient_id, {
    title: 'تذكير من العائلة',
    body: `${caregiver.full_name || 'أحد أفراد عائلتك'} يذكّرك بلطف بمراجعة جرعاتك اليوم`,
    data: { type: 'family_reminder', caregiver_id: caregiver.id },
  });

  return new Response(JSON.stringify({ success: true }), {
    headers: { 'Content-Type': 'application/json' },
  });
});


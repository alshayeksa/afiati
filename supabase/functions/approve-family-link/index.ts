import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async (req) => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: authData } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''));
  if (!authData.user) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: profile } = await supabase.from('profiles').select('id').eq('auth_user_id', authData.user.id).single();
  if (!profile) return new Response(JSON.stringify({ error: 'Profile not found' }), { status: 404 });
  const patient_id = profile.id;

  const { caregiver_id, privacy_level, action } = await req.json();
  const newStatus = action === 'approve' ? 'active' : 'rejected';
  await supabase
    .from('family_links')
    .update({
      status: newStatus,
      privacy_level: privacy_level || 'full',
      approved_at: action === 'approve' ? new Date().toISOString() : null,
    })
    .eq('patient_id', patient_id)
    .eq('caregiver_id', caregiver_id);

  await sendPushToUser(supabase, caregiver_id, {
    title: action === 'approve' ? 'تم القبول' : 'تم الرفض',
    body: action === 'approve' ? 'تم قبول طلب المتابعة' : 'لم يتم قبول طلب المتابعة',
    data: { type: 'invite_response', status: newStatus, patient_id },
  });

  return new Response(JSON.stringify({ success: true }), { headers: { 'Content-Type': 'application/json' } });
});

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { sendPushToUser } from '../_shared/push.ts';

Deno.serve(async (req) => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: authData } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''));
  if (!authData.user) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: caregiverProfile } = await supabase.from('profiles').select('id').eq('auth_user_id', authData.user.id).single();
  if (!caregiverProfile) return new Response(JSON.stringify({ error: 'Profile not found' }), { status: 404 });
  const caregiver_id = caregiverProfile.id;

  const { code } = await req.json();
  const { data: invite, error } = await supabase
    .from('invitations')
    .select('*, profiles!patient_id(full_name)')
    .eq('code', code)
    .eq('used', false)
    .gt('expires_at', new Date().toISOString())
    .single();

  if (error || !invite) {
    return new Response(JSON.stringify({ error: 'الرمز غير صحيح أو منتهي الصلاحية' }), { status: 400 });
  }

  if (invite.patient_id === caregiver_id) {
    return new Response(JSON.stringify({ error: 'لا يمكنك متابعة نفسك' }), { status: 400 });
  }

  await supabase.from('family_links').upsert({ patient_id: invite.patient_id, caregiver_id, status: 'pending' });
  const { data: caregiver } = await supabase.from('profiles').select('full_name').eq('id', caregiver_id).single();

  await sendPushToUser(supabase, invite.patient_id, {
    title: 'طلب متابعة جديد',
    body: `${caregiver?.full_name || 'أحد أفراد العائلة'} يريد متابعة أدويتك`,
    data: { type: 'invite_request', caregiver_id },
  });

  return new Response(JSON.stringify({ success: true, patient_name: invite.profiles?.full_name }), {
    headers: { 'Content-Type': 'application/json' },
  });
});

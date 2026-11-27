import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

Deno.serve(async (req) => {
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!);
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: authData } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''));
  if (!authData.user) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  const { data: profile } = await supabase.from('profiles').select('id').eq('auth_user_id', authData.user.id).single();
  if (!profile) return new Response(JSON.stringify({ error: 'Profile not found' }), { status: 404 });
  const patient_id = profile.id;

  await supabase.from('invitations').update({ used: true }).eq('patient_id', patient_id).eq('used', false);
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const expires_at = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  const { data, error } = await supabase.from('invitations').insert({ patient_id, code, expires_at }).select().single();

  if (error) return new Response(JSON.stringify({ error }), { status: 400 });
  return new Response(JSON.stringify({ code: data.code, expires_at: data.expires_at }), {
    headers: { 'Content-Type': 'application/json' },
  });
});

export async function sendPushToUser(supabase: any, userId: string, notification: any) {
  const { data: tokens } = await supabase.from('push_tokens').select('token').eq('user_id', userId);

  for (const { token } of tokens || []) {
    await fetch('https://exp.host/--/api/v2/push/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ to: token, ...notification }),
    });
  }

  await supabase.from('notifications_log').insert({
    user_id: userId,
    type: notification.data?.type || 'reminder',
    title: notification.title,
    body: notification.body,
    data: notification.data || {},
  });
}

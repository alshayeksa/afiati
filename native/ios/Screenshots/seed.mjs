// Demo families for the App Store screenshots, on the LOCAL test server started by ios-screenshots.yml.
// Prints the JSON that AfiatiUITests/ScreenshotTests.swift reads. Never run it against production.
const base = process.argv[2] || 'http://127.0.0.1:8798';
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) throw new Error('local test server only');

const headers = { 'Content-Type': 'application/json', 'X-Timezone': 'Asia/Riyadh' };
async function call(method, path, token, body) {
  const r = await fetch(base + path, { method, headers: { ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const json = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(json)}`);
  return json;
}
async function person(name, phone, gender, lang) {
  const id = crypto.randomUUID();
  const r = await fetch(base + '/v1/register', { method: 'POST', headers: { ...headers, 'X-Language': lang }, body: JSON.stringify({ profile_id: id, full_name: name, phone, gender, platform: 'ios' }) });
  const { token } = await r.json();
  return { id, token, name, phone, gender };
}

// Times in Riyadh relative to now: a dose taken earlier, one due 20 minutes ago, and later ones
const riyadh = new Date(Date.now() + 3 * 3600 * 1000);
const hour = riyadh.getUTCHours();
const minute = riyadh.getUTCMinutes();
const pad = (n) => String(n).padStart(2, '0');
const at = (h, m = 0) => `${pad(Math.max(0, Math.min(23, h)))}:${pad(m)}`;
const dueTotal = hour * 60 + minute - 20;
const due = at(Math.floor(dueTotal / 60), Math.floor((dueTotal % 60) / 5) * 5);
const earlier = at(hour - 2);
const later = at(hour + 3);
const evening = at(hour + 8);
const night = at(Math.min(hour + 11, 23), hour + 11 > 23 ? 30 : 0);

async function family(lang) {
  const ar = lang === 'ar';
  const elder = await person(ar ? 'أم محمد' : 'Fatima', '0501234567', 'female', lang);
  const son = await person(ar ? 'محمد' : 'Mohammed', '0559876543', 'male', lang);
  const daughter = await person(ar ? 'سارة' : 'Sara', '0551234567', 'female', lang);
  const yesterday = new Date(Date.now() - 2 * 86400 * 1000).toISOString();
  const meds = [
    [ar ? 'ميتفورمين 500' : 'Metformin 500', 1, 'حبة', [earlier, evening], 41],
    [ar ? 'أملوديبين 5' : 'Amlodipine 5', 1, 'حبة', [due], 6],
    [ar ? 'فيتامين د' : 'Vitamin D', 1, 'حبة', [later], 25],
    [ar ? 'قطرة العين' : 'Eye drops', 2, 'قطرة', [night], null],
  ];
  const operations = meds.map(([name, amount, unit, times, stock]) => {
    const id = crypto.randomUUID();
    return { operation_id: crypto.randomUUID(), entity_type: 'medication', entity_id: id, operation_type: 'upsert', payload: {
      id, patient_id: elder.id, name, dose_amount: amount, dose_unit: unit, repeat_type: 'daily', repeat_days: [], frequency: times.length,
      times: [...new Set(times)].sort(), stock_count: stock, stock_alert: 7, is_active: true, notes: null, created_at: yesterday, updated_at: yesterday, deleted_at: null } };
  });
  const pushed = await call('POST', '/v1/sync/push', elder.token, { operations });
  if (pushed.results.some((r) => r.status !== 'applied')) throw new Error('medicines not applied ' + JSON.stringify(pushed));
  for (const relative of [daughter, son]) {
    const { code } = await call('POST', '/v1/family/invitations', elder.token);
    await call('POST', '/v1/family/join', relative.token, { code });
    await call('POST', '/v1/family/respond', elder.token, { caregiver_id: relative.id, action: 'approve', privacy_level: 'full' });
  }
  return { elder, son, firstMedicine: meds[0][0] };
}

const seed = { base, ar: await family('ar'), en: await family('en') };
process.stderr.write(`Riyadh ${pad(hour)}:${pad(minute)} doses ${earlier} ${due} ${later} ${evening} ${night}\n`);
console.log(JSON.stringify(seed));

// The owner's control page (/forceupdate), rendered on the server: plain HTML forms and inline SVG,
// no scripts (the page's CSP forbids them). Hover on a chart bar shows its numbers (SVG <title>).
import type { PlatformRule, Settings } from './control';
import type { Partner } from './partners';
import { countriesOf } from './partners';
import { countryName, flag, type Stats } from './stats';

export type Reveal = { name: string; key: string; secret: string };
export type PartnerRow = Partner & { dispatched: number; failed: number; last_dispatch: string | null };

export const escapeHtml = (text: string) =>
  text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string);

const CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

const STYLE = `
  :root {
    --bg: #f3f6f5; --card: #ffffff; --line: #dde6e2; --ink: #0f2a22; --muted: #5d7068; --soft: #eef4f1;
    --brand: #13684f; --brand-ink: #ffffff; --mark: #1e8e6c; --mark-hover: #13684f; --grid: #e6ece9;
    --ok-bg: #e5f5ec; --ok: #17794a; --warn-bg: #fff3df; --warn: #9a5a00; --bad-bg: #fde9e9; --bad: #b4262b;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #050b09; --card: #0c1814; --line: #1b2d27; --ink: #e8f1ed; --muted: #8fa59c; --soft: #10201b;
      --brand: #22b085; --brand-ink: #03140e; --mark: #22b085; --mark-hover: #4ff0b0; --grid: #18291f;
      --ok-bg: #0f2a1d; --ok: #5fd99a; --warn-bg: #2e220c; --warn: #f2b24c; --bad-bg: #321214; --bad: #ff8a8e;
    }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font: 16px/1.65 -apple-system, "Segoe UI", system-ui, Tahoma, sans-serif; background: var(--bg); color: var(--ink); }
  a { color: var(--brand); }
  .top { position: sticky; top: 0; z-index: 2; background: color-mix(in srgb, var(--bg) 88%, transparent); backdrop-filter: blur(10px);
         border-bottom: 1px solid var(--line); }
  .top .in { max-width: 1120px; margin: auto; padding: 12px 20px; display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
  .brand { display: flex; gap: 10px; align-items: center; font-weight: 700; font-size: 1.15rem; }
  .brand svg { width: 34px; height: 34px; }
  .who { margin-inline-start: auto; color: var(--muted); font-size: .9rem; }
  nav { max-width: 1120px; margin: auto; padding: 0 20px 10px; display: flex; gap: 6px; flex-wrap: wrap; }
  nav a { text-decoration: none; color: var(--ink); padding: 6px 14px; border-radius: 999px; background: var(--soft); font-size: .92rem; }
  nav a:hover { background: var(--line); }
  main { max-width: 1120px; margin: auto; padding: 8px 20px 60px; }
  section { scroll-margin-top: 120px; }
  h2 { font-size: 1.3rem; margin: 34px 0 4px; }
  .sub { color: var(--muted); margin: 0 0 14px; font-size: .95rem; }
  h3 { font-size: 1.05rem; margin: 0 0 10px; }
  .grid { display: grid; gap: 14px; }
  .g4 { grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); }
  .g2 { grid-template-columns: repeat(auto-fit, minmax(330px, 1fr)); }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 18px; padding: 18px; }
  .kpi .label { color: var(--muted); font-size: .92rem; }
  .kpi .value { font-size: 2.1rem; font-weight: 700; line-height: 1.2; margin: 4px 0 2px; font-variant-numeric: tabular-nums; }
  .kpi .note { color: var(--muted); font-size: .85rem; }
  .pill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; font-size: .88rem; font-weight: 600; }
  .pill::before { content: ""; width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
  .pill.ok { background: var(--ok-bg); color: var(--ok); } .pill.bad { background: var(--bad-bg); color: var(--bad); }
  .pill.warn { background: var(--warn-bg); color: var(--warn); } .pill.off { background: var(--soft); color: var(--muted); }
  .state { border-radius: 14px; padding: 10px 14px; margin: 0 0 12px; background: var(--soft); }
  .state.warn { background: var(--warn-bg); } .state.bad { background: var(--bad-bg); } .state.ok { background: var(--ok-bg); }
  .chart svg { width: 100%; height: auto; display: block; }
  .chart .bar { fill: var(--mark); } .chart .col:hover .bar { fill: var(--mark-hover); }
  .chart .col:hover .hit { fill: var(--soft); }
  .chart .grid-line { stroke: var(--grid); stroke-width: 1; } .chart .base { stroke: var(--line); stroke-width: 1; }
  .chart text { fill: var(--muted); font-size: 11px; font-family: inherit; }
  .rows { display: grid; gap: 10px; }
  .row { display: grid; grid-template-columns: 26px minmax(140px, 1.4fr) 2fr 52px; gap: 10px; align-items: center; }
  .row .flag { font-size: 1.2rem; } .row .n { text-align: end; font-variant-numeric: tabular-nums; font-weight: 600; }
  .track { height: 10px; border-radius: 6px; background: var(--soft); overflow: hidden; }
  .fill { height: 100%; border-radius: 6px; background: var(--mark); }
  .split { display: flex; height: 12px; border-radius: 6px; overflow: hidden; gap: 2px; background: var(--card); margin: 10px 0 8px; }
  .split span { display: block; }
  .legend { display: flex; gap: 16px; flex-wrap: wrap; color: var(--muted); font-size: .9rem; }
  .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-inline-end: 6px; vertical-align: -1px; }
  .list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
  .list li { display: flex; justify-content: space-between; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--line); }
  .list li:last-child { border-bottom: 0; } .list b { font-variant-numeric: tabular-nums; }
  details { margin-top: 12px; } summary { cursor: pointer; color: var(--muted); font-size: .9rem; }
  table { width: 100%; border-collapse: collapse; font-size: .92rem; }
  th, td { text-align: start; padding: 7px 8px; border-bottom: 1px solid var(--line); } th { color: var(--muted); font-weight: 600; }
  td.num, th.num { text-align: end; font-variant-numeric: tabular-nums; }
  label { display: block; margin: 12px 0 4px; font-weight: 600; font-size: .95rem; }
  label.check { display: flex; gap: 8px; align-items: center; font-weight: 500; }
  input[type=number], input[type=url], input[type=text] { width: 100%; padding: 11px 12px; border-radius: 12px; border: 1px solid var(--line);
         background: var(--bg); color: var(--ink); font: inherit; }
  input:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
  input[type=checkbox] { width: 18px; height: 18px; accent-color: var(--brand); }
  .hint { color: var(--muted); font-size: .86rem; margin: 6px 0 0; }
  .btn { display: inline-flex; align-items: center; gap: 6px; margin-top: 14px; padding: 10px 20px; border: 0; border-radius: 12px;
         background: var(--brand); color: var(--brand-ink); font: inherit; font-weight: 700; cursor: pointer; text-decoration: none; }
  .btn.danger { background: #d83a3f; color: #fff; } .btn.ghost { background: var(--soft); color: var(--ink); }
  .btn.small { margin-top: 0; padding: 6px 12px; font-size: .88rem; font-weight: 600; }
  .actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; } .actions form { margin: 0; }
  .msg { border-radius: 14px; padding: 12px 16px; margin: 16px 0 0; font-weight: 600; }
  .msg.ok { background: var(--ok-bg); color: var(--ok); } .msg.err { background: var(--bad-bg); color: var(--bad); }
  .secret { background: var(--warn-bg); border-radius: 16px; padding: 16px; margin: 16px 0 0; }
  .secret code { display: block; direction: ltr; text-align: left; background: var(--card); padding: 10px 12px; border-radius: 10px;
         margin: 4px 0 10px; word-break: break-all; font-size: .9rem; user-select: all; }
  code.inline { direction: ltr; unicode-bidi: embed; background: var(--soft); padding: 2px 6px; border-radius: 6px; font-size: .88rem; }
  .empty { color: var(--muted); text-align: center; padding: 22px 8px; }
  .center { max-width: 30rem; margin: 12vh auto; padding: 0 20px; text-align: center; }
`;

const LOGO = `<svg viewBox="0 0 40 40" aria-hidden="true"><rect width="40" height="40" rx="11" fill="#13684f"/>
<path d="M20 29s-8.5-5.2-8.5-11a4.6 4.6 0 0 1 8.5-2.4A4.6 4.6 0 0 1 28.5 18c0 5.8-8.5 11-8.5 11z" fill="none" stroke="#4ff0b0" stroke-width="2.2" stroke-linejoin="round"/>
<path d="M7 21h7l2-4 3 8 2.5-6 1.5 2h10" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

export function html(body: string, status = 200, cache = 'no-store') {
  return new Response(
    `<!doctype html>
<html lang="ar" dir="rtl">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>عافيتي · لوحة التحكم</title>
<style>${STYLE}</style>
${body}
</html>`,
    {
      status,
      headers: {
        'Content-Type': 'text/html; charset=utf-8',
        'Content-Security-Policy': CSP,
        // same-origin (not no-referrer) so the browser sends a real Origin when a form is saved
        'Referrer-Policy': 'same-origin',
        'X-Content-Type-Options': 'nosniff',
        'Cache-Control': cache,
      },
    },
  );
}

/** A small centred page (sign-in needed, update link). */
export const simplePage = (inner: string, status = 200) => html(`<div class="center">${LOGO.replace('<svg', '<svg width="64" height="64"')}${inner}</div>`, status);

const n = (value: number) => value.toLocaleString('en-US');
const dayLabel = (day: string) => `${Number(day.slice(8, 10))}/${Number(day.slice(5, 7))}`;

/** Rounds up to 1, 2 or 5 × 10^k, so the axis reads in round numbers. */
function niceMax(value: number) {
  if (value <= 4) return 4;
  const power = 10 ** Math.floor(Math.log10(value));
  for (const step of [1, 2, 5, 10]) if (step * power >= value) return step * power;
  return 10 * power;
}

/** A bar whose top corners are rounded and whose foot sits flat on the baseline. */
function barPath(x: number, y: number, w: number, base: number) {
  const h = base - y;
  if (h <= 0) return '';
  const r = Math.min(4, h, w / 2);
  return `M${x},${base}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${base}Z`;
}

function dailyChart(daily: Stats['daily']) {
  const W = 760, H = 230, left = 34, right = 8, top = 12, bottom = 26;
  const plotW = W - left - right, plotH = H - top - bottom, base = top + plotH;
  const max = niceMax(Math.max(...daily.map((d) => d.active), 0));
  const slot = plotW / daily.length;
  const barW = Math.max(3, slot - 2); // a 2px gap between bars
  const y = (v: number) => base - (v / max) * plotH;
  const grid = [0, max / 2, max]
    .map((v) => `<line class="${v === 0 ? 'base' : 'grid-line'}" x1="${left}" x2="${W - right}" y1="${y(v)}" y2="${y(v)}"/>
      <text x="${left - 6}" y="${y(v) + 4}" text-anchor="end">${n(v)}</text>`)
    .join('');
  const cols = daily
    .map((d, i) => {
      const x = left + i * slot + (slot - barW) / 2;
      const label = i % 5 === daily.length % 5 || i === daily.length - 1
        ? `<text x="${x + barW / 2}" y="${H - 8}" text-anchor="middle">${i === daily.length - 1 ? 'اليوم' : dayLabel(d.day)}</text>`
        : '';
      return `<g class="col"><title>${dayLabel(d.day)}: ${n(d.active)} مستخدم نشط${d.joined ? `، ${n(d.joined)} جديد` : ''}</title>
        <rect class="hit" x="${left + i * slot}" y="${top}" width="${slot}" height="${plotH}" fill="transparent"/>
        <path class="bar" d="${barPath(x, y(d.active), barW, base)}"/>${label}</g>`;
    })
    .join('');
  return `<svg viewBox="0 0 ${W} ${H}" dir="ltr" role="img" aria-label="المستخدمون النشطون يوميًا خلال آخر ${daily.length} يومًا">${grid}${cols}</svg>`;
}

function statsSection(stats: Stats) {
  const kpi = (label: string, value: number, note: string) =>
    `<div class="card kpi"><div class="label">${label}</div><div class="value">${n(value)}</div><div class="note">${note}</div></div>`;
  const totalCountries = stats.countries.reduce((sum, c) => sum + c.devices, 0);
  const top = stats.countries.slice(0, 10);
  const rest = stats.countries.slice(10).reduce((sum, c) => sum + c.devices, 0);
  const countryRows = top
    .map((c) => {
      const share = totalCountries ? (c.devices / totalCountries) * 100 : 0;
      return `<div class="row" title="${escapeHtml(countryName(c.code))}: ${n(c.devices)} جهاز (${share.toFixed(0)}٪)">
        <span class="flag">${flag(c.code)}</span><span>${escapeHtml(countryName(c.code))}</span>
        <span class="track"><span class="fill" style="display:block;width:${Math.max(2, share).toFixed(1)}%"></span></span>
        <span class="n">${n(c.devices)}</span></div>`;
    })
    .join('');
  const p = stats.platforms;
  const pct = (v: number) => (stats.devices ? (v / stats.devices) * 100 : 0);
  return `<section id="stats">
  <h2>الإحصاءات</h2>
  <p class="sub">أعداد فقط، بلا أسماء ولا أرقام جوال. الدولة يحددها Cloudflare من اتصال الجهاز، ولا يُحفظ عنوان IP.</p>
  <div class="grid g4">
    ${kpi('إجمالي المستخدمين', stats.users, `${n(stats.newUsers7)} جديد هذا الأسبوع · ${n(stats.newUsers30)} هذا الشهر`)}
    ${kpi('نشطون اليوم', stats.active1, 'أجهزة فتحت التطبيق اليوم')}
    ${kpi('نشطون خلال 7 أيام', stats.active7, 'أجهزة مختلفة')}
    ${kpi('نشطون خلال 30 يومًا', stats.active30, `من ${n(stats.devices)} جهاز مسجّل`)}
  </div>
  <div class="card chart" style="margin-top:14px">
    <h3>المستخدمون النشطون يوميًا · آخر 30 يومًا</h3>
    ${dailyChart(stats.daily)}
    <details><summary>عرض الأرقام</summary>
      <table><thead><tr><th>اليوم</th><th class="num">نشطون</th><th class="num">مستخدمون جدد</th></tr></thead><tbody>
      ${[...stats.daily].reverse().map((d) => `<tr><td>${dayLabel(d.day)}</td><td class="num">${n(d.active)}</td><td class="num">${n(d.joined)}</td></tr>`).join('')}
      </tbody></table>
    </details>
  </div>
  <div class="grid g2" style="margin-top:14px">
    <div class="card">
      <h3>الدول</h3>
      ${top.length ? `<div class="rows">${countryRows}</div>${rest ? `<p class="hint">ودول أخرى: ${n(rest)} جهاز</p>` : ''}` : '<div class="empty">لا توجد أجهزة بعد</div>'}
    </div>
    <div class="card">
      <h3>المنصات</h3>
      <div class="split" role="img" aria-label="آيفون ${n(p.ios)}، أندرويد ${n(p.android)}">
        ${p.ios ? `<span style="width:${pct(p.ios)}%;background:var(--mark)"></span>` : ''}
        ${p.android ? `<span style="width:${pct(p.android)}%;background:#4d7fb5"></span>` : ''}
        ${p.other ? `<span style="width:${pct(p.other)}%;background:var(--muted)"></span>` : ''}
      </div>
      <div class="legend">
        <span><i style="background:var(--mark)"></i>آيفون ${n(p.ios)}</span>
        <span><i style="background:#4d7fb5"></i>أندرويد ${n(p.android)}</span>
        ${p.other ? `<span><i style="background:var(--muted)"></i>أخرى ${n(p.other)}</span>` : ''}
      </div>
      <h3 style="margin-top:20px">الاستخدام</h3>
      <ul class="list">
        <li><span>أدوية نشطة</span><b>${n(stats.medicines)}</b></li>
        <li><span>جرعات أُخذت خلال 7 أيام</span><b>${n(stats.dosesTaken7)}</b></li>
        <li><span>طلبات مساعدة خلال 30 يومًا</span><b>${n(stats.help30)}</b></li>
        <li><span>روابط عائلية نشطة</span><b>${n(stats.familyLinks)}</b></li>
      </ul>
    </div>
  </div>
  </section>`;
}

function serviceSection(settings: Settings) {
  return `<section id="service">
  <h2>حالة الخدمة</h2>
  <p class="sub">إيقاف الخدمة يعرض للمستخدمين «الخدمة متوقفة مؤقتًا» مع زري الاتصال بالطوارئ.</p>
  <div class="card">
  ${settings.serviceOn
    ? `<div class="state ok">الخدمة <b>تعمل</b>.</div>
    <form method="post" action="/forceupdate">
      <input type="hidden" name="section" value="service"><input type="hidden" name="serviceOn" value="0">
      <label class="check"><input type="checkbox" name="confirm" value="1" required> أؤكد إيقاف الخدمة لكل المستخدمين</label>
      <button class="btn danger" type="submit">إيقاف الخدمة</button>
    </form>`
    : `<div class="state bad">الخدمة <b>متوقفة</b>: التطبيقات تعرض «الخدمة متوقفة مؤقتًا»، والخادم يرفض الطلبات، وتنبيهات العائلة المجدولة متوقفة. تذكيرات الأدوية في الجوالات تبقى تعمل.</div>
    <form method="post" action="/forceupdate">
      <input type="hidden" name="section" value="service"><input type="hidden" name="serviceOn" value="1">
      <button class="btn" type="submit">تشغيل الخدمة</button>
    </form>`}
  </div>
  </section>`;
}

function platformCard(title: string, section: 'ios' | 'android', rule: PlatformRule, hint: string) {
  const status = rule.enabled
    ? `<div class="state warn">مفعّل: كل نسخة رقمها أقل من <b>${rule.build}</b> تظهر لها شاشة التحديث.${rule.enforce ? ' <b>والخادم يرفض اتصالها.</b>' : ''}</div>`
    : `<div class="state">متوقف.</div>`;
  return `<div class="card">
  <h3>${title}</h3>
  ${status}
  <form method="post" action="/forceupdate">
    <input type="hidden" name="section" value="${section}">
    <label class="check"><input type="checkbox" name="enabled" value="1"${rule.enabled ? ' checked' : ''}> تفعيل التحديث الإلزامي</label>
    <label for="build-${section}">أقل رقم بناء مسموح</label>
    <input id="build-${section}" name="build" type="number" inputmode="numeric" min="1" max="100000" value="${rule.build || ''}" required>
    <p class="hint">${hint}</p>
    <label for="url-${section}">رابط التحديث (المتجر أو TestFlight)</label>
    <input id="url-${section}" name="url" type="url" dir="ltr" maxlength="300" value="${escapeHtml(rule.url)}">
    <label class="check"><input type="checkbox" name="enforce" value="1"${rule.enforce ? ' checked' : ''}> ارفض النسخ القديمة من الخادم أيضًا</label>
    <p class="hint">مع هذا الخيار لا تتصل النسخة الأقدم بالخادم إطلاقًا. النسخ التي لا ترسل رقمها تُعامل كقديمة، فلا تفعّله حتى يحدّث الناس.</p>
    <button class="btn" type="submit">حفظ</button>
  </form>
  </div>`;
}

function serverSection(settings: Settings, configUrl: string) {
  return `<section id="server">
  <h2>عنوان الخادم</h2>
  <p class="sub">التطبيقات تقرأ هذا العنوان عند كل فتح من <code class="inline">${escapeHtml(configUrl)}</code>، فإذا نقلت الخادم غيّره هنا ولا حاجة لتحديث التطبيق.</p>
  <div class="card">
    <div class="state${settings.apiUrl ? ' warn' : ''}">${settings.apiUrl
      ? `التطبيقات تتصل الآن بـ <code class="inline">${escapeHtml(settings.apiUrl)}</code>`
      : 'التطبيقات تستخدم العنوان المدمج فيها (الافتراضي).'}</div>
    <form method="post" action="/forceupdate">
      <input type="hidden" name="section" value="server">
      <label for="api-url">عنوان الخادم (اتركه فارغًا للعنوان الافتراضي)</label>
      <input id="api-url" name="apiUrl" type="url" dir="ltr" maxlength="200" placeholder="https://api.example.com" value="${escapeHtml(settings.apiUrl)}">
      <p class="hint">يبدأ بـ https:// وبلا مسار. قبل الحفظ يتحقق الخادم من أن العنوان الجديد يجيب على <code class="inline">/health</code>، والتطبيق يتحقق مرة أخرى قبل أن ينتقل إليه، فلا ينقطع المستخدمون بعنوان خاطئ.</p>
      <button class="btn" type="submit">حفظ العنوان</button>
    </form>
  </div>
  </section>`;
}

function partnerStatus(p: PartnerRow) {
  if (!p.active) return '<span class="pill off">موقوفة</span>';
  if (p.failed) return `<span class="pill warn">${n(p.failed)} تسليم فشل</span>`;
  return '<span class="pill ok">مفعّلة</span>';
}

function partnersSection(partners: PartnerRow[], reveal: Reveal | null) {
  const action = (section: string, id: string, label: string, cls = 'ghost', extra = '') =>
    `<form method="post" action="/forceupdate"><input type="hidden" name="section" value="${section}"><input type="hidden" name="id" value="${escapeHtml(id)}">${extra}<button class="btn small ${cls}" type="submit">${label}</button></form>`;
  const rows = partners
    .map((p) => {
      const countries = countriesOf(p);
      return `<tr>
        <td><b>${escapeHtml(p.name)}</b><div class="hint" dir="ltr" style="text-align:right">${p.webhook_url ? escapeHtml(p.webhook_url) : 'بلا webhook (تستعلم بنفسها)'}</div></td>
        <td>${countries.length ? countries.map((c) => `${flag(c)} ${escapeHtml(countryName(c))}`).join('، ') : 'كل الدول'}</td>
        <td class="num">${n(p.dispatched)}</td>
        <td>${partnerStatus(p)}</td>
        <td><div class="actions">
          ${action('partner-toggle', p.id, p.active ? 'إيقاف' : 'تفعيل', 'ghost', `<input type="hidden" name="active" value="${p.active ? '0' : '1'}">`)}
          ${action('partner-rotate', p.id, 'مفاتيح جديدة')}
          ${action('partner-delete', p.id, 'حذف', 'danger', '<label class="check" style="margin:0"><input type="checkbox" name="confirm" value="1" required> تأكيد</label>')}
        </div></td></tr>`;
    })
    .join('');
  return `<section id="partners">
  <h2>الجهات الطبية</h2>
  <p class="sub">الخادم جاهز للربط مع الإسعاف والمستشفيات: حين يضغط الكبير زر المساعدة يصل الطلب إلى العائلة كالمعتاد، وإلى الجهات المفعّلة في دولته أيضًا (إن وافق الكبير على المشاركة). والجهة ترد «استلمنا / وصلنا» فيُبلَّغ الكبير والعائلة.</p>
  ${reveal ? `<div class="secret">
    <b>مفاتيح «${escapeHtml(reveal.name)}»: انسخها الآن وأرسلها للجهة بطريقة آمنة، لن تظهر مرة أخرى.</b>
    <div style="margin-top:10px">مفتاح الواجهة (API key):</div><code>${escapeHtml(reveal.key)}</code>
    <div>سر التوقيع (Webhook secret):</div><code>${escapeHtml(reveal.secret)}</code>
  </div>` : ''}
  <div class="card" style="margin-top:14px">
    ${partners.length
      ? `<div style="overflow-x:auto"><table><thead><tr><th>الجهة</th><th>الدول</th><th class="num">طلبات وصلتها</th><th>الحالة</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>`
      : '<div class="empty">لا توجد جهات مربوطة بعد. أضف جهة حين تطلب الربط.</div>'}
  </div>
  <div class="grid g2" style="margin-top:14px">
    <div class="card">
      <h3>إضافة جهة</h3>
      <form method="post" action="/forceupdate">
        <input type="hidden" name="section" value="partner-add">
        <label for="p-name">اسم الجهة كما يظهر للكبير</label>
        <input id="p-name" name="name" type="text" maxlength="80" required placeholder="هيئة الهلال الأحمر السعودي">
        <label for="p-hook">رابط استقبال الطلبات (Webhook) · اختياري</label>
        <input id="p-hook" name="webhook" type="url" dir="ltr" maxlength="300" placeholder="https://partner.example.com/afiati">
        <label for="p-countries">الدول التي تخدمها · اختياري</label>
        <input id="p-countries" name="countries" type="text" dir="ltr" maxlength="200" placeholder="SA, AE">
        <p class="hint">رموز الدول من حرفين مفصولة بفواصل. فارغ = كل الدول.</p>
        <button class="btn" type="submit">إضافة وإنشاء المفاتيح</button>
      </form>
    </div>
    <div class="card">
      <h3>كيف يعمل الربط</h3>
      <ul class="list">
        <li><span>الجهة تستلم الطلب على رابطها، موقّعًا بسرّها (<code class="inline">X-Afiati-Signature</code>)</span></li>
        <li><span>أحداث: <code class="inline">help.created</code> ثم <code class="inline">help.location</code> و<code class="inline">help.resolved</code></span></li>
        <li><span>ترد بـ <code class="inline">POST /v1/partner/help/:id/status</code>: acknowledged، arrived، closed</span></li>
        <li><span>بلا رابط؟ تستعلم بـ <code class="inline">GET /v1/partner/help/active</code></span></li>
        <li><span>التسليم الفاشل يُعاد كل دقيقة لمدة ساعة. الدليل الكامل: <code class="inline">docs/partner-api.md</code></span></li>
        <li><span>لا يصل للجهة شيء إلا من كبير فعّل «مشاركة طلب المساعدة مع الجهات الطبية» في التطبيق.</span></li>
      </ul>
    </div>
  </div>
  </section>`;
}

export function controlPage(options: {
  settings: Settings;
  stats: Stats;
  partners: PartnerRow[];
  user: string;
  configUrl: string;
  message?: string;
  ok?: boolean;
  reveal?: Reveal | null;
}) {
  const { settings, stats, partners, user, configUrl, message = '', ok = true, reveal = null } = options;
  return html(`<header class="top"><div class="in">
    <div class="brand">${LOGO}<span>عافيتي · لوحة التحكم</span></div>
    ${settings.serviceOn ? '<span class="pill ok">الخدمة تعمل</span>' : '<span class="pill bad">الخدمة متوقفة</span>'}
    <span class="who">مسجّل الدخول: <span dir="ltr">${escapeHtml(user)}</span></span>
  </div>
  <nav><a href="#stats">الإحصاءات</a><a href="#service">الخدمة</a><a href="#update">التحديث الإلزامي</a><a href="#server">عنوان الخادم</a><a href="#partners">الجهات الطبية</a></nav>
  </header>
  <main>
  ${message ? `<div class="msg ${ok ? 'ok' : 'err'}" role="status">${escapeHtml(message)}</div>` : ''}
  ${statsSection(stats)}
  ${serviceSection(settings)}
  <section id="update">
    <h2>التحديث الإلزامي</h2>
    <p class="sub">النسخ الأقدم من الرقم المحدد تعرض شاشة «يتوفر تحديث» مع رابط التحديث.</p>
    <div class="grid g2">
      ${platformCard('الآيفون', 'ios', settings.ios, 'الرقم بين القوسين في TestFlight أو في «عن التطبيق».')}
      ${platformCard('أندرويد', 'android', settings.android, 'الرقم بين القوسين في «عن التطبيق».')}
    </div>
  </section>
  ${serverSection(settings, configUrl)}
  ${partnersSection(partners, reveal)}
  </main>`);
}

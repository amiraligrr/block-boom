<?php
/**
 * monitor.php — live gameplay monitoring.
 *
 * Answers the questions the other pages cannot: is anyone playing *right now*,
 * how hard, and is the combo mechanic landing. Everything refreshes on a timer
 * against monitor_data.php rather than reloading, so this page can be left open
 * on a second monitor without generating a request storm on a shared host.
 *
 * ── A warning this page has to carry ──────────────────────────────────────
 * Everything shown here is reported by the client and is therefore forgeable.
 * It is the same trust level as ads.php. It is explicitly NOT the same as the
 * leaderboard, which is authenticated by server-signed run tokens. The banner
 * at the top says so, because a monitoring page that reads as authoritative is
 * how a forgeable metric ends up driving a decision.
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/layout.php';
requireAuth();

renderHead('مانیتورینگ زنده', 'monitor', true);
renderPageHeader(
    'مانیتورینگ زنده',
    'بازیکنان آنلاین، اجراها و کمبوها — هر ۵ ثانیه به‌روزرسانی می‌شود',
    '<button id="pauseBtn" class="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold transition-colors">'
    . '<i class="fas fa-pause ml-1"></i> توقف به‌روزرسانی</button>'
);
?>

<!-- Trust banner. Not decoration: every figure on this page is client-reported. -->
<div class="mb-6 bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-start gap-3">
    <i class="fas fa-triangle-exclamation text-amber-500 mt-0.5 shrink-0"></i>
    <p class="text-amber-800 text-sm leading-6">
        این اعداد از سمت کلاینت گزارش می‌شوند و برای پایش رفتاری‌اند، نه امنیتی.
        امتیاز و جایگاه بازیکن در لیدربرد با توکن امضاشدهٔ سرور اعتبارسنجی می‌شود و
        از این جدول‌ها مستقل است. برای مقابله با تقلب، صفحهٔ
        <a href="integrity.php" class="font-semibold underline">ضدتقلب</a> را ببینید.
    </p>
</div>

<div id="healthStrip" class="mb-6 flex flex-wrap gap-2 text-xs"></div>

<!-- ── live tiles ───────────────────────────────────────────────────────── -->
<div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-emerald-600 text-xs font-medium">آنلاین الان (۵ دقیقه)</p>
                <p class="text-3xl font-black text-emerald-900 mt-1" id="kpiOnline">—</p>
                <p class="text-emerald-500 text-xs mt-1" id="kpiOnlineSub">&nbsp;</p>
            </div>
            <div class="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center">
                <i class="fas fa-signal text-white text-sm"></i>
            </div>
        </div>
    </div>
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-emerald-600 text-xs font-medium">اجرای بازی (۲۴ ساعت)</p>
                <p class="text-3xl font-black text-emerald-900 mt-1" id="kpiRuns">—</p>
                <p class="text-emerald-500 text-xs mt-1" id="kpiRunsSub">&nbsp;</p>
            </div>
            <div class="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-sky-500 to-cyan-600 flex items-center justify-center">
                <i class="fas fa-play text-white text-sm"></i>
            </div>
        </div>
    </div>
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-emerald-600 text-xs font-medium">بلوک شکسته (۲۴ ساعت)</p>
                <p class="text-3xl font-black text-emerald-900 mt-1" id="kpiBlocks">—</p>
                <p class="text-emerald-500 text-xs mt-1" id="kpiBlocksSub">&nbsp;</p>
            </div>
            <div class="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center">
                <i class="fas fa-cubes text-white text-sm"></i>
            </div>
        </div>
    </div>
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-emerald-600 text-xs font-medium">بهترین کمبو (۲۴ ساعت)</p>
                <p class="text-3xl font-black text-emerald-900 mt-1" id="kpiCombo">—</p>
                <p class="text-emerald-500 text-xs mt-1" id="kpiComboSub">&nbsp;</p>
            </div>
            <div class="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center">
                <i class="fas fa-fire text-white text-sm"></i>
            </div>
        </div>
    </div>
</div>

<!-- ── charts ───────────────────────────────────────────────────────────── -->
<div class="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-center justify-between mb-4">
            <h2 class="font-bold text-emerald-900">بازیکنان فعال — ۲۴ ساعت گذشته</h2>
            <span class="text-xs text-emerald-500">تعداد بازیکن یکتا در هر ساعت</span>
        </div>
        <?php chartBox('playersChart', 240, 'بازیکنان فعال در هر ساعت'); ?>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-center justify-between mb-4">
            <h2 class="font-bold text-emerald-900">اجرا و لول — ۲۴ ساعت گذشته</h2>
            <span class="text-xs text-emerald-500">اجرا = شروع بازی، لول = اتمام لول</span>
        </div>
        <?php chartBox('runsChart', 240, 'اجرا و لول در هر ساعت'); ?>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-center justify-between mb-4">
            <h2 class="font-bold text-emerald-900">توزیع کمبو — ۷ روز</h2>
            <span class="text-xs text-emerald-500">چند بار به هر طول زنجیره رسیدیم</span>
        </div>
        <?php chartBox('comboChart', 240, 'توزیع کمبو'); ?>
    </div>

    <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
        <div class="flex items-center justify-between mb-4">
            <h2 class="font-bold text-emerald-900">شدت بازی — ۲۴ ساعت گذشته</h2>
            <span class="text-xs text-emerald-500">شوت‌ها و دقیقهٔ بازی در هر ساعت</span>
        </div>
        <?php chartBox('intensityChart', 240, 'شوت و زمان بازی'); ?>
    </div>
</div>

<!-- ── live feed + versions + outcomes ──────────────────────────────────── -->
<div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
    <div class="xl:col-span-2 bg-white/90 rounded-2xl shadow-lg border border-emerald-100 overflow-hidden">
        <div class="px-5 py-4 border-b border-emerald-100 flex items-center justify-between">
            <h2 class="font-bold text-emerald-900 flex items-center gap-2">
                <span class="relative flex h-2.5 w-2.5">
                    <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span class="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                </span>
                رویدادهای زنده
            </h2>
            <span class="text-xs text-emerald-500">۲۵ رویداد آخر</span>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead class="bg-emerald-50 text-emerald-600 text-xs">
                    <tr>
                        <th class="px-4 py-3 text-right font-bold">زمان</th>
                        <th class="px-4 py-3 text-right font-bold">رویداد</th>
                        <th class="px-4 py-3 text-right font-bold">دستگاه</th>
                        <th class="px-4 py-3 text-right font-bold">لول</th>
                        <th class="px-4 py-3 text-right font-bold">مقدار</th>
                        <th class="px-4 py-3 text-right font-bold">جزئیات</th>
                    </tr>
                </thead>
                <tbody id="feedBody" class="divide-y divide-emerald-50">
                    <tr><td colspan="6" class="px-4 py-10 text-center text-emerald-400">در حال بارگذاری…</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <div class="space-y-6">
        <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
            <h2 class="font-bold text-emerald-900 mb-4">پایان اجراها (۲۴ ساعت)</h2>
            <div id="outcomeList" class="space-y-2 text-sm text-emerald-700">—</div>
            <div class="mt-4 pt-4 border-t border-emerald-100 text-xs text-emerald-600 space-y-1" id="durationBox"></div>
        </div>

        <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
            <h2 class="font-bold text-emerald-900 mb-1">نسخه‌های فعال</h2>
            <p class="text-xs text-emerald-500 mb-4">اگر نسخه‌ای ناگهان ناپدید شد، یعنی قبل از پایان onCreate کرش می‌کند.</p>
            <div id="versionList" class="space-y-2 text-sm text-emerald-700">—</div>
        </div>

        <div class="bg-white/90 rounded-2xl shadow-lg p-5 border border-emerald-100">
            <h2 class="font-bold text-emerald-900 mb-4">عمیق‌ترین لول (۷ روز)</h2>
            <div id="levelList" class="space-y-2 text-sm text-emerald-700">—</div>
        </div>
    </div>
</div>

<script>
const FA = 'fa-IR';
const nf = n => Number(n || 0).toLocaleString(FA);
const short = n => {
    n = Number(n || 0);
    if (n < 1000) return n.toLocaleString(FA);
    if (n < 1e6) return (n / 1000).toFixed(1) + 'هزار';
    return (n / 1e6).toFixed(1) + 'م';
};
const clockOf = ts => {
    const d = new Date(ts * 1000);
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
};
const agoOf = ts => {
    const s = Math.max(0, Math.floor(Date.now() / 1000 - ts));
    if (s < 60) return s + ' ثانیه پیش';
    if (s < 3600) return Math.floor(s / 60) + ' دقیقه پیش';
    if (s < 86400) return Math.floor(s / 3600) + ' ساعت پیش';
    return Math.floor(s / 86400) + ' روز پیش';
};
const durOf = ms => {
    if (!ms) return '—';
    const s = Math.round(ms / 1000);
    return s < 60 ? s + ' ثانیه' : Math.floor(s / 60) + 'د ' + (s % 60) + 'ث';
};

// Chart.js defaults, so every chart on the page matches without repeating these.
Chart.defaults.font.family = 'Vazirmatn, system-ui, sans-serif';
Chart.defaults.color = '#047857';
Chart.defaults.plugins.legend.labels.usePointStyle = true;
Chart.defaults.plugins.legend.labels.boxWidth = 8;

const grid = { color: 'rgba(16,185,129,.10)' };
const baseOpts = (yLabel) => ({
    responsive: true, maintainAspectRatio: false, resizeDelay: 120,
    interaction: { mode: 'index', intersect: false },
    scales: {
        x: { grid: { display: false } },
        y: { beginAtZero: true, grid: grid, title: { display: !!yLabel, text: yLabel } }
    },
    plugins: { legend: { position: 'bottom' } }
});

const charts = {};

function upsert(id, cfg) {
    if (charts[id]) { charts[id].data = cfg.data; charts[id].options = cfg.options; charts[id].update('none'); }
    else charts[id] = new Chart(document.getElementById(id), cfg);
}

const EVENT_STYLE = {
    run_start:      ['bg-sky-100 text-sky-700',        'شروع اجرا'],
    run_end:        ['bg-rose-100 text-rose-700',      'پایان اجرا'],
    level_complete: ['bg-emerald-100 text-emerald-700','اتمام لول'],
    combo:          ['bg-amber-100 text-amber-700',    'کمبو'],
    powerup:        ['bg-violet-100 text-violet-700',  'پاورآپ'],
    session_start:  ['bg-slate-100 text-slate-700',    'شروع نشست']
};

function renderFeed(feed) {
    const body = document.getElementById('feedBody');
    if (!feed || !feed.length) {
        body.innerHTML = '<tr><td colspan="6" class="px-4 py-10 text-center text-emerald-400">هنوز رویدادی ثبت نشده</td></tr>';
        return;
    }
    body.innerHTML = feed.map(e => {
        const st = EVENT_STYLE[e.type] || ['bg-emerald-50 text-emerald-700', e.type];
        const dev = e.device.length > 12 ? e.device.slice(0, 10) + '…' : e.device;
        return '<tr class="hover:bg-emerald-50/60">'
            + '<td class="px-4 py-2.5 text-emerald-500 text-xs whitespace-nowrap">' + clockOf(e.at) + '</td>'
            + '<td class="px-4 py-2.5"><span class="px-2 py-1 rounded-lg text-xs font-semibold ' + st[0] + '">' + st[1] + '</span></td>'
            + '<td class="px-4 py-2.5 text-emerald-500 font-mono text-xs" dir="ltr">' + dev + '</td>'
            + '<td class="px-4 py-2.5 text-emerald-700">' + (e.level || '—') + '</td>'
            + '<td class="px-4 py-2.5 text-emerald-700">' + (e.value ? nf(e.value) : '—') + '</td>'
            + '<td class="px-4 py-2.5 text-emerald-500 text-xs">' + (e.meta || (e.duration ? durOf(e.duration) : '—')) + '</td>'
            + '</tr>';
    }).join('');
}

function renderHealth(h) {
    const strip = document.getElementById('healthStrip');
    const chip = (ok, label, val) =>
        '<span class="px-3 py-1.5 rounded-xl font-semibold ' + (ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700') + '">'
        + '<i class="fas ' + (ok ? 'fa-circle-check' : 'fa-triangle-exclamation') + ' ml-1"></i>'
        + label + ': ' + val + '</span>';
    strip.innerHTML =
        chip(h.tables, 'جداول تله‌متری', h.tables ? 'نصب است' : 'نصب نیست — MIGRATION را اجرا کنید')
        + chip(h.query_ms < 250, 'پاسخ کوئری', h.query_ms + ' میلی‌ثانیه')
        + chip(h.devices_1h > 0, 'دستگاه فعال (۱ ساعت)', nf(h.devices_1h))
        + chip(h.packets_1h > 0, 'رویداد (۱ ساعت)', nf(h.packets_1h));
}

function refresh() {
    fetch('monitor_data.php', { headers: { 'X-Requested-With': 'XMLHttpRequest' }, cache: 'no-store' })
        .then(r => { if (r.status === 401) { throw new Error('unauthorized'); } return r.json(); })
        .then(d => {
            if (!d || !d.ok) return;
            const L = d.live, T = d.today, S = d.series;

            document.getElementById('kpiOnline').textContent = nf(L.online_now);
            document.getElementById('kpiOnlineSub').textContent =
                nf(L.online_15m) + ' نفر در ۱۵ دقیقه · ' + nf(L.ever_seen) + ' بازیکن کل';

            document.getElementById('kpiRuns').textContent = nf(T.runs);
            document.getElementById('kpiRunsSub').textContent =
                nf(T.levels) + ' لول · ' + nf(T.active_min) + ' دقیقه بازی';

            document.getElementById('kpiBlocks').textContent = short(T.blocks);
            document.getElementById('kpiBlocksSub').textContent =
                nf(T.shots) + ' شوت · ' + nf(T.coins) + ' سکه';

            document.getElementById('kpiCombo').textContent = T.max_combo ? 'x' + nf(T.max_combo) : '—';
            document.getElementById('kpiComboSub').textContent =
                'میانهٔ اجرا ' + durOf(T.median_run_ms);

            renderHealth(d.health);

            upsert('playersChart', {
                type: 'line',
                data: {
                    labels: S.labels,
                    datasets: [{
                        label: 'بازیکن', data: S.players, borderColor: '#10B981',
                        backgroundColor: 'rgba(16,185,129,.15)', fill: true, tension: .35,
                        pointRadius: 0, borderWidth: 2
                    }]
                },
                options: baseOpts('نفر')
            });

            upsert('runsChart', {
                type: 'bar',
                data: {
                    labels: S.labels,
                    datasets: [
                        { label: 'اجرا', data: S.runs, backgroundColor: '#0EA5E9', borderRadius: 4 },
                        { label: 'لول', data: S.levels, backgroundColor: '#34D399', borderRadius: 4 }
                    ]
                },
                options: baseOpts('تعداد')
            });

            upsert('intensityChart', {
                type: 'line',
                data: {
                    labels: S.labels,
                    datasets: [
                        {
                            label: 'شوت (ده‌تایی)', data: S.shots.map(v => Math.round(v / 10)),
                            borderColor: '#8B5CF6', backgroundColor: 'rgba(139,92,246,.12)',
                            fill: true, tension: .35, pointRadius: 0, borderWidth: 2, yAxisID: 'y'
                        },
                        {
                            label: 'دقیقهٔ بازی', data: S.active_min,
                            borderColor: '#F59E0B', backgroundColor: 'transparent',
                            tension: .35, pointRadius: 0, borderWidth: 2,
                            borderDash: [5, 4], yAxisID: 'y1'
                        }
                    ]
                },
                options: (() => {
                    const o = baseOpts('');
                    o.scales.y.title.text = 'شوت (ده‌تایی)';
                    o.scales.y1 = {
                        beginAtZero: true, position: 'right', grid: { display: false },
                        title: { display: true, text: 'دقیقه' }
                    };
                    return o;
                })()
            });

            const combos = d.combos || [];
            upsert('comboChart', {
                type: 'bar',
                data: {
                    labels: combos.map(c => 'x' + c.value),
                    datasets: [{
                        label: 'تعداد', data: combos.map(c => c.count),
                        backgroundColor: '#F59E0B', borderRadius: 4
                    }]
                },
                options: baseOpts('دفعه')
            });

            renderFeed(d.feed);

            const out = d.outcomes || [];
            document.getElementById('outcomeList').innerHTML = out.length
                ? out.map(o => {
                    const label = o.outcome === 'hit' ? 'بلوک به توپ خورد'
                        : o.outcome === 'gameover' ? 'اتمام اجرا'
                        : o.outcome === 'quit' ? 'خروج دستی'
                        : o.outcome;
                    return '<div class="flex items-center justify-between"><span>' + label
                        + '</span><span class="font-bold">' + nf(o.count) + '</span></div>';
                }).join('')
                : '<span class="text-emerald-400">—</span>';

            document.getElementById('durationBox').innerHTML =
                '<div class="flex justify-between"><span>میانگین طول اجرا</span><span class="font-bold">' + durOf(T.avg_run_ms) + '</span></div>'
                + '<div class="flex justify-between"><span>میانهٔ طول اجرا</span><span class="font-bold">' + durOf(T.median_run_ms) + '</span></div>';

            const vs = d.versions || [];
            document.getElementById('versionList').innerHTML = vs.length
                ? vs.map(v => '<div class="flex items-center justify-between"><span class="font-mono" dir="ltr">'
                    + v.version + '</span><span class="text-xs text-emerald-500">' + nf(v.devices)
                    + ' دستگاه · ' + agoOf(v.last_seen) + '</span></div>').join('')
                : '<span class="text-emerald-400">—</span>';

            const tl = d.top_levels || [];
            document.getElementById('levelList').innerHTML = tl.length
                ? tl.map(l => '<div class="flex items-center justify-between"><span>لول '
                    + nf(l.level) + '</span><span class="font-bold">' + nf(l.players)
                    + ' بازیکن</span></div>').join('')
                : '<span class="text-emerald-400">—</span>';
        })
        .catch(err => {
            if (String(err.message).includes('unauthorized')) {
                window.location.href = 'login.php';
            }
        });
}

// Polling. 5s while the tab is visible; stopped entirely while hidden, because
// a monitor left open on a hidden tab hammering the API every 5 seconds is a
// self-inflicted load problem on a shared host.
let paused = false;
let timer = null;

function schedule() {
    clearInterval(timer);
    if (paused) return;
    timer = setInterval(() => { if (!document.hidden) refresh(); }, 5000);
}

document.getElementById('pauseBtn').addEventListener('click', function () {
    paused = !paused;
    this.innerHTML = paused
        ? '<i class="fas fa-play ml-1"></i> ادامهٔ به‌روزرسانی'
        : '<i class="fas fa-pause ml-1"></i> توقف به‌روزرسانی';
    schedule();
});

document.addEventListener('visibilitychange', schedule);
refresh();
schedule();
</script>
<?php renderFoot(); ?>

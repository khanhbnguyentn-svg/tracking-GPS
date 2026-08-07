'use strict';

const dashboardHtml = `<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Thiết bị GPS</title><style>
:root{font-family:system-ui,sans-serif;color:#14213d;background:#f5f7fb}body{margin:0}.wrap{max-width:1200px;margin:auto;padding:24px}
h1{margin:0 0 8px}.muted{color:#53657d}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin:20px 0}
.card,table{background:white;border:1px solid #dce3ed;border-radius:10px;box-shadow:0 2px 8px #182b4910}.card{padding:16px}.value{font-size:1.7rem;font-weight:700}
.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:12px;border-bottom:1px solid #e8edf4;white-space:nowrap}
.status{font-weight:700}.active{color:#087f5b}.inactive{color:#b42318}@media(max-width:600px){.wrap{padding:14px}th,td{padding:9px;font-size:.88rem}}
</style></head><body><main class="wrap"><h1>Thiết bị GPS</h1><p class="muted">Mất dữ liệu không đồng nghĩa với tai nạn. Cập nhật tự động theo thời gian thực.</p>
<section class="cards"><div class="card"><div class="muted">Thiết bị</div><div class="value" id="devices">0</div></div><div class="card"><div class="muted">Đã nhận</div><div class="value" id="accepted">0</div></div><div class="card"><div class="muted">Bị từ chối</div><div class="value" id="rejected">0</div></div><div class="card"><div class="muted">Bản tin/giây</div><div class="value" id="rate">0</div></div></section>
<div class="table-wrap"><table><thead><tr><th>Trạng thái</th><th>Device ID</th><th>Vĩ độ</th><th>Kinh độ</th><th>Speed (knot)</th><th>Accuracy (m)</th><th>Server nhận</th></tr></thead><tbody id="rows"></tbody></table></div></main>
<script>
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function refresh(){const [stats,devices]=await Promise.all([fetch('/api/stats').then(r=>r.json()),fetch('/api/devices').then(r=>r.json())]);
for(const k of ['devices','accepted','rejected'])document.getElementById(k).textContent=stats[k];document.getElementById('rate').textContent=stats.recentPerSecond;
document.getElementById('rows').innerHTML=devices.map(d=>'<tr><td class="status '+esc(d.status)+'">'+(d.status==='active'?'Đang nhận':'Gián đoạn')+'</td><td>'+esc(d.deviceId)+'</td><td>'+esc(d.latitude)+'</td><td>'+esc(d.longitude)+'</td><td>'+esc(d.speedKnots)+'</td><td>'+esc(d.accuracyMeters)+'</td><td>'+esc(new Date(d.receivedAt).toLocaleString('vi-VN'))+'</td></tr>').join('');}
refresh();setInterval(refresh,15000);new EventSource('/events').onmessage=refresh;
</script></body></html>`;

module.exports = { dashboardHtml };

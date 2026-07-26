
const BASE = '';
let AUTH_TOKEN = '';

function setActive(el) {
    document.querySelectorAll('.sidebar a').forEach(a => a.classList.remove('active'));
    el.classList.add('active');
}
document.querySelectorAll('.sidebar a[data-tab]').forEach(a => {
    a.addEventListener('click', e => {
        e.preventDefault();
        setActive(a);
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
        document.getElementById(a.dataset.tab).classList.add('active');
        if (a.dataset.tab === 'mig-status') loadMigrationStatus();
        if (a.dataset.tab === 'overview') loadOverview();
    });
});

function show(id, text, isError) {
    const el = document.getElementById(id);
    el.style.color = isError ? '#f87171' : '#a5f3fc';
    el.textContent = typeof text === 'string' ? text : JSON.stringify(text, null, 2);
}

async function callGet(path, outId) {
    try {
        const r = await fetch(path);
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function callGetAuth(path, outId) {
    const token = document.getElementById('prof-token').value || AUTH_TOKEN;
    if (!token) { show(outId, 'No token provided', true); return; }
    try {
        const r = await fetch(path, { headers: { 'Authorization': 'Bearer ' + token } });
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function callPost(path, body, outId) {
    try {
        const r = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function callPostAuth(path, body, outId) {
    const token = document.getElementById('prof-token').value || AUTH_TOKEN;
    try {
        const r = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, body: JSON.stringify(body) });
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function callPutAuth(path, body, outId) {
    const token = document.getElementById('prof-token').value || AUTH_TOKEN;
    if (!token) { show(outId, 'No token', true); return; }
    try {
        const r = await fetch(path, { method: 'PUT', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, body: JSON.stringify(body) });
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function callDeleteAuth(path, outId) {
    const token = document.getElementById('prof-token').value || AUTH_TOKEN;
    if (!token) { show(outId, 'No token', true); return; }
    try {
        const r = await fetch(path, { method: 'DELETE', headers: { 'Authorization': 'Bearer ' + token } });
        const j = await r.json();
        show(outId, j);
    } catch(e) { show(outId, e.message, true); }
}

async function loadOverview() {
    try {
        const info = await fetch('/api/info').then(r => r.json());
        document.getElementById('ov-status').textContent = info.status;
        document.getElementById('ov-issuer').textContent = info.issuer;
    } catch(e) {}
    try {
        const st = await fetch('/api/migration/status').then(r => r.json());
        document.getElementById('ov-users').textContent = st.totalActiveUsersInIdPlatform || 0;
    } catch(e) {}
    try {
        const clients = await fetch('/oauth2/clients').then(r => r.json());
        document.getElementById('ov-clients').textContent = clients.length;
        const tbody = document.getElementById('clients-tbody');
        tbody.innerHTML = '';
        clients.forEach(c => {
            const tr = document.createElement('tr');
            tr.innerHTML = '<td>' + c.client_id + '</td><td>' + (c.client_name||'') + '</td><td>' + (c.redirect_uris||[]).join(', ') + '</td>';
            tbody.appendChild(tr);
        });
    } catch(e) {}
}

async function loadMigrationStatus() {
    try {
        const st = await fetch('/api/migration/status').then(r => r.json());
        show('mig-status-out', st);
        if (st.main) {
            document.getElementById('ms-phase').innerHTML = st.main.phase === 'CUTOVER_COMPLETE' ?
                '<span class="badge badge-green">' + st.main.phase + '</span>' :
                '<span class="badge badge-yellow">' + st.main.phase + '</span>';
            document.getElementById('ms-migrated').textContent = (st.main.migratedUsers||0) + '/' + (st.main.totalUsers||0);
        }
        if (st.ma) {
            document.getElementById('ma-phase').innerHTML = st.ma.phase === 'CUTOVER_COMPLETE' ?
                '<span class="badge badge-green">' + st.ma.phase + '</span>' :
                '<span class="badge badge-yellow">' + st.ma.phase + '</span>';
            document.getElementById('ma-migrated').textContent = (st.ma.migratedUsers||0) + '/' + (st.ma.totalUsers||0);
        }
    } catch(e) { show('mig-status-out', e.message, true); }
}

async function doRegister() {
    const body = { email: document.getElementById('reg-email').value, password: document.getElementById('reg-pass').value, displayName: document.getElementById('reg-name').value };
    await callPost('/api/auth/register', body, 'reg-out');
}

async function doLogin() {
    const body = { email: document.getElementById('login-email').value, password: document.getElementById('login-pass').value };
    try {
        const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        const j = await r.json();
        show('login-out', j);
        if (j.accessToken) { AUTH_TOKEN = j.accessToken; document.getElementById('prof-token').value = j.accessToken; }
    } catch(e) { show('login-out', e.message, true); }
}

function doOidcLogin() { window.location.href = '/oauth2/authorize?response_type=code&client_id=main-site&redirect_uri=http://localhost:3000/&scope=openid+profile+email&state=demo&code_challenge=demo&code_challenge_method=S256'; }

async function doUpdateProfile() {
    const body = {};
    if (document.getElementById('upd-name').value) body.displayName = document.getElementById('upd-name').value;
    if (document.getElementById('upd-email').value) body.email = document.getElementById('upd-email').value;
    await callPutAuth('/api/auth/me', body, 'upd-out');
}

async function doImport() {
    const site = document.getElementById('imp-site').value;
    const body = JSON.parse(document.getElementById('imp-body').value);
    await callPost('/api/migration/import?site=' + site, body, 'imp-out');
}

async function doCutover() {
    const site = document.getElementById('cut-site').value;
    await callPost('/api/migration/cutover?site=' + site, {}, 'cut-out');
}

async function doRollback() {
    const site = document.getElementById('rb-site').value;
    await callPost('/api/migration/rollback?site=' + site, {}, 'rb-out');
}

async function doShadowValidate() {
    const body = { email: document.getElementById('shad-email').value, password: document.getElementById('shad-pass').value };
    await callPost('/api/migration/shadow-validate', body, 'shad-out');
}

async function doDualWrite() {
    const site = document.getElementById('dw-site').value;
    const body = JSON.parse(document.getElementById('dw-body').value);
    await callPost('/api/migration/dual-write?site=' + site, body, 'dw-out');
}

async function doRegisterClient() {
    const body = {
        client_id: document.getElementById('creg-client-id').value,
        client_name: document.getElementById('creg-client-name').value,
        redirect_uris: document.getElementById('creg-redirects').value,
        scope: document.getElementById('creg-scopes').value
    };
    await callPost('/oauth2/register', body, 'creg-out');
    loadOverview();
    loadClientTable();
}

async function runFullDemo() {
    const btn = document.getElementById('run-demo-btn');
    const progress = document.getElementById('demo-progress');
    btn.disabled = true;
    btn.textContent = 'Running...';
    progress.innerHTML = '<div style="color:#fbbf24;font-size:14px">Executing migration phases...</div>';

    try {
        const resp = await fetch('/api/migration/run-demo', { method: 'POST' });
        const result = await resp.json();
        renderDemoResult(result);
    } catch(e) {
        progress.innerHTML = '<div style="color:#f87171">Error: ' + e.message + '</div>';
    }
    btn.disabled = false;
    btn.textContent = 'Run Full Demo';
}

function renderDemoResult(result) {
    const progress = document.getElementById('demo-progress');
    let html = '';

    const phaseColors = { 0: '#38bdf8', 1: '#a78bfa', 2: '#fbbf24', 3: '#34d399', 4: '#f87171' };
    const phaseIcons = { 0: '0', 1: '1', 2: '2', 3: '3', 4: '4' };

    if (result.phases) {
        result.phases.forEach(phase => {
            const color = phaseColors[phase.phase] || '#94a3b8';
            const icon = phaseIcons[phase.phase] || '?';
            const statusBadge = phase.success ?
                '<span class="badge badge-green">SUCCESS</span>' :
                '<span class="badge badge-red">FAILED</span>';

            html += '<div class="card" style="margin-bottom:12px;border-left:3px solid ' + color + '">';
            html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">';
            html += '<span style="background:' + color + ';color:#0f172a;width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px">' + icon + '</span>';
            html += '<h3 style="color:' + color + ';margin:0">Phase ' + phase.phase + ': ' + phase.name + '</h3>';
            html += statusBadge;
            html += '<span style="color:#64748b;font-size:12px;margin-left:auto">' + phase.durationMs + 'ms</span>';
            html += '</div>';
            html += '<p style="color:#94a3b8;font-size:13px;margin-bottom:8px">' + phase.message + '</p>';

            if (phase.steps) {
                html += '<table style="font-size:12px">';
                html += '<thead><tr><th>Step</th><th>Type</th><th>Result</th></tr></thead><tbody>';
                phase.steps.forEach(step => {
                    const stepBadge = step.success ?
                        '<span class="badge badge-green">OK</span>' :
                        '<span class="badge badge-red">FAIL</span>';
                    const detail = step.detail ? ' <span style="color:#64748b">(' + step.detail + ')</span>' : '';
                    html += '<tr><td>' + step.step + detail + '</td><td><code>' + step.type + '</code></td><td>' + stepBadge + '</td></tr>';
                });
                html += '</tbody></table>';
            }
            html += '</div>';
        });

        html += '<div class="card" style="border-left:3px solid #22c55e">';
        html += '<h3 style="color:#22c55e">Demo Complete</h3>';
        html += '<p style="color:#94a3b8;font-size:13px">Total duration: ' + result.durationMs + 'ms | Phases: ' + result.totalPhases + '</p>';
        html += '</div>';
    }

    progress.innerHTML = html;
}

loadOverview();
    loadClientTable();
}

async function loadClientTable() {
    try {
        const clients = await fetch('/oauth2/clients').then(r => r.json());
        const tbody = document.getElementById('clist-tbody');
        tbody.innerHTML = '';
        if (clients.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" style="color:#64748b">No clients registered yet</td></tr>';
            return;
        }
        clients.forEach(c => {
            const tr = document.createElement('tr');
            tr.innerHTML = '<td><code>' + c.client_id + '</code></td><td>' + (c.client_name||'') + '</td><td style="font-size:12px">' + (c.redirect_uris||[]).join('<br>') + '</td><td>' + (c.scope||'') + '</td>';
            tbody.appendChild(tr);
        });
    } catch(e) {
        document.getElementById('clist-tbody').innerHTML = '<tr><td colspan="4" style="color:#f87171">Error loading clients</td></tr>';
    }
}

loadOverview();
loadClientTable();

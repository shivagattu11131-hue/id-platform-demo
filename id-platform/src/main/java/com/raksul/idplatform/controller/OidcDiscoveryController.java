package com.raksul.idplatform.controller;

import com.raksul.idplatform.service.OidcService;
import com.raksul.idplatform.service.MigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OidcDiscoveryController {

    @Autowired
    private OidcService oidcService;

    @Autowired
    private MigrationService migrationService;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String root() {
        return DASHBOARD_HTML;
    }

    @GetMapping("/api/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "service", "Raksul ID Platform",
            "version", "1.0.0",
            "status", "running",
            "issuer", "http://localhost:3000",
            "endpoints", Map.of(
                "discovery", "http://localhost:3000/.well-known/openid-configuration",
                "jwks", "http://localhost:3000/jwks.json",
                "authorize", "http://localhost:3000/oauth2/authorize",
                "token", "http://localhost:3000/oauth2/token",
                "userinfo", "http://localhost:3000/oauth2/userinfo",
                "health", "http://localhost:3000/api/health"
            ),
            "registered_clients", Map.of(
                "main-site", "Main Site (Port 3001)",
                "ma-site", "MA Site (Port 3002)"
            )
        ));
    }

    @GetMapping("/api/migration/status")
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        return ResponseEntity.ok(migrationService.getMigrationStatus());
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> getDiscoveryDocument() {
        return ResponseEntity.ok(oidcService.getDiscoveryDocument());
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(oidcService.getJwks());
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "id-platform",
            "version", "1.0.0",
            "issuer", "http://localhost:3000"
        ));
    }

    private static final String DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Raksul ID Platform - Dashboard</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #0f172a; color: #e2e8f0; }
.topbar { background: #1e293b; border-bottom: 1px solid #334155; padding: 12px 24px; display: flex; align-items: center; gap: 12px; }
.topbar h1 { font-size: 18px; color: #38bdf8; }
.topbar .version { color: #64748b; font-size: 13px; }
.topbar .status { margin-left: auto; display: flex; align-items: center; gap: 6px; font-size: 13px; }
.topbar .dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; }
.layout { display: flex; min-height: calc(100vh - 49px); }
.sidebar { width: 220px; background: #1e293b; border-right: 1px solid #334155; padding: 16px 0; flex-shrink: 0; }
.sidebar .group { padding: 8px 16px; font-size: 11px; text-transform: uppercase; color: #64748b; letter-spacing: 1px; margin-top: 8px; }
.sidebar a { display: block; padding: 8px 16px; color: #94a3b8; text-decoration: none; font-size: 14px; border-left: 3px solid transparent; }
.sidebar a:hover { background: #334155; color: #e2e8f0; }
.sidebar a.active { background: #1e3a5f; color: #38bdf8; border-left-color: #38bdf8; }
.main { flex: 1; padding: 24px; overflow-y: auto; max-height: calc(100vh - 49px); }
.section { display: none; }
.section.active { display: block; }
.card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.card h2 { font-size: 16px; color: #f1f5f9; margin-bottom: 12px; }
.card h3 { font-size: 14px; color: #94a3b8; margin-bottom: 8px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.stat { background: #0f172a; border: 1px solid #334155; border-radius: 6px; padding: 16px; }
.stat .label { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.stat .value { font-size: 20px; font-weight: 600; color: #38bdf8; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.badge-green { background: #065f46; color: #34d399; }
.badge-yellow { background: #713f12; color: #fbbf24; }
.badge-blue { background: #1e3a5f; color: #38bdf8; }
.badge-red { background: #7f1d1d; color: #f87171; }
label { display: block; font-size: 13px; color: #94a3b8; margin-bottom: 4px; }
input, textarea, select { width: 100%; padding: 8px 12px; background: #0f172a; border: 1px solid #334155; border-radius: 6px; color: #e2e8f0; font-size: 13px; font-family: monospace; }
textarea { min-height: 80px; resize: vertical; }
.btn { padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 13px; margin-right: 8px; margin-top: 8px; }
.btn-blue { background: #1d4ed8; color: white; }
.btn-green { background: #16a34a; color: white; }
.btn-red { background: #dc2626; color: white; }
.btn-yellow { background: #ca8a04; color: white; }
.btn:hover { opacity: 0.9; }
.output { margin-top: 12px; padding: 12px; background: #0f172a; border: 1px solid #334155; border-radius: 6px; font-family: monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 400px; overflow-y: auto; color: #a5f3fc; }
.endpoint-list { list-style: none; }
.endpoint-list li { padding: 8px 0; border-bottom: 1px solid #1e293b; display: flex; align-items: center; gap: 8px; }
.method { display: inline-block; width: 56px; padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 700; text-align: center; }
.method-get { background: #166534; color: #4ade80; }
.method-post { background: #1e3a5f; color: #60a5fa; }
.method-put { background: #713f12; color: #fbbf24; }
.method-delete { background: #7f1d1d; color: #f87171; }
.row { display: flex; gap: 12px; margin-bottom: 12px; }
.row > * { flex: 1; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.tag-public { background: #166534; color: #4ade80; }
.tag-protected { background: #713f12; color: #fbbf24; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #334155; font-size: 13px; }
th { color: #64748b; font-weight: 600; }
</style>
</head>
<body>
<div class="topbar">
    <div class="dot"></div>
    <h1>Raksul ID Platform</h1>
    <span class="version">v1.0.0</span>
    <div class="status"><div class="dot"></div> Running on port 3000</div>
</div>
<div class="layout">
<div class="sidebar">
    <div class="group">Overview</div>
    <a href="#" data-tab="overview" class="active">Dashboard</a>
    <a href="#" data-tab="endpoints">All Endpoints</a>
    <div class="group">OIDC</div>
    <a href="#" data-tab="discovery">Discovery</a>
    <a href="#" data-tab="jwks">JWKS Keys</a>
    <div class="group">Auth</div>
    <a href="#" data-tab="register">Register</a>
    <a href="#" data-tab="login">Login</a>
    <a href="#" data-tab="profile">Profile</a>
    <div class="group">Migration</div>
    <a href="#" data-tab="mig-status">Status</a>
    <a href="#" data-tab="mig-import">Import</a>
    <a href="#" data-tab="mig-cutover">Cutover</a>
    <a href="#" data-tab="mig-rollback">Rollback</a>
    <a href="#" data-tab="mig-shadow">Shadow Validate</a>
    <a href="#" data-tab="mig-dualwrite">Dual Write</a>
</div>
<div class="main">

<div class="section active" id="overview">
    <div class="card">
        <h2>Service Overview</h2>
        <div class="grid">
            <div class="stat"><div class="label">Status</div><div class="value" id="ov-status">...</div></div>
            <div class="stat"><div class="label">Issuer</div><div class="value" id="ov-issuer" style="font-size:14px">...</div></div>
            <div class="stat"><div class="label">Total Users</div><div class="value" id="ov-users">...</div></div>
            <div class="stat"><div class="label">Clients</div><div class="value" id="ov-clients">...</div></div>
        </div>
    </div>
    <div class="card">
        <h2>Quick Health Check</h2>
        <button class="btn btn-blue" onclick="callGet('/api/health','ov-health')">Run Health Check</button>
        <div class="output" id="ov-health">Click to check...</div>
    </div>
    <div class="card">
        <h2>Registered Clients</h2>
        <table>
            <thead><tr><th>Client ID</th><th>Description</th><th>Redirect URI</th></tr></thead>
            <tbody>
                <tr><td>main-site</td><td>Main Site (Port 3001)</td><td>http://localhost:3001/callback</td></tr>
                <tr><td>ma-site</td><td>MA Site (Port 3002)</td><td>http://localhost:3002/callback</td></tr>
            </tbody>
        </table>
    </div>
</div>

<div class="section" id="endpoints">
    <div class="card">
        <h2>All API Endpoints</h2>
        <table>
            <thead><tr><th>Method</th><th>Path</th><th>Auth</th><th>Description</th></tr></thead>
            <tbody>
                <tr><td><span class="method method-get">GET</span></td><td>/</td><td><span class="tag tag-public">Public</span></td><td>Dashboard UI</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/api/info</td><td><span class="tag tag-public">Public</span></td><td>Service metadata</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/api/health</td><td><span class="tag tag-public">Public</span></td><td>Health check</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/.well-known/openid-configuration</td><td><span class="tag tag-public">Public</span></td><td>OIDC discovery document</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/jwks.json</td><td><span class="tag tag-public">Public</span></td><td>JSON Web Key Set</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/auth/register</td><td><span class="tag tag-public">Public</span></td><td>Register new user</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/auth/login</td><td><span class="tag tag-public">Public</span></td><td>Login user</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/api/auth/me</td><td><span class="tag tag-protected">Bearer</span></td><td>Get current user profile</td></tr>
                <tr><td><span class="method method-put">PUT</span></td><td>/api/auth/me</td><td><span class="tag tag-protected">Bearer</span></td><td>Update current user</td></tr>
                <tr><td><span class="method method-delete">DELETE</span></td><td>/api/auth/me</td><td><span class="tag tag-protected">Bearer</span></td><td>Deactivate current user</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/oauth2/authorize</td><td><span class="tag tag-public">Public</span></td><td>OIDC authorization endpoint</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/oauth2/authorize</td><td><span class="tag tag-public">Public</span></td><td>OIDC authorize (form submit)</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/oauth2/token</td><td><span class="tag tag-public">Public</span></td><td>Token exchange</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/oauth2/userinfo</td><td><span class="tag tag-protected">Bearer</span></td><td>Get user claims</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/oauth2/revoke</td><td><span class="tag tag-protected">Bearer</span></td><td>Revoke token</td></tr>
                <tr><td><span class="method method-get">GET</span></td><td>/api/migration/status</td><td><span class="tag tag-public">Public</span></td><td>Migration status</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/migration/import</td><td><span class="tag tag-public">Public</span></td><td>Bulk import users</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/migration/shadow-validate</td><td><span class="tag tag-public">Public</span></td><td>Shadow validate credentials</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/migration/dual-write</td><td><span class="tag tag-public">Public</span></td><td>Dual write user data</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/migration/cutover</td><td><span class="tag tag-public">Public</span></td><td>Execute cutover</td></tr>
                <tr><td><span class="method method-post">POST</span></td><td>/api/migration/rollback</td><td><span class="tag tag-public">Public</span></td><td>Rollback migration</td></tr>
            </tbody>
        </table>
    </div>
</div>

<div class="section" id="discovery">
    <div class="card">
        <h2>OIDC Discovery Document</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">GET /.well-known/openid-configuration</p>
        <button class="btn btn-blue" onclick="callGet('/.well-known/openid-configuration','disc-out')">Fetch</button>
        <div class="output" id="disc-out">Click Fetch to load...</div>
    </div>
</div>

<div class="section" id="jwks">
    <div class="card">
        <h2>JSON Web Key Set</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">GET /jwks.json</p>
        <button class="btn btn-blue" onclick="callGet('/jwks.json','jwks-out')">Fetch</button>
        <div class="output" id="jwks-out">Click Fetch to load...</div>
    </div>
</div>

<div class="section" id="register">
    <div class="card">
        <h2>Register New User</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/auth/register</p>
        <div class="row">
            <div><label>Email</label><input id="reg-email" placeholder="user@example.com"></div>
            <div><label>Password</label><input id="reg-pass" type="password" placeholder="password"></div>
        </div>
        <div class="row">
            <div><label>Display Name</label><input id="reg-name" placeholder="John Doe"></div>
        </div>
        <button class="btn btn-green" onclick="doRegister()">Register</button>
        <div class="output" id="reg-out">Result will appear here...</div>
    </div>
</div>

<div class="section" id="login">
    <div class="card">
        <h2>Login (Password Grant)</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/auth/login - Returns Bearer token</p>
        <div class="row">
            <div><label>Email</label><input id="login-email" placeholder="user@example.com"></div>
            <div><label>Password</label><input id="login-pass" type="password" placeholder="password"></div>
        </div>
        <button class="btn btn-green" onclick="doLogin()">Login</button>
        <button class="btn btn-blue" onclick="doOidcLogin()">OIDC Browser Login</button>
        <div class="output" id="login-out">Result will appear here...</div>
    </div>
</div>

<div class="section" id="profile">
    <div class="card">
        <h2>Current User Profile</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">GET /api/auth/me (requires Bearer token)</p>
        <label>Bearer Token</label>
        <textarea id="prof-token" placeholder="Paste token from login response..."></textarea>
        <button class="btn btn-blue" onclick="callGetAuth('/api/auth/me','prof-out')">Get Profile</button>
        <div class="output" id="prof-out">Paste token and click Get Profile...</div>
    </div>
    <div class="card">
        <h2>Update Profile</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">PUT /api/auth/me</p>
        <label>Display Name</label>
        <input id="upd-name" placeholder="New display name" style="margin-bottom:8px">
        <label>Email</label>
        <input id="upd-email" placeholder="New email" style="margin-bottom:8px">
        <button class="btn btn-yellow" onclick="doUpdateProfile()">Update</button>
        <div class="output" id="upd-out">Result will appear here...</div>
    </div>
    <div class="card">
        <h2>Deactivate Account</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">DELETE /api/auth/me</p>
        <button class="btn btn-red" onclick="callDeleteAuth('/api/auth/me','del-out')">Delete Account</button>
        <div class="output" id="del-out">Warning: This will deactivate the account...</div>
    </div>
</div>

<div class="section" id="mig-status">
    <div class="card">
        <h2>Migration Status</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">GET /api/migration/status</p>
        <button class="btn btn-blue" onclick="loadMigrationStatus()">Refresh</button>
        <div class="grid" style="margin-top:12px">
            <div class="stat"><div class="label">Main Site</div><div class="value" id="ms-phase">...</div></div>
            <div class="stat"><div class="label">Main - Migrated</div><div class="value" id="ms-migrated">...</div></div>
            <div class="stat"><div class="label">MA Site</div><div class="value" id="ma-phase">...</div></div>
            <div class="stat"><div class="label">MA - Migrated</div><div class="value" id="ma-migrated">...</div></div>
        </div>
        <div class="output" id="mig-status-out" style="margin-top:12px">Loading...</div>
    </div>
</div>

<div class="section" id="mig-import">
    <div class="card">
        <h2>Bulk Import Users</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/migration/import?site={main|ma}</p>
        <label>Site</label>
        <select id="imp-site" style="margin-bottom:8px">
            <option value="main">Main Site</option>
            <option value="ma">MA Site</option>
        </select>
        <label>Users JSON Array</label>
        <textarea id="imp-body" rows="6">[
  {"id":"99","email":"test@example.com","passwordHash":"abc123","displayName":"Test User","source":"main","createdAt":"2024-01-01"}
]</textarea>
        <button class="btn btn-green" onclick="doImport()">Import</button>
        <div class="output" id="imp-out">Result will appear here...</div>
    </div>
</div>

<div class="section" id="mig-cutover">
    <div class="card">
        <h2>Execute Cutover</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/migration/cutover?site={main|ma}</p>
        <label>Site</label>
        <select id="cut-site" style="margin-bottom:8px">
            <option value="main">Main Site</option>
            <option value="ma">MA Site</option>
        </select>
        <button class="btn btn-green" onclick="doCutover()">Cutover</button>
        <div class="output" id="cut-out">Warning: Switches auth to ID Platform for this site...</div>
    </div>
</div>

<div class="section" id="mig-rollback">
    <div class="card">
        <h2>Rollback Migration</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/migration/rollback?site={main|ma}</p>
        <label>Site</label>
        <select id="rb-site" style="margin-bottom:8px">
            <option value="main">Main Site</option>
            <option value="ma">MA Site</option>
        </select>
        <button class="btn btn-red" onclick="doRollback()">Rollback</button>
        <div class="output" id="rb-out">Warning: Reverts auth back to legacy system...</div>
    </div>
</div>

<div class="section" id="mig-shadow">
    <div class="card">
        <h2>Shadow Validate</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/migration/shadow-validate</p>
        <div class="row">
            <div><label>Email</label><input id="shad-email" placeholder="user@example.com"></div>
            <div><label>Password</label><input id="shad-pass" type="password" placeholder="password"></div>
        </div>
        <button class="btn btn-blue" onclick="doShadowValidate()">Validate</button>
        <div class="output" id="shad-out">Tests credentials against both legacy and new system...</div>
    </div>
</div>

<div class="section" id="mig-dualwrite">
    <div class="card">
        <h2>Dual Write</h2>
        <p style="color:#64748b;font-size:13px;margin-bottom:12px">POST /api/migration/dual-write?site={main|ma}</p>
        <label>Site</label>
        <select id="dw-site" style="margin-bottom:8px">
            <option value="main">Main Site</option>
            <option value="ma">MA Site</option>
        </select>
        <label>User JSON</label>
        <textarea id="dw-body" rows="4">{"email":"user@example.com","displayName":"Dual User","source":"main"}</textarea>
        <button class="btn btn-yellow" onclick="doDualWrite()">Dual Write</button>
        <div class="output" id="dw-out">Writes to both legacy and ID Platform simultaneously...</div>
    </div>
</div>

</div>
</div>

<script>
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
        document.getElementById('ov-clients').textContent = Object.keys(info.registered_clients).length;
    } catch(e) {}
    try {
        const st = await fetch('/api/migration/status').then(r => r.json());
        document.getElementById('ov-users').textContent = st.totalActiveUsersInIdPlatform || 0;
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

loadOverview();
</script>
</body>
</html>
""";
}

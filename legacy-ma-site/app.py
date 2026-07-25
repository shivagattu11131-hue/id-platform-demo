from flask import Flask, request, jsonify, session, render_template_string, redirect
from flask_cors import CORS
import sqlite3
import hashlib
import os
import secrets
import requests
from datetime import datetime
from urllib.parse import urlencode

app = Flask(__name__)
app.secret_key = secrets.token_hex(32)
CORS(app, origins="*", supports_credentials=True)

DB_PATH = os.path.join(os.path.dirname(__file__), 'ma_site.db')
ID_PLATFORM_URL = "http://localhost:3000"


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    conn.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            display_name TEXT,
            company_name TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    conn.commit()
    conn.close()


def hash_password(password):
    salt = "acquired_ma_site_salt_2024"
    return hashlib.sha256(f"{salt}{password}".encode()).hexdigest()


def is_oidc_enabled():
    """Check if OIDC cutover has been completed for this site."""
    try:
        resp = requests.get(f"{ID_PLATFORM_URL}/api/migration/status", timeout=2)
        if resp.status_code == 200:
            status = resp.json()
            ma_status = status.get("ma", {})
            return ma_status.get("phase") == "CUTOVER_COMPLETE"
    except:
        pass
    return False


@app.route('/')
def index():
    oidc_enabled = is_oidc_enabled()

    if oidc_enabled and 'user_id' in session:
        userinfo = session.get('user_info', {})
        return render_template_string(LOGGED_IN_PAGE, user=userinfo, oidc_enabled=True)

    if oidc_enabled:
        return render_template_string(HOME_PAGE_OIDC)
    else:
        if 'user_id' in session:
            conn = get_db()
            user = conn.execute('SELECT * FROM users WHERE id = ?', (session['user_id'],)).fetchone()
            conn.close()
            if user:
                return render_template_string(LOGGED_IN_PAGE, user=user, oidc_enabled=False)
        return render_template_string(HOME_PAGE)


@app.route('/login')
def login_oidc():
    """Redirect to ID Platform for OIDC login."""
    if not is_oidc_enabled():
        return redirect('/')
    import hashlib as hl
    import base64
    code_verifier = secrets.token_urlsafe(32)
    digest = hl.sha256(code_verifier.encode('ascii')).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b'=').decode('ascii')

    session['pkce_verifier'] = code_verifier
    session['oauth_state'] = secrets.token_urlsafe(16)

    params = {
        'response_type': 'code',
        'client_id': 'ma-site',
        'redirect_uri': 'http://localhost:3002/callback',
        'scope': 'openid profile email',
        'state': session['oauth_state'],
        'code_challenge': code_challenge,
        'code_challenge_method': 'S256'
    }

    return redirect(f"{ID_PLATFORM_URL}/oauth2/authorize?{urlencode(params)}")


@app.route('/callback')
def callback():
    code = request.args.get('code')
    state = request.args.get('state')

    if not code or state != session.get('oauth_state'):
        return redirect('/')

    code_verifier = session.get('pkce_verifier')
    token_resp = requests.post(f"{ID_PLATFORM_URL}/oauth2/token", json={
        'grant_type': 'authorization_code',
        'code': code,
        'client_id': 'ma-site',
        'code_verifier': code_verifier,
        'redirect_uri': 'http://localhost:3002/callback'
    })

    if token_resp.status_code == 200:
        tokens = token_resp.json()
        session['access_token'] = tokens['access_token']
        session['idp_session'] = True

        userinfo = requests.get(f"{ID_PLATFORM_URL}/oauth2/userinfo",
            headers={'Authorization': f"Bearer {tokens['access_token']}"})
        if userinfo.status_code == 200:
            info = userinfo.json()
            session['user_id'] = info.get('sub')
            session['user_info'] = info

    session.pop('pkce_verifier', None)
    session.pop('oauth_state', None)
    return redirect('/')


@app.route('/logout')
def logout():
    session.clear()
    return redirect('/')


@app.route('/do-login', methods=['POST'])
def do_login():
    email = request.form.get('email')
    password = request.form.get('password')

    conn = get_db()
    user = conn.execute('SELECT * FROM users WHERE email = ?', (email,)).fetchone()
    conn.close()

    if user and user['password_hash'] == hash_password(password):
        session['user_id'] = user['id']
        return render_template_string(LOGGED_IN_PAGE, user=user, oidc_enabled=False)

    return render_template_string(HOME_PAGE, error="Invalid credentials")


@app.route('/do-logout')
def do_logout():
    session.clear()
    return render_template_string(HOME_PAGE)


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'site': 'MA Site (Acquired - Legacy Monolith)',
        'port': 3002,
        'auth_type': 'session-based',
        'oidc_enabled': is_oidc_enabled()
    })


@app.route('/api/auth/register', methods=['POST'])
def register():
    data = request.json
    username = data.get('username')
    email = data.get('email')
    password = data.get('password')
    display_name = data.get('display_name', username)
    company_name = data.get('company_name', 'Unknown')

    if not username or not email or not password:
        return jsonify({'error': 'username, email, and password required'}), 400

    try:
        conn = get_db()
        conn.execute(
            'INSERT INTO users (username, email, password_hash, display_name, company_name) VALUES (?, ?, ?, ?, ?)',
            (username, email, hash_password(password), display_name, company_name)
        )
        conn.commit()
        user = conn.execute('SELECT * FROM users WHERE email = ?', (email,)).fetchone()
        conn.close()

        return jsonify({
            'id': user['id'],
            'username': user['username'],
            'email': user['email'],
            'display_name': user['display_name'],
            'company_name': user['company_name'],
            'created_at': user['created_at'],
            'source': 'ma_site'
        }), 201
    except sqlite3.IntegrityError:
        return jsonify({'error': 'Username or email already exists'}), 409


@app.route('/api/auth/login', methods=['POST'])
def login():
    data = request.json
    email = data.get('email')
    password = data.get('password')

    if not email or not password:
        return jsonify({'error': 'email and password required'}), 400

    conn = get_db()
    user = conn.execute('SELECT * FROM users WHERE email = ?', (email,)).fetchone()
    conn.close()

    if user and user['password_hash'] == hash_password(password):
        session['user_id'] = user['id']
        return jsonify({
            'id': user['id'],
            'username': user['username'],
            'email': user['email'],
            'display_name': user['display_name'],
            'company_name': user['company_name'],
            'session_active': True,
            'source': 'ma_site',
            'message': 'Logged in via legacy MA site session auth'
        })

    return jsonify({'error': 'Invalid credentials'}), 401


@app.route('/api/users/me', methods=['GET'])
def get_profile():
    user_id = session.get('user_id')
    if not user_id:
        return jsonify({'error': 'Not logged in'}), 401

    conn = get_db()
    user = conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
    conn.close()

    if user:
        return jsonify({
            'id': user['id'],
            'username': user['username'],
            'email': user['email'],
            'display_name': user['display_name'],
            'company_name': user['company_name'],
            'source': 'ma_site'
        })
    return jsonify({'error': 'User not found'}), 404


@app.route('/api/users/me', methods=['PUT'])
def update_profile():
    user_id = session.get('user_id')
    if not user_id:
        return jsonify({'error': 'Not logged in'}), 401

    data = request.json
    conn = get_db()

    if 'display_name' in data:
        conn.execute('UPDATE users SET display_name = ?, updated_at = ? WHERE id = ?',
                     (data['display_name'], datetime.now().isoformat(), user_id))
    if 'email' in data:
        conn.execute('UPDATE users SET email = ?, updated_at = ? WHERE id = ?',
                     (data['email'], datetime.now().isoformat(), user_id))
    if 'password' in data:
        conn.execute('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?',
                     (hash_password(data['password']), datetime.now().isoformat(), user_id))
    if 'company_name' in data:
        conn.execute('UPDATE users SET company_name = ?, updated_at = ? WHERE id = ?',
                     (data['company_name'], datetime.now().isoformat(), user_id))

    conn.commit()
    user = conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
    conn.close()

    return jsonify({
        'id': user['id'],
        'username': user['username'],
        'email': user['email'],
        'display_name': user['display_name'],
        'company_name': user['company_name'],
        'message': 'Profile updated (legacy dual-write pending)'
    })


@app.route('/api/users/me', methods=['DELETE'])
def delete_user():
    user_id = session.get('user_id')
    if not user_id:
        return jsonify({'error': 'Not logged in'}), 401

    conn = get_db()
    conn.execute('DELETE FROM users WHERE id = ?', (user_id,))
    conn.commit()
    conn.close()
    session.clear()

    return jsonify({'message': 'Account deleted from MA Site legacy DB'})


@app.route('/api/users/all', methods=['GET'])
def list_all_users():
    conn = get_db()
    users = conn.execute('SELECT * FROM users').fetchall()
    conn.close()

    return jsonify([{
        'id': u['id'],
        'username': u['username'],
        'email': u['email'],
        'display_name': u['display_name'],
        'company_name': u['company_name'],
        'password_hash': u['password_hash'],
        'created_at': u['created_at']
    } for u in users])


@app.route('/api/users/count', methods=['GET'])
def user_count():
    conn = get_db()
    count = conn.execute('SELECT COUNT(*) as count FROM users').fetchone()['count']
    conn.close()
    return jsonify({'count': count, 'site': 'ma_site'})


HOME_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>MA Site - Acquired Company</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #388e3c; color: white; padding: 20px; }
        .header h1 { margin: 0; }
        .header p { margin: 5px 0 0 0; opacity: 0.9; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-legacy { background: #ff9800; color: white; }
        .form-group { margin-bottom: 16px; }
        label { display: block; margin-bottom: 6px; font-weight: 500; }
        input[type="email"], input[type="password"] { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; box-sizing: border-box; }
        .btn { padding: 10px 20px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; }
        .btn-primary { background: #388e3c; color: white; }
        .error { background: #fee2e2; color: #dc2626; padding: 10px; border-radius: 6px; margin-bottom: 16px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>MA Site</h1>
        <p>Acquired Company E-commerce | Port 3002 | <span class="badge badge-legacy">Pre-Migration</span></p>
    </div>
    <div class="container">
        <div class="card">
            <h2>Legacy Authentication</h2>
            <p>This site uses its own session-based auth (SHA-256 + different salt). No OIDC, no SSO.</p>
            <p style="color:#666; font-size:13px;">Different salt from Main Site means users need separate passwords on each site.</p>

            <div id="error" class="error" style="display:none"></div>

            <form action="/do-login" method="post">
                <div class="form-group">
                    <label>Email</label>
                    <input type="email" name="email" placeholder="mauser1@example.com" required>
                </div>
                <div class="form-group">
                    <label>Password</label>
                    <input type="password" name="password" placeholder="mapass1" required>
                </div>
                <button type="submit" class="btn btn-primary">Login (Legacy)</button>
            </form>
        </div>
    </div>
    <script>
        var params = new URLSearchParams(window.location.search);
        if (params.get('error')) {
            document.getElementById('error').textContent = params.get('error');
            document.getElementById('error').style.display = 'block';
        }
    </script>
</body>
</html>
"""

HOME_PAGE_OIDC = """
<!DOCTYPE html>
<html>
<head>
    <title>MA Site - ID Platform</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #388e3c; color: white; padding: 20px; }
        .header h1 { margin: 0; }
        .header p { margin: 5px 0 0 0; opacity: 0.9; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-oidc { background: #4caf50; color: white; }
        .btn { display: inline-block; padding: 12px 24px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; text-decoration: none; font-size: 16px; }
        .btn-primary { background: #388e3c; color: white; }
    </style>
</head>
<body>
    <div class="header">
        <h1>MA Site</h1>
        <p>Acquired Company E-commerce | Port 3002 | <span class="badge badge-oidc">OIDC Enabled</span></p>
    </div>
    <div class="container">
        <div class="card">
            <h2>Single Sign-On (SSO)</h2>
            <p>This site now uses the <strong>Raksul ID Platform</strong> for authentication.</p>
            <p>Click below to login via OIDC. You can then access Main Site without re-login.</p>
            <br>
            <a href="/login" class="btn btn-primary">Login with ID Platform (OIDC)</a>
        </div>
    </div>
</body>
</html>
"""

LOGGED_IN_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>MA Site - Dashboard</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #388e3c; color: white; padding: 20px; }
        .header h1 { margin: 0; }
        .header p { margin: 5px 0 0 0; opacity: 0.9; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-oidc { background: #4caf50; color: white; }
        .badge-legacy { background: #ff9800; color: white; }
        .btn { display: inline-block; padding: 10px 20px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; text-decoration: none; margin: 5px; }
        .btn-primary { background: #1976d2; color: white; }
        .btn-danger { background: #dc3545; color: white; }
        .user-info { background: #e8f5e9; padding: 16px; border-radius: 8px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>MA Site</h1>
        {% if oidc_enabled %}
        <p>Acquired Company E-commerce | Port 3002 | <span class="badge badge-oidc">OIDC Enabled</span></p>
        {% else %}
        <p>Acquired Company E-commerce | Port 3002 | <span class="badge badge-legacy">Pre-Migration</span></p>
        {% endif %}
    </div>
    <div class="container">
        <div class="card">
            <h2>Welcome, {{ user.get('display_name') or user.get('name') or user.get('email', 'User') }}!</h2>
            <div class="user-info">
                <p><strong>Email:</strong> {{ user.get('email', 'N/A') }}</p>
                {% if oidc_enabled %}
                <p><strong>Auth:</strong> ID Platform (OIDC) <span class="badge badge-oidc">SSO</span></p>
                {% else %}
                <p><strong>Auth:</strong> Legacy Session Auth <span class="badge badge-legacy">Session</span></p>
                {% endif %}
            </div>
            {% if oidc_enabled %}
            <p>You are logged in via the unified ID Platform. You can now access Main Site without re-login.</p>
            <a href="http://localhost:3001/" class="btn btn-primary">Go to Main Site (SSO)</a>
            {% else %}
            <p>You are logged in via the legacy session-based authentication.</p>
            <a href="http://localhost:3001/" class="btn btn-primary">Go to Main Site</a>
            {% endif %}
            <a href="/do-logout" class="btn btn-danger">Logout</a>
        </div>
    </div>
</body>
</html>
"""


if __name__ == '__main__':
    init_db()
    print("=" * 60)
    print("  LEGACY MA SITE (Simulating Acquired Company Monolith)")
    print("  Port: 3002 | Auth: Session-based | DB: SQLite")
    print("  Pre-Migration: Legacy login")
    print("  Post-Migration: OIDC login via ID Platform")
    print("=" * 60)
    app.run(port=3002, debug=True)

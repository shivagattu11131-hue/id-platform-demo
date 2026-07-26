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

DB_PATH = os.path.join(os.path.dirname(__file__), 'main_site.db')
ID_PLATFORM_URL = os.environ.get("ID_PLATFORM_URL", "http://localhost:3000")
EXTERNAL_BASE_URL = os.environ.get("EXTERNAL_BASE_URL", "http://localhost")


def get_db():
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    conn.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            first_name TEXT,
            last_name TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    conn.commit()
    conn.close()


def hash_password(password):
    salt = "raksul_main_site_salt_2024"
    return hashlib.sha256(f"{salt}{password}".encode()).hexdigest()


def is_oidc_enabled():
    try:
        resp = requests.get(f"{ID_PLATFORM_URL}/api/migration/status", timeout=2)
        if resp.status_code == 200:
            status = resp.json()
            main_status = status.get("main", {})
            return main_status.get("phase") == "CUTOVER_COMPLETE"
    except:
        pass
    return False


@app.route('/')
def index():
    oidc_enabled = is_oidc_enabled()

    if oidc_enabled and 'user_id' in session:
        userinfo = session.get('user_info', {})
        if userinfo:
            return render_template_string(LOGGED_IN_PAGE, user=userinfo, oidc_enabled=True, external_url=EXTERNAL_BASE_URL)

    if oidc_enabled:
        return render_template_string(HOME_PAGE_OIDC)
    else:
        if 'user_id' in session:
            conn = get_db()
            user = conn.execute('SELECT * FROM users WHERE id = ?', (session['user_id'],)).fetchone()
            conn.close()
            if user:
                return render_template_string(LOGGED_IN_PAGE, user=dict(user), oidc_enabled=False, external_url=EXTERNAL_BASE_URL)
        return render_template_string(HOME_PAGE)


@app.route('/login')
def login_oidc():
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
        'client_id': 'main-site',
        'redirect_uri': f'{EXTERNAL_BASE_URL}:3001/callback',
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
        'client_id': 'main-site',
        'code_verifier': code_verifier,
        'redirect_uri': f'{EXTERNAL_BASE_URL}:3001/callback'
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
            session['user_email'] = info.get('email')
            session['user_name'] = info.get('name')
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
        return render_template_string(LOGGED_IN_PAGE, user=dict(user), oidc_enabled=False, external_url=EXTERNAL_BASE_URL)

    return render_template_string(HOME_PAGE, error="Invalid credentials")


@app.route('/do-logout')
def do_logout():
    session.clear()
    return render_template_string(HOME_PAGE)


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'site': 'Main Site (Legacy Rails Monolith)',
        'port': 3001,
        'auth_type': 'session-based',
        'oidc_enabled': is_oidc_enabled()
    })


@app.route('/api/auth/register', methods=['POST'])
def register():
    data = request.json
    email = data.get('email')
    password = data.get('password')
    first_name = data.get('first_name', '')
    last_name = data.get('last_name', '')

    if not email or not password:
        return jsonify({'error': 'email and password required'}), 400

    try:
        conn = get_db()
        try:
            conn.execute(
                'INSERT INTO users (email, password_hash, first_name, last_name) VALUES (?, ?, ?, ?)',
                (email, hash_password(password), first_name, last_name)
            )
            conn.commit()
            user = conn.execute('SELECT * FROM users WHERE email = ?', (email,)).fetchone()

            return jsonify({
                'id': user['id'],
                'email': user['email'],
                'first_name': user['first_name'],
                'last_name': user['last_name'],
                'created_at': user['created_at'],
                'source': 'main_site'
            }), 201
        except sqlite3.IntegrityError:
            return jsonify({'error': 'Email already exists'}), 409
        finally:
            conn.close()
    except Exception as e:
        return jsonify({'error': str(e)}), 500


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
            'email': user['email'],
            'first_name': user['first_name'],
            'last_name': user['last_name'],
            'session_active': True,
            'source': 'main_site',
            'message': 'Logged in via legacy Rails session auth'
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
            'email': user['email'],
            'first_name': user['first_name'],
            'last_name': user['last_name'],
            'source': 'main_site'
        })
    return jsonify({'error': 'User not found'}), 404


@app.route('/api/users/me', methods=['PUT'])
def update_profile():
    user_id = session.get('user_id')
    if not user_id:
        return jsonify({'error': 'Not logged in'}), 401

    data = request.json
    conn = get_db()

    if 'first_name' in data:
        conn.execute('UPDATE users SET first_name = ?, updated_at = ? WHERE id = ?',
                     (data['first_name'], datetime.now().isoformat(), user_id))
    if 'last_name' in data:
        conn.execute('UPDATE users SET last_name = ?, updated_at = ? WHERE id = ?',
                     (data['last_name'], datetime.now().isoformat(), user_id))
    if 'email' in data:
        try:
            conn.execute('UPDATE users SET email = ?, updated_at = ? WHERE id = ?',
                         (data['email'], datetime.now().isoformat(), user_id))
        except sqlite3.IntegrityError:
            conn.close()
            return jsonify({'error': 'Email already in use'}), 409
    if 'password' in data:
        conn.execute('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?',
                     (hash_password(data['password']), datetime.now().isoformat(), user_id))

    conn.commit()
    user = conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
    conn.close()

    return jsonify({
        'id': user['id'],
        'email': user['email'],
        'first_name': user['first_name'],
        'last_name': user['last_name'],
        'message': 'Profile updated'
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

    return jsonify({'message': 'Membership cancelled. Account deleted from Main Site.'})


@app.route('/api/users/all', methods=['GET'])
def list_all_users():
    conn = get_db()
    users = conn.execute('SELECT * FROM users').fetchall()
    conn.close()

    return jsonify([{
        'id': u['id'],
        'email': u['email'],
        'first_name': u['first_name'],
        'last_name': u['last_name'],
        'password_hash': u['password_hash'],
        'created_at': u['created_at']
    } for u in users])


@app.route('/api/users/count', methods=['GET'])
def user_count():
    conn = get_db()
    count = conn.execute('SELECT COUNT(*) as count FROM users').fetchone()['count']
    conn.close()
    return jsonify({'count': count, 'site': 'main_site'})


HOME_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>Main Site - Legacy Rails Monolith</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #1976d2; color: white; padding: 20px; }
        .header h1 { margin: 0; }
        .header p { margin: 5px 0 0 0; opacity: 0.9; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-legacy { background: #ff9800; color: white; }
        .form-group { margin-bottom: 16px; }
        label { display: block; margin-bottom: 6px; font-weight: 500; }
        input[type="email"], input[type="password"], input[type="text"] { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; box-sizing: border-box; }
        .btn { padding: 10px 20px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; }
        .btn-primary { background: #1976d2; color: white; }
        .btn-sm { padding: 6px 14px; font-size: 13px; }
        .btn-outline { background: white; color: #1976d2; border: 1px solid #1976d2; }
        .error { background: #fee2e2; color: #dc2626; padding: 10px; border-radius: 6px; margin-bottom: 16px; }
        .tabs { display: flex; gap: 0; margin-bottom: 20px; }
        .tab { padding: 10px 20px; cursor: pointer; border: 1px solid #ddd; background: #f5f5f5; }
        .tab:first-child { border-radius: 6px 0 0 6px; }
        .tab:last-child { border-radius: 0 6px 6px 0; }
        .tab.active { background: #1976d2; color: white; border-color: #1976d2; }
        .form-section { display: none; }
        .form-section.active { display: block; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Main Site</h1>
        <p>Legacy Rails Monolith | Port 3001 | <span class="badge badge-legacy">Pre-Migration</span></p>
    </div>
    <div class="container">
        <div class="card">
            <div class="tabs">
                <div class="tab active" onclick="showTab('login')">Login</div>
                <div class="tab" onclick="showTab('register')">Register</div>
            </div>
            <div id="error" class="error" style="display:none"></div>

            <div id="login-form" class="form-section active">
                <h2>Login</h2>
                <form action="/do-login" method="post">
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" name="email" placeholder="john@example.com" required>
                    </div>
                    <div class="form-group">
                        <label>Password</label>
                        <input type="password" name="password" placeholder="password123" required>
                    </div>
                    <button type="submit" class="btn btn-primary">Login</button>
                </form>
            </div>

            <div id="register-form" class="form-section">
                <h2>User Registration</h2>
                <form action="/do-register" method="post">
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" name="email" placeholder="john@example.com" required>
                    </div>
                    <div class="form-group">
                        <label>Password</label>
                        <input type="password" name="password" placeholder="password123" required>
                    </div>
                    <div class="form-group">
                        <label>First Name</label>
                        <input type="text" name="first_name" placeholder="John">
                    </div>
                    <div class="form-group">
                        <label>Last Name</label>
                        <input type="text" name="last_name" placeholder="Doe">
                    </div>
                    <button type="submit" class="btn btn-primary">Register</button>
                </form>
            </div>
        </div>
    </div>
    <script>
        function showTab(tab) {
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.form-section').forEach(f => f.classList.remove('active'));
            if (tab === 'login') {
                document.querySelectorAll('.tab')[0].classList.add('active');
                document.getElementById('login-form').classList.add('active');
            } else {
                document.querySelectorAll('.tab')[1].classList.add('active');
                document.getElementById('register-form').classList.add('active');
            }
        }
        var params = new URLSearchParams(window.location.search);
        if (params.get('error')) {
            document.getElementById('error').textContent = params.get('error');
            document.getElementById('error').style.display = 'block';
        }
        if (params.get('tab')) showTab(params.get('tab'));
    </script>
</body>
</html>
"""

HOME_PAGE_OIDC = """
<!DOCTYPE html>
<html>
<head>
    <title>Main Site - ID Platform</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #1976d2; color: white; padding: 20px; }
        .header h1 { margin: 0; }
        .header p { margin: 5px 0 0 0; opacity: 0.9; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
        .badge-oidc { background: #4caf50; color: white; }
        .btn { display: inline-block; padding: 12px 24px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; text-decoration: none; font-size: 16px; }
        .btn-primary { background: #1976d2; color: white; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Main Site</h1>
        <p>E-commerce Platform | Port 3001 | <span class="badge badge-oidc">OIDC Enabled</span></p>
    </div>
    <div class="container">
        <div class="card">
            <h2>Single Sign-On (SSO)</h2>
            <p>This site now uses the <strong>Raksul ID Platform</strong> for authentication.</p>
            <p>Click below to login via OIDC. You can then access MA Site without re-login.</p>
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
    <title>Main Site - Dashboard</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #1976d2; color: white; padding: 20px; }
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
        .btn-outline { background: white; color: #1976d2; border: 1px solid #1976d2; }
        .user-info { background: #e3f2fd; padding: 16px; border-radius: 8px; margin-bottom: 20px; }
        .profile-form { margin-top: 16px; }
        .profile-form .form-group { margin-bottom: 12px; }
        .profile-form label { display: block; margin-bottom: 4px; font-weight: 500; font-size: 14px; }
        .profile-form input { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Main Site</h1>
        {% if oidc_enabled %}
        <p>E-commerce Platform | Port 3001 | <span class="badge badge-oidc">OIDC Enabled</span></p>
        {% else %}
        <p>Legacy Rails Monolith | Port 3001 | <span class="badge badge-legacy">Pre-Migration</span></p>
        {% endif %}
    </div>
    <div class="container">
        <div class="card">
            <h2>Welcome, {{ user.get('first_name') or user.get('name') or user.get('email', 'User') }}!</h2>
            <div class="user-info">
                <p><strong>Email:</strong> {{ user.get('email', 'N/A') }}</p>
                <p><strong>Name:</strong> {{ user.get('first_name', '') }} {{ user.get('last_name', '') }}</p>
                {% if oidc_enabled %}
                <p><strong>Auth:</strong> ID Platform (OIDC) <span class="badge badge-oidc">SSO</span></p>
                {% else %}
                <p><strong>Auth:</strong> Legacy Session Auth <span class="badge badge-legacy">Session</span></p>
                {% endif %}
            </div>

            {% if not oidc_enabled %}
            <div class="profile-form">
                <h3>Profile Update</h3>
                <form action="/api/users/me" method="post" id="profile-form">
                    <div class="form-group">
                        <label>First Name</label>
                        <input type="text" name="first_name" value="{{ user.get('first_name', '') }}">
                    </div>
                    <div class="form-group">
                        <label>Last Name</label>
                        <input type="text" name="last_name" value="{{ user.get('last_name', '') }}">
                    </div>
                    <button type="button" class="btn btn-primary" onclick="updateProfile()">Update Profile</button>
                </form>
            </div>
            <br>
            {% endif %}

            {% if oidc_enabled %}
            <p>You are logged in via the unified ID Platform. You can now access MA Site without re-login.</p>
            <a href="{{ external_url }}:3002/" class="btn btn-primary">Go to MA Site (SSO)</a>
            {% else %}
            <p>You are logged in via the legacy session-based authentication.</p>
            <a href="{{ external_url }}:3002/" class="btn btn-primary">Go to MA Site</a>
            {% endif %}
            <a href="/do-logout" class="btn btn-danger">Logout</a>
            {% if not oidc_enabled %}
            <a href="#" class="btn btn-outline" onclick="cancelMembership()">Cancel Membership</a>
            {% endif %}
        </div>
    </div>
    <script>
        function updateProfile() {
            var form = document.getElementById('profile-form');
            var data = {
                first_name: form.querySelector('[name=first_name]').value,
                last_name: form.querySelector('[name=last_name]').value
            };
            fetch('/api/users/me', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                credentials: 'same-origin',
                body: JSON.stringify(data)
            }).then(r => r.json()).then(d => {
                if (d.message) { alert('Profile updated!'); location.reload(); }
                else { alert('Error: ' + (d.error || 'Unknown')); }
            });
        }
        function cancelMembership() {
            if (confirm('Are you sure you want to cancel your membership? This action cannot be undone.')) {
                fetch('/api/users/me', { method: 'DELETE', credentials: 'same-origin' })
                .then(r => r.json()).then(d => { alert(d.message); location.href = '/'; });
            }
        }
    </script>
</body>
</html>
"""


@app.route('/do-register', methods=['POST'])
def do_register():
    email = request.form.get('email')
    password = request.form.get('password')
    first_name = request.form.get('first_name', '')
    last_name = request.form.get('last_name', '')

    if not email or not password:
        return render_template_string(HOME_PAGE, error="Email and password required")

    try:
        conn = get_db()
        conn.execute(
            'INSERT INTO users (email, password_hash, first_name, last_name) VALUES (?, ?, ?, ?)',
            (email, hash_password(password), first_name, last_name)
        )
        conn.commit()
        conn.close()
        return redirect('/?tab=login')
    except sqlite3.IntegrityError:
        return render_template_string(HOME_PAGE, error="Email already exists")


if __name__ == '__main__':
    init_db()
    print("=" * 60)
    print("  LEGACY MAIN SITE (Simulating Ruby on Rails Monolith)")
    print("  Port: 3001 | Auth: Session-based | DB: SQLite")
    print("  Table: users (email, password_hash, first_name, last_name)")
    print("  Features: Registration, Login/Logout, Profile, Cancel Membership")
    print("=" * 60)
    app.run(host='0.0.0.0', port=3001, debug=os.environ.get("FLASK_DEBUG", "false").lower() == "true")

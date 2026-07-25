from flask import Flask, request, jsonify, session, redirect, url_for, render_template_string
from flask_cors import CORS
import sqlite3
import hashlib
import os
import secrets
import requests
from datetime import datetime
from urllib.parse import urlencode, urlparse, parse_qs

app = Flask(__name__)
app.secret_key = "ma-site-secret-key-for-production-demo"
CORS(app, origins="*", supports_credentials=True)

DB_PATH = os.path.join(os.path.dirname(__file__), 'ma_site.db')

ID_PLATFORM_URL = "http://localhost:3000"
CLIENT_ID = "ma-site"
REDIRECT_URI = "http://localhost:3002/callback"
SCOPES = "openid profile email"


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


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'site': 'MA Site (Acquired - Legacy Monolith)',
        'port': 3002,
        'auth_type': 'session-based',
        'oidc_enabled': True,
        'note': 'Acquired via M&A, now with OIDC support'
    })


@app.route('/')
def index():
    if 'user_id' in session:
        user = get_user_by_id(session['user_id'])
        if user:
            return render_template_string(HOME_PAGE_LOGGED_IN, user=user, site='MA Site')
    return render_template_string(HOME_PAGE, site='MA Site')


@app.route('/login')
def login_page():
    """Redirect to ID Platform for OIDC authentication."""
    # Check if user already has IDP session (SSO)
    idp_session = session.get('idp_session')
    
    if idp_session:
        # User already logged in at ID Platform - silent auth
        return redirect_to_idp(prompt="none")
    
    # No session - redirect to ID Platform login page
    return redirect_to_idp(prompt="login")


def redirect_to_idp(prompt="login"):
    """Build redirect URL to ID Platform."""
    import hashlib, base64, secrets as sec
    
    # Generate PKCE
    code_verifier = sec.token_urlsafe(32)
    digest = hashlib.sha256(code_verifier.encode('ascii')).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b'=').decode('ascii')
    
    # Store PKCE values in session
    session['pkce_verifier'] = code_verifier
    session['oauth_state'] = sec.token_urlsafe(16)
    
    params = {
        'response_type': 'code',
        'client_id': CLIENT_ID,
        'redirect_uri': REDIRECT_URI,
        'scope': SCOPES,
        'state': session['oauth_state'],
        'code_challenge': code_challenge,
        'code_challenge_method': 'S256',
        'prompt': prompt
    }
    
    auth_url = f"{ID_PLATFORM_URL}/oauth2/authorize?{urlencode(params)}"
    return redirect(auth_url)


@app.route('/callback')
def callback():
    """Handle OIDC callback from ID Platform."""
    code = request.args.get('code')
    state = request.args.get('state')
    error = request.args.get('error')
    
    if error:
        return render_template_string(ERROR_PAGE, error=error, site='MA Site')
    
    if not code:
        return render_template_string(ERROR_PAGE, error="No authorization code received", site='MA Site')
    
    # Validate state
    if state != session.get('oauth_state'):
        return render_template_string(ERROR_PAGE, error="Invalid state parameter", site='MA Site')
    
    # Exchange code for tokens
    code_verifier = session.get('pkce_verifier')
    
    token_response = requests.post(
        f"{ID_PLATFORM_URL}/oauth2/token",
        json={
            'grant_type': 'authorization_code',
            'code': code,
            'client_id': CLIENT_ID,
            'code_verifier': code_verifier,
            'redirect_uri': REDIRECT_URI
        }
    )
    
    if token_response.status_code != 200:
        return render_template_string(ERROR_PAGE, error="Token exchange failed", site='MA Site')
    
    tokens = token_response.json()
    
    # Store tokens server-side (in real app, use Redis/DB)
    session['access_token'] = tokens['access_token']
    session['refresh_token'] = tokens['refresh_token']
    session['id_token'] = tokens.get('id_token')
    session['idp_session'] = True  # Mark IDP session as active
    
    # Get user info from ID token or userinfo endpoint
    userinfo_response = requests.get(
        f"{ID_PLATFORM_URL}/oauth2/userinfo",
        headers={'Authorization': f"Bearer {tokens['access_token']}"}
    )
    
    if userinfo_response.status_code == 200:
        user_info = userinfo_response.json()
        session['user_id'] = user_info.get('sub')
        session['user_email'] = user_info.get('email')
        session['user_name'] = user_info.get('name')
    
    # Clean up PKCE and state
    session.pop('pkce_verifier', None)
    session.pop('oauth_state', None)
    
    return redirect(url_for('index'))


@app.route('/logout')
def logout():
    """Logout from both local session and ID Platform."""
    # Revoke tokens at ID Platform
    access_token = session.get('access_token')
    if access_token:
        try:
            requests.post(
                f"{ID_PLATFORM_URL}/oauth2/revoke",
                json={'token': access_token}
            )
        except:
            pass
    
    # Clear local session
    session.clear()
    return redirect(url_for('index'))


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
            'source': 'ma_site',
            'oidc_authenticated': 'access_token' in session
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


def get_user_by_id(user_id):
    conn = get_db()
    user = conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
    conn.close()
    return user


HOME_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>{{ site }} - Home</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #388e3c; color: white; padding: 20px; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .btn { display: inline-block; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 500; margin: 5px; }
        .btn-primary { background: #388e3c; color: white; }
        .btn:hover { opacity: 0.9; }
    </style>
</head>
<body>
    <div class="header">
        <h1>{{ site }} (Acquired Company)</h1>
    </div>
    <div class="container">
        <div class="card">
            <h2>Welcome to {{ site }}</h2>
            <p>This is the acquired company's e-commerce site. Click below to login via the unified ID Platform.</p>
            <a href="/login" class="btn btn-primary">Login with ID Platform (OIDC)</a>
        </div>
    </div>
</body>
</html>
"""

HOME_PAGE_LOGGED_IN = """
<!DOCTYPE html>
<html>
<head>
    <title>{{ site }} - Dashboard</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #388e3c; color: white; padding: 20px; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .btn { display: inline-block; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 500; margin: 5px; }
        .btn-primary { background: #388e3c; color: white; }
        .btn-danger { background: #dc3545; color: white; }
        .btn:hover { opacity: 0.9; }
        .user-info { background: #e8f5e9; padding: 15px; border-radius: 6px; margin-bottom: 20px; }
        .sso-badge { background: #4caf50; color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>{{ site }} - Dashboard</h1>
    </div>
    <div class="container">
        <div class="card">
            <h2>Welcome, {{ user.display_name or user.email }}!</h2>
            <div class="user-info">
                <p><strong>Email:</strong> {{ user.email }}</p>
                <p><strong>Company:</strong> {{ user.company_name }}</p>
                <p><strong>Authenticated via:</strong> ID Platform (OIDC) <span class="sso-badge">SSO</span></p>
            </div>
            <p>You are now logged in. Your session is managed by the ID Platform.</p>
            <a href="/api/users/me" class="btn btn-primary">View Profile (API)</a>
            <a href="/logout" class="btn btn-danger">Logout</a>
            <a href="http://localhost:3001/" class="btn btn-primary">Go to Main Site (SSO Test)</a>
        </div>
    </div>
</body>
</html>
"""

ERROR_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>{{ site }} - Error</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 0; background: #f5f5f5; }
        .header { background: #dc3545; color: white; padding: 20px; }
        .container { max-width: 800px; margin: 40px auto; padding: 20px; }
        .card { background: white; border-radius: 8px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .btn { display: inline-block; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 500; }
        .btn-primary { background: #388e3c; color: white; }
    </style>
</head>
<body>
    <div class="header">
        <h1>{{ site }} - Authentication Error</h1>
    </div>
    <div class="container">
        <div class="card">
            <h2>Authentication Failed</h2>
            <p>Error: {{ error }}</p>
            <a href="/login" class="btn btn-primary">Try Again</a>
        </div>
    </div>
</body>
</html>
"""


if __name__ == '__main__':
    init_db()
    print("=" * 60)
    print("  LEGACY MA SITE (Simulating Acquired Company Monolith)")
    print("  Port: 3002 | Auth: Session-based + OIDC | DB: SQLite")
    print("  OIDC Enabled: Users can login via ID Platform")
    print("  SSO Enabled: Login once on Main Site, access MA Site")
    print("=" * 60)
    app.run(port=3002, debug=True)

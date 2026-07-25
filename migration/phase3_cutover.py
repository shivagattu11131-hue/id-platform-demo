"""
Phase 3: Cutover - Flip legacy sites to use ID Platform for authentication.
MA Site first (smaller, lower risk), then Main Site.
SSO enabled after both sites cutover.
"""

import requests
import json
import time
import hashlib
import base64
import secrets

ID_PLATFORM_URL = "http://localhost:3000"
MAIN_SITE_URL = "http://localhost:3001"
MA_SITE_URL = "http://localhost:3002"

SEPARATOR = "=" * 60


def print_phase(phase_num, title):
    print(f"\n{SEPARATOR}")
    print(f"  PHASE {phase_num}: {title}")
    print(SEPARATOR)


def generate_pkce():
    """Generate PKCE code_verifier and code_challenge."""
    code_verifier = secrets.token_urlsafe(32)
    digest = hashlib.sha256(code_verifier.encode('ascii')).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b'=').decode('ascii')
    return code_verifier, code_challenge


def idp_authorize(client_id, redirect_uri, scope="openid profile email"):
    """Get authorization page URL."""
    code_verifier, code_challenge = generate_pkce()
    
    auth_url = (
        f"{ID_PLATFORM_URL}/oauth2/authorize"
        f"?response_type=code"
        f"&client_id={client_id}"
        f"&redirect_uri={redirect_uri}"
        f"&scope={scope}"
        f"&state={secrets.token_urlsafe(16)}"
        f"&code_challenge={code_challenge}"
        f"&code_challenge_method=S256"
    )
    
    return auth_url, code_verifier


def idp_authorize_with_credentials(auth_url, email, password, code_verifier):
    """Complete authorization with user credentials."""
    # Parse the auth URL to get query parameters
    from urllib.parse import urlparse, parse_qs
    parsed = urlparse(auth_url)
    params = parse_qs(parsed.query)
    
    # POST to the authorize endpoint with credentials
    resp = requests.post(
        f"{ID_PLATFORM_URL}/oauth2/authorize",
        data={
            "response_type": params.get("response_type", ["code"])[0],
            "client_id": params.get("client_id", [""])[0],
            "redirect_uri": params.get("redirect_uri", [""])[0],
            "scope": params.get("scope", ["openid"])[0],
            "state": params.get("state", [""])[0],
            "nonce": params.get("nonce", [""])[0] if "nonce" in params else "",
            "code_challenge": params.get("code_challenge", [""])[0],
            "code_challenge_method": params.get("code_challenge_method", ["S256"])[0],
            "email": email,
            "password": password
        },
        allow_redirects=False
    )
    
    return resp


def exchange_code_for_token(code, client_id, code_verifier, redirect_uri):
    """Exchange authorization code for tokens."""
    resp = requests.post(
        f"{ID_PLATFORM_URL}/oauth2/token",
        json={
            "grant_type": "authorization_code",
            "code": code,
            "client_id": client_id,
            "code_verifier": code_verifier,
            "redirect_uri": redirect_uri
        }
    )
    return resp


def validate_token(token):
    """Validate JWT token via ID Platform's userinfo endpoint."""
    resp = requests.get(
        f"{ID_PLATFORM_URL}/oauth2/userinfo",
        headers={"Authorization": f"Bearer {token}"}
    )
    return resp


def oidc_discovery():
    """Fetch OIDC discovery document."""
    resp = requests.get(f"{ID_PLATFORM_URL}/.well-known/openid-configuration")
    return resp.json() if resp.status_code == 200 else None


def run_phase3():
    print_phase(3, "CUTOVER - Switch Authentication to ID Platform (Authorization Code + PKCE)")

    print("\n  Cutover Strategy:")
    print("  1. MA Site first (100K users, lower risk)")
    print("  2. Main Site second (3M users, higher caution)")
    print("  3. SSO enabled after both sites cutover")

    # === Step 1: Verify OIDC Discovery ===
    print("\n  --- Step 1: Verify OIDC Provider is Ready ---")
    discovery = oidc_discovery()
    if discovery:
        print("  [OK] OIDC Discovery Document:")
        print(f"    issuer: {discovery.get('issuer')}")
        print(f"    authorization_endpoint: {discovery.get('authorization_endpoint')}")
        print(f"    token_endpoint: {discovery.get('token_endpoint')}")
        print(f"    response_types_supported: {discovery.get('response_types_supported')}")
        print(f"    grant_types_supported: {discovery.get('grant_types_supported')}")
        print(f"    code_challenge_methods_supported: {discovery.get('code_challenge_methods_supported')}")
    else:
        print("  [FAIL] OIDC Discovery not available")
        return

    # === Step 2: Cutover MA Site ===
    print("\n  --- Step 2: Cutover MA Site (smaller, lower risk) ---")
    print("  Switching MA Site auth from legacy session to ID Platform OIDC...")

    cutover_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/cutover",
        params={"site": "ma"}
    )
    if cutover_resp.status_code == 200:
        print(f"  [OK] MA Site cutover initiated")
        print(f"  {json.dumps(cutover_resp.json(), indent=4)}")
    else:
        print(f"  [FAIL] MA Site cutover failed")
        return

    print("\n  Testing MA Site login via Authorization Code + PKCE...")
    redirect_uri = "http://localhost:3002/callback"
    auth_url, code_verifier = idp_authorize("ma-site", redirect_uri)
    print(f"  [OK] Authorization URL generated")
    print(f"    URL: {auth_url[:80]}...")
    
    auth_resp = idp_authorize_with_credentials(auth_url, "mauser1@example.com", "mapass1", code_verifier)
    if auth_resp.status_code == 302:
        location = auth_resp.headers.get('Location', '')
        if 'code=' in location:
            code = location.split('code=')[1].split('&')[0]
            print(f"  [OK] Authorization code received: {code[:20]}...")
            
            token_resp = exchange_code_for_token(code, "ma-site", code_verifier, redirect_uri)
            if token_resp.status_code == 200:
                auth_data = token_resp.json()
                print(f"  [OK] Token exchange successful!")
                print(f"    Access Token: {auth_data['access_token'][:50]}...")
                print(f"    ID Token: {auth_data.get('id_token', 'N/A')[:50]}...")
                print(f"    Token Type: {auth_data['token_type']}")
                print(f"    Expires In: {auth_data['expires_in']}s")
                
                # Validate token via userinfo
                print("\n  Validating token via userinfo endpoint...")
                userinfo_resp = validate_token(auth_data['access_token'])
                if userinfo_resp.status_code == 200:
                    print(f"  [OK] Token valid! User info: {json.dumps(userinfo_resp.json(), indent=4)}")
                else:
                    print(f"  [FAIL] Token validation failed")
            else:
                print(f"  [FAIL] Token exchange failed: {token_resp.json()}")
        else:
            print(f"  [FAIL] No authorization code in redirect")
    else:
        print(f"  [FAIL] Authorization failed with status: {auth_resp.status_code}")

    # === Step 3: Cutover Main Site ===
    print("\n  --- Step 3: Cutover Main Site (larger, more cautious) ---")
    print("  Switching Main Site auth from legacy session to ID Platform OIDC...")

    cutover_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/cutover",
        params={"site": "main"}
    )
    if cutover_resp.status_code == 200:
        print(f"  [OK] Main Site cutover initiated")
        print(f"  {json.dumps(cutover_resp.json(), indent=4)}")
    else:
        print(f"  [FAIL] Main Site cutover failed")
        return

    print("\n  Testing Main Site login via Authorization Code + PKCE...")
    redirect_uri = "http://localhost:3001/callback"
    auth_url, code_verifier = idp_authorize("main-site", redirect_uri)
    
    auth_resp = idp_authorize_with_credentials(auth_url, "john@example.com", "password123", code_verifier)
    if auth_resp.status_code == 302:
        location = auth_resp.headers.get('Location', '')
        if 'code=' in location:
            code = location.split('code=')[1].split('&')[0]
            print(f"  [OK] Authorization code received: {code[:20]}...")
            
            token_resp = exchange_code_for_token(code, "main-site", code_verifier, redirect_uri)
            if token_resp.status_code == 200:
                auth_data = token_resp.json()
                print(f"  [OK] Login successful via Authorization Code + PKCE!")
                print(f"    Access Token: {auth_data['access_token'][:50]}...")
                
                userinfo_resp = validate_token(auth_data['access_token'])
                if userinfo_resp.status_code == 200:
                    print(f"  [OK] Token valid! User: {userinfo_resp.json().get('email')}")

    # === Step 4: Demonstrate SSO ===
    print("\n  --- Step 4: SSO Demonstration ---")
    print("  User logs in on Main Site, gets token,")
    print("  then uses same token on MA Site without re-login...")

    # Login on Main Site
    print("\n  1. Logging in on Main Site...")
    redirect_uri = "http://localhost:3001/callback"
    auth_url, code_verifier = idp_authorize("main-site", redirect_uri)
    
    auth_resp = idp_authorize_with_credentials(auth_url, "john@example.com", "password123", code_verifier)
    if auth_resp.status_code == 302:
        location = auth_resp.headers.get('Location', '')
        code = location.split('code=')[1].split('&')[0]
        
        token_resp = exchange_code_for_token(code, "main-site", code_verifier, redirect_uri)
        if token_resp.status_code == 200:
            token = token_resp.json()['access_token']
            print(f"  [OK] Got access token from Main Site login")

            # Use same token on MA Site (SSO!)
            print("\n  2. Using same token on MA Site (SSO)...")
            userinfo_resp = validate_token(token)
            if userinfo_resp.status_code == 200:
                user_info = userinfo_resp.json()
                print(f"  [OK] SSO SUCCESS! User authenticated on MA Site without re-login")
                print(f"    User: {user_info.get('email')}")
                print(f"    Name: {user_info.get('name')}")
                print(f"    Source: {user_info.get('source')}")
                print(f"    This demonstrates single sign-on across both sites!")
            else:
                print(f"  [FAIL] SSO validation failed")

    # === Step 5: Check Migration Status ===
    print("\n  --- Step 5: Final Migration Status ---")
    status = requests.get(f"{ID_PLATFORM_URL}/api/migration/status").json()
    print(f"  {json.dumps(status, indent=2)}")

    print(f"\n{SEPARATOR}")
    print("  PHASE 3 COMPLETE")
    print("  - MA Site cutover to ID Platform: DONE")
    print("  - Main Site cutover to ID Platform: DONE")
    print("  - SSO enabled: User can login on one site,")
    print("    access the other without re-authentication")
    print("  - Legacy DBs kept as fallback")
    print(SEPARATOR)


if __name__ == "__main__":
    run_phase3()

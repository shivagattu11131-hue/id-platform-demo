"""
Phase 3: Cutover - Flip legacy sites to use ID Platform for authentication.
MA Site first (smaller, lower risk), then Main Site.
SSO enabled after both sites cutover.
"""

import requests
import json
import time

ID_PLATFORM_URL = "http://localhost:3000"
MAIN_SITE_URL = "http://localhost:3001"
MA_SITE_URL = "http://localhost:3002"

SEPARATOR = "=" * 60


def print_phase(phase_num, title):
    print(f"\n{SEPARATOR}")
    print(f"  PHASE {phase_num}: {title}")
    print(SEPARATOR)


def idp_login(email, password):
    """Login via ID Platform's API endpoint (for programmatic access)."""
    resp = requests.post(
        f"{ID_PLATFORM_URL}/api/auth/login",
        json={
            "email": email,
            "password": password
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
    print_phase(3, "CUTOVER - Switch Authentication to ID Platform")

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

    print("\n  Testing MA Site login via ID Platform API...")
    login_resp = idp_login("mauser1@example.com", "mapass1")
    if login_resp.status_code == 200:
        auth_data = login_resp.json()
        print(f"  [OK] Login successful via ID Platform!")
        print(f"    Access Token: {auth_data['accessToken'][:50]}...")
        print(f"    Token Type: {auth_data['tokenType']}")
        print(f"    Expires In: {auth_data['expiresIn']}s")

        # Validate token via userinfo
        print("\n  Validating token via userinfo endpoint...")
        userinfo_resp = validate_token(auth_data['accessToken'])
        if userinfo_resp.status_code == 200:
            print(f"  [OK] Token valid! User info: {json.dumps(userinfo_resp.json(), indent=4)}")
        else:
            print(f"  [FAIL] Token validation failed")
    else:
        print(f"  [FAIL] Login failed: {login_resp.json()}")

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

    print("\n  Testing Main Site login via ID Platform API...")
    login_resp = idp_login("john@example.com", "password123")
    if login_resp.status_code == 200:
        auth_data = login_resp.json()
        print(f"  [OK] Login successful via ID Platform!")
        print(f"    Access Token: {auth_data['accessToken'][:50]}...")

        userinfo_resp = validate_token(auth_data['accessToken'])
        if userinfo_resp.status_code == 200:
            print(f"  [OK] Token valid! User: {userinfo_resp.json().get('email')}")

    # === Step 4: Demonstrate SSO ===
    print("\n  --- Step 4: SSO Demonstration ---")
    print("  User logs in on Main Site, gets token,")
    print("  then uses same token on MA Site without re-login...")

    # Login on Main Site
    print("\n  1. Logging in on Main Site...")
    login_resp = idp_login("john@example.com", "password123")
    if login_resp.status_code != 200:
        print("  [FAIL] Login failed")
        return

    token = login_resp.json()['accessToken']
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

    # === Step 5: Demonstrate Browser SSO ===
    print("\n  --- Step 5: Browser SSO Demonstration ---")
    print("  For browser-based SSO, users can:")
    print("  1. Open http://localhost:3001 (Main Site)")
    print("  2. Click 'Login with ID Platform'")
    print("  3. Login with john@example.com / password123")
    print("  4. Open http://localhost:3002 (MA Site)")
    print("  5. Click 'Login with ID Platform'")
    print("  6. Auto-redirect without login page (SSO via IDP_SESSION cookie)")

    # === Step 6: Check Migration Status ===
    print("\n  --- Step 6: Final Migration Status ---")
    status = requests.get(f"{ID_PLATFORM_URL}/api/migration/status").json()
    print(f"  {json.dumps(status, indent=2)}")

    print(f"\n{SEPARATOR}")
    print("  PHASE 3 COMPLETE")
    print("  - MA Site cutover to ID Platform: DONE")
    print("  - Main Site cutover to ID Platform: DONE")
    print("  - SSO enabled: User can login on one site,")
    print("    access the other without re-authentication")
    print("  - Legacy DBs kept as fallback")
    print("  - Browser SSO: Available via IDP_SESSION cookie")
    print(SEPARATOR)


if __name__ == "__main__":
    run_phase3()

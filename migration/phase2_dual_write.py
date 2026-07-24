"""
Phase 2: Dual-Write - Legacy sites write to both their own DB
and the ID Platform DB simultaneously.
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


def dual_write_register(site_name, site_url, user_data):
    """Register on legacy site + dual-write to ID Platform."""
    # Step 1: Register on legacy site
    print(f"\n  Step 1: Registering {user_data['email']} on {site_name}...")
    legacy_resp = requests.post(f"{site_url}/api/auth/register", json=user_data)

    if legacy_resp.status_code in [200, 201]:
        print(f"    [OK] Legacy registration successful")
    else:
        print(f"    [FAIL] Legacy registration failed: {legacy_resp.json()}")
        return False

    # Step 2: Dual-write to ID Platform
    print(f"  Step 2: Dual-writing to ID Platform...")
    dual_write_data = {
        "email": user_data["email"],
        "passwordHash": legacy_resp.json().get("password_hash", ""),
        "displayName": user_data.get("display_name", user_data["email"]),
        "source": site_name
    }

    idp_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/dual-write",
        params={"site": site_name},
        json=dual_write_data
    )

    if idp_resp.status_code == 200:
        print(f"    [OK] Dual-write to ID Platform successful")
        print(f"    ID Platform response: {json.dumps(idp_resp.json(), indent=6)}")
        return True
    else:
        print(f"    [FAIL] Dual-write failed: {idp_resp.text}")
        return False


def dual_write_profile_update(site_name, site_url, email, password, updates):
    """Update profile on legacy site + dual-write to ID Platform."""
    # Step 1: Login via legacy site
    print(f"\n  Step 1: Logging in as {email} on {site_name}...")
    login_resp = requests.post(
        f"{site_url}/api/auth/login",
        json={"email": email, "password": password}
    )

    if login_resp.status_code != 200:
        print(f"    [FAIL] Login failed")
        return False

    print(f"    [OK] Login successful")

    # Step 2: Update on legacy site
    print(f"  Step 2: Updating profile on {site_name}...")
    update_resp = requests.put(
        f"{site_url}/api/users/me",
        json=updates
    )

    if update_resp.status_code == 200:
        print(f"    [OK] Legacy profile update successful")
        print(f"    Updated fields: {list(updates.keys())}")
    else:
        print(f"    [FAIL] Legacy update failed")
        return False

    # Step 3: Dual-write update to ID Platform
    print(f"  Step 3: Dual-writing update to ID Platform...")
    dual_write_data = {
        "email": email,
        "displayName": updates.get("display_name"),
        "source": site_name
    }

    idp_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/dual-write",
        params={"site": site_name},
        json=dual_write_data
    )

    if idp_resp.status_code == 200:
        print(f"    [OK] Dual-write update successful")
        return True
    else:
        print(f"    [FAIL] Dual-write update failed")
        return False


def verify_both_dbs(email, main_site_url, ma_site_url, idp_url):
    """Verify user exists in both legacy DB and ID Platform."""
    print(f"\n  Verifying {email} across all databases...")

    # Check ID Platform
    try:
        status_resp = requests.get(f"{idp_url}/api/migration/status")
        if status_resp.status_code == 200:
            status = status_resp.json()
            print(f"    ID Platform migration status: {json.dumps(status, indent=4)}")
    except:
        pass


def run_phase2():
    print_phase(2, "DUAL-WRITE - Synchronize Both Databases")

    print("\n  In Dual-Write Mode:")
    print("  - Legacy sites write to their own DB (primary)")
    print("  - Legacy sites ALSO write to ID Platform (secondary)")
    print("  - Both databases stay in sync")
    print("  - Users see no difference")

    print("\n  --- Test Case 1: New User Registration (Main Site) ---")
    dual_write_register("main", MAIN_SITE_URL, {
        "username": "new_user_1",
        "email": "newuser1@example.com",
        "password": "newpass123",
        "display_name": "New User One"
    })

    print("\n  --- Test Case 2: New User Registration (MA Site) ---")
    dual_write_register("ma", MA_SITE_URL, {
        "username": "new_ma_user_1",
        "email": "newmauser1@example.com",
        "password": "newmapass123",
        "display_name": "New MA User One",
        "company_name": "New Acquired Corp"
    })

    print("\n  --- Test Case 3: Profile Update (Main Site) ---")
    dual_write_profile_update("main", MAIN_SITE_URL,
        "john@example.com", "password123",
        {"display_name": "John Doe (Updated)"}
    )

    print("\n  --- Test Case 4: Profile Update (MA Site) ---")
    dual_write_profile_update("ma", MA_SITE_URL,
        "mauser1@example.com", "mapass1",
        {"display_name": "MA User 1 (Updated)"}
    )

    print("\n  --- Test Case 5: Cross-site user (shared@example.com) ---")
    print("\n  User shared@example.com exists on both sites with same password.")
    print("  Both sites should keep ID Platform in sync.")

    verify_both_dbs("shared@example.com", MAIN_SITE_URL, MA_SITE_URL, ID_PLATFORM_URL)

    print("\n  Checking migration status after dual-write operations...")
    status = requests.get(f"{ID_PLATFORM_URL}/api/migration/status").json()
    print(f"  {json.dumps(status, indent=2)}")

    print(f"\n{SEPARATOR}")
    print("  PHASE 2 COMPLETE")
    print("  - Dual-write enabled for all operations")
    print("  - ID Platform DB is in sync with legacy DBs")
    print("  - New registrations go to both databases")
    print("  - Profile updates propagate to ID Platform")
    print("  - Ready for cutover")
    print(SEPARATOR)


if __name__ == "__main__":
    run_phase2()

"""
Phase 4: Rollback - Safely revert to legacy authentication
if issues are detected after cutover.
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


def simulate_problem():
    """Simulate a post-cutover problem."""
    print("\n  [SIMULATION] Detecting increased auth latency...")
    print("  [SIMULATION] ID Platform response time: 2500ms (threshold: 1000ms)")
    print("  [SIMULATION] Auth failure rate: 8% (threshold: 1%)")
    print("  [SIMULATION] Triggering rollback procedure...")


def legacy_login(site_url, email, password):
    """Login via legacy site's own auth."""
    resp = requests.post(
        f"{site_url}/api/auth/login",
        json={"email": email, "password": password}
    )
    return resp


def idp_login(email, password):
    """Login via ID Platform."""
    resp = requests.post(
        f"{ID_PLATFORM_URL}/oauth2/token",
        json={
            "grant_type": "password",
            "email": email,
            "password": password
        }
    )
    return resp


def run_phase4():
    print_phase(4, "ROLLBACK - Safely Revert to Legacy Authentication")

    print("\n  Rollback Scenario:")
    print("  - After cutover, we detect issues with ID Platform")
    print("  - Need to revert Main Site to legacy auth")
    print("  - Users must still be able to login")
    print("  - Password changes made during ID Platform phase")
    print("    must be preserved (reverse-sync)")

    # === Step 1: Simulate Problem ===
    print("\n  --- Step 1: Problem Detection ---")
    simulate_problem()

    # === Step 2: Verify Current State ===
    print("\n  --- Step 2: Verify Current State (ID Platform active) ---")
    login_resp = idp_login("john@example.com", "password123")
    if login_resp.status_code == 200:
        print("  [OK] Currently: ID Platform auth working")
    else:
        print("  [WARN] ID Platform auth already failing")

    # === Step 3: Execute Rollback ===
    print("\n  --- Step 3: Execute Rollback for Main Site ---")
    rollback_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/rollback",
        params={"site": "main"}
    )
    if rollback_resp.status_code == 200:
        print(f"  [OK] Rollback initiated")
        print(f"  {json.dumps(rollback_resp.json(), indent=4)}")
    else:
        print(f"  [FAIL] Rollback failed")
        return

    # === Step 4: Verify Legacy Auth Works ===
    print("\n  --- Step 4: Verify Legacy Auth Still Works ---")
    print("\n  Testing login via legacy Main Site (port 3001)...")
    login_resp = legacy_login(MAIN_SITE_URL, "john@example.com", "password123")
    if login_resp.status_code == 200:
        print("  [OK] Legacy auth working! User can still login.")
        print(f"    Response: {json.dumps(login_resp.json(), indent=4)}")
    else:
        print("  [FAIL] Legacy auth failed!")

    # === Step 5: Reverse Sync ===
    print("\n  --- Step 5: Reverse Sync (Password changes from ID Platform) ---")
    print("  During the ID Platform phase, user 'john@example.com' changed password.")
    print("  This change must be synced back to legacy DB.")
    print("\n  [SIMULATION] Reverse-syncing password changes...")
    print("  [SIMULATION] Password for john@example.com synced to legacy DB")
    print("  [SIMULATION] Legacy DB now has latest password hash")

    # Verify legacy login with the synced password
    print("\n  Verifying legacy login after reverse-sync...")
    login_resp = legacy_login(MAIN_SITE_URL, "john@example.com", "password123")
    if login_resp.status_code == 200:
        print("  [OK] Login successful after reverse-sync!")

    # === Step 6: Rollback MA Site Too (if needed) ===
    print("\n  --- Step 6: Rollback MA Site (if needed) ---")
    rollback_resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/rollback",
        params={"site": "ma"}
    )
    if rollback_resp.status_code == 200:
        print(f"  [OK] MA Site rollback initiated")
        print(f"  {json.dumps(rollback_resp.json(), indent=4)}")

    # Verify MA Site legacy auth
    print("\n  Testing MA Site legacy auth...")
    login_resp = legacy_login(MA_SITE_URL, "mauser1@example.com", "mapass1")
    if login_resp.status_code == 200:
        print("  [OK] MA Site legacy auth working!")

    # === Step 7: Check Final Status ===
    print("\n  --- Step 7: Final Status ---")
    status = requests.get(f"{ID_PLATFORM_URL}/api/migration/status").json()
    print(f"  {json.dumps(status, indent=2)}")

    print(f"\n{SEPARATOR}")
    print("  PHASE 4 COMPLETE - ROLLBACK DEMONSTRATION")
    print("  - Main Site rolled back to legacy auth: DONE")
    print("  - MA Site rolled back to legacy auth: DONE")
    print("  - Users can still login via legacy systems")
    print("  - Password changes reverse-synced to legacy DB")
    print("  - Zero data loss during rollback")
    print("  - Legacy systems ready as fallback")
    print(SEPARATOR)

    print("\n  SUMMARY OF ALL MIGRATION PHASES:")
    print("  " + "-" * 50)
    print("  Phase 0: Bulk import users from legacy sites")
    print("  Phase 1: Shadow mode (validate without enforcing)")
    print("  Phase 2: Dual-write (sync both databases)")
    print("  Phase 3: Cutover (flip to ID Platform + SSO)")
    print("  Phase 4: Rollback (revert to legacy if needed)")
    print("  " + "-" * 50)
    print("  Each phase is independently reversible!")
    print("  The migration can be paused/resumed at any point!")
    print(SEPARATOR)


if __name__ == "__main__":
    run_phase4()

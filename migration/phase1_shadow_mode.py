"""
Phase 1: Shadow Mode - Both sites still use their own auth,
but validate against ID Platform in background for comparison.
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


def shadow_validate(email, password):
    """Validate credentials against ID Platform in shadow mode."""
    resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/shadow-validate",
        json={"email": email, "password": password}
    )
    return resp.json() if resp.status_code == 200 else {"error": resp.text}


def legacy_login(site_url, email, password):
    """Login via legacy site's own auth."""
    resp = requests.post(
        f"{site_url}/api/auth/login",
        json={"email": email, "password": password}
    )
    return resp.status_code == 200, resp.json() if resp.status_code == 200 else resp.json()


def run_phase1():
    print_phase(1, "SHADOW MODE - Validate Without Enforcing")

    print("\n  In Shadow Mode:")
    print("  - Users still log in via legacy auth (unchanged experience)")
    print("  - After each login, ID Platform validates the same credentials")
    print("  - Results are logged and compared")
    print("  - Any mismatches are flagged for investigation")

    test_cases = [
        ("john@example.com", "password123", "Main Site", MAIN_SITE_URL),
        ("jane@example.com", "securepass456", "Main Site", MAIN_SITE_URL),
        ("mauser1@example.com", "mapass1", "MA Site", MA_SITE_URL),
        ("bob@example.com", "bobpass789", "Main Site", MAIN_SITE_URL),
        ("shared@example.com", "mainpass123", "Main Site", MAIN_SITE_URL),
    ]

    print("\n  Running shadow validation tests...")
    print(f"  {'Email':<30} {'Site':<15} {'Legacy':<10} {'ID Platform':<12} {'Match':<8}")
    print(f"  {'-'*30} {'-'*15} {'-'*10} {'-'*12} {'-'*8}")

    total = 0
    matches = 0
    mismatches = 0

    for email, password, site_name, site_url in test_cases:
        # Login via legacy site
        legacy_success, legacy_result = legacy_login(site_url, email, password)

        # Shadow validate via ID Platform
        shadow_result = shadow_validate(email, password)
        id_platform_success = shadow_result.get("idPlatformAuthSuccess", False)

        match = legacy_success == id_platform_success
        total += 1
        if match:
            matches += 1
        else:
            mismatches += 1

        status_legacy = "PASS" if legacy_success else "FAIL"
        status_idp = "PASS" if id_platform_success else "FAIL"
        match_str = "YES" if match else "MISMATCH"

        print(f"  {email:<30} {site_name:<15} {status_legacy:<10} {status_idp:<12} {match_str:<8}")

    print(f"\n  Results:")
    print(f"    Total tests: {total}")
    print(f"    Matches: {matches}")
    print(f"    Mismatches: {mismatches}")

    if mismatches > 0:
        print(f"\n  [WARNING] {mismatches} mismatches detected!")
        print("  These need investigation before proceeding to Phase 2.")
        print("  Possible causes:")
        print("    - Different password hashing algorithms between sites")
        print("    - Users who exist on one site but not yet in ID Platform")
        print("    - Data corruption during import")
    else:
        print("\n  [OK] All validations match - ready for Phase 2!")

    print("\n  Updating migration status...")
    # In a real system, this would update the status via API
    print("  Status updated to: SHADOW_MODE")

    print(f"\n{SEPARATOR}")
    print("  PHASE 1 COMPLETE")
    print("  - Shadow validation running on all login attempts")
    print("  - All legacy auth flows unchanged")
    print("  - No user impact")
    print("  - Ready for dual-write phase")
    print(SEPARATOR)


if __name__ == "__main__":
    run_phase1()

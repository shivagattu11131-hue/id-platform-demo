"""
Full Demo Walkthrough - Runs all migration phases sequentially.
Demonstrates the complete ID Platform migration lifecycle.
"""

import subprocess
import sys
import time
import os
import requests

SEPARATOR = "=" * 70
ID_PLATFORM_URL = "http://localhost:3000"
MAIN_SITE_URL = "http://localhost:3001"
MA_SITE_URL = "http://localhost:3002"
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def print_banner():
    print(f"""
{SEPARATOR}
  RAKSUL ID PLATFORM MIGRATION DEMO
  ==================================
  Simulating: Legacy Monoliths -> Unified ID Platform

  Architecture:
    - Main Site (Port 3001): Legacy Rails Monolith (3M users)
    - MA Site (Port 3002): Acquired Company Monolith (100K users)
    - ID Platform (Port 3000): Unified Identity Provider (OIDC)

  Tech Stack:
    - ID Platform: Java 17, Spring Boot 3, OpenID Connect, JWT (RS256)
    - Legacy Sites: Python Flask (simulating Ruby on Rails)
    - Database: H2/Aurora MySQL compatible
    - Zero-trust security model
{SEPARATOR}
""")


def check_services():
    print("\n[Pre-flight] Checking all services are running...")
    services = [
        (MAIN_SITE_URL, "Main Site"),
        (MA_SITE_URL, "MA Site"),
        (ID_PLATFORM_URL, "ID Platform"),
    ]

    all_ok = True
    for url, name in services:
        try:
            resp = requests.get(f"{url}/api/health", timeout=5)
            if resp.status_code == 200:
                print(f"  [OK] {name} is running")
            else:
                print(f"  [WARN] {name} returned status {resp.status_code}")
                all_ok = False
        except:
            print(f"  [FAIL] {name} is NOT running at {url}")
            all_ok = False

    return all_ok


def run_import(module_name, title):
    print(f"\n{'─' * 50}")
    print(f"  Running: {title}")
    print(f"{'─' * 50}")

    try:
        result = subprocess.run(
            [sys.executable, os.path.join(BASE_DIR, "migration", f"{module_name}.py")],
            capture_output=True,
            text=True,
            timeout=60
        )
        print(result.stdout)
        if result.stderr:
            print("STDERR:", result.stderr)
        return result.returncode == 0
    except subprocess.TimeoutExpired:
        print(f"  [TIMEOUT] {title} took too long")
        return False
    except Exception as e:
        print(f"  [ERROR] {e}")
        return False


def interactive_pause(message):
    print(f"\n  >> {message}")
    input("  >> Press Enter to continue...")


def main():
    print_banner()

    if not check_services():
        print("\n  Please start all three services first:")
        print("  " + "─" * 50)
        print("  Terminal 1: cd legacy-main-site && pip install -r requirements.txt && python app.py")
        print("  Terminal 2: cd legacy-ma-site && pip install -r requirements.txt && python app.py")
        print("  Terminal 3: cd id-platform && mvn spring-boot:run")
        print("  " + "─" * 50)
        print("\n  Then run this script again.")
        return

    print("\n  All services are running! Starting migration demo...")

    interactive_pause("Ready to start Phase 0 (Bulk Import)?")

    # Phase 0
    success = run_import("phase0_bulk_import", "Phase 0: Bulk Import")
    if not success:
        print("\n  Phase 0 failed. Please check the error messages.")
        return

    interactive_pause("Phase 0 complete. Ready for Phase 1 (Shadow Mode)?")

    # Phase 1
    run_import("phase1_shadow_mode", "Phase 1: Shadow Mode")

    interactive_pause("Phase 1 complete. Ready for Phase 2 (Dual-Write)?")

    # Phase 2
    run_import("phase2_dual_write", "Phase 2: Dual-Write")

    interactive_pause("Phase 2 complete. Ready for Phase 3 (Cutover)?")

    # Phase 3
    run_import("phase3_cutover", "Phase 3: Cutover + SSO")

    interactive_pause("Phase 3 complete. Ready for Phase 4 (Rollback)?")

    # Phase 4
    run_import("phase4_rollback", "Phase 4: Rollback")

    print(f"""
{SEPARATOR}
  DEMO COMPLETE!
  ================

  What was demonstrated:
    1. Two independent legacy sites with separate auth systems
    2. Bulk migration of users to unified ID Platform
    3. Shadow validation (compare old vs new auth)
    4. Dual-write synchronization
    5. Cutover to OIDC-based authentication
    6. Single Sign-On (SSO) across both sites
    7. Safe rollback to legacy systems

  Architecture highlights:
    - OpenID Connect (OIDC) provider in Spring Boot
    - JWT tokens with RSA-256 signing
    - Zero-trust security: every request validated
    - Conflict resolution for M&A user consolidation
    - Rollback-safe: any phase is reversible
    - Zero-downtime migration strategy

  Key constraints addressed:
    - No forced password resets (lazy migration)
    - Zero downtime (phased cutover)
    - Safe rollback (dual-write + reverse sync)
    - M&A identity consolidation

  For interview discussion:
    - Reference architecture diagrams in /architecture
    - Explain trade-offs in each phase
    - Discuss production scaling considerations
    - Talk about monitoring and alerting strategy
{SEPARATOR}
""")


if __name__ == "__main__":
    main()

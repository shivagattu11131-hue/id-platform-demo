"""
Phase 0: Bulk Import - Migrate users from legacy sites to ID Platform
This simulates the initial data migration from Ruby on Rails monoliths
to the new unified ID Platform.
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


def check_server(url, name):
    try:
        resp = requests.get(f"{url}/api/health", timeout=5)
        if resp.status_code == 200:
            print(f"  [OK] {name} is running")
            return True
    except:
        pass
    print(f"  [FAIL] {name} is NOT running at {url}")
    return False


def seed_main_site_users():
    """Seed users into the legacy Main Site (simulating existing Rails DB)."""
    print("\n  Seeding Main Site with sample users...")

    users = [
        {"username": "john_doe", "email": "john@example.com", "password": "password123", "display_name": "John Doe"},
        {"username": "jane_smith", "email": "jane@example.com", "password": "securepass456", "display_name": "Jane Smith"},
        {"username": "bob_wilson", "email": "bob@example.com", "password": "bobpass789", "display_name": "Bob Wilson"},
        {"username": "alice_jones", "email": "alice@example.com", "password": "alicepass", "display_name": "Alice Jones"},
        {"username": "charlie_brown", "email": "charlie@example.com", "password": "charlie123", "display_name": "Charlie Brown"},
        # This user also exists on MA site (conflict scenario)
        {"username": "dual_user_main", "email": "shared@example.com", "password": "mainpass123", "display_name": "Dual User (Main)"},
        # Another conflict scenario
        {"username": "conflict_user1", "email": "conflict@example.com", "password": "passA", "display_name": "Conflict User A"},
    ]

    created = 0
    for user in users:
        try:
            resp = requests.post(f"{MAIN_SITE_URL}/api/auth/register", json=user)
            if resp.status_code in [200, 201]:
                created += 1
                print(f"    + Created: {user['email']} (Main Site)")
            else:
                print(f"    - Skipped: {user['email']} (already exists)")
        except Exception as e:
            print(f"    ! Error creating {user['email']}: {e}")

    print(f"  Main Site: {created} users created")
    return created


def seed_ma_site_users():
    """Seed users into the legacy MA Site (simulating acquired company's DB)."""
    print("\n  Seeding MA Site with sample users...")

    users = [
        {"username": "ma_user1", "email": "mauser1@example.com", "password": "mapass1", "display_name": "MA User 1", "company_name": "Acquired Corp A"},
        {"username": "ma_user2", "email": "mauser2@example.com", "password": "mapass2", "display_name": "MA User 2", "company_name": "Acquired Corp A"},
        {"username": "ma_user3", "email": "mauser3@example.com", "password": "mapass3", "display_name": "MA User 3", "company_name": "Acquired Corp B"},
        {"username": "acquired_bob", "email": "bob@example.com", "password": "differentpass", "display_name": "Acquired Bob", "company_name": "Acquired Corp A"},
        # Same email as Main Site, SAME password (merge scenario)
        {"username": "dual_user_ma", "email": "shared@example.com", "password": "mainpass123", "display_name": "Dual User (MA)", "company_name": "Acquired Corp A"},
        # Same email as Main Site, DIFFERENT password (conflict scenario)
        {"username": "conflict_user2", "email": "conflict@example.com", "password": "passB", "display_name": "Conflict User B", "company_name": "Acquired Corp B"},
    ]

    created = 0
    for user in users:
        try:
            resp = requests.post(f"{MA_SITE_URL}/api/auth/register", json=user)
            if resp.status_code in [200, 201]:
                created += 1
                print(f"    + Created: {user['email']} (MA Site)")
            else:
                print(f"    - Skipped: {user['email']} (already exists)")
        except Exception as e:
            print(f"    ! Error creating {user['email']}: {e}")

    print(f"  MA Site: {created} users created")
    return created


def fetch_legacy_users(url, site_name):
    """Fetch all users from a legacy site."""
    try:
        resp = requests.get(f"{url}/api/users/all")
        if resp.status_code == 200:
            return resp.json()
    except Exception as e:
        print(f"  Error fetching from {site_name}: {e}")
    return []


def import_to_id_platform(site_name, users):
    """Bulk import users to ID Platform."""
    legacy_users = []
    for u in users:
        legacy_users.append({
            "id": str(u.get("id", "")),
            "email": u["email"],
            "passwordHash": u["password_hash"],
            "displayName": u.get("display_name", u["email"]),
            "source": site_name,
            "createdAt": u.get("created_at", "")
        })

    resp = requests.post(
        f"{ID_PLATFORM_URL}/api/migration/import",
        params={"site": site_name},
        json=legacy_users
    )
    return resp.json() if resp.status_code == 200 else {"error": resp.text}


def run_phase0():
    print_phase(0, "BULK IMPORT - Migrate Legacy Users to ID Platform")

    print("\n  Step 1: Checking all services are running...")
    all_running = True
    all_running &= check_server(MAIN_SITE_URL, "Main Site (Port 3001)")
    all_running &= check_server(MA_SITE_URL, "MA Site (Port 3002)")
    all_running &= check_server(ID_PLATFORM_URL, "ID Platform (Port 3000)")

    if not all_running:
        print("\n  [ERROR] Not all services are running!")
        print("  Please start all three servers first:")
        print("    Terminal 1: python legacy-main-site/app.py")
        print("    Terminal 2: python legacy-ma-site/app.py")
        print("    Terminal 3: cd id-platform && mvn spring-boot:run")
        return False

    print("\n  Step 2: Seeding legacy sites with sample users...")
    main_count = seed_main_site_users()
    ma_count = seed_ma_site_users()

    print(f"\n  Total legacy users: {main_count} (Main) + {ma_count} (MA)")
    print(f"  Expected conflicts: 2 (shared@example.com same password = merge)")
    print(f"                       (conflict@example.com different password = flagged)")
    print(f"                       (bob@example.com different password = new user from MA)")

    print("\n  Step 3: Fetching users from Main Site...")
    main_users = fetch_legacy_users(MAIN_SITE_URL, "Main Site")
    print(f"  Fetched {len(main_users)} users from Main Site")

    print("\n  Step 4: Importing Main Site users to ID Platform...")
    result = import_to_id_platform("main", main_users)
    print(f"  Result: {json.dumps(result, indent=2)}")

    print("\n  Step 5: Fetching users from MA Site...")
    ma_users = fetch_legacy_users(MA_SITE_URL, "MA Site")
    print(f"  Fetched {len(ma_users)} users from MA Site")

    print("\n  Step 6: Importing MA Site users to ID Platform...")
    result = import_to_id_platform("ma", ma_users)
    print(f"  Result: {json.dumps(result, indent=2)}")

    print("\n  Step 7: Checking migration status...")
    status = requests.get(f"{ID_PLATFORM_URL}/api/migration/status").json()
    print(f"  Migration Status: {json.dumps(status, indent=2)}")

    print(f"\n{SEPARATOR}")
    print("  PHASE 0 COMPLETE")
    print("  - All legacy users imported to ID Platform")
    print("  - Duplicate users with same email detected")
    print("  - Conflicts flagged for manual resolution")
    print("  - No user-facing changes yet")
    print(SEPARATOR)
    return True


if __name__ == "__main__":
    run_phase0()

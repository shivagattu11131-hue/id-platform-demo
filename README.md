# Raksul ID Platform - Migration Demo

## Overview

This project demonstrates the migration from two independent legacy authentication systems (Ruby on Rails monoliths) to a unified Identity Provider (IdP) using **Java Spring Boot** with **OpenID Connect (OIDC)**.

### Scenario
- **Main Site**: E-commerce platform with 3M customers, ~100M yen/day sales
- **MA Site**: Acquired company with 100K members, 20K MAU
- **Goal**: Unify auth systems into a single ID Platform with SSO

### Tech Stack
| Component | Technology |
|-----------|-----------|
| ID Platform | Java 17, Spring Boot 3, OIDC, JWT (RS256) |
| Legacy Sites | Python Flask (simulating Ruby on Rails) |
| Database | H2 (demo) / Aurora MySQL (production) |
| Security | Zero-trust, RSA-256 token signing |
| Infrastructure | AWS ECS, EC2, Aurora MySQL, CloudFront, Lambda |

---

## Project Structure

```
id-platform-demo/
├── id-platform/                    # Java Spring Boot - Core ID Platform
│   ├── pom.xml
│   └── src/main/java/com/raksul/idplatform/
│       ├── IdPlatformApplication.java
│       ├── config/
│       │   └── JwksConfig.java          # RSA key pair for JWT signing
│       ├── controller/
│       │   ├── AuthController.java      # /api/auth/* endpoints
│       │   ├── OidcController.java      # /oauth2/* OIDC endpoints
│       │   └── MigrationController.java # /api/migration/* endpoints
│       ├── model/
│       │   ├── User.java                # User entity
│       │   ├── AuthToken.java           # Token entity
│       │   ├── MigrationStatus.java     # Migration state tracking
│       │   ├── AuthRequest.java         # Request DTOs
│       │   ├── AuthResponse.java        # Response DTOs
│       │   ├── LegacyUser.java          # Legacy user DTO
│       │   ├── MigrationResult.java     # Migration result DTO
│       │   └── ShadowValidationResult.java
│       ├── repository/
│       │   ├── UserRepository.java
│       │   ├── AuthTokenRepository.java
│       │   └── MigrationStatusRepository.java
│       ├── service/
│       │   ├── AuthService.java         # Registration, login, profile
│       │   ├── TokenService.java        # JWT generation & validation
│       │   ├── OidcService.java         # OIDC discovery, JWKS, userinfo
│       │   └── MigrationService.java    # Bulk import, dual-write, cutover, rollback
│       └── security/
│           └── ZeroTrustFilter.java     # Zero-trust auth filter
│
├── legacy-main-site/               # Python Flask - Simulates Rails Monolith
│   ├── app.py                      # Port 3001
│   └── requirements.txt
│
├── legacy-ma-site/                 # Python Flask - Simulates Acquired Site
│   ├── app.py                      # Port 3002
│   └── requirements.txt
│
├── migration/                      # Migration phase scripts
│   ├── phase0_bulk_import.py       # Import users from legacy sites
│   ├── phase1_shadow_mode.py       # Validate without enforcing
│   ├── phase2_dual_write.py        # Sync both databases
│   ├── phase3_cutover.py           # Flip to ID Platform + SSO
│   ├── phase4_rollback.py          # Safely revert to legacy
│   └── requirements.txt
│
├── scripts/
│   └── demo.py                     # Full demo walkthrough
│
├── architecture/
│   ├── current-state.drawio        # Before migration
│   ├── intermediate-state.drawio   # During migration
│   └── final-state.drawio          # After migration
│
└── README.md
```

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Python 3.8+
- pip

### Step 1: Install Dependencies

```bash
# Legacy sites
cd legacy-main-site
pip install -r requirements.txt
cd ../legacy-ma-site
pip install -r requirements.txt
cd ..

# Migration scripts
cd migration
pip install -r requirements.txt
cd ..
```

### Step 2: Start All Services

Open **3 terminals**:

```bash
# Terminal 1: Legacy Main Site
cd legacy-main-site
python app.py
# Running on http://localhost:3001

# Terminal 2: Legacy MA Site
cd legacy-ma-site
python app.py
# Running on http://localhost:3002

# Terminal 3: ID Platform
cd id-platform
mvn spring-boot:run
# Running on http://localhost:3000
```

### Step 3: Run the Demo

```bash
# Terminal 4: Run full demo
cd migration
python ../scripts/demo.py
```

---

## Migration Phases

### Phase 0: Bulk Import
- Fetch all users from both legacy sites
- Resolve conflicts (same email on both sites)
- Insert into ID Platform's unified database
- **No user-facing changes**

### Phase 1: Shadow Mode
- Both sites still use own auth for login
- After each login, ID Platform validates credentials in background
- Results logged and compared for discrepancies
- **No user impact**

### Phase 2: Dual-Write
- Legacy sites write to both own DB and ID Platform
- Both databases stay in sync
- New registrations, profile updates propagate to ID Platform
- **Users see no difference**

### Phase 3: Cutover
- MA Site flipped first (smaller, lower risk)
- Main Site flipped second (more cautious)
- SSO enabled: Login on Main → Access MA without re-login
- Legacy DBs kept as fallback

### Phase 4: Rollback
- Simulate post-cutover issues
- Rollback Main Site to legacy auth
- Reverse-sync password changes to legacy DB
- Users can still login (seamless)
- **Zero data loss**

---

## API Reference

### OIDC Endpoints (ID Platform)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/.well-known/openid-configuration` | OIDC discovery document |
| GET | `/jwks.json` | Public keys for token verification |
| POST | `/oauth2/authorize` | Authorization endpoint |
| POST | `/oauth2/token` | Token endpoint (password, refresh_token grants) |
| GET | `/oauth2/userinfo` | User profile from valid token |
| POST | `/oauth2/revoke` | Token revocation |

### Auth Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login (returns JWT tokens) |
| GET | `/api/users/me` | Get current user profile |
| PUT | `/api/users/me` | Update profile |
| DELETE | `/api/users/me` | Deactivate account |

### Migration Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/migration/import?site={name}` | Bulk import users |
| POST | `/api/migration/shadow-validate` | Shadow mode validation |
| POST | `/api/migration/dual-write?site={name}` | Dual-write a user |
| POST | `/api/migration/cutover?site={name}` | Cutover site to ID Platform |
| POST | `/api/migration/rollback?site={name}` | Rollback to legacy auth |
| GET | `/api/migration/status` | Migration progress |

---

## Key Design Decisions

### 1. No Forced Password Resets
Users' existing credentials are imported into ID Platform. On first login after cutover, their password hash is validated against the imported hash. No re-registration needed.

### 2. Zero Downtime Migration
Phased approach: Shadow → Dual-Write → Cutover. Each phase can run for days/weeks. Cutover is a simple config flip, not a data migration.

### 3. Safe Rollback
Dual-write ensures old DB always has latest data. Rollback flips auth routing back to legacy. Reverse-sync handles password changes made during ID Platform phase.

### 4. M&A Conflict Resolution
| Scenario | Resolution |
|----------|-----------|
| Same email, same password hash | Merge into one account |
| Same email, different password | Flag for manual resolution |
| Different emails | Keep both, link under profile |

### 5. Zero-Trust Security
- Every request must carry a valid JWT
- Tokens signed with RSA-256 (asymmetric)
- No implicit trust between services
- Token revocation supported
- Rate limiting per user/IP

---

## Production Considerations

| Aspect | Demo | Production |
|--------|------|-----------|
| Database | H2 (file-based) | Aurora MySQL with read replicas |
| Token Cache | None | Redis for fast validation |
| Rate Limiting | None | AWS WAF + custom limiter |
| Monitoring | Logs | Datadog, CloudWatch |
| Deployment | Local | AWS ECS with rolling deploys |
| CI/CD | Manual | GitHub Actions + CodePipeline |
| Load Balancing | None | ALB with health checks |
| Secrets | Config file | AWS Secrets Manager |

---

## Architecture Diagrams

Open the `.drawio` files in `architecture/` folder using [draw.io](https://app.diagrams.net) or VS Code with Draw.io extension.

- **current-state.drawio**: Two independent auth systems (before migration)
- **intermediate-state.drawio**: During migration with dual-write
- **final-state.drawio**: Unified ID Platform with SSO (after migration)

---

## Interview Discussion Points

### Technical Deep Dives
1. Why OIDC over custom JWT? (Standards, interoperability, tooling)
2. How does zero-trust work in practice? (Every request validated, no implicit trust)
3. What happens during a network partition between sites?
4. How do you handle token expiry during long-running operations?

### Migration Strategy
1. Why start with MA site (smaller)? (Lower risk, faster feedback)
2. How long should shadow mode run? (Depends on traffic, typically 1-2 weeks)
3. What metrics indicate readiness for cutover?
4. How do you handle users who change passwords during migration?

### Scale Considerations
1. How does this work with 3M+ users? (Connection pooling, read replicas, caching)
2. What's the latency impact of OIDC validation? (JWT is stateless, <1ms with cache)
3. How do you handle geographic distribution? (Multi-region Aurora, CloudFront)

### Incident Response
1. What's your rollback SLA? (<5 minutes via feature flag)
2. How do you detect auth degradation? (Failure rate, latency percentiles)
3. What's the blast radius of an ID Platform outage? (All sites affected, mitigation: legacy fallback)

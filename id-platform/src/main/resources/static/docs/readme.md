# Raksul ID Platform — Unified Identity Migration Demo

A working demonstration of migrating two independent legacy authentication systems into a single Identity Provider using Java Spring Boot with OpenID Connect support.

## Business scenario

| | Main Site | MA Site (Acquired) |
|---|---|---|
| Type | E-commerce platform | Acquired company platform |
| Users | ~3M customers | ~100K members |
| Password Hash | SHA-256 + salt | Plain MD5 |
| DB Schema | `password_hash` column | `password_md5` column |

**Goal:** Unify both auth systems into a single ID platform with SSO, minimal user disruption, and safe migration.

## Architecture overview

```text
┌─────────────────────────────────────────────────────────┐
│                    Users / Browsers                    │
└───────────┬──────────────────┬─────────────────────────┘
            │                  │
            ▼                  ▼
┌───────────────────┐  ┌────────────────────────────────┐
│   ID Platform     │  │        Dashboard UI             │
│   (Port 3000)     │◄─┤  Migration phases + SSE stream  │
│                   │  └────────────────────────────────┘
│  ┌─────────────┐  │
│  │ OIDC Engine │  │         ┌──────────────────────┐
│  │ RS256 JWT   │  │◄────────│  Legacy Main Site    │
│  │ PKCE        │  │  OIDC   │  (Port 3001)         │
│  └─────────────┘  │  Fed    │  Python Flask/SQLite │
│  ┌─────────────┐  │         │  SHA-256+salt        │
│  │ Migration   │  │         └──────────────────────┘
│  │ Engine      │  │
│  │ Import      │  │         ┌──────────────────────┐
│  │ Shadow      │  │◄────────│  Legacy MA Site      │
│  │ Dual-Write  │  │  OIDC   │  (Port 3002)         │
│  │ Cutover     │  │  Fed    │  Python Flask/SQLite │
│  │ Rollback    │  │         │  Plain MD5           │
│  └─────────────┘  │         └──────────────────────┘
└───────────────────┘
         │
         ▼
┌───────────────────┐
│  H2 Database      │
│  (Aurora MySQL    │
│   in production)  │
└───────────────────┘
```

## Architecture diagrams

### Current state

Two independent auth systems with no connection between them.

![Current State](/architecture/current-state.png)

### Intermediate state

Both legacy sites still handle auth. The ID platform is populated and validated in the background.

![Intermediate State](/architecture/intermediate-state.png)

### Final state

Unified ID platform with OIDC federation and SSO across both sites.

![Final State](/architecture/final-state.png)

## Migration strategy

### Phase 0 — Bulk import

No user-facing changes. Fetches all users from both legacy sites and imports them into the ID platform.

- resolves email conflicts
- preserves legacy password hashes
- tracks user origin per site

### Phase 1 — Shadow mode

No user impact. Validates credentials against the ID platform in the background while legacy auth remains active.

- compares legacy and ID platform behavior
- highlights mismatches before cutover
- builds confidence before traffic is switched

### Phase 2 — Dual-write

Users see no difference. New registrations and profile updates write to both legacy systems and the ID platform.

- keeps both systems in sync
- reduces rollback risk
- preserves recent changes during migration

### Phase 3 — Cutover plus SSO

Authentication switches to the ID platform. MA site cutover happens first, then Main Site.

- both sites become OIDC clients
- SSO is enabled across services
- the IDP session supports silent re-authentication

### Phase 4 — Rollback

Emergency revert path. Both sites can switch back to legacy auth if needed.

- legacy auth path remains available
- rollback is site-by-site
- migration data is retained for re-attempts

## Key implementation points

### Password migration

The ID platform supports multiple credential formats during migration:

| Source | Algorithm | Support |
|---|---|---|
| ID Platform native | BCrypt | Native |
| Main Site legacy | SHA-256 + salt | Supported |
| MA Site legacy | MD5 | Supported |

On successful login after cutover, legacy hashes can be upgraded to the stronger platform hash without forcing a password reset.

### M&A overlap handling

When the same email exists on both sites:

- same credentials can be treated as merge candidates
- conflicting credentials should be flagged for manual resolution

### SSO model

The legacy sites integrate with the ID platform using OIDC Authorization Code Flow with PKCE. Tokens are signed with RS256 and can be verified through JWKS.

### Dynamic client registration

The demo also implements Dynamic Client Registration (RFC 7591), so a new service can register as an OIDC client without restarting the ID platform.

You can register a new service from the dashboard or by calling `POST /oauth2/register` with:

- `client_id` — unique client identifier, for example `my-new-service`
- `client_name` — display name, for example `My New Service`
- `redirect_uris` — space-separated callback URLs, for example `http://localhost:4000/callback`
- `scope` — requested scopes, for example `openid profile email`

The registration response returns `client_id` and `client_secret`. Save the `client_secret` immediately, because the dashboard shows it only once.

### How to onboard a new service

1. Register the service in the dashboard or via `POST /oauth2/register`.
2. Store the returned `client_id` and `client_secret` in the service's OIDC configuration.
3. Point the service to the discovery endpoint: `http://localhost:3000/.well-known/openid-configuration`.
4. Implement the OIDC Authorization Code Flow with PKCE in the service.
5. After registration, users can log in once on any participating service and access other registered services through SSO.

## Main endpoints

### OIDC endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/.well-known/openid-configuration` | OIDC discovery document |
| GET | `/jwks.json` | Public RSA keys |
| GET | `/oauth2/authorize` | Authorization endpoint |
| POST | `/oauth2/token` | Token exchange |
| GET | `/oauth2/userinfo` | User profile from Bearer token |
| POST | `/oauth2/revoke` | Token revocation |
| POST | `/oauth2/register` | Dynamic client registration |
| GET | `/oauth2/clients` | List registered clients |

### Auth endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Direct login |
| GET | `/api/auth/me` | Current user profile |
| PUT | `/api/auth/me` | Update profile |
| DELETE | `/api/auth/me` | Deactivate account |

### Migration endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/migration/import?site={name}` | Bulk import users |
| POST | `/api/migration/shadow-validate` | Shadow-mode validation |
| POST | `/api/migration/dual-write?site={name}` | Sync user into ID platform |
| POST | `/api/migration/cutover?site={name}` | Cut over a site |
| POST | `/api/migration/rollback?site={name}` | Roll back a site |
| GET | `/api/migration/status` | Current migration status |
| POST | `/api/migration/run-demo` | Run the demo flow |

## Tech stack

| Layer | Technology |
|---|---|
| ID Platform | Java 17, Spring Boot 3, Spring Security, JPA |
| Auth | OIDC, JWT RS256, PKCE, BCrypt |
| Legacy Sites | Python 3, Flask, SQLite |
| Database | H2 for demo, Aurora MySQL in production |
| Deployment | Docker, Docker Compose |

## Notes

- `ASSIGNMENT_RESPONSE.md` is the best interview-facing answer to the prompt.
- This README is the project overview for the demo repository.
- The implementation should be read as a working demonstration of the migration direction, not as a claim that every production-hardening concern is complete.

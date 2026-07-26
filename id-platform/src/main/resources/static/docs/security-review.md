# Security Review

**Project:** Unified ID Platform Integration
**Author:** Tech Lead + Security
**Date:** 2026-07-26

---

## 1. Threat Model

### 1.1 STRIDE Analysis

| Threat | Category | Risk | Mitigation |
|--------|----------|------|-----------|
| Attacker steals authorization code | Spoofing | High | PKCE (S256) prevents code interception |
| Attacker forges JWT token | Tampering | High | RS256 asymmetric signing, JWKS endpoint |
| Attacker replays captured token | Repudiation | Medium | Token revocation, single-use auth codes |
| Attacker intercepts token in transit | Information Disclosure | High | HTTPS in production, HttpOnly cookies |
| Attacker brute-forces login | Denial of Service | High | Rate limiting, account lockout (recommended) |
| Attacker compromises legacy site | Elevation of Privilege | Medium | Zero-trust: each service validates tokens independently |

### 1.2 Attack Vectors

| Vector | Description | Current Mitigation | Recommendation |
|--------|------------|-------------------|----------------|
| Authorization code interception | Attacker captures code in redirect | PKCE S256 | ✅ Implemented |
| Token theft via XSS | Attacker steals token from storage | HttpOnly cookies for session | ⚠️ Client-side tokens in localStorage (demo) |
| Man-in-the-middle | Attacker intercepts traffic | HTTP (demo) | ⚠️ HTTPS required in production |
| Brute-force login | Attacker tries many passwords | None | ❌ Add rate limiting |
| Session fixation | Attacker sets victim's session | New session on login | ✅ IDP_SESSION set on successful auth |
| JWT key compromise | Attacker signs fake tokens | Ephemeral keys (regenerated on restart) | ⚠️ Use persistent keys in production |

---

## 2. Authentication Security

### 2.1 Password Handling

| Aspect | Implementation | Status |
|--------|---------------|--------|
| Storage algorithm | BCrypt (native), SHA-256+salt (legacy), MD5 (legacy) | ✅ |
| Salt generation | BCrypt auto-generates salt | ✅ |
| Password policy | Minimum 8 characters | ✅ |
| Password history | Not enforced | ⚠️ Recommended |
| Hash upgrade | Lazy upgrade on first login | ✅ |

### 2.2 Token Security

| Aspect | Implementation | Status |
|--------|---------------|--------|
| Algorithm | RS256 (asymmetric) | ✅ |
| Key size | 2048-bit RSA | ✅ |
| Access token expiry | 15 minutes | ✅ |
| Refresh token expiry | 7 days | ✅ |
| Auth code expiry | 10 minutes | ✅ |
| Auth code reuse | Single-use (marked used) | ✅ |
| Token revocation | DB-backed revocation | ✅ |

### 2.3 Session Security

| Aspect | Implementation | Status |
|--------|---------------|--------|
| Cookie name | IDP_SESSION | ✅ |
| HttpOnly | true | ✅ |
| Secure flag | false (demo) → true (production) | ⚠️ |
| SameSite | Lax | ✅ |
| Session timeout | 24 hours | ✅ |

---

## 3. Transport Security

| Aspect | Demo | Production |
|--------|------|-----------|
| Protocol | HTTP | HTTPS (TLS 1.2+) |
| Certificate | None | AWS ACM |
| HSTS | No | Yes (max-age=31536000) |
| Mixed content | Allowed | Blocked |

---

## 4. Input Validation

| Endpoint | Validation | Status |
|----------|-----------|--------|
| `/api/auth/login` | Email format, password min length | ✅ |
| `/api/auth/register` | Email format, password min length, display name not blank | ✅ |
| `/oauth2/authorize` | Client ID exists, redirect URI matches | ✅ |
| `/oauth2/token` | Grant type, code format, client ID | ✅ |

---

## 5. Access Control

| Aspect | Implementation | Status |
|--------|---------------|--------|
| Zero-trust filter | All requests validated | ✅ |
| Public endpoints | Explicit allowlist | ✅ |
| Token type validation | Access vs Refresh vs ID | ✅ |
| Token revocation check | DB lookup on every request | ✅ |
| User isolation | Users can only access own profile | ✅ |

---

## 6. Data Protection

| Aspect | Implementation | Status |
|--------|---------------|--------|
| Password hashing | Never logged or exposed in API responses | ✅ |
| Token storage | HttpOnly cookies (session), client-side (demo) | ⚠️ |
| PII in logs | User ID logged, email not logged | ✅ |
| Database encryption | Not enforced (H2) | ⚠️ Aurora encryption at rest in production |
| Backup encryption | Not applicable (demo) | ⚠️ Required in production |

---

## 7. Compliance Considerations

| Standard | Requirement | Our Status |
|----------|------------|-----------|
| SOC 2 | Access controls, audit logging | Partial — no audit trail |
| ISO 27001 | Risk assessment, security controls | Partial — this document |
| GDPR | Right to deletion, data portability | ✅ Account withdrawal implemented |
| PCI DSS | If handling payment data | N/A — auth only |

---

## 8. Security Recommendations

| # | Recommendation | Priority | Effort |
|---|---------------|----------|--------|
| 1 | Add rate limiting on login endpoints | Critical | 1 day |
| 2 | Add account lockout after 5 failed attempts | Critical | 1 day |
| 3 | Enable HTTPS in production (TLS 1.2+) | Critical | 0.5 day |
| 4 | Set Secure flag on cookies in production | High | 0.5 day |
| 5 | Add audit logging for auth events | High | 2 days |
| 6 | Use persistent RSA keys (not ephemeral) | Medium | 1 day |
| 7 | Add CSRF protection | Medium | 1 day |
| 8 | Implement refresh token rotation | Medium | 1 day |
| 9 | Add Content-Security-Policy headers | Low | 0.5 day |
| 10 | Penetration testing before production | High | External vendor |

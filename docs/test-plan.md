# Test Plan

**Project:** Unified ID Platform Integration
**Author:** QA Lead + Tech Lead
**Date:** 2026-07-26

---

## 1. Test Strategy Overview

| Test Level | Scope | Tool | Frequency |
|-----------|-------|------|-----------|
| Unit | Individual functions, hash validation, token generation | JUnit 5, pytest | Every commit |
| Integration | API endpoint behavior, DB operations | Spring Boot Test, pytest | Every commit |
| System | Full migration flow end-to-end | curl, Postman, Selenium | Per phase |
| Performance | Load testing, latency benchmarks | JMeter, k6 | Before each cutover |
| Security | Auth bypass, token manipulation | Manual + automated scan | Before Phase 3 |

---

## 2. Phase 0 Test Cases — Bulk Import

### 2.1 Import Correctness

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T0-01 | Import 1000 Main Site users | All imported with correct email, display name, source=MAIN_SITE | High |
| T0-02 | Import 1000 MA Site users | All imported with correct email, display name, source=MA_SITE | High |
| T0-03 | Import Main Site user with SHA-256+salt hash | Password validates correctly | High |
| T0-04 | Import MA Site user with MD5 hash | Password validates correctly | High |
| T0-05 | Import user with BCrypt hash | Password validates correctly | Medium |
| T0-06 | Import user that exists on both sites (same email, same password) | Accounts merged, source=MERGED | High |
| T0-07 | Import user that exists on both sites (same email, different password) | Conflict flagged, user not auto-merged | High |
| T0-08 | Import user with duplicate email within same site | Last import wins, no crash | Medium |
| T0-09 | Import user with special characters in email/display name | Handled correctly (escaped, not SQL-injected) | Medium |
| T0-10 | Import 0 users (empty legacy site) | No error, status shows 0 imported | Low |

### 2.2 Import Resilience

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T0-11 | Legacy site unreachable during import | Retry with backoff, error reported | High |
| T0-12 | Import interrupted mid-way (server restart) | Can resume from last checkpoint | Medium |
| T0-13 | Import duplicate run (same data twice) | No duplicate users created (upsert) | High |

---

## 3. Phase 1 Test Cases — Shadow Mode

### 3.1 Credential Comparison

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T1-01 | Validate Main Site user credentials against IDP | Match = true for valid credentials | High |
| T1-02 | Validate MA Site user credentials against IDP | Match = true for valid credentials | High |
| T1-03 | Validate with wrong password | Both legacy and IDP reject, match = true | High |
| T1-04 | Validate user that was merged (same email, both sites) | Both site credentials validated | Medium |
| T1-05 | Validate user with expired legacy hash | IDP validates correctly | Medium |

### 3.2 Shadow Mode Accuracy

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T1-06 | Run shadow validation on 100% of users | ≥99.9% match rate | High |
| T1-07 | Log all mismatches with root cause | Mismatch report includes user ID, error type | High |
| T1-08 | Shadow mode performance (10K users) | Completes within 5 minutes | Medium |

---

## 4. Phase 2 Test Cases — Dual-Write

### 4.1 Write Consistency

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T2-01 | New registration on Main Site | User appears in both IDP and legacy DB | High |
| T2-02 | New registration on MA Site | User appears in both IDP and legacy DB | High |
| T2-03 | Profile update (email) on legacy site | IDP updated within 5 seconds | High |
| T2-04 | Profile update (password) on legacy site | IDP updated, hash preserved | High |
| T2-05 | Account deletion on legacy site | IDP account marked inactive | Medium |

### 4.2 Conflict Detection

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T2-06 | Simultaneous update on both systems | Conflict detected and logged | High |
| T2-07 | Write to IDP while legacy is down | Write queued, retried when legacy recovers | Medium |
| T2-08 | Legacy site returns error during dual-write | IDP write succeeds, legacy failure logged | Medium |

### 4.3 Performance

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T2-09 | 100 concurrent registrations | All complete without data corruption | High |
| T2-10 | Dual-write latency under load | <5 seconds p99 | Medium |

---

## 5. Phase 3 Test Cases — Cutover + SSO

### 5.1 OIDC Flow

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T3-01 | Full OIDC Authorization Code flow | User gets access token, id token, refresh token | High |
| T3-02 | OIDC login with BCrypt user | Login succeeds | High |
| T3-03 | OIDC login with SHA-256+salt user | Login succeeds, hash upgraded to BCrypt | High |
| T3-04 | OIDC login with MD5 user | Login succeeds, hash upgraded to BCrypt | High |
| T3-05 | OIDC login with wrong password | Login fails, error returned | High |
| T3-06 | OIDC login with non-existent user | Login fails, error returned | High |
| T3-07 | PKCE S256 validation | Code verifier matches challenge | High |
| T3-08 | Expired authorization code | Token exchange fails with invalid_grant | High |
| T3-09 | Used authorization code (replay) | Token exchange fails (single-use) | High |

### 5.2 SSO

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T3-10 | Login on Main Site, visit MA Site | MA Site shows logged-in user (no re-login) | High |
| T3-11 | Login on MA Site, visit Main Site | Main Site shows logged-in user (no re-login) | High |
| T3-12 | Silent auth (`prompt=none`) with session | Authorization code issued automatically | High |
| T3-13 | Silent auth without session | `login_required` error returned | High |
| T3-14 | Logout on Main Site | MA Site session also expired | High |

### 5.3 Profile Operations Post-Cutover

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T3-15 | Update email via IDP | Profile updated in IDP | High |
| T3-16 | Change password via IDP | New password BCrypt-hashed, old hash removed | High |
| T3-17 | Withdraw account via IDP | Account deactivated, tokens revoked | High |

### 5.4 Rollback

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| T3-18 | Rollback MA Site to legacy auth | MA Site uses local auth again | High |
| T3-19 | Rollback Main Site to legacy auth | Main Site uses local auth again | High |
| T3-20 | User who changed password during IDP phase logs in after rollback | Login succeeds (reverse-sync worked) | High |
| T3-21 | User who did NOT change password logs in after rollback | Login succeeds with original hash | High |

---

## 6. Performance Test Cases

| Test ID | Description | Target | Priority |
|---------|------------|--------|----------|
| P-01 | Login throughput | 1000 requests/second | High |
| P-02 | Token generation latency | <100ms p95 | High |
| P-03 | Login response time under load | <500ms p95 | High |
| P-04 | Concurrent OIDC flows | 10,000 simultaneous | Medium |
| P-05 | Database connection pool under load | No connection exhaustion | Medium |

---

## 7. Security Test Cases

| Test ID | Description | Expected Result | Priority |
|---------|------------|----------------|----------|
| S-01 | Send request without Authorization header | 401 Unauthorized | High |
| S-02 | Send request with expired token | 401 Token has expired | High |
| S-03 | Send request with revoked token | 401 Token has been revoked | High |
| S-04 | Send request with wrong token type (ID token as access) | 401 Invalid token type | High |
| S-05 | Attempt SQL injection via login | Rejected, no DB error | High |
| S-06 | Attempt XSS via display name | Sanitized, no script execution | Medium |
| S-07 | Brute-force login attempt | Rate limited / account locked | High |
| S-08 | Authorization code interception (no PKCE) | Token exchange fails | High |

---

## 8. Acceptance Criteria Summary

| Phase | Gate | Threshold |
|-------|------|----------|
| Phase 0 → 1 | Import complete | 100% users imported, 3 hash formats working |
| Phase 1 → 2 | Shadow validated | ≥99.9% match rate |
| Phase 2 → 3 | Dual-write stable | ≥99.99% sync accuracy, <5s latency |
| Phase 3 → 4 | Cutover successful | ≤0.1% login failure, SSO working |
| Phase 4 → Done | Rollback tested | <15 min rollback, zero data loss |

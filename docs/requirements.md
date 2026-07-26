# Requirements Gathering Document

**Project:** Unified ID Platform Integration
**Author:** Tech Lead, ID Platform Team
**Date:** 2026-07-26

---

## 1. Business Context

The company operates an e-commerce platform (Main Site) with 3 million customers and ~100M yen/day revenue. A new e-commerce site (MA Site) was acquired through M&A with 100,000 members and 20,000 MAU. Both sites have independent authentication systems with no shared identity.

**Business Goal:** Build a unified ID Platform to consolidate member management, enable SSO, and create a foundation for rapid new service launches via M&A.

---

## 2. Stakeholder Requirements

### 2.1 Platform Team (ID Platform Owner)

| ID | Requirement | Priority | Rationale |
|----|------------|----------|-----------|
| PR-01 | Unified authentication for both sites | Critical | Core goal — single identity system |
| PR-02 | Single Sign-On (SSO) across both sites | High | Improve UX, reduce friction |
| PR-03 | Centralized profile management | High | Unified customer data |
| PR-04 | New service onboarding capability | Medium | Enable rapid SaaS launches |
| PR-05 | Zero downtime migration | Critical | Main Site revenue cannot be interrupted |
| PR-06 | Safe rollback capability | Critical | Risk mitigation for production systems |

### 2.2 Main Site Team (Existing)

| ID | Requirement | Priority | Rationale |
|----|------------|----------|-----------|
| MS-01 | No forced password resets for 3M users | Critical | User disruption = revenue loss |
| MS-02 | Existing profile update flows must continue | High | No regression in functionality |
| MS-03 | Minimal code changes to legacy site | Medium | Reduce risk, speed up integration |
| MS-04 | Maintain existing URL structure | Low | SEO and bookmark preservation |

### 2.3 MA Site Team (Acquired)

| ID | Requirement | Priority | Rationale |
|----|------------|----------|-----------|
| MA-01 | All 100K members migrated | Critical | Complete integration |
| MA-02 | Existing user experience preserved | High | Member retention post-acquisition |
| MA-03 | MD5 password hashes handled | Critical | Legacy constraint |
| MA-04 | Profile data preserved | High | Data integrity |

### 2.4 Security & Compliance

| ID | Requirement | Priority | Rationale |
|----|------------|----------|-----------|
| SC-01 | Industry-standard auth protocol (OIDC) | Critical | Compliance, interoperability |
| SC-02 | Asymmetric JWT signing (RS256) | High | No shared secrets between services |
| SC-03 | PKCE for authorization code protection | High | Prevent code interception |
| SC-04 | Token revocation capability | High | Security incident response |
| SC-05 | Audit trail for auth events | Medium | Compliance, debugging |

### 2.5 Operations

| ID | Requirement | Priority | Rationale |
|----|------------|----------|-----------|
| OP-01 | Health checks for all services | High | Monitoring, auto-recovery |
| OP-02 | Rollback within 15 minutes | Critical | Incident response SLA |
| OP-03 | Zero data loss on rollback | Critical | Data integrity guarantee |
| OP-04 | Maximum 1 day maintenance window | Medium | Acceptable for planned maintenance |

---

## 3. Functional Requirements

### 3.1 Member Registration

| ID | Requirement | Acceptance Criteria |
|----|------------|-------------------|
| FR-01 | Users can register with email + password | Registration form validates email format, password ≥8 chars |
| FR-02 | Passwords are BCrypt-hashed in ID Platform | Stored hash starts with `$2a$` or `$2b$` |
| FR-03 | Duplicate email detection | Registration fails with clear error message |
| FR-04 | Dual-write during migration | New users written to both ID Platform and legacy DB |

### 3.2 Authentication (Login/Logout)

| ID | Requirement | Acceptance Criteria |
|----|------------|-------------------|
| FR-05 | Login via OIDC Authorization Code + PKCE | Standard OIDC flow with S256 challenge |
| FR-06 | Support legacy password hashes | BCrypt, SHA-256+salt, MD5 all validated |
| FR-07 | Session management via IDP cookie | `IDP_SESSION` cookie enables SSO |
| FR-08 | Logout clears session and tokens | Both IDP session and client session cleared |
| FR-09 | Lazy password upgrade on first login | Non-BCrypt hashes upgraded to BCrypt after successful auth |

### 3.3 Profile Management

| ID | Requirement | Acceptance Criteria |
|----|------------|-------------------|
| FR-10 | Update display name | Profile change reflected immediately |
| FR-11 | Update email address | Email change reflected in ID Platform and legacy DB |
| FR-12 | Change password | New password BCrypt-hashed, legacy hash replaced |

### 3.4 Account Withdrawal

| ID | Requirement | Acceptance Criteria |
|----|------------|-------------------|
| FR-13 | Users can deactivate their account | Account marked inactive, tokens revoked |
| FR-14 | Withdrawal propagated to legacy systems | Legacy DB updated during dual-write phase |

### 3.5 SSO

| ID | Requirement | Acceptance Criteria |
|----|------------|-------------------|
| FR-15 | Silent authentication (`prompt=none`) | If session exists, auth code issued without login page |
| FR-16 | Cross-site session sharing | Login on Main Site → access MA Site without re-login |
| FR-17 | Session timeout | IDP session expires after 24 hours |

---

## 4. Non-Functional Requirements

| ID | Requirement | Metric |
|----|------------|--------|
| NFR-01 | Login response time | <500ms p95 |
| NFR-02 | Token generation time | <100ms p95 |
| NFR-03 | Dual-write sync latency | <5 seconds |
| NFR-04 | Rollback completion time | <15 minutes |
| NFR-05 | System availability | 99.9% uptime |
| NFR-06 | Shadow mode match rate | ≥99.9% |
| NFR-07 | Concurrent user support | 10,000 simultaneous logins |

---

## 5. Constraints

| Constraint | Impact | Mitigation |
|-----------|--------|-----------|
| Main Site revenue ~100M yen/day | Zero tolerance for downtime | Phased migration, no service interruption |
| 3M existing users | Cannot force password resets | Lazy hash upgrade strategy |
| MA Site uses plain MD5 | Security liability | Migrate to BCrypt on first login |
| M&A email overlap | Same email on both sites | Conflict detection + manual resolution |
| Legacy Ruby on Rails | Cannot rewrite legacy systems | OIDC client integration, minimal changes |

---

## 6. Assumptions

1. Legacy sites have APIs to fetch user data for import
2. Legacy sites can be modified to add OIDC client (redirect to IDP for login)
3. Aurora MySQL can handle combined user base (~3.1M rows)
4. Network latency between ID Platform and legacy sites is <50ms
5. Legacy site teams are available for integration work
6. Security team approves OIDC-based approach
7. Product team accepts phased cutover timeline

---

## 7. Out of Scope

- Mobile app authentication (this plan covers web only)
- Multi-factor authentication (can be added later)
- Social login (Google, Facebook)
- User analytics / behavioral tracking
- Legacy site UI redesign

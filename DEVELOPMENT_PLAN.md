# Development Plan: Unified ID Platform Integration

**Author:** Tech Lead, ID Platform Team
**Status:** Active
**Estimated Duration:** 12 weeks (~3 months)
**Last Updated:** 2026-07-26

---

## 1. Executive Summary

This plan outlines the integration of two independent authentication systems (Main Site and MA Site) into a single Identity Provider (ID Platform) using OpenID Connect (OIDC). The migration follows a 5-phase approach that eliminates user disruption, avoids forced password resets, and provides safe rollback at every stage.

**Key Constraints:**
- Main Site processes ~100M yen/day — zero downtime mandatory
- MA Site is newly acquired — lower risk, cutover first
- Both sites have incompatible password hash formats
- No user-facing changes until Phase 3 (cutover)

---

## 2. Objectives & Success Criteria

| Objective | Success Metric |
|-----------|---------------|
| Unified authentication | Both sites authenticate via single ID Platform |
| Single Sign-On (SSO) | User logs in once, accesses both sites without re-login |
| Zero downtime | No service interruption during any migration phase |
| No forced password resets | 100% of existing users retain their passwords |
| Safe rollback | Ability to revert to legacy auth within 15 minutes |
| Zero data loss | All user data preserved through rollback |
| Security parity or better | OIDC + JWT RS256 + PKCE vs legacy session cookies |

---

## 3. Current State Analysis

| | Main Site | MA Site |
|---|---|---|
| **Users** | ~3,000,000 | ~100,000 |
| **MAU** | ~500,000 (est.) | ~20,000 |
| **Daily Revenue** | ~100M yen | Unknown |
| **Password Hash** | SHA-256 + salt | Plain MD5 |
| **DB** | Aurora MySQL | Aurora MySQL |
| **Framework** | Ruby on Rails | Ruby on Rails |
| **Session Mgmt** | Server-side cookies | Server-side cookies |
| **SSO** | No | No |
| **Auth Protocol** | Custom (form-based) | Custom (form-based) |

**Risks in Current State:**
- Two separate user databases with overlapping emails (M&A overlap)
- MD5 hashing on MA Site is a security liability
- No centralized session management
- Duplicated auth logic increases maintenance cost
- Acquired users have no integration path

---

## 4. Target Architecture

```
                    ┌─────────────────┐
                    │    Users/Browser │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │ ID Platform│  │  Main Site │  │   MA Site  │
     │  (OIDC IdP)│  │ (OIDC Rely │  │ (OIDC Rely │
     │  Port 3000 │  │  ing Party)│  │  ing Party)│
     └──────┬─────┘  └─────┬──────┘  └─────┬──────┘
            │               │               │
            │        OIDC (Authorization    │
            │         Code + PKCE)          │
            │               │               │
            ▼               ▼               ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │  Aurora    │  │  Aurora    │  │  Aurora    │
     │  MySQL     │  │  MySQL     │  │  MySQL     │
     │  (Users,   │  │  (Products,│  │  (Content, │
     │   Tokens)  │  │   Orders)  │  │   Members) │
     └────────────┘  └────────────┘  └────────────┘
```

**Key Design Decisions:**
1. **OIDC Authorization Code + PKCE** — industry standard, prevents code interception
2. **RS256 JWT signing** — asymmetric keys, legacy sites verify via JWKS without shared secrets
3. **Lazy password upgrade** — legacy hashes upgraded to BCrypt on first login, no forced reset
4. **Dual-write during transition** — both old and new DB stay in sync
5. **Per-site cutover** — MA Site first (smaller, lower risk), then Main Site

---

## 5. Migration Phases — Timeline & Deliverables

### Phase 0: Bulk Import + ID Platform Foundation
**Duration:** 2 weeks (Weeks 1–2)
**User Impact:** None

| Week | Deliverable | Owner |
|------|------------|-------|
| 1 | ID Platform core: user registration, login, JWT generation | Backend |
| 1 | OIDC discovery endpoint, JWKS endpoint | Backend |
| 1 | Password validation engine (BCrypt + SHA-256 + MD5) | Backend |
| 2 | Bulk import script: fetch all users from both legacy sites | Backend |
| 2 | Email conflict resolution: merge logic for same-email users | Backend |
| 2 | Dashboard UI: service overview, import status | Frontend |
| 2 | Integration testing: import 100K + 3M user datasets | QA |

**Exit Criteria:**
- [ ] All 3.1M users imported into ID Platform
- [ ] Password validation works for all 3 hash formats
- [ ] Email conflicts flagged and resolution logged
- [ ] Dashboard shows import counts and source breakdown

---

### Phase 1: Shadow Mode Validation
**Duration:** 2 weeks (Weeks 3–4)
**User Impact:** None

| Week | Deliverable | Owner |
|------|------------|-------|
| 3 | Shadow validation engine: compare legacy vs IDP auth per login | Backend |
| 3 | Logging infrastructure: per-user match/mismatch tracking | Backend |
| 3 | Shadow mode dashboard: success rate, mismatch reports | Frontend |
| 4 | Run shadow validation against full user dataset | QA |
| 4 | Shadow mode confidence report: target ≥99.9% match rate | QA |

**Exit Criteria:**
- [ ] Shadow mode runs with zero false negatives
- [ ] ≥99.9% credential match rate across both sites
- [ ] All mismatches documented with root cause
- [ ] Stakeholder sign-off to proceed to Phase 2

---

### Phase 2: Dual-Write Synchronization
**Duration:** 2 weeks (Weeks 5–6)
**User Impact:** None (users see no difference)

| Week | Deliverable | Owner |
|------|------------|-------|
| 5 | Dual-write middleware: intercept new registrations on both sites | Backend |
| 5 | Profile update propagation: email/password/display name changes | Backend |
| 5 | Dual-write validation: verify IDP and legacy DB stay in sync | Backend |
| 6 | Load testing: simulate concurrent registrations | QA |
| 6 | Dual-write monitoring: alerting on sync lag | DevOps |

**Exit Criteria:**
- [ ] All new registrations write to both systems
- [ ] Profile updates propagate within 5 seconds
- [ ] Zero data conflicts over observation period
- [ ] Stakeholder sign-off to proceed to Phase 3

---

### Phase 3: Cutover + SSO Activation
**Duration:** 4 weeks (Weeks 7–10)
**User Impact:** Auth switches to ID Platform; SSO enabled

| Week | Deliverable | Owner |
|------|------------|-------|
| 7 | MA Site OIDC integration: login/logout/profile via IDP | Backend |
| 7 | MA Site cutover: flip `migration.status` to `CUTOVER` | DevOps |
| 8 | MA Site monitoring: error rates, login success, latency | DevOps |
| 8 | MA Site rollback drill: practice revert to legacy auth | QA |
| 9 | Main Site OIDC integration: login/logout/profile via IDP | Backend |
| 9 | Main Site cutover: flip `migration.status` to `CUTOVER` | DevOps |
| 10 | Main Site monitoring: error rates, login success, latency | DevOps |
| 10 | Main Site rollback drill: practice revert to legacy auth | QA |

**Exit Criteria:**
- [ ] MA Site cutover with ≤0.1% login failure rate
- [ ] Main Site cutover with ≤0.1% login failure rate
- [ ] SSO works: login on Main Site, access MA Site without re-login
- [ ] Lazy password upgrade working: MD5/SHA-256 → BCrypt on first login
- [ ] Rollback drill completed successfully for both sites

---

### Phase 4: Rollback Readiness + Operational Runbook
**Duration:** 1 week (Week 11)
**User Impact:** None

| Week | Deliverable | Owner |
|------|------------|-------|
| 11 | Rollback automation: one-command revert for each site | Backend |
| 11 | Password reverse-sync: IDP changes → legacy DB | Backend |
| 11 | Operational runbook: step-by-step rollback procedures | DevOps |
| 11 | DR drill: full rollback + re-cutover exercise | QA + DevOps |

**Exit Criteria:**
- [ ] Rollback completes within 15 minutes
- [ ] Zero data loss after rollback (verified via DB comparison)
- [ ] Reverse-sync preserves all password changes made during IDP phase
- [ ] On-call team trained on runbook procedures

---

### Buffer: Production Hardening
**Duration:** 1 week (Week 12)

| Week | Deliverable | Owner |
|------|------------|-------|
| 12 | Production monitoring, alerting, load testing | DevOps |
| 12 | Security review, pen testing, documentation cleanup | Security + QA |

---

## 6. Team Structure

| Role | Responsibility | FTE |
|------|---------------|-----|
| **Tech Lead** | Architecture decisions, code review, stakeholder communication | 1 |
| **Backend Engineer (IDP)** | ID Platform core, OIDC, migration engine, dual-write | 2 |
| **Backend Engineer (Legacy)** | Legacy site OIDC integration, cutover coordination | 1 |
| **Frontend Engineer** | Dashboard UI, monitoring dashboards | 1 |
| **QA Engineer** | Testing each phase, shadow validation, rollback drills | 1 |
| **DevOps Engineer** | Deployment, monitoring, alerting, runbook | 0.5 |
| **Total** | | **6.5 FTE** |

---

## 7. Risk Register

| # | Risk | Probability | Impact | Mitigation |
|---|------|------------|--------|------------|
| R1 | MD5 hash on MA Site cannot be validated by ID Platform | Low | High | Phase 1 shadow mode catches this before any cutover |
| R2 | Email overlap between sites causes data corruption during merge | Medium | High | Merge logic flags conflicts for manual resolution; auto-merge only when passwords match |
| R3 | Main Site cutover causes revenue loss due to login failures | Low | Critical | MA Site cutover first (lower risk); rollback drill before Main Site cutover |
| R4 | Dual-write creates inconsistent state between IDP and legacy DB | Medium | Medium | Real-time sync validation; conflict detection dashboard; alerting on divergence |
| R5 | Users experience slow login during OIDC redirect | Low | Medium | Load test OIDC endpoint; monitor p99 latency; autoscaling on ID Platform |
| R6 | Rollback fails to restore legacy auth state | Low | Critical | Reverse-sync tested in staging; rollback drill before each cutover |
| R7 | Token expiration causes session drops during SSO | Low | Low | 15-min access token + 7-day refresh token; silent re-auth via `prompt=none` |
| R8 | Legacy site downtime during migration prevents import/dual-write | Medium | High | Retry logic with exponential backoff; health checks before proceeding |
| R9 | Key personnel unavailability blocks phase transitions | Medium | Medium | Cross-training; documented runbooks; no single point of failure |
| R10 | Regulatory/audit requirements not met by OIDC flow | Low | High | PKCE + RS256 + token revocation meet SOC2/ISO27001 requirements |

---

## 8. Go/No-Go Criteria

### Phase 0 → Phase 1 (Import Complete)
| Criterion | Threshold | Status |
|-----------|----------|--------|
| Users imported | 100% of both sites | ☐ |
| Password validation | All 3 hash formats working | ☐ |
| Email conflicts resolved | All flagged conflicts addressed | ☐ |
| Dashboard functional | Import status visible | ☐ |

### Phase 1 → Phase 2 (Shadow Mode Complete)
| Criterion | Threshold | Status |
|-----------|----------|--------|
| Shadow match rate | ≥99.9% | ☐ |
| Mismatch root causes | All documented and addressed | ☐ |
| Shadow mode duration | ≥2 weeks observation | ☐ |
| Stakeholder sign-off | Approved by product + eng lead | ☐ |

### Phase 2 → Phase 3 (Dual-Write Complete)
| Criterion | Threshold | Status |
|-----------|----------|--------|
| Sync accuracy | ≥99.99% | ☐ |
| Sync latency | <5 seconds p99 | ☐ |
| Conflict rate | Zero unresolved conflicts | ☐ |
| Load test passed | 10K concurrent registrations | ☐ |
| Stakeholder sign-off | Approved by product + eng lead | ☐ |

### Phase 3 → Phase 4 (Cutover Complete)
| Criterion | Threshold | Status |
|-----------|----------|--------|
| MA Site login success | ≥99.9% | ☐ |
| Main Site login success | ≥99.9% | ☐ |
| SSO functional | Cross-site login working | ☐ |
| Rollback drill | Completed successfully | ☐ |
| Lazy upgrade working | MD5/SHA-256 → BCrypt verified | ☐ |
| Stakeholder sign-off | Approved by product + eng lead | ☐ |

### Phase 4 → Decommission (Rollback Ready)
| Criterion | Threshold | Status |
|-----------|----------|--------|
| Rollback time | <15 minutes | ☐ |
| Data integrity post-rollback | Zero data loss verified | ☐ |
| Reverse-sync working | Password changes preserved | ☐ |
| On-call team trained | Runbook walkthrough complete | ☐ |

---

## 9. Testing Strategy

| Phase | Test Type | Scope | Frequency |
|-------|----------|-------|-----------|
| Phase 0 | Unit tests | Password validation, hash detection | Every commit |
| Phase 0 | Integration tests | Import from mock legacy APIs | Every commit |
| Phase 0 | Data tests | 3M user import correctness | Weekly |
| Phase 1 | Shadow tests | Credential comparison accuracy | Daily |
| Phase 1 | Edge case tests | Duplicate emails, special chars, locked accounts | Weekly |
| Phase 2 | Sync tests | Dual-write consistency checks | Daily |
| Phase 2 | Conflict tests | Concurrent writes to same user | Weekly |
| Phase 2 | Load tests | 10K concurrent registrations | Weekly |
| Phase 3 | E2E tests | Full OIDC flow via legacy sites | Every cutover |
| Phase 3 | SSO tests | Cross-site silent auth (`prompt=none`) | Every cutover |
| Phase 3 | Rollback tests | Revert + re-cutover drill | Before each cutover |
| Phase 4 | DR tests | Full rollback + reverse-sync | Weekly |

---

## 10. Dependencies & Blockers

| Dependency | Owner | Blocker For | ETA |
|-----------|-------|------------|-----|
| Legacy site API access (user data) | Legacy Eng | Phase 0 import | Week 1 |
| Legacy site OIDC client integration | Legacy Eng | Phase 3 cutover | Week 17 |
| Aurora MySQL provisioned for IDP | DevOps | Phase 0 (prod) | Week 1 |
| Monitoring stack (Datadog/CloudWatch) | DevOps | Phase 1 shadow mode | Week 5 |
| Stakeholder approval per phase gate | Product | Phase transitions | Ongoing |
| Security review of OIDC flow | Security | Phase 3 cutover | Week 15 |
| Load balancer (ALB) configuration | DevOps | Phase 3 cutover | Week 17 |
| DNS cutover for auth endpoints | DevOps | Phase 3 cutover | Week 21 |

---

## 11. Communication Plan

| Event | Frequency | Attendees | Purpose |
|-------|----------|-----------|---------|
| Standup | Daily | Full team | Progress, blockers |
| Phase gate review | Per phase | Team + stakeholders | Go/No-Go decision |
| Migration status report | Weekly | Team + leadership | Progress dashboard |
| Cutover war room | During cutover | Full team + on-call | Real-time monitoring |
| Post-cutover review | After each cutover | Full team | Lessons learned |
| Rollback drill | Before each cutover | DevOps + QA | Verify rollback readiness |

---

## 12. Budget Estimates (Infrastructure)

| Resource | Monthly Cost (Est.) | Notes |
|----------|-------------------|-------|
| ID Platform (ECS Fargate) | $200–500 | 2 tasks, 1 vCPU / 2GB |
| Aurora MySQL (IDP DB) | $300–800 | db.r5.large, multi-AZ |
| ALB | $50–100 | Shared with legacy sites |
| CloudFront | $50–200 | Static assets + OIDC endpoints |
| Monitoring (Datadog) | $200–400 | APM + logs |
| **Total** | **$800–2,000/mo** | Scales with traffic |

---

## 13. Timeline Summary

```
Week  1 ─────────── Week 2  │ Phase 0: Bulk Import + ID Platform Foundation
Week  3 ─────────── Week 4  │ Phase 1: Shadow Mode Validation
Week  5 ─────────── Week 6  │ Phase 2: Dual-Write Synchronization
Week  7 ─────────── Week 10 │ Phase 3: Cutover + SSO Activation
                            │   ├─ Week 7-8: MA Site cutover + observation
                            │   └─ Week 9-10: Main Site cutover + observation
Week 11              │ Phase 4: Rollback Readiness + Runbook
Week 12              │ Buffer: Production Hardening
```

**Total Duration:** ~3 months (12 weeks)
**Critical Path:** Phase 0 → Phase 1 → Phase 2 → Phase 3 (MA) → Phase 3 (Main)

---

## 14. Appendix

### A. Password Hash Compatibility Matrix

| Source | Algorithm | IDP Validation | Upgrade Path |
|--------|----------|---------------|--------------|
| ID Platform (native) | BCrypt | Native | None |
| Main Site | SHA-256 + salt | Supported | Lazy upgrade to BCrypt on first login |
| MA Site | Plain MD5 | Supported | Lazy upgrade to BCrypt on first login |

### B. OIDC Client Configuration

| Client ID | Secret | Redirect URI | Scope |
|-----------|--------|-------------|-------|
| `main-site` | `main-site-secret` | `http://{VM_IP}:3001/callback` | `openid profile email` |
| `ma-site` | `ma-site-secret` | `http://{VM_IP}:3002/callback` | `openid profile email` |

### C. Token Specifications

| Token Type | Algorithm | Expiry | Storage |
|-----------|----------|--------|---------|
| Access Token | RS256 | 15 minutes | Client-side (Bearer) |
| Refresh Token | RS256 | 7 days | Client-side (httpOnly cookie) |
| ID Token | RS256 | 10 minutes | Client-side |
| Authorization Code | N/A | 10 minutes | Server-side (DB, single-use) |
| IDP Session | N/A | 24 hours | Cookie (`IDP_SESSION`) |

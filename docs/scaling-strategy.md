# Scaling Strategy

**Project:** Unified ID Platform Integration
**Author:** Tech Lead + DevOps
**Date:** 2026-07-26

---

## 1. Current Scale (Demo)

| Metric | Value |
|--------|-------|
| Main Site users | ~1,000 (test data) |
| MA Site users | ~1,000 (test data) |
| Database | H2 (file-based, single node) |
| Deployment | Single Docker container per service |
| Load balancing | None (direct access) |

---

## 2. Production Scale

| Metric | Value |
|--------|-------|
| Main Site users | ~3,000,000 |
| MA Site users | ~100,000 |
| Combined | ~3,100,000 |
| Daily revenue | ~100M yen |
| Peak concurrent logins | ~10,000 |
| Login requests/sec (peak) | ~500 |

---

## 3. Infrastructure Scaling

### 3.1 ID Platform

| Component | Demo | Production |
|-----------|------|-----------|
| Compute | 1 Docker container | ECS Fargate, 2+ tasks, autoscaling 2-10 |
| Database | H2 file | Aurora MySQL (db.r5.large, multi-AZ) |
| Session storage | Cookie (user ID) | Cookie + Redis for token cache |
| Token revocation | DB lookup per request | Redis cache (TTL 15min) + DB fallback |
| Health checks | Docker healthcheck | ALB target group health checks |

### 3.2 Legacy Sites

| Component | Demo | Production |
|-----------|------|-----------|
| Compute | 1 Docker container | Existing Ruby on Rails servers |
| Database | SQLite | Existing Aurora MySQL |
| OIDC integration | Added in Phase 3 | Same — minimal changes |

### 3.3 Network

| Component | Demo | Production |
|-----------|------|-----------|
| Load balancing | None | ALB with path-based routing |
| CDN | None | CloudFront for static assets |
| DNS | Direct IP | Route 53 with health checks |
| TLS | HTTP (demo) | HTTPS via ACM + CloudFront |

---

## 4. Database Scaling

### 4.1 Schema Sizing

| Table | Rows (Est.) | Size (Est.) | Index Strategy |
|-------|------------|-------------|----------------|
| `users` | 3.1M | ~1.5 GB | PK on id, unique index on email |
| `auth_tokens` | ~100K active | ~500 MB | Index on token, TTL cleanup |
| `authorization_codes` | ~1K active | ~5 MB | Index on code, TTL cleanup |
| `oidc_clients` | ~10 | <1 MB | PK on client_id |
| `migration_status` | ~2 | <1 KB | PK on site_name |

### 4.2 Scaling Strategy

| Strategy | Implementation |
|----------|---------------|
| Read replicas | Aurora MySQL read replica for token verification |
| Connection pooling | HikariCP (default in Spring Boot), max 20 connections |
| Query optimization | Index on `email` for login lookups, `token` for validation |
| Archival | Move revoked tokens to cold storage after 30 days |
| Partitioning | Partition `auth_tokens` by `created_at` for efficient cleanup |

---

## 5. Application Scaling

### 5.1 Horizontal Scaling

| Metric | Current | Target |
|--------|---------|--------|
| ID Platform instances | 1 | 2-10 (autoscaling) |
| Requests per instance | 500/sec | 500/sec per instance |
| Total capacity | 500/sec | 5,000/sec |

### 5.2 Autoscaling Policy

| Metric | Scale Out | Scale In |
|--------|----------|----------|
| CPU utilization | >70% | <30% |
| Request count | >400/sec per task | <100/sec per task |
| Memory | >80% | <40% |
| Cooldown | 60 seconds | 300 seconds |

### 5.3 Caching Strategy

| Data | Cache Layer | TTL | Invalidation |
|------|-----------|-----|-------------|
| OIDC discovery document | In-memory | 1 hour | Server restart |
| JWKS keys | In-memory | 1 hour | Server restart |
| User profile | Redis | 5 minutes | Profile update |
| Token validation | Redis | 15 minutes | Token revocation |

---

## 6. Legacy Site Scaling

The legacy sites don't need scaling changes — they're not handling auth after cutover. The only change is the OIDC redirect, which adds ~100ms latency per login (acceptable).

| Concern | Impact | Mitigation |
|---------|--------|-----------|
| OIDC redirect adds latency | Login takes ~200ms longer | Acceptable; SSO eliminates repeat logins |
| Legacy DB still handles profile data | No change | Legacy DB handles its own domain data |
| Legacy site downtime during import | Import fails gracefully | Retry with backoff |

---

## 7. Cost Estimates (Production)

| Resource | Monthly Cost | Notes |
|----------|-------------|-------|
| ECS Fargate (ID Platform) | $200-500 | 2 tasks, 1 vCPU / 2GB, autoscaling |
| Aurora MySQL (IDP) | $300-800 | db.r5.large, multi-AZ, storage |
| Aurora MySQL (read replica) | $200-400 | For token verification |
| Redis (ElastiCache) | $100-300 | Token cache, session cache |
| ALB | $50-100 | Shared with legacy sites |
| CloudFront | $50-200 | Static assets + OIDC endpoints |
| Route 53 | $10-20 | DNS with health checks |
| Monitoring (Datadog) | $200-400 | APM + logs |
| **Total** | **$1,100-2,800/mo** | Scales with traffic |

---

## 8. Load Testing Plan

| Scenario | Tool | Target | Pass Criteria |
|----------|------|--------|--------------|
| Login throughput | k6 | 500 req/sec | p95 < 500ms |
| Concurrent logins | k6 | 10,000 simultaneous | No errors, p95 < 1s |
| Token generation | k6 | 1000 req/sec | p95 < 100ms |
| Database under load | JMeter | 10K concurrent queries | No connection exhaustion |
| Sustained load | k6 | 500 req/sec for 1 hour | No memory leaks, stable latency |

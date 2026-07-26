# Operational Runbook

**Project:** Unified ID Platform Integration
**Author:** DevOps + Tech Lead
**Date:** 2026-07-26

---

## 1. Service Architecture

```
ID Platform (port 3000)     → Java/Spring Boot, H2/Aurora MySQL
Legacy Main Site (port 3001) → Python/Flask, SQLite/Aurora MySQL
Legacy MA Site (port 3002)   → Python/Flask, SQLite/Aurora MySQL
```

**Health Check Endpoints:**
- ID Platform: `GET http://localhost:3000/api/health`
- Main Site: `GET http://localhost:3001/`
- MA Site: `GET http://localhost:3002/`

---

## 2. Common Operations

### 2.1 Check Service Status

```bash
# Docker
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Health check
curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/
curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/
curl -s -o /dev/null -w '%{http_code}' http://localhost:3002/
```

### 2.2 View Service Logs

```bash
# ID Platform
docker logs -f id-platform --tail 100

# Legacy sites
docker logs -f legacy-main-site --tail 100
docker logs -f legacy-ma-site --tail 100
```

### 2.3 Restart a Service

```bash
docker restart id-platform
docker restart legacy-main-site
docker restart legacy-ma-site
```

### 2.4 Check Migration Status

```bash
curl -s http://localhost:3000/api/migration/status | jq .
```

---

## 3. Deployment Procedures

### 3.1 Deploy ID Platform (Code Change)

```bash
# 1. Build JAR locally
cd id-platform && mvn clean package -DskipTests

# 2. Upload to server
scp target/id-platform-*.jar opc@{VM_IP}:/tmp/id-platform.jar

# 3. Copy to container and restart
ssh opc@{VM_IP} "sudo docker cp /tmp/id-platform.jar id-platform:/app/app.jar && sudo docker restart id-platform"

# 4. Verify (wait 60s for startup)
sleep 60 && curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/
```

### 3.2 Deploy Legacy Site (Code Change)

```bash
# 1. Upload
scp legacy-main-site/app.py opc@{VM_IP}:/tmp/main-site-app.py

# 2. Copy and restart
ssh opc@{VM_IP} "sudo docker cp /tmp/main-site-app.py legacy-main-site:/app/app.py && sudo docker restart legacy-main-site"

# 3. Verify
curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/
```

### 3.3 Full Redeploy (Docker Compose)

```bash
ssh opc@{VM_IP}
cd /opt/raksul-id-platform
sudo VM_IP={VM_IP} docker compose up -d --force-recreate --no-build
```

---

## 4. Migration Operations

### 4.1 Run Full Demo

```bash
curl -X POST http://localhost:3000/api/migration/run-demo \
  -H "Content-Type: application/json"
```

### 4.2 Cutover a Site

```bash
# Cutover MA Site
curl -X POST "http://localhost:3000/api/migration/cutover?site=ma-site"

# Cutover Main Site
curl -X POST "http://localhost:3000/api/migration/cutover?site=main-site"

# Verify
curl -s http://localhost:3000/api/migration/status | jq .
```

### 4.3 Rollback a Site

```bash
# Rollback MA Site
curl -X POST "http://localhost:3000/api/migration/rollback?site=ma-site"

# Rollback Main Site
curl -X POST "http://localhost:3000/api/migration/rollback?site=main-site"

# Verify
curl -s http://localhost:3000/api/migration/status | jq .
```

---

## 5. Incident Response

### 5.1 Service Down (HTTP 5xx)

**Symptom:** Health check returns 500/503

**Diagnosis:**
```bash
docker logs --tail 50 id-platform | grep -i error
```

**Resolution:**
```bash
# Restart the service
docker restart id-platform

# If still failing, check DB connectivity
docker exec id-platform curl -s http://localhost:3000/api/health
```

### 5.2 Login Failures Spike

**Symptom:** Users reporting "Invalid credentials" after cutover

**Diagnosis:**
```bash
# Check migration status
curl -s http://localhost:3000/api/migration/status | jq .

# Check IDP logs for auth failures
docker logs id-platform 2>&1 | grep "auth" | tail -20
```

**Resolution:**
```bash
# If hash validation issue, rollback immediately
curl -X POST "http://localhost:3000/api/migration/rollback?site=main-site"

# Then investigate hash mismatch
docker exec id-platform curl -s http://localhost:3000/api/migration/status
```

### 5.3 SSO Not Working

**Symptom:** User logs in on Main Site, redirected to login on MA Site

**Diagnosis:**
```bash
# Check IDP session cookie
curl -v http://localhost:3000/oauth2/userinfo -H "Authorization: Bearer {token}"

# Check OIDC client configuration
curl -s http://localhost:3000/oauth2/clients | jq .
```

**Resolution:**
```bash
# Re-seed OIDC clients (restart triggers seedClient())
docker restart id-platform
```

### 5.4 Database Locked (Legacy Site)

**Symptom:** `database is locked` error on legacy Flask app

**Diagnosis:**
```bash
docker logs legacy-main-site 2>&1 | grep "locked"
```

**Resolution:**
```bash
# Restart the legacy site to release connections
docker restart legacy-main-site
```

---

## 6. Rollback Procedure (Emergency)

### 6.1 Full Rollback (Both Sites)

```bash
# 1. Rollback both sites
curl -X POST "http://localhost:3000/api/migration/rollback?site=main-site"
curl -X POST "http://localhost:3000/api/migration/rollback?site=ma-site"

# 2. Verify status
curl -s http://localhost:3000/api/migration/status | jq .

# 3. Test login on legacy sites
curl -X POST http://localhost:3001/api/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}'
```

### 6.2 Estimated Rollback Time

| Step | Duration |
|------|----------|
| Execute rollback API calls | 10 seconds |
| Verify legacy auth working | 1 minute |
| Full verification (all endpoints) | 5 minutes |
| **Total** | **< 15 minutes** |

---

## 7. Monitoring & Alerts

### 7.1 Key Metrics to Monitor

| Metric | Threshold | Action |
|--------|----------|--------|
| Login success rate | <99% | Investigate, possible rollback |
| Login latency p95 | >1s | Check DB performance |
| Token generation latency | >200ms | Check CPU/memory |
| Dual-write sync lag | >10s | Check legacy site health |
| Error rate (5xx) | >1% | Restart service, investigate |
| DB connection pool usage | >80% | Increase pool size or scale |

### 7.2 Log Patterns to Watch

```
# Auth failures
grep "auth" logs/id-platform.log | grep -i "fail\|error"

# Token issues
grep "token" logs/id-platform.log | grep -i "expire\|revoke\|invalid"

# Migration errors
grep "migration" logs/id-platform.log | grep -i "error\|fail"
```

---

## 8. Contact & Escalation

| Level | Who | When | Response Time |
|-------|-----|------|--------------|
| L1 | On-call engineer | Service down, login failures | 15 minutes |
| L2 | Tech Lead | Data corruption, rollback needed | 30 minutes |
| L3 | Engineering Manager | Revenue impact, extended outage | 1 hour |

# Design Decisions Document

**Project:** Unified ID Platform Integration
**Author:** Tech Lead, ID Platform Team
**Date:** 2026-07-26

---

## 1. Why OIDC (Not SAML or Custom)?

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **OIDC** | Industry standard, REST/JSON, mobile-friendly, PKCE support | New protocol for team to learn | ✅ Chosen |
| SAML | Enterprise standard, XML-based | Complex, verbose, harder to integrate with modern SPAs | ❌ |
| Custom JWT | Full control | Non-standard, no ecosystem, maintenance burden | ❌ |

**Rationale:** OIDC is the modern standard for web authentication. It's simpler than SAML, widely supported by libraries, and natively supports the Authorization Code + PKCE flow which prevents code interception. It also enables future mobile app integration without protocol changes.

---

## 2. Why RS256 (Not HS256)?

| Algorithm | Pros | Cons | Decision |
|-----------|------|------|----------|
| **RS256** | Asymmetric keys, no shared secret, legacy sites verify via JWKS | Larger token size, slower signing | ✅ Chosen |
| HS256 | Fast, simple | Requires shared secret between all services | ❌ |

**Rationale:** With multiple services (ID Platform + 2 legacy sites), asymmetric signing means legacy sites only need the public key to verify tokens. No secrets are shared between services, reducing the blast radius if a legacy site is compromised.

---

## 3. Why Lazy Password Upgrade (Not Forced Reset)?

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| **Lazy upgrade** | Zero user disruption, transparent | Old hashes remain until first login | ✅ Chosen |
| Forced reset | Clean break, all users on BCrypt | 3M users must reset passwords, revenue risk | ❌ |
| Background batch | Gradual, no user action needed | Complex, must handle concurrent logins | ⚠️ Future option |

**Rationale:** The requirement explicitly states "avoid forcing users to do additional work such as password resets." Lazy upgrade is the only approach that meets this constraint. Users who never log in again keep their old hash — no harm done. Users who do log in get silently upgraded.

---

## 4. Why 5-Phase Migration (Not Big Bang)?

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| **5-phase** | Each phase validates before proceeding, rollback at any stage | Takes longer | ✅ Chosen |
| Big bang | Faster completion | High risk, no intermediate validation | ❌ |
| Per-user migration | Granular control | Complex, slow, hard to track | ❌ |

**Rationale:** With 3M users and ~100M yen/day revenue, the cost of a failed cutover is catastrophic. Each phase builds confidence:
- Phase 0: "Can we import all users?"
- Phase 1: "Does our password validation match the legacy system?"
- Phase 2: "Can we keep both systems in sync?"
- Phase 3: "Can we switch auth without issues?"
- Phase 4: "Can we rollback if something goes wrong?"

---

## 5. Why MA Site Cutover First?

| Order | Risk | Revenue Impact | Decision |
|-------|------|---------------|----------|
| **MA Site → Main Site** | MA Site first (lower risk, smaller user base) | MA Site has lower revenue | ✅ Chosen |
| Main Site → MA Site | Main Site first (higher risk, larger user base) | Main Site has ~100M yen/day revenue | ❌ |

**Rationale:** MA Site has 100K members vs Main Site's 3M. If cutover fails on MA Site, the blast radius is 30x smaller. The team learns from MA Site cutover before attempting the revenue-critical Main Site.

---

## 6. Why Authorization Code + PKCE (Not Implicit)?

| Flow | Pros | Cons | Decision |
|------|------|------|----------|
| **Auth Code + PKCE** | Tokens never exposed in URL, PKCE prevents code interception | Slightly more complex | ✅ Chosen |
| Implicit | Simpler | Tokens exposed in URL fragment, vulnerable to XSS | ❌ |

**Rationale:** The Implicit flow is deprecated in OAuth 2.1. Authorization Code + PKCE is the recommended approach for all client types. PKCE (S256) prevents authorization code interception attacks without requiring client secrets.

---

## 7. Why H2 for Demo (Not Aurora MySQL)?

| Database | Pros | Cons | Decision |
|----------|------|------|----------|
| **H2** | Zero setup, portable, file-based | Not for production | ✅ Demo only |
| Aurora MySQL | Production-grade, scalable | Requires AWS setup | Production |

**Rationale:** H2 is used for the demo to make the project self-contained and easy to deploy. The schema is compatible with MySQL — the same JPA entities work with Aurora MySQL in production with a config change.

---

## 8. Why Dual-Write (Not Shadow Read)?

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| **Dual-write** | Both DBs stay in sync, rollback has latest data | Write amplification, potential conflicts | ✅ Chosen |
| Shadow read | No write changes | Legacy DB may be stale at cutover | ❌ |

**Rationale:** Dual-write ensures that if we need to rollback, the legacy database has all the latest data (including changes made while the ID Platform was active). Shadow read would leave the legacy DB out of date, causing data loss on rollback.

---

## 9. Why SSE for Demo Streaming?

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| **SSE** | Real-time updates, simple implementation, auto-reconnect | Unidirectional | ✅ Chosen |
| WebSocket | Bidirectional | More complex, overkill for demo | ❌ |
| Polling | Simple | Delayed updates, many requests | ❌ |

**Rationale:** The demo needs to stream migration progress in real-time. SSE (Server-Sent Events) is perfect for this — it's unidirectional (server → client), auto-reconnects, and works through proxies.

---

## 10. Why Stateless JWT (Not Server Sessions)?

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| **JWT** | Stateless verification, scalable, cross-service | No server-side revocation without DB lookup | ✅ Chosen |
| Server sessions | Simple revocation | Requires shared session store, tight coupling | ❌ |

**Rationale:** JWT tokens allow each service to verify tokens independently without calling the ID Platform. This is critical for scalability — legacy sites don't need to hit the ID Platform on every request. Token revocation is handled by checking the `auth_tokens` table (acceptable tradeoff for a demo).

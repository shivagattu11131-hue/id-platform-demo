# API Contract Specification

**Project:** Unified ID Platform Integration
**Base URL:** `http://{VM_IP}:3000`
**Content-Type:** `application/json` (unless noted)

---

## 1. OIDC Discovery

### GET `/.well-known/openid-configuration`

**Response:** 200 OK

```json
{
  "issuer": "http://{VM_IP}:3000",
  "authorization_endpoint": "http://{VM_IP}:3000/oauth2/authorize",
  "token_endpoint": "http://{VM_IP}:3000/oauth2/token",
  "userinfo_endpoint": "http://{VM_IP}:3000/oauth2/userinfo",
  "jwks_uri": "http://{VM_IP}:3000/jwks.json",
  "revocation_endpoint": "http://{VM_IP}:3000/oauth2/revoke",
  "registration_endpoint": "http://{VM_IP}:3000/oauth2/register",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "code_challenge_methods_supported": ["S256", "plain"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "token_endpoint_auth_methods_supported": ["none", "client_secret_basic", "client_secret_post"]
}
```

### GET `/jwks.json`

**Response:** 200 OK

```json
{
  "keys": [{
    "kty": "RSA",
    "kid": "id-platform-key-1",
    "use": "sig",
    "alg": "RS256",
    "n": "...",
    "e": "AQAB"
  }]
}
```

---

## 2. Authorization

### GET `/oauth2/authorize`

**Query Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `response_type` | Yes | — | Must be `"code"` |
| `client_id` | Yes | — | Registered client ID |
| `redirect_uri` | Yes | — | Must match registered URI |
| `scope` | No | `"openid"` | Space-separated scopes |
| `state` | No | — | Opaque value returned to client |
| `nonce` | No | — | Nonce for ID token |
| `code_challenge` | No | — | PKCE challenge |
| `code_challenge_method` | No | `"S256"` | `"S256"` or `"plain"` |
| `prompt` | No | `"login"` | `"login"` or `"none"` |

**Response:** 302 Redirect to login page, or 302 Redirect to `redirect_uri?code={authCode}&state={state}` (if `prompt=none` and session exists)

### POST `/oauth2/authorize`

**Content-Type:** `application/x-www-form-urlencoded`

**Form Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `response_type` | Yes | Echoed from GET |
| `client_id` | Yes | Echoed from GET |
| `redirect_uri` | Yes | Echoed from GET |
| `scope` | Yes | Echoed from GET |
| `state` | No | Echoed from GET |
| `nonce` | No | Echoed from GET |
| `code_challenge` | No | Echoed from GET |
| `code_challenge_method` | Yes | Echoed from GET |
| `email` | Yes | User email |
| `password` | Yes | User password |

**Response (success):** 302 Redirect to `redirect_uri?code={authCode}&state={state}`

**Response (failure):** 302 Redirect to `redirect_uri?error=access_denied&error_description=Invalid+credentials`

---

## 3. Token

### POST `/oauth2/token`

**Grant Type: `authorization_code`**

```json
{
  "grant_type": "authorization_code",
  "code": "{authorization_code}",
  "client_id": "main-site",
  "client_secret": "main-site-secret",
  "redirect_uri": "http://{VM_IP}:3001/callback",
  "code_verifier": "{pkce_verifier}"
}
```

**Response (success):** 200 OK

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiJ9...",
  "id_token": "eyJhbGciOiJSUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "openid profile email"
}
```

**Response (error):** 400 Bad Request

```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code has expired"
}
```

**Grant Type: `refresh_token`**

```json
{
  "grant_type": "refresh_token",
  "refresh_token": "{refresh_token}",
  "client_id": "main-site"
}
```

**Response:** Same as authorization_code success (new access + id token, same refresh token)

---

## 4. UserInfo

### GET `/oauth2/userinfo`

**Header:** `Authorization: Bearer {access_token}`

**Response:** 200 OK

```json
{
  "sub": "1",
  "email": "user@example.com",
  "name": "John Doe",
  "email_verified": true,
  "source": "MAIN_SITE"
}
```

---

## 5. Auth (Direct Login — Non-OIDC)

### POST `/api/auth/login`

```json
{
  "email": "user@example.com",
  "password": "minimum6chars"
}
```

**Response:** 200 OK

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "displayName": "John Doe",
    "source": "INTERNAL"
  }
}
```

### POST `/api/auth/register`

```json
{
  "email": "newuser@example.com",
  "password": "minimum6chars",
  "displayName": "New User"
}
```

**Response:** 201 Created (same format as login)

---

## 6. User Profile

### GET `/api/users/me`

**Header:** `Authorization: Bearer {access_token}`

**Response:** 200 OK

```json
{
  "id": 1,
  "email": "user@example.com",
  "displayName": "John Doe",
  "source": "INTERNAL",
  "createdAt": "2026-07-26T00:00:00Z"
}
```

### PUT `/api/users/me`

**Header:** `Authorization: Bearer {access_token}`

```json
{
  "displayName": "Updated Name",
  "email": "newemail@example.com"
}
```

**Response:** 200 OK (updated user object)

### DELETE `/api/users/me`

**Header:** `Authorization: Bearer {access_token}`

**Response:** 200 OK

```json
{
  "message": "Account deactivated successfully"
}
```

---

## 7. Migration

### GET `/api/migration/status`

**Response:** 200 OK

```json
{
  "main_site": {
    "phase": "IMPORTED",
    "totalUsers": 1000,
    "migratedUsers": 1000,
    "conflictCount": 5
  },
  "ma_site": {
    "phase": "CUTOVER",
    "totalUsers": 1000,
    "migratedUsers": 1000,
    "conflictCount": 0
  },
  "totalActiveUsersInIdPlatform": 1995
}
```

### POST `/api/migration/run-demo`

**Response:** 200 OK (SSE stream)

```
data: {"phase":"IMPORT","status":"started","message":"Starting Phase 0: Bulk Import"}
data: {"phase":"IMPORT","status":"completed","imported":1000,"conflicts":5}
data: {"phase":"SHADOW","status":"started","message":"Starting Phase 1: Shadow Mode"}
data: {"phase":"SHADOW","status":"completed","matchRate":0.9995}
...
data: {"phase":"DONE","status":"completed","message":"Migration complete!"}
```

### POST `/api/migration/cutover?site={siteName}`

**Response:** 200 OK

```json
{
  "site": "main-site",
  "phase": "CUTOVER",
  "cutoverAt": "2026-07-26T12:00:00Z",
  "message": "main-site has been cut over to ID Platform authentication"
}
```

### POST `/api/migration/rollback?site={siteName}`

**Response:** 200 OK

```json
{
  "site": "main-site",
  "phase": "ROLLED_BACK",
  "rollbackAt": "2026-07-26T12:30:00Z",
  "message": "main-site has been rolled back to legacy authentication",
  "reverseSync": "Password changes made during ID Platform phase have been synced to legacy DB"
}
```

---

## 8. Client Registration

### POST `/oauth2/register`

```json
{
  "client_id": "new-service",
  "client_name": "New Service",
  "redirect_uris": "http://localhost:4000/callback",
  "scope": "openid profile email"
}
```

**Response:** 201 Created

```json
{
  "client_id": "new-service",
  "client_secret": "auto-generated-uuid",
  "client_name": "New Service",
  "redirect_uris": ["http://localhost:4000/callback"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_post",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"]
}
```

### GET `/oauth2/clients`

**Response:** 200 OK

```json
[
  {
    "client_id": "main-site",
    "client_name": "Main Site",
    "redirect_uris": ["http://{VM_IP}:3001/callback"],
    "scope": "openid profile email"
  }
]
```

---

## 9. Error Codes

| HTTP Status | Error | Description |
|------------|-------|-------------|
| 400 | `invalid_request` | Missing required parameter |
| 400 | `unsupported_grant_type` | Grant type not supported |
| 401 | `invalid_client` | Unknown client_id or wrong secret |
| 401 | `invalid_grant` | Expired/used code or invalid token |
| 401 | `access_denied` | Invalid credentials |
| 409 | `conflict` | User already exists (handled as success) |

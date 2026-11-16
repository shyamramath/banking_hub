# Northbridge Bank — single-endpoint backend with BFF security

A working reference implementation of:

```
Next.js UI ──(HttpOnly session cookie + CSRF token)──▶ Express BFF ──(user's Bearer JWT)──▶ Spring Boot  POST /api/execute
                                                          │                                     │
                                                 OIDC code + PKCE                     JWT validation (iss/aud/exp/sig)
                                                 tokens kept server-side              @BankOperation policy per operation
                                                 Redis sessions                       ownership checks in services
                                                          │                                     │
                                                       Keycloak  ◀──────── JWKS ────────────────┘
```

* **One business endpoint:** `POST /api/execute` and `POST /api/execute/batch`. A dispatcher routes each
  `{ "operation": "...", "payload": {...} }` to a typed handler.
* **Security is per operation:** each handler declares its scopes, roles, audit and rate limit with one annotation.
  The application refuses to start if any handler has no policy.
* **No tokens in the browser:** the BFF does the OIDC login and keeps the tokens in Redis. The browser only gets an
  HttpOnly, SameSite session cookie.

---

## Run it

### Option A: everything in Docker

```bash
docker compose up --build
```

Then open **http://localhost:3000**. Keycloak takes about 30–60 s on the first start.

| User    | Password   | Role                 | What to try |
|---------|------------|----------------------|-------------|
| `alice` | `alice123` | customer (Premier)   | Dashboard, transfers, Security center |
| `bob`   | `bob123`   | customer             | Receives Alice's payee transfers (his checking is `4021009911214455`) |
| `carol` | `carol123` | auditor (staff)      | Audit log; customer operations are denied for her |

Keycloak admin console: http://localhost:8080 (`admin` / `admin`).

### Option B: run the apps locally (hot reload)

```bash
docker compose up keycloak redis            # infra only

cd backend && mvn spring-boot:run           # :8081  (Java 21)
cd bff     && npm install && REDIS_URL=redis://localhost:6379 npm run dev   # :4000
cd ui      && npm install && npm run dev    # :3000
```

All the defaults point at `localhost`, so no extra environment variables are needed.

### Tests

```bash
cd backend && mvn test     # security matrix: no token / wrong scope / wrong role / wrong owner / bad payload / replay / rate limit / batch
cd bff     && npm test     # PKCE, cookie flags, session rotation, CSRF, origin check, allow-list, envelope rebuild, logout
```

---

## Where each control lives

| Layer | Control | File |
|---|---|---|
| UI | Talks only to same-origin `/bff/*`, so there's no CORS. Strict security headers. Idle-timeout warning | `ui/next.config.ts`, `ui/lib/api.ts`, `ui/components/AppShell.tsx` |
| BFF | OIDC Authorization Code + PKCE (S256) as a confidential client. Validates state and nonce | `bff/src/app.js` (`/auth/login`, `/auth/callback`) |
| BFF | Tokens kept only in the server-side session (Redis). Session ID rotates on login (stops session fixation). Idle and absolute timeouts | `bff/src/app.js`, `bff/src/security.js` |
| BFF | CSRF: SameSite=Lax cookie, plus an Origin check, plus a synchronizer token (`X-CSRF-Token`) | `bff/src/security.js` |
| BFF | Silent token refresh, with only one refresh per session at a time (needed for refresh-token rotation) | `bff/src/tokens.js` |
| BFF | Edge allow-list of operations. Writes can't be batched. Per-user rate limits. The envelope is rebuilt so client extras are dropped | `bff/src/operations.js`, `bff/src/app.js` |
| BFF | Logout revokes the refresh token and ends the IdP session | `bff/src/app.js` (`/auth/logout`) |
| Backend | Stateless resource server. RS256 only. Checks issuer, **audience** (`bank-backend`) and expiry. Every other URL returns `denyAll` | `security/SecurityConfig.java` |
| Backend | Keycloak scopes become `SCOPE_*` and realm roles become `ROLE_*` | `security/KeycloakJwtConverter.java` |
| Backend | `@BankOperation(name, scopes, anyRole, audit, ratePerMinute)`. Fails closed at startup | `dispatch/BankOperation.java`, `dispatch/OperationRegistry.java` |
| Backend | Function-level authorization (scopes AND role). Unknown operations get exactly the same 403 as forbidden ones | `dispatch/PolicyEnforcer.java`, `api/ApiException.java` |
| Backend | Object-level authorization: every lookup is scoped to the JWT `sub`. Someone else's record returns 404 | `service/AccountService.java` |
| Backend | Strict payload binding: unknown fields are rejected (stops mass assignment), then Bean Validation runs | `dispatch/PayloadBinder.java` |
| Backend | Rate limit per (user, operation), idempotency keys for transfers, body-size cap, batch-size cap | `dispatch/OperationRateLimiter.java`, `service/TransferService.java`, `web/RequestSizeLimitFilter.java` |
| Backend | Audit trail for audited operations and every denial. Metrics tagged by operation. Correlation ID from UI to BFF to backend | `audit/AuditService.java`, `dispatch/OperationDispatcher.java`, `web/CorrelationIdFilter.java` |
| Network | Backend and BFF aren't published. Only the UI (:3000) and Keycloak (:8080) are reachable from the host | `docker-compose.yml` |

## Adding an operation

```java
@Component
@BankOperation(name = "payees.list", scopes = "accounts:read", anyRole = "customer")
public class ListPayees implements OperationHandler<NoInput, List<Payee>> {
    public Class<NoInput> inputType() { return NoInput.class; }
    public List<Payee> handle(NoInput in, OperationContext ctx) { return payees.of(ctx.user()); }  // scope to ctx.user()!
}
```

Then add `'payees.list': { write: false }` to `bff/src/operations.js`. That's all: no controller, no URL rule, no
security config.

## Moving to production

* **TLS everywhere.** With `NODE_ENV=production` the BFF switches to a `__Host-` cookie with `Secure`. Also set a
  real `SESSION_SECRET` and Keycloak client secret from a secrets manager.
* **mTLS or a service mesh between the BFF and the backend** on top of the JWT check, or use OAuth token exchange
  (RFC 8693) to mint a narrower token for the backend.
* **Shared state:** move the rate limiters (BFF and backend) and the idempotency store to Redis or a database. Send
  the `AUDIT` logger to a WORM store or SIEM (PCI DSS Req. 10).
* **Nonce-based CSP** in Next.js (`proxy.ts`) instead of `'unsafe-inline'`.
* **Step-up authentication** (`acr` claim) for high-value transfers. The dispatcher is the natural place to check it,
  as another attribute on `@BankOperation`.
* Swap `BankStore` for real persistence. The handlers and services keep the same contracts.

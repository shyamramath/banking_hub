# Northbridge Bank — project context for Claude Code

Reference implementation for a banking app: **Next.js UI → Express BFF → Spring Boot backend with ONE business endpoint**
(a command dispatcher instead of many controllers). Keycloak for OIDC, Redis for BFF sessions. Built in a Claude cloud
session on 2026-11-14; this file carries that session's context forward.

## Design decisions (keep these unless asked to change)
- **Single endpoint**: `POST /api/execute` `{operation, payload, meta}` and `POST /api/execute/batch` (max 10, read ops only at the BFF).
  `GatewayController` → `OperationDispatcher` → typed `OperationHandler<I,O>` → service.
- **Per-operation security via one annotation**: `@BankOperation(name, scopes, anyRole, audit, ratePerMinute)`.
  `OperationRegistry` fails startup if a handler lacks it, has no scopes, bad name, or duplicates a name (fail closed).
- **Dispatcher order**: resolve (unknown op ⇒ same 403 as forbidden) → scopes(all)+roles(any) → rate limit per (user, op)
  → strict payload binding (unknown fields rejected; Bean Validation) → handler → metrics tag (bounded) + audit (audited ops + every denial).
- **Object-level authz in services** (`AccountService.ownedAccount`): scoped to JWT `sub`; other users' records ⇒ 404, never trust ids in payload for ownership.
- **Backend** is a stateless resource server: RS256 only, validates iss, **aud=`bank-backend`**, exp; `anyRequest().denyAll()`.
  `KeycloakJwtConverter`: `scope` → `SCOPE_*`, `realm_access.roles` → `ROLE_*`.
- **BFF pattern**: OIDC Auth Code + PKCE (confidential client); tokens only in server session (Redis); browser gets HttpOnly SameSite=Lax cookie;
  session id regenerated on login; CSRF = Origin check + `X-CSRF-Token` synchronizer token; edge allow-list in `bff/src/operations.js`;
  single-flight token refresh (`bff/src/tokens.js`); logout revokes refresh token + RP-initiated logout. BFF rebuilds the envelope (drops client extras).
- **UI** only calls same-origin `/bff/*` and `/auth/*` (Next rewrites → BFF). No tokens in JS.
- Transfers: idempotency key (UUID per intended transfer), $10,000 limit, no credit-card source, rate limit 5/min, audited.

## Layout
- `backend/` Spring Boot 3.5.6, Java 21. Packages: `api`, `dispatch`, `security`, `handlers` (+`dto`), `service`, `domain` (in-memory `BankStore`), `audit`, `web`, `config`.
- `bff/` Express 4, ESM. `src/app.js` (routes), `security.js`, `tokens.js`, `oidc.js`, `backend.js`, `operations.js`, `config.js`. Tests: `npm test` (node:test + supertest).
- `ui/` Next.js 16 App Router, plain CSS (`app/globals.css`). Pages under `app/(bank)/`: dashboard, accounts/[id], transfer, security (attack-simulation console), audit.
- `keycloak/northbridge-realm.json` realm `northbridge`, client `bank-bff`, scopes `accounts:read transfers:write audit:read`,
  users alice/alice123, bob/bob123 (customers), carol/carol123 (auditor). Fixed user ids match `BankStore` constants.
- `docker-compose.yml`: keycloak(:8080), redis, backend(internal), bff(internal), ui(:3000).

## Status / what's verified
- BFF: 12 tests pass. UI: `next build` passes; full browser walk-through passed against a mock IdP + stub backend.
- Backend compiles and starts cleanly under `docker compose up --build` (Spring Boot, all 7 operations register, no handler/scope errors).
- Keycloak realm import verified against real Keycloak 26.0.7 — **fixed a real bug**: `passwordPolicy` was `length(8)` but `bob123` is only 6 chars, so Keycloak refused the full import (`invalidPasswordMinLengthMessage`) and the container exited. Changed policy to `length(6)` in `keycloak/northbridge-realm.json` to match the shortest documented demo password rather than changing the documented credentials.
- Full browser walkthrough done 2026-11-14: `docker compose up -d`, logged in as bob end-to-end (UI → BFF OIDC/PKCE → Keycloak → backend dispatcher), dashboard loaded real data via `profile.get`/`accounts.list`/`transactions.recent`.

## Adding an operation
1. Handler class with `@Component @BankOperation(...)`, scope all lookups to `ctx.user()`.
2. Add the name to `bff/src/operations.js` (`write: true` for state-changing).
3. Add security-matrix tests in `DispatcherSecurityTest`.

## Production follow-ups (not done)
TLS + `__Host-` cookie (automatic with NODE_ENV=production), secrets manager, mTLS or RFC 8693 token exchange BFF→backend,
Redis-backed rate limits/idempotency, audit to WORM/SIEM, nonce-based CSP via `proxy.ts`, step-up auth (`acr`) for large transfers, real persistence.

## Owner context
Shyam — staff engineer / solution architect (Java/Spring Boot, payments). Prefers concrete, production-minded code.

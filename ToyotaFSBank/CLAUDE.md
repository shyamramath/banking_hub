# ToyotaFSBank

A banking dashboard demo: two independent backends implementing the same
features (ToyotaFSBank account CRUD + Plaid account-linking/aggregation),
plus one frontend. Built to compare a classic REST+blocking stack against a
GraphQL+reactive one side by side.

## Structure & ports

| Dir | Stack | Port |
|---|---|---|
| `backend/` | Spring Boot 4.1.2, Java 21, Spring MVC (blocking) + Spring Data JPA | 8081 |
| `graphql-backend/` | Spring Boot 3.3.5, Java 21, Spring WebFlux + Spring for GraphQL (reactive) + R2DBC | 8082 |
| `frontend/` | Next.js 16, TypeScript, Tailwind, App Router | 3002 |

**The frontend is wired to `graphql-backend` (8082), not `backend`.**
`backend/` runs standalone and is fully functional, just not called by the UI.

Ports were chosen to avoid other unrelated apps already running on this
machine on 8080/3000/3001 — if those are free when you read this, there's no
need to avoid them, but don't assume 8080/3000 are free without checking.

## Running things

Each backend has its own `.env` (gitignored, real credentials) + `.env.example`
(committed template) + `start.sh` (loads `.env` into the environment, then
runs `spring-boot:run`):

```bash
cd backend && ./start.sh            # REST API on :8081
cd graphql-backend && ./start.sh    # GraphQL API on :8082
cd frontend && npm run dev          # UI on :3002
```

If `.env` is missing/empty, Plaid endpoints return a clean error (REST: 502
with Plaid's message; GraphQL: a classified GraphQL error) instead of
crashing — everything else still works. Get sandbox keys from
https://dashboard.plaid.com/developers/keys.

**Both backends use in-memory H2 — all data is lost on every restart.**
Don't be surprised when accounts/linked items disappear after a restart;
that's expected, not a bug.

## Where things live

| Feature | REST backend | GraphQL backend |
|---|---|---|
| Plaid client setup | `plaid/config/PlaidClientConfig.java` | `config/PlaidClientConfig.java` |
| Plaid business logic | `plaid/service/PlaidService.java` | `service/PlaidService.java` |
| Plaid HTTP/GraphQL surface | `plaid/controller/PlaidController.java` | `graphql/PlaidController.java` |
| Linked-item entity | `plaid/model/PlaidItem.java` (JPA `@Entity`) | `domain/PlaidItem.java` (R2DBC record) |
| Account entity | `model/Account.java` | `domain/Account.java` |
| GraphQL schema | — | `src/main/resources/graphql/schema.graphqls` |
| Frontend API client | — | `frontend/src/lib/api.ts` (talks GraphQL to 8082) |
| Frontend Plaid Link trigger | — | `frontend/src/components/PlaidLinkButton.tsx` |
| Main dashboard | — | `frontend/src/components/BankingHubDashboard.tsx` |

Full Plaid flow writeup (sequence diagram, what's persisted vs. fetched
live, security caveats) is in `docs/PLAID_INTEGRATION.md` — read that before
touching anything Plaid-related.

## Conventions to follow when extending this project

- **The Plaid Java SDK is blocking** (Retrofit2, no reactive client exists).
  In `graphql-backend`, every new Plaid call MUST be wrapped:
  `Mono.fromCallable(() -> blockingCall()).subscribeOn(Schedulers.boundedElastic())`.
  Never call it directly from a controller/service method that returns
  `Mono`/`Flux` without this wrapping — it'll block Netty's event loop.
- **GraphQL DTO naming**: `*Input` for mutation arguments, `*Payload` for
  returned types (e.g. `AccountInput` → `AccountPayload`). REST DTO naming:
  `*Request`/`*Response`.
- **Timestamps in GraphQL are `String`** (ISO-8601 via `Instant.toString()`),
  not a custom scalar. Keep doing this unless there's a real reason to add
  `graphql-java-extended-scalars`.
- **R2DBC entities are plain Java records** with `@Table`/`@Id`
  (`graphql-backend/domain/`). JPA entities are Lombok `@Data` classes with
  `@Entity` (`backend/model/`). Don't mix the styles within a backend.
- **Security**: both the GraphQL backend's `SecurityConfig` and its CORS
  config follow `spring-graphql-app`'s pattern — permissive by default,
  `anyExchange().authenticated()` only under `@Profile("secure")`. If you
  add real auth, follow that same opt-in-profile shape rather than making
  the default insecure-by-omission.
- **GraphiQL**: don't re-enable `spring.graphql.graphiql.enabled: true` —
  Boot's built-in page hardcodes `unpkg.com` CDN URLs that hang in the
  browser. Use the vendored one at `/graphiql` (`GraphiqlController` +
  `static/vendor/graphiql/`) instead; if assets ever need updating, copy
  fresh ones from `spring-graphql-app/src/main/resources/static/vendor/graphiql/`.
- **No comments explaining what code does** — only why, when it's
  non-obvious (see existing files for the level of terseness expected).

## Known gaps (intentional, not oversights)

- **No `User` entity / no auth.** Linked Plaid items and accounts aren't
  scoped to anyone. Adding real users is the biggest piece of unfinished
  scope — see `docs/PLAID_INTEGRATION.md` → "Current limitations".
- **Plaid `access_token` stored in plaintext.** Fine for sandbox; needs
  encryption at rest before this is ever near real bank data.
- **No webhooks** — data is pulled synchronously on every request, never pushed.
- **`transactionsSync` always starts from a null cursor** — no cursor
  persistence/resumption. Fine at sandbox volume.

# ToyotaFSBank — Session Summary

**Saved deliberately outside `ToyotaFSBank/`** at
`/Users/shyamramath/development/src/github/fiserv/ToyotaFSBank-SESSION-SUMMARY.md`
so it survives if that folder gets deleted. This is a full recap of what was
built, why, and how to pick it back up — not a code dump (the actual files
are/were in `ToyotaFSBank/`).

## What this project is

A banking demo app ("Banking Hub") with two independent backends
implementing the same features, plus one frontend:

```
ToyotaFSBank/
├── backend/          Spring Boot 4.1.2, REST, blocking (Spring MVC + JPA) — port 8081
├── graphql-backend/  Spring Boot 3.3.5, GraphQL, reactive (WebFlux + R2DBC) — port 8082
├── frontend/         Next.js 16, TypeScript, Tailwind, App Router — port 3002
└── docs/
    └── PLAID_INTEGRATION.md   (detailed Plaid flow writeup, saved before this file)
```

The frontend is wired to **`graphql-backend` (port 8082)**, not `backend`.
`backend` (REST) still works standalone but nothing currently calls it.

## Why two backends

Built `backend/` first (REST). Then asked to build a second one,
specifically **GraphQL + reactive**, implementing the *same* features
(ToyotaFSBank accounts + Plaid), modeled on conventions from an existing
unrelated reference project at `/Users/shyamramath/development/src/github/spring-graphql-app`
(which was explicitly pointed to for style — permissive-by-default Spring
Security with an opt-in `secure` JWT profile, reactive `CorsConfigurationSource`,
`@QueryMapping`/`@MutationMapping` controllers, R2DBC repositories on
records, vendored GraphiQL assets instead of Boot's CDN-dependent default).
Then the frontend was switched over to talk to the GraphQL one exclusively.

## Feature set (identical in both backends)

**ToyotaFSBank accounts** — a simple domain: `ownerName`, `accountType`
(`CHECKING`/`SAVINGS`/`AUTO_LOAN`), `balance`. Full CRUD. Auto-generated
account numbers (`TFS-XXXXXXXX`). No relation to any user — just a flat list.

**Plaid account linking/aggregation** — the real feature:
1. Create a Plaid Link token
2. Open Plaid's hosted Link UI (`react-plaid-link`) in the browser
3. Exchange the resulting public token for an access token (stored server-side)
4. Fetch live balances/transactions for every linked institution, every time (nothing cached)
5. Unlink an institution (calls Plaid's `/item/remove`, deletes the local row)

Full step-by-step + sequence diagram + what's stored vs. fetched live is in
`docs/PLAID_INTEGRATION.md` — that file has the complete technical writeup
and is worth preserving separately if useful.

## Key architectural decisions & why

- **Plaid Java SDK is blocking** (Retrofit2, no reactive client exists). In
  `graphql-backend`, every Plaid call is wrapped:
  `Mono.fromCallable(() -> blockingCall()).subscribeOn(Schedulers.boundedElastic())`
  — keeps blocking HTTP off Netty's event loop while the API stays `Mono`/`Flux`.
- **No `User` entity anywhere.** Explicitly discussed and left as a known
  gap — see Limitations below.
- **R2DBC entities are Java records** with `@Table`/`@Id` (Spring Data
  R2DBC), e.g. `Account`, `PlaidItem` in `graphql-backend`. JPA backend uses
  Lombok `@Data` classes instead (`@Entity`).
- **Enum/date handling in GraphQL**: `AccountType` is a native GraphQL enum;
  all timestamps are exposed as `String` (ISO-8601 via `.toString()`) rather
  than wiring up a custom `Instant` scalar — simplest option, no extended
  scalars library needed.
- **Each backend has its own `.env`/`.env.example`/`start.sh`**, same
  convention as the `plaid/quickstart` reference project: `start.sh` does
  `env $(cat .env | grep -v "#" | xargs) ./mvnw spring-boot:run`. `.env` is
  gitignored; `.env.example` documents the keys with blanks.
- **Plaid sandbox credentials**: pulled from
  `/Users/shyamramath/development/src/github/plaid/quickstart/java/.env`
  (user's own existing Plaid dashboard sandbox app) and copied into both
  `backend/.env` and `graphql-backend/.env`. **Not reproduced in this
  summary file** — if you need them again, they're still in the quickstart
  project's `.env`, or your Plaid dashboard at
  dashboard.plaid.com/developers/keys.
- **Ports**: 8081 (REST), 8082 (GraphQL), 3002 (frontend) — all chosen to
  avoid colliding with other unrelated apps already running on this machine
  (something was already on 8080/3000/3001).
- **GraphiQL fix**: Spring Boot's built-in GraphiQL page (`spring.graphql.graphiql.enabled=true`)
  hardcodes `unpkg.com` CDN URLs with no override, which hung on "Loading…"
  in the browser. Fixed by vendoring the GraphiQL JS/CSS assets (copied from
  `spring-graphql-app/src/main/resources/static/vendor/graphiql/`) and
  serving them via a custom `GraphiqlController` at `/graphiql`, with Boot's
  version disabled.

## What was verified (not just written — actually run)

For **both** backends, with real Plaid sandbox credentials, no mocking:

1. Compiled and booted cleanly (REST: full Spring context; GraphQL: schema
   inspection reported zero unmapped fields/arguments)
2. Account CRUD round-tripped through real HTTP/GraphQL calls
3. Created a real Plaid sandbox Link token (actual network call to
   `sandbox.plaid.com`, confirmed via DNS resolution + an established TCP
   connection from the backend's own PID to Plaid's sandbox IP at request time)
4. Used Plaid's sandbox-only `/sandbox/public_token/create` shortcut to
   simulate a completed Link session for **"First Platypus Bank"** (`ins_109508`)
   without needing a browser, then exchanged it through each backend
5. Fetched back the real sandbox dataset — **15 accounts**, **48
   transactions** — through both backends
6. Unlinked and confirmed Plaid's `/item/remove` was actually called
7. GraphQL error classification (`NOT_FOUND`) and CORS preflight both confirmed
8. Frontend: type-checks, lints (including a new strict React Compiler
   hook rule — had to remove a synchronous `setState` at the top of an
   effect-triggered async function and add a documented eslint-disable for
   the fetch-on-mount pattern), builds for production, confirmed against
   live GraphQL data via curl with the frontend's actual origin

All backend processes were stopped after each verification pass — the
intent throughout was to leave nothing running unless actively being looked at.

## UI changes made along the way

- Homepage replaced entirely: was a generic "create a ToyotaFSBank account"
  CRUD demo (`AccountDashboard.tsx`, deleted), now `BankingHubDashboard.tsx`
  — net worth summary, linked institutions with live balances (with
  "+ Add account" and "Unlink" actions), ToyotaFSBank accounts section
  (create/close), and a searchable combined transaction feed.
- Removed the duplicate "Connect a bank account" button from the page
  header — the "+ Add account" button inside the "Linked accounts" card
  covers the same action.

## Known limitations (discussed explicitly, not yet addressed)

- **No user scoping** — `plaid_items` has no `user_id`; every linked item
  is globally visible via the API. A real app needs a `User` table + auth.
- **`access_token` stored in plaintext** in an in-memory H2 database. Fine
  for sandbox/dev; production needs encryption at rest + a durable DB.
- **No webhooks** — everything is pulled synchronously on request.
- **`transactionsSync` always starts from a null cursor** rather than
  storing/resuming Plaid's `next_cursor` — fine at sandbox volume, wasteful
  at scale.
- **In-memory H2 means all data resets on every backend restart** — ran
  into this directly: restarting `graphql-backend` to pick up the GraphiQL
  fix wiped the one linked item and accounts that existed at the time.
- **Frontend `updateAccount`/GraphQL mutation exists but is unused** — the
  dashboard only creates/deletes accounts, no edit UI.

## How to pick this back up

If `ToyotaFSBank/` still exists:
```bash
cd ToyotaFSBank/graphql-backend && ./start.sh   # port 8082
cd ToyotaFSBank/frontend && npm run dev          # port 3002, already points at 8082
```
Then re-link a sandbox account (data won't have survived a restart) — see
`docs/PLAID_INTEGRATION.md` for sample GraphQL mutations, or just click
"+ Add account" in the UI and use Plaid sandbox test login `user_good` /
`pass_good` against any test institution (e.g. search "Platypus").

If `ToyotaFSBank/` has been deleted: this file plus
`docs/PLAID_INTEGRATION.md` (if you copied it too) describe everything that
existed in enough detail to rebuild deliberately rather than from scratch —
the "Key architectural decisions" section above is the part most worth
reusing, since it has the reasoning, not just the outcome.

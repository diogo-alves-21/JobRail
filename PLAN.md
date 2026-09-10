# JobRail — Distributed Job Queue: Build Plan

## Context

Building a portfolio flagship: a Postgres-backed job queue service with concurrent
workers, exponential-backoff retries, and a dead-letter queue — containerized and
deployed to a live URL. The repo is already scaffolded (Java 21, Maven), but two things
diverge from the original pitch and must be settled before feature work:

- **Framework is Quarkus, not Spring Boot.** The pitch text says Spring Boot; the actual
  scaffold ([pom.xml](pom.xml) `packaging=quarkus`, Hibernate/Panache, `quarkus-jdbc-postgresql`,
  Docker/compose) is fully Quarkus. **Decision: keep Quarkus.** The two architecture rules
  translate 1:1 — "no HTTP/Spring-web in the core" becomes "no JAX-RS/CDI-web in the core."
- The scaffold has gaps that will break the build or later phases (see Phase 0).

The two architecture rules that carry the project:
1. **Keep the core free of web/framework concepts.** No JAX-RS annotations, no request
   objects, no `@Transactional` in core classes. The core is plain Java called by
   controllers today, callable by a library tomorrow.
2. **One clean seam, not a framework.** Separate the layers now; do *not* build plugin
   points / config hooks for a hypothetical library caller that doesn't exist yet.

Current state: only [Job.java](src/main/java/org/jobrail/Job.java) exists (empty class).
No tests, no migrations. Effectively greenfield on top of a good scaffold.

---

## Architecture: package layout (honors both rules)

Hexagonal / ports-and-adapters, kept to **one seam** (the store port) — not a plugin system.

```
org.jobrail
├─ core/                     ← PLAIN JAVA. No Quarkus, no JAX-RS, no @Transactional.
│  ├─ Job.java               ← domain model (POJO/record): id, type, payload(String JSON),
│  │                            status, attempts, maxAttempts, runAfter, timestamps
│  ├─ JobStatus.java         ← enum: PENDING, PROCESSING, SUCCEEDED, FAILED, DEAD
│  ├─ JobStore.java          ← PORT interface (the one seam). Adapter implements it.
│  ├─ JobQueue.java          ← enqueue / get / listByStatus / retry (API-facing ops)
│  ├─ JobProcessor.java      ← claim→run→complete/fail orchestration (worker body)
│  ├─ Backoff.java           ← PURE function: runAfterFor(attempts). Unit-testable, no DB.
│  ├─ JobHandler.java        ← interface: void handle(Job); + a registry keyed by type
│  └─ exceptions            ← core error types (Clean Code ch.7: no returning null/codes)
├─ infra/                    ← ADAPTERS. Quarkus/JPA lives here.
│  ├─ JobEntity.java         ← @Entity, payload via @JdbcTypeCode(SqlTypes.JSON) → jsonb
│  ├─ PostgresJobStore.java  ← implements JobStore; owns @Transactional + native SKIP LOCKED SQL
│  └─ JobMapper.java         ← MapStruct (already a dep) maps JobEntity <-> core.Job
├─ api/                      ← JAX-RS controllers + DTOs. Thin. Call JobQueue only.
│  ├─ JobResource.java
│  ├─ StatsResource.java
│  └─ dto/                   ← request/response records (no core types leak to wire)
└─ worker/                   ← runtime glue: @Scheduled poller that calls JobProcessor
   └─ WorkerScheduler.java   ← configurable concurrency; NOT in core
```

Why this satisfies rule 2: the *only* abstraction is `JobStore`. Transaction boundaries
and the Postgres-specific claim SQL live in the adapter, so `core` imports nothing from
Quarkus. `JobProcessor` calls `store.claimNext(...)`; it never sees JPA or `@Transactional`.

---

## Phase 0 — Fix the scaffold (prerequisite, ~30 min)

Do this first or the build/later phases break.

- **Add `quarkus-flyway`** to [pom.xml](pom.xml) — `application.properties` already sets
  `quarkus.flyway.migrate-at-start=true` and `locations=database/migrations`, but the
  extension is missing.
- **Add `quarkus-scheduler`** (needed for Phase 3 worker pool).
- **Resolve the dual JSON provider conflict**: `quarkus-rest-jsonb` and `quarkus-rest-jackson`
  are both present and are mutually incompatible. **Keep `quarkus-rest-jackson`, remove `rest-jsonb`.**
- **Clean up test deps**: `quarkus-junit` (not a real artifact) vs `quarkus-junit5`; and
  `testcontainers 2.0.4` (Testcontainers is on 1.x — likely wrong coordinate). Prefer
  **Quarkus Dev Services** (auto-starts a Postgres container for `%test`, already configured
  in `application.properties`) and drop the explicit Testcontainers dep unless a raw
  multi-worker test needs it.
- Create `src/main/resources/database/migrations/` directory.

---

## Phase 1 — The core engine (spend real time here)

The heart. No HTTP.

**Migration** `V1__create_jobs.sql`:
```sql
CREATE TABLE jobs (
  id           BIGSERIAL PRIMARY KEY,
  type         TEXT        NOT NULL,
  payload      JSONB       NOT NULL DEFAULT '{}',
  status       TEXT        NOT NULL DEFAULT 'pending',
  attempts     INT         NOT NULL DEFAULT 0,
  max_attempts INT         NOT NULL DEFAULT 5,
  run_after    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- partial index that makes the claim query fast
CREATE INDEX idx_jobs_runnable ON jobs (run_after)
  WHERE status = 'pending';
```

**Core operations** (`JobQueue` + `JobProcessor` against `JobStore`):
- **Enqueue** — insert a row in `pending` with `run_after = now()`.
- **Claim (the crux)** — implemented in `PostgresJobStore.claimNext()`, `@Transactional`,
  native SQL:
  ```sql
  UPDATE jobs SET status='processing', updated_at=now()
  WHERE id = (
    SELECT id FROM jobs
    WHERE status='pending' AND run_after <= now()
    ORDER BY run_after
    FOR UPDATE SKIP LOCKED
    LIMIT 1
  )
  RETURNING *;
  ```
  Returns the claimed `Job` or empty. `SKIP LOCKED` is what lets N workers pull disjoint
  rows without blocking each other.
- **Complete** — `markSucceeded(id)`: status `succeeded`.
- **Fail** — `markFailed(...)`: increment `attempts`; retry/backoff decision lands in Phase 2.

**Proof test (the interview story):** with a Dev Services / real Postgres, enqueue N jobs,
launch M threads each looping `claimNext()` until the table drains, record every claimed id.
Assert: no id claimed twice, all N processed. This is the evidence that `SKIP LOCKED`
prevents double-processing.

**Study task (do it, it's the differentiator):** read the Postgres docs on `SKIP LOCKED`
and row-level locking; write 3–4 sentences in your notes on the isolation behavior (why
`SKIP LOCKED` skips *locked* rows rather than blocking, and how `FOR UPDATE` interacts with
READ COMMITTED). This becomes a README paragraph and your best interview answer.

---

## Phase 2 — Retries and the dead-letter queue

Read Clean Code ch. 7 (error handling) alongside — throw meaningful exceptions from
handlers, don't return null/error codes; the processor catches and decides retry vs DLQ.

- `Backoff.runAfterFor(attempts)` — **pure function**, e.g. `base * 2^attempts` capped at a
  max (say base=2s, cap=5m). Unit-tested with zero DB.
- On handler failure with `attempts + 1 < max_attempts`: `store.reschedule(id, runAfter)`
  sets `status='pending'`, `attempts=attempts+1`, `run_after = now() + backoff(attempts)`.
  **The claim query already filters `run_after <= now()`, so backoff falls out for free** —
  call this elegance out in the README.
- Exhausted (`attempts + 1 >= max_attempts`): `store.markDead(id)` → `status='dead'`.
  **DLQ is a *state*, not a separate table** — simpler and defensible.
- **Test:** an always-failing `JobHandler` lands in `dead` after *exactly* `max_attempts`
  tries, and the recorded `run_after` gaps match the backoff schedule.

---

## Phase 3 — The service shell (HTTP over the core)

Controllers are thin; they contain zero queue logic — they call `JobQueue`.

- `POST /jobs` — enqueue (body: type + payload) → returns id/status
- `GET /jobs/{id}` — status
- `GET /jobs?status=dead` — list DLQ
- `POST /jobs/{id}/retry` — requeue a dead job (`dead → pending`, `run_after = now()`)
- **Worker pool**: `worker/WorkerScheduler` uses `@Scheduled` (fixed interval) with
  **configurable concurrency** (`jobrail.workers.count`, `jobrail.workers.poll-interval` in
  `application.properties`); each tick fans out to N workers, each calling
  `JobProcessor.claimAndRunOnce()`. Lives outside `core`.
- DTOs in `api/dto` (records) keep core `Job` off the wire. rest-assured tests per endpoint.
- This controller-over-core seam is the subject of the Month 4 ADR — consider drafting it
  with the `engineering:architecture` skill.

---

## Phase 4 — Observability (simple `/stats`, per decision)

- `GET /stats` — SQL-backed: counts by status + DLQ depth, e.g.
  `SELECT status, count(*) FROM jobs GROUP BY status`, plus `dead` count surfaced as DLQ
  depth. No Micrometer for now (can add `quarkus-micrometer-registry-prometheus` later for
  processed/failed rates — noted as a future add, not built now).
- **Structured logging with job ids**: enable JSON logging (`quarkus-logging-json`) and put
  the job id in MDC around `JobProcessor` so a single job's full lifecycle
  (claimed → running → succeeded/failed/rescheduled/dead) is greppable by id.
- Backs the "observability" claim the README makes.

---

## Phase 5 — Ship it (Railway)

- **Multi-stage Dockerfile** already exists ([Dockerfile](Dockerfile)) and matches the Quarkus
  jar layout — verify it builds after Phase 0 dep changes.
- **docker-compose** already wires service + Postgres ([docker-compose.yml](docker-compose.yml)) —
  smoke-test `docker compose up` end to end.
- **Deploy to Railway**: Postgres plugin + service-from-Dockerfile; set `JOBRAIL_DATASOURCE_*`
  env vars from Railway's Postgres connection. Get the live URL.
- **GitHub Actions**: `test → build → deploy on merge to main`. Test job runs `./mvnw verify`
  (Dev Services provides Postgres in CI via Docker); deploy step uses Railway's GitHub
  integration or CLI token.
- **README** (half the project): architecture diagram + design-decisions section + live link.

---

## The README must cover
- The `SKIP LOCKED` concurrency decision and the isolation behavior (from Phase 1 study).
- DLQ-as-state (not a separate table) and why.
- The exponential-backoff approach and the "backoff falls out of the claim filter" elegance.
- The core/HTTP separation and the future-library rationale (the `JobStore` seam).
- One honest **"what I'd do differently at scale"** paragraph (e.g. polling vs
  `LISTEN/NOTIFY` or a broker; single-table contention; visibility-timeout for crashed
  workers holding `processing` rows; partitioning/archival of terminal jobs).

## Deliberately deferred (do NOT build)
- Library/embedded version — the reason to keep `core` clean, but don't design its public
  API until a real caller exists.
- Any Micrometer/Prometheus, plugin points, or config hooks beyond the single `JobStore`
  seam. One clean seam, not a framework.

---

## Verification
- **Phase 1:** `./mvnw test` — backoff unit test (pure) + multi-worker proof test (no id
  claimed twice, all drained).
- **Phase 2:** always-failing job reaches `dead` after exactly `max_attempts`; `run_after`
  spacing matches backoff.
- **Phase 3:** rest-assured tests for each endpoint; manual `./mvnw quarkus:dev` +
  curl `POST /jobs` then `GET /jobs/{id}` watching it go `pending→processing→succeeded`.
- **Phase 4:** `GET /stats` returns correct counts after a run; logs show one job id across
  its full lifecycle.
- **Phase 5:** `docker compose up` runs the stack locally; CI green on a PR; live Railway URL
  serves `GET /stats`.

## Suggested commit cadence
One commit per phase (or per operation within Phase 1), on a feature branch, matching the
existing `[JobRail] Feature: ...` message style.
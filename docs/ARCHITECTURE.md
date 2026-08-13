# Architecture Review

Written: 2026-07-06. Based on a full read of every source file, config file, and document in the
repository, cross-referenced against `docs/ANALYSIS.md` (2026-07-02), `docs/RESEARCH.md`,
`designdoc.md`, and `docs/discussion.md`. This document describes *what the system is* and
*how good it is*; the forward-looking plan lives in `docs/ROADMAP.md`.

---

## 1. What This System Is

A GitHub webhook event capture and notification platform built on Spring Boot 3.3 / Java 17.
It ingests GitHub events, persists them, fans them out to subscribers by event type, and runs a
scheduled "stale issue" alert pipeline on top of the stored events.

```
                        GitHub (webhook POST /webhook, X-GitHub-Event header)
                                          │
                              WebHookController (returns 202 immediately)
                                          │
                              EventProducerImpl → Kafka "github-event-topic"
                                          │  (key = eventType, value = normalized DTO JSON)
              ┌───────────────────────────┴───────────────────────────┐
              ▼                                                       ▼
   KafkaDatabaseConsumer                                   FilteredEventConsumer
   (group: database-consumer)                              (group: filter-consumer)
              │                                                       │
              ▼                                                       ▼
   MongoDB raw event store                              EventTypeSubscribers lookup
   (PushEvents / IssueEvents)                           (inverted index: eventType → uids)
              │                                                       │
              │                                         uids → emails (PostgreSQL Users)
              │                                                       │
              │                                         QueueMessageDTO per subscriber
              │                                                       ▼
              │                                    AWS SQS "EventNotificationsQueue"
              │                                                       ▲
              ▼                                                       │
   IssueAlertScheduler (hourly cron)                                  │
              │                                                       │
   IssueAlertServiceImpl.scanAndAlert()  ─────────────────────────────┘
   (aggregation on IssueEvents → per-user TTL check →
    AlertRecord dedup → enqueue alert)
                                                                      │
                                                                      ▼
                                       EventNotificationImplConcurrency
                                       (poller thread + Guava RateLimiter 10/s,
                                        10-thread pool, exponential backoff)
                                                                      │
                                                                      ▼
                                                     AWS SES → subscriber email
```

### Component inventory

| Layer | Components | Notes |
|---|---|---|
| Ingestion | `WebHookController`, `EventProducerImpl`, `EventAccess` | `EventAccess` is a static registry mapping event-type strings → DTO classes (`issues`, `push`) |
| Streaming | Kafka topic `github-event-topic`, two consumer groups | Persistence and fan-out are decoupled — each can lag or fail independently |
| Persistence | MongoDB (events, filters, indexes, alert history, TTL configs), PostgreSQL (users) | Polyglot by design: relational identity, document events |
| Subscription | `EventFiltersServiceImpl`, `Filters`, `EventTypeMap`, `RepositoryMap` | Dual inverted indexes: eventType → uids, repository → uids, maintained via `$addToSet` bulk upserts |
| Notification | `AsyncQueueserviceImpl` (SQS batch send), `EventNotificationImplConcurrency` (poll + dispatch), `EmailSenderServiceImpl` (SES) | Queue decouples event processing from delivery |
| Alerting | `IssueAlertScheduler`, `IssueAlertServiceImpl`, `TtlConfigController/Service`, `AlertRecord` | Per-user TTL thresholds; append-only event log + aggregation determines "currently open" |
| Auth | `AuthController`, `AuthServiceImpl`, `JwtService`, `SecretManager`, `JwtAuthenticationFilter` | HS512 JWT; signing key from AWS Secrets Manager; stateless sessions |
| Observability | `MonitorServiceImpl`, `CounterAspectConfig` (`@Counted` AOP), Prometheus + Grafana | Throughput counters on every repository method |
| Infra | `local-dev/docker-compose.yml` (Kafka, Mongo, Postgres, Prometheus, Grafana, LocalStack), STS AssumeRole for AWS | Dev/prod parity via LocalStack |

### Data model (MongoDB collections)

| Collection | Entity | Purpose | Key |
|---|---|---|---|
| `PushEvents`, `IssueEvents` | `PushEventDTO`, `IssueEventDTO` | Raw event log (append-only) | auto `_id` |
| `Filters` | `Filters` | user → subscribed event types | `uid` (not `@Id` — see weakness §3.3) |
| `EventTypeSubscribers` | `EventTypeMap` | eventType → uids (inverted index) | `eventType` |
| `RepositorySubscribers` | `RepositoryMap` | repository → uids (inverted index) | `repository` |
| `ttlConfig` | `ttlConfig` | per-user stale-issue threshold (day/hour) | `uid` (`@Id`) |
| `AlertHistory` | `AlertRecord` | alert dedup: (issueId, uid, alertedAt) | auto `_id` |

### Key design decisions and their rationale

1. **Two Kafka consumer groups on one topic** — persistence and notification are independent
   concerns with different failure modes. Textbook pattern, correctly applied.
2. **Inverted indexes for subscriber lookup** — O(1) lookup per incoming event instead of
   scanning all `Filters` documents. Correct structure for a read-heavy fan-out path.
3. **SQS between filtering and email delivery** — if the delivery worker crashes, events keep
   flowing and queue up; delivery is retryable. Also the load-test discovery that a *synchronous*
   SQS call inside a Kafka listener throttled the whole pipeline drove the move to
   `SqsAsyncBatchManager` — a real, diagnosed-from-metrics engineering decision.
4. **Append-only event log + aggregation for issue state** — rather than maintaining a mutable
   `isOpen` flag (vulnerable to out-of-order Kafka delivery), "currently open" is computed by
   grouping `IssueEvents` by `issueInfo.id` and taking the latest action. Documented and
   justified in `designdoc.md`.
5. **STS AssumeRole for AWS credentials** — no long-lived keys in code; all AWS clients share an
   auto-refreshing temporary credential provider.
6. **`@Scheduled` over Quartz** — single-instance app; Quartz's clustered scheduling was
   correctly rejected as unjustified complexity.

---

## 2. Strengths

### 2.1 The topology is right
The macro-architecture — webhook → log → independent consumers → queue → rate-limited workers —
is the correct shape for this problem and would survive a 100× traffic increase with only
configuration and index changes. Most of the system's problems (§3) are *implementation defects
inside a sound structure*, which is the good way around: fixing them doesn't require redesign.

### 2.2 Observability is a first-class habit
`@Counted` AOP on repositories, typed counters in `MonitorServiceImpl`, Prometheus + Grafana in
docker-compose, and — most tellingly — the project's biggest bug (sync SQS blocking the Kafka
listener thread) was *found by reading Grafana during a load test*, not by luck. That closed loop
(instrument → load test → observe → diagnose → fix with async) is the strongest engineering
story in the repository.

### 2.3 The documentation trail is unusually good for a solo project
`designdoc.md` records design *alternatives and rejections* (Quartz, `isOpen` flag, per-scan
caching) with reasons. `discussion.md` is honest self-critique. `docs/ANALYSIS.md` is a ranked
defect inventory. `CLAUDE.md` onboards a coding agent. Very few solo projects have a written
record of *why* decisions were made — this is a durable asset, and (see `ROADMAP.md` §2) it is
exactly the asset that compounds in agent-driven development.

### 2.4 The newest code is the best code
`IssueAlertServiceImpl` (guard-clause structure, projection instead of full-document loads,
dedup via `AlertRecord`) and its 11 pure-Mockito unit tests are markedly better than the older
layers. The quality gradient across git history shows real growth — and shows the newer
docs-driven, phased workflow (design doc → phases → tests per phase) producing better output
than the earlier ad-hoc workflow.

### 2.5 Concurrency handled with real mechanisms, not hand-waving
`EventNotificationImplConcurrency` has graceful shutdown (`DisposableBean` + 30 s
`awaitTermination`), interrupt propagation, capped exponential backoff, and a Guava
`RateLimiter` added in response to an actual observed overload. Imperfect (§3.4) but the
operational concerns were recognized and addressed.

### 2.6 Dev/prod parity
LocalStack for SQS, docker-compose for everything else, and profile switching means the full
pipeline runs on a laptop. This matters double in agent-driven development: an environment an
agent can start and exercise autonomously is a precondition for autonomous verification.

---

## 3. Weaknesses

`docs/ANALYSIS.md` H1–H8 remain open and are not repeated in detail here. Grouped by theme,
with new findings from this review marked **[NEW]**.

### 3.1 Reliability: the pipeline loses or duplicates events at every seam

The core promise of an event-capture system is "no event lost, no notification duplicated."
Every stage currently violates one or both:

| Seam | Failure | Ref |
|---|---|---|
| Webhook → Kafka | `EventProducerImpl` catches `JsonProcessingException`, logs, drops. Also: unknown event type → `EventAccess.getEventObj()` returns `null` → NPE in `mapper.readValue`, yet the controller already returned 202. **Every GitHub event type other than `issues`/`push` (e.g. `pull_request`, `star`) currently throws on ingest.** [NEW — sharper than RESEARCH.md flag #2] | `EventProducerImpl.java:26` |
| Kafka → MongoDB | Offset auto-committed regardless of write success; failed Mongo write = silent event loss | RESEARCH.md flag #7 |
| Kafka → filter path | `Optional.get()` without presence check kills the consumer thread permanently on any event type with no subscribers | ANALYSIS H2, `FilteredEventConsumer.java:56` |
| SQS → email | `deleteMessage()` is called **before** the email send is attempted (`EventNotificationImplConcurrency.java:76-84`) — a failed SES send after a successful delete is a *lost* notification, and the code comments/docs describe the opposite (delete after dispatch). **[NEW]** | |
| Anywhere | No DLQ, no retry policy, no idempotency keys anywhere in the pipeline | RESEARCH.md flag #13 |

### 3.2 Security: multiple independent full-compromise paths

- **[NEW] `/webhook` accepts unauthenticated, unverified payloads.** GitHub signs webhook
  deliveries with `X-Hub-Signature-256` (HMAC-SHA256 over the body); `WebHookController` never
  checks it. Anyone who finds the URL can inject arbitrary fake events — which flow into the
  database, trigger emails to all subscribers, and (post-ROADMAP) would trigger agent actions.
  This is the highest-severity *new* finding of this review.
- Plaintext credentials in the repo: Confluent SASL key (`application-prod.yml`), MongoDB Atlas
  URI with password + RDS password (`application-prod.properties`), and the same DB password in
  dev `application.properties` and `docker-compose.yml` (ANALYSIS H6 — wider than H6 states).
- JWT signing key logged at INFO (`SecretManager.java:47`, ANALYSIS H3); `SecretManager` returns
  `null` on failure and `JwtService` caches it forever (H4); Spring Security `debug=true` (H5);
  all actuator endpoints exposed including `/actuator/env` and `/actuator/heapdump`.
- No input validation on any DTO; `JwtAuthenticationFilter` rethrows any exception as a raw
  `RuntimeException` (500 + stack trace instead of 401).

### 3.3 Consistency: multi-collection writes with no transaction or upsert discipline

- `createFilters()` writes `Filters`, then `EventTypeSubscribers`, then `RepositorySubscribers`
  as three separate operations through a **stateful, non-thread-safe** `MongoTemplateService`
  (ANALYSIS H1). Partial failure leaves the indexes disagreeing with `Filters`.
- **[NEW]** `Filters` has no `@Id` and `createFilters()` calls `save()` (insert), so a user
  re-submitting filters accumulates *multiple* `Filters` documents; `findByUserId` returns
  `Optional<Filters>` and will fail or return an arbitrary one once duplicates exist.
- `clearAllFilters()` deletes only `Filters` — orphaned uids remain in both inverted indexes,
  so "unsubscribed" users keep receiving notifications and alerts (ANALYSIS medium).
- No MongoDB indexes on any collection — every lookup is a collection scan (ANALYSIS H8).

### 3.4 Configuration and wiring: environment selection by code edit, not by profile

- Queue URLs, region, role ARN, sender email are hardcoded in Java
  (`AsyncQueueserviceImpl.java:30`, `QueueServiceImpl.java:22`,
  `EventNotificationImplConcurrency.java:30`, `AwsCredentialsConfig.java:15`).
  `AsyncQueueserviceImpl` — used by the *dev* pipeline — points at the **cloud** queue and the
  `sqsAsyncClientCloud` bean; switching environments means editing source.
- `applicationl.yml` (typo filename) silently ignored; dev Kafka config actually comes from it
  never loading — confusing config surface. Dead code: unused `ProfileCredentialsProvider`s in
  `SqsConfiguration` and `EmailSenderServiceImpl` (the latter builds a whole sync `SesClient`
  from a `my-dev-profile` that will fail on any machine without that AWS profile).
- Interfaces exist (`EventProducer`, `QueueService`, `EventNotification`, `EventFiltersService`)
  but controllers and services inject the `*Impl` classes directly — the seams for
  testing/substitution were declared and then bypassed.

### 3.5 Verification: CI proves style, not behavior

- `.github/workflows/maven.yml` runs **only `mvn validate` (checkstyle)** — zero tests execute
  in CI. The 11 Mockito tests pass locally but nothing gates a regression.
- Most "tests" under `src/test` are manual scripts against real AWS or a locally-running
  docker-compose (`SQStest` has real queue URLs and STS role ARNs; `MongoTemplateServiceTest`
  needs a live Mongo). They cannot run headlessly and are effectively unrunnable by CI or by a
  coding agent — which matters (ROADMAP §2.2).
- Only the `issues` event path is exercised end-to-end; the architecture's claimed generality
  (`EventAccess` + per-type collections) is unproven for any second event type.

### 3.6 Product completeness

The original requirements (label frequency analysis, high-priority daily digests, PR-opened
stakeholder notifications, statistics APIs) were silently descoped to: *issue events, filtered,
emailed, plus TTL alerts*. The email itself is raw event JSON wrapped in `<p>` tags
(`FormatEmail.java`) — functional, not consumable. There is no read API over the captured data
(no "show me my open alerts", no stats endpoint), so the stored events are write-only value.

---

## 4. The Five Engineering Problems That Matter

Sections 3.1–3.6 list defects individually; most of them are instances of five underlying
engineering problems. These are the problems worth understanding deeply — fixing symptoms
without naming the problem invites recurrence. `ROADMAP.md` §3 maps each one forward to its
AI-era escalation.

### P-A. Delivery semantics were never decided
The system never chose what it guarantees: at-least-once, at-most-once, or best-effort.
Consequence: events can be **lost** at four seams (producer swallows deserialization failures;
Kafka offset commits regardless of Mongo write success; notification worker deletes the SQS
message before attempting the SES send; no DLQ anywhere) and **duplicated** at others (no
idempotency keys; `auto-offset-reset=earliest` in prod replays history for any new consumer
group). `discussion.md` §1 identified the root cause correctly: the capture goal ("never lose
an event") and the notification goal ("timely, no spam") have *different* delivery requirements
and were treated as one pipeline. Every §3.1 row is a symptom of this one undecided question.

### P-B. The ingestion trust boundary is open
`/webhook` accepts any payload from anyone — GitHub's `X-Hub-Signature-256` HMAC is never
verified. Everything downstream (database, subscriber emails, TTL alerts) implicitly trusts
that events describe real repository activity. The entire pipeline's integrity rests on an
endpoint whose only protection is URL obscurity.

### P-C. Blocking I/O inside streaming consumers
The project's defining bug — a synchronous SQS call inside a Kafka listener that silently
throttled the whole consumer group, found via Grafana under load test — is an instance of a
general rule: *any blocking call inside a stream consumer converts that consumer's throughput
to the latency of the slowest dependency.* The fix (async batch manager) resolved the instance;
the rule must be re-applied every time a new dependency enters a consumer (see ROADMAP §5,
first decision seed — LLM calls are the next candidate violation).

### P-D. Shared mutable state and multi-write consistency
Two related shapes: (1) `MongoTemplateService` holds `domainClass` and `BulkOperations` as
singleton instance fields mutated in a three-step call sequence from concurrent Kafka-listener
and HTTP threads — a silent cross-collection data-corruption race (ANALYSIS H1). (2)
`createFilters()` writes three collections (`Filters`, `EventTypeSubscribers`,
`RepositorySubscribers`) with no transaction and no upsert discipline, so partial failure —
or `clearAllFilters()`, which deletes only one of the three — leaves the inverted indexes
permanently disagreeing with the source of truth.

### P-E. Verification does not exist as a machine-checkable artifact
CI runs checkstyle only; zero tests execute on any push. The integration "tests" require real
AWS credentials or a hand-started docker-compose, so no machine — CI runner or coding agent —
can prove a change works. The consequence today is that regressions land silently; the
consequence in agent-driven development (ROADMAP §2.1) is that the cheap half of engineering
(writing code) accelerates while the binding half (proving it correct) stays manual.

---

## 5. Overall Assessment

**Architecture: sound.** The event-log-plus-consumers shape, the inverted indexes, the queue
decoupling, and the append-only issue-state model are all defensible and were chosen for
articulated reasons.

**Implementation: a working prototype with production-disqualifying defects.** The defects
cluster where they always do — error paths, concurrency, config hygiene, and the seams between
systems — and they are enumerated, ranked, and mostly cheap to fix (`ANALYSIS.md` §4 priority
list, plus the webhook-signature and delete-before-send findings above).

**Highest-leverage insight:** this codebase's structure (event pipeline over GitHub activity)
is coincidentally the exact substrate that AI-agent development workflows need — a machine
that watches repository events and takes actions. The gap between "portfolio notification demo"
and "agent trigger/observability layer" is mostly the hardening work already on the books.
That argument, and the plan, are in `docs/ROADMAP.md`.

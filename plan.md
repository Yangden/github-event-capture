# Test Plans

All test implementation plans for this repository live in this file. Each section is one
planned (or completed) test effort; keep status lines current as work lands.

---

## Plan 1 — IssueAlert Integration Test (designdoc Phase 6, final part)

**Status:** complete (2026-07-18)
**Scope:** full scan-to-SQS path of `IssueAlertServiceImpl.scanAndAlert()` against real
local MongoDB + LocalStack SQS, per designdoc.md "Phase 6 Integration Test".

**Outcome:** `IssueAlertIntegrationTest` (`service.impl` package), 5 scenarios per §5 below.
Step 0 (externalizing the queue URL) was skipped per owner instruction, so the queue URL stayed
hardcoded in `AsyncQueueserviceImpl`; queue resolution was instead solved entirely test-side by
overriding the `sqsAsyncClientCloud` bean (`LocalStackSqsTestConfig`) to point at LocalStack and
using the LocalStack multi-account trick (access key id `038462794128`, matching the account
segment of the hardcoded AWS-shaped URL, for both that bean and the test's own SQS client) —
verified manually against running LocalStack before relying on it. 2 of 5 scenarios pass
(within-TTL, closed-latest-wins); the other 3 (happy path, reopened, dedup) are `@Disabled` —
they exposed a real main-source bug in `IssueAlertServiceImpl.queryOpenIssues()`'s aggregation
(`Aggregation.group("issueInfo.id")` groups on a field path Spring Data Mongo never actually
persists — see designdoc.md "Phase 6 Integration Test — COMPLETE" for the full analysis). Not
fixed here; src/main was out of scope for this task.

### 1. Why this tier exists — division of labor with the unit tests

`IssueAlertServiceImplTest` (11 tests, complete) owns the **branch logic**: every guard
clause and the happy-path call sequence, with all collaborators mocked. The integration
test does NOT re-test branches. It verifies the layer the mocks hid:

| Never executed by unit tests | Where |
|---|---|
| The aggregation pipeline: sort DESC → group by `issueInfo.id` → `first()` ("latest event wins"), `action != "closed"`, date cutoff, `@Field("_id")` → `OpenIssueResult` mapping | `IssueAlertServiceImpl.queryOpenIssues` |
| Spring Data derived/annotated queries against real Mongo: `findByRepository`, `findByUserId`, `existsByIssueIdAndUid` | repositories |
| Document round-trip: seeded `IssueEventDTO` docs (Java field names, `Instant` dates) actually matching the aggregation's field paths | Mongo mapping layer |
| A real SQS send: serialized `QueueMessageDTO` landing in a queue and deserializing back | `AsyncQueueserviceImpl.batchSend` |

The designdoc's two verifications (TTL filtering, deduplication) stay in scope — but as
end-to-end behavior over real queries, not as branch re-tests.

### 2. Step 0 — precondition code change (skip it for now- main source)

`AsyncQueueserviceImpl` hardcodes the production queue URL as `private static final`
(`AsyncQueueserviceImpl.java:30`). Even with a LocalStack-pointed `SqsAsyncClient` bean,
sends would target `https://sqs.us-east-1.amazonaws.com/...`.

- Replace the constant with `@Value("${sqs.queue-url:https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue}")`
  (default preserves current prod behavior; no other call-site changes).
- This pulls forward one line of ROADMAP Phase 2.5 ("externalize hardcoded queue URLs");
  the rest of 2.5 stays where it is.

### 3. Test infrastructure decisions

- **Class:** `IssueAlertIntegrationTest` in `service.impl` test package.
- **Context:** `@SpringBootTest`. (`@DataMongoTest` is not viable — `UserRepository` is JPA.)
- **User store:** H2 in-memory via test properties (`jdbc:h2:mem:...`, `ddl-auto=create-drop`),
  consistent with existing PostgreSQL-dependent tests. Real Postgres NOT required.
- **MongoDB:** real local instance from `local-dev` docker-compose (`mongodb://localhost:27017/test`),
  same convention as `MongoTemplateServiceTest`.
- **SQS:** LocalStack (`http://localhost:4566`), static `test/test` credentials — same pattern
  as `SQStest.sqsClientLocal`. A `@TestConfiguration` provides:
  - `@Bean @Qualifier("sqsAsyncClientCloud") SqsAsyncClient` with
    `endpointOverride(http://localhost:4566)` (overrides the production bean by name).
  - `@BeforeAll`/setup creates a dedicated queue (e.g. `issue-alert-integration-queue`)
    via `createQueue`; test property `sqs.queue-url` points at it.
- **Startup isolation:** `@MockBean` on `SecretManager` (or `JwtService`) so context startup
  does not call AWS Secrets Manager. If Kafka listeners fail to connect without docker Kafka,
  disable via `spring.kafka` autoconfig exclusion or accept docker-compose Kafka being up
  (docker-compose is a stated precondition anyway — decide at implementation time, prefer
  whatever keeps the test green with plain `docker-compose up -d`).
- **Isolation between tests:** `@BeforeEach` drops/clears `IssueEvents`, `ttlConfig`,
  `Filters`, `RepositorySubscribers`, `AlertHistory` collections, clears the H2 user table,
  and purges the test queue (`purgeQueue`).

### 4. Data seeding strategy

Populate simulated data directly, replicating the production write path:

- **IssueEvents:** `IssueEventDTO`'s inner classes (`IssueInfo`, `Repository`) are private
  with no setters — fields are Jackson-populated only. Seed by building a minimal GitHub
  webhook JSON string, `objectMapper.readValue(json, IssueEventDTO.class)`, then
  `mongoTemplate.save(dto)`. This is byte-for-byte the production persistence path
  (`KafkaDatabaseConsumer` → `saveEvent`), so document shape and Java-field-name mapping
  are exercised honestly. Helper: `seedIssueEvent(issueId, action, createdAt, repoName)`.
- **ttlConfig:** entity has setters — construct and save via `TtlConfigRepository` or
  `mongoTemplate.save`. Helper: `seedTtl(uid, day, hour)`.
- **RepositorySubscribers:** save a `RepositoryMap` with `repository` + uid list.
- **Filters:** save a `Filters` doc for the uid with event type `"issues"`.
- **User (H2):** save a `User` with uid + email via `UserRepository`.

Time control: no clock injection exists; use relative timestamps —
"past TTL" = `Instant.now().minus(ttlHours + margin)`, "within TTL" = `Instant.now().minus(small)`.
Margin ≥ 1h to keep the test immune to slow runs.

### 5. Scenarios and assertions

Each test: seed → `issueAlertService.scanAndAlert()` → assert on queue + `AlertHistory`.

| # | Scenario | Seed | Assert |
|---|---|---|---|
| 1 | Happy path | Issue opened past TTL; full subscriber setup (repo map, filters "issues", ttlConfig, H2 user) | Exactly 1 message in queue; body deserializes to `QueueMessageDTO` with `eventType="alert"`, correct email; embedded event JSON has right `issueId`/`repository`; 1 `AlertRecord` in `AlertHistory` with `(issueId, uid)` |
| 2 | Within TTL | Same setup, issue newer than the user's TTL | Queue empty; no `AlertRecord` |
| 3 | Closed issue (latest wins) | Two events for one issueId: `opened` (older), `closed` (newer, but still past cutoff); full subscriber setup | Queue empty — verifies sort→group→first picks the latest event. **Not covered anywhere today; highest-value scenario** |
| 4 | Reopened issue | Two events: `closed` (older), `reopened` (newer, past TTL) | 1 message — latest-wins in the alerting direction |
| 5 | Deduplication end-to-end | Happy-path seed; call `scanAndAlert()` **twice** | Exactly 1 message total; second run adds nothing — verifies `AlertRecord` write-then-read through real Mongo (stronger than pre-seeding a record) |

Multiple-issue fan-out and per-subscriber guard permutations are deliberately excluded —
unit-test territory.

### 6. Assertion mechanics

- `batchManager.sendMessage` buffers (flushes on batch size or ~200 ms frequency): never
  assert immediately. Positive cases: `receiveMessage` with `waitTimeSeconds(5–10)` or
  Awaitility polling until 1 message or timeout.
- Negative cases (expect empty): short-poll after a fixed grace period (~1–2 s) covering the
  flush window; assert 0 messages.
- Delete received messages within each test (or rely on `purgeQueue` in setup).
- `AlertHistory` assertions via `AlertRecordRepository` / `mongoTemplate.find`.

### 7. Execution & documentation

- Precondition: `cd local-dev && docker-compose up -d` (Mongo + LocalStack; Kafka per §3).
- Run: `./mvnw test -Dtest=IssueAlertIntegrationTest`.
- This remains a manual-infrastructure test like `MongoTemplateServiceTest`/`SQStest`.
  Migration to Testcontainers (headless, CI-runnable) is ROADMAP Phase 1.2 — out of scope
  here, but keep the test's docker assumptions in one place (properties/`@TestConfiguration`)
  so the migration is a config swap.
- On completion: mark designdoc.md Phase 6 Integration Test COMPLETE; update CLAUDE.md
  Testing section (two test tiers + this test's docker requirement).

### 8. Implementation order (commit-sized steps)

1. Externalize `sqs.queue-url` in `AsyncQueueserviceImpl` (main source, behavior-preserving).
2. Test scaffold: `@SpringBootTest` boots green with docker-compose up — test config bean
   overrides, H2 properties, `@MockBean` isolation, queue creation, cleanup hooks.
3. Seeding helpers + scenario 1 (happy path).
4. Scenarios 2–4 (TTL boundary, closed-latest, reopened).
5. Scenario 5 (double-scan dedup).
6. Doc deltas: designdoc.md progress section, CLAUDE.md testing notes.

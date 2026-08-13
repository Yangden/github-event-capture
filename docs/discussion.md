# Project Review & Discussion

---

## 1. Defining the Problem

**Strengths**

The problem scope is well-chosen for a backend portfolio project. GitHub webhooks provide structured, real-world event data without the instability of scraping, and they naturally expose a multi-stage processing pipeline (ingest → filter → notify) that exercises Kafka, databases, queues, and cloud services simultaneously. The decision to add actionable outputs — TTL-based alerts on stale issues, per-subscriber event filtering, SQS-decoupled notifications — elevates the project above a simple "data logger" and gives it a product-level narrative.

**Weaknesses**

The problem definition mixes two different goals that were never fully reconciled:

- *Operational goal*: capture and store every event reliably.
- *Notification goal*: fan out relevant events to interested users in near-real-time.

These have different reliability requirements (at-least-once vs. exactly-once delivery, tolerance for duplicates, acceptable latency). Because they were treated as a single pipeline, the architectural tension between them — Kafka offset commits vs. MongoDB write failures (flag #7), no deduplication on SQS (flag #13) — was never explicitly resolved. Stating these as separate concerns up front would have made the tradeoffs clearer and driven better design decisions earlier.

The acknowledged scope reduction — delivering notifications for a single event type (issues) only — also means several requirements (push events, pull request notifications, label-frequency statistics, daily digests) were defined but never delivered. The gap between stated and actual scope should be documented as a conscious decision rather than left implicit.

---

## 2. Project Architecture

**Strengths**

- **Dual Kafka consumer groups** is architecturally correct. Persistence and fan-out are independent concerns; separating them means one can lag or fail without affecting the other. This is a textbook pattern executed correctly.
- **Inverted index (`EventTypeSubscribers`)** avoids a full-collection scan per incoming event. For a subscription-heavy workload this is the right structure.
- **STS AssumeRole** is a good credential hygiene choice. It avoids long-lived keys in code and is closer to how production AWS environments actually work.
- **Async SQS send + concurrent SQS receive** correctly resolves the bottleneck discovered during load testing. Recognising that a synchronous SQS call inside a Kafka listener blocks the listener thread — and tracing that all the way back to reduced DB throughput in Grafana — is a non-trivial debugging insight.
- **Polyglot persistence** (PostgreSQL for users, MongoDB for events/filters) is defensible: user identity benefits from relational constraints; event documents benefit from a flexible schema.

**Weaknesses**

- **`MongoTemplateService` is stateful and not thread-safe** (flag #8). Storing `domainClass` and `BulkOperations` as instance fields means concurrent calls from `KafkaDatabaseConsumer` (which runs in a Kafka listener thread pool) can corrupt each other. This is a latent data-corruption bug.
- **No transactional boundary between Kafka commit and MongoDB write** (flag #7). If the write fails after the offset is committed, the event is silently dropped. Because MongoDB and Kafka cannot participate in the same distributed transaction cheaply, the standard fix is to commit the Kafka offset *only after* a successful write, or to use an idempotent write key so replaying the message is safe. Neither is in place.
- **`FilteredEventConsumer` crashes on unseen event types** (flag #3). `Optional.get()` without an `isPresent()` guard means the Kafka filter consumer will throw `NoSuchElementException` and stop processing for any event type that has no subscribers yet. This is a hard failure on a routine condition.
- **`ttlConfig` entity exists but is inert** (flag #11). The TTL alert feature — which was a core requirement — has a data model but no scheduler, no enforcement, and no consumer logic. The `IssueAlertScheduler` added in recent commits (git history) addresses part of this, but the connection to `ttlConfig` is unclear.
- **The `EventAccess` registry is a global mutable static map.** It cannot be injected, mocked, or extended at runtime, and adding a new event type requires touching this class directly. For a small project this is acceptable, but it is a hidden coupling point.

---

## 3. Implementation Quality

**What went well**

- **Structured logging** throughout the service layer (`LOGGER.info(...)` after every significant operation) is directly responsible for making the SQS bottleneck diagnosable from log output alone. This is a good engineering habit.
- **Micrometer `@Counted` on repository methods** provides a clean, non-invasive way to expose throughput metrics. The insight from load testing — that DB counters lagged far behind send rate — was only possible because these metrics existed.
- **Exponential backoff in `EventNotificationImplConcurrency`** and the Guava `RateLimiter` show awareness of real operational concerns (thundering herd, SQS request rate limits).
- **Separate dev/prod profiles** with LocalStack substituting for real SQS locally is a solid developer experience choice.

**What needs improvement**

- **Hardcoded credentials in `application-prod.yml`** (flag #1) is the most critical issue. MongoDB Atlas URI, RDS password, and Confluent Cloud API keys are in the repository. Anyone with read access to the repo has full database access. These must move to environment variables or AWS Secrets Manager immediately.
- **No input validation on request DTOs** (flag #10). `FiltersDTO` and `UserDTO` accept any input without `@Valid` constraints. A blank username, empty event type list, or malformed email address will propagate silently into the database.
- **`debug=true` on `@EnableWebSecurity`** (flag #5) writes request-level security decisions to application logs in production. This is both a performance issue and an information-leakage risk.
- **Silent event drop in the producer** (flag #2). `EventProducerImpl` catches `JsonProcessingException` and logs it without any recovery path. There is no dead-letter queue and no alert. Under any real traffic this will cause invisible data loss.
- **No deduplication** (flag #13). Kafka at-least-once delivery plus SQS visibility timeout retries means users can receive duplicate notifications. This is a correctness issue, not just a performance issue.

---

## 4. Addressing Notes from requirement.md

**"Core issue: broken rhythm, no stable continuous documentation"**

This is visible in the codebase. The gap between what is specified in requirement.md and what is actually implemented suggests work proceeded in disconnected bursts. The practical consequence is that there are multiple half-implemented features (`ttlConfig`, label frequency analysis, push event fan-out) that add complexity without delivering value. Going forward, a habit of closing the loop on each feature — implement, test, document in one session — is worth more than any architectural improvement.

**"The whole project is simplified to: filter a single type of event (issue), and successfully send its notification"**

This is an honest assessment. The architecture supports multiple event types (via `EventAccess`, dual collections, generic filter model), but only the `issues` path is exercised end-to-end. This means the generality of the design is untested. Push events, for example, would require a separate DTO and collection entry, but whether `FilteredEventConsumer` handles them without regression is unknown.

**"Textbook knowledge like concurrency, thread, async — not applied to project analysis"**

The SQS bottleneck was the clearest example: a synchronous call inside a Kafka consumer thread blocked the entire consumer group. The mental model to catch this earlier is: *every Kafka listener runs on a fixed thread pool; any blocking call inside it reduces effective parallelism to zero for that partition*. The `@KafkaListener` annotation abstracts the thread management, which hides this. A useful rule: any I/O call inside a Kafka listener that is not the Kafka read itself should be async or offloaded.

The `EventNotificationImplConcurrency` concurrency model also has an underexplored risk: 10 threads all sharing the same `SesAsyncClient` and `SqsAsyncBatchManager`. If SES rate limits are hit, all 10 threads will back off simultaneously, and the exponential backoff counter is shared state. Whether this is thread-safe under `volatile` or `AtomicInteger` is worth verifying.

**"Design decision: why use concurrency on the SQS receive side, how it affects performance and creates potential bugs"**

The decision was driven by the observed bottleneck: synchronous SQS polling was a serial chokepoint. The 10-thread model solves throughput but introduces:

1. **Message ordering** — if two threads receive messages for the same user near-simultaneously, email delivery order is non-deterministic. For most notification use cases this is acceptable, but it is worth stating explicitly.
2. **Duplicate processing** — if a thread successfully sends the SES email but crashes before calling `deleteMessage`, SQS will redeliver after the visibility timeout, and the user gets a duplicate email. A deduplication table (e.g., in Redis or a separate MongoDB collection) keyed on the SQS message ID would close this gap.
3. **Thread-pool exhaustion** — if SES latency spikes, all 10 threads can be simultaneously blocked on `CompletableFuture.get()`, stalling new message processing. Using `CompletableFuture` chains (`thenCompose`) rather than blocking `get()` inside the thread pool would avoid this.

**"Missing steps: source codes (SQS and JWT auth parts), bugs faced, monitoring on cloud"**

- **JWT / Secrets Manager**: the JWT secret is retrieved from AWS Secrets Manager at startup via `SecretManager`. If Secrets Manager is unreachable (network issue, IAM policy change), the application will fail to start with no clear error message to the operator. Adding a startup health check or a fallback dev secret would improve this.
- **Monitoring on cloud**: Prometheus is currently configured to scrape `host.docker.internal:8080`, which only works in a local Docker environment. For Heroku (or any hosted environment), this scrape target is unreachable. Micrometer's push-based remote write (Prometheus Remote Write or DataDog) would be the right approach for cloud deployment.

---

## 5. Next Steps & Effectiveness Assessment

**Is the project effective?**

As a learning vehicle: yes. It touches a meaningful slice of backend engineering — event streaming, async I/O, cloud services, auth, observability — and the load-testing + bottleneck-diagnosis cycle is a high-quality learning experience. The architecture is coherent and defensible.

As a production system: no, not yet. The credential exposure issue alone would make it unacceptable in any real environment. The silent event loss and the crashing `FilteredEventConsumer` are correctness failures. The unimplemented features make it hard to evaluate against the original requirements.

**Recommended next steps (priority order)**

1. **Fix credential exposure immediately.** Move all secrets in `application-prod.yml` to environment variables. This is a security obligation before any further work.

2. **Fix the `FilteredEventConsumer` crash** (flag #3). Replace `Optional.get()` with `Optional.ifPresent()` or an explicit `isPresent()` guard. This is a five-line fix that prevents total pipeline failure on new event types.

3. **Complete and wire `ttlConfig` / `IssueAlertScheduler`.** The scheduler exists; connect it to the `ttlConfig` collection so that per-user alert thresholds are actually read and enforced. This finishes the most interesting stated requirement.

4. **Add input validation to DTOs.** `@NotEmpty`, `@Email`, `@Size` annotations on `UserDTO` and `FiltersDTO`, with `@Valid` on controller method parameters. This is low-effort and prevents a class of runtime bugs.

5. **Extend end-to-end coverage to push events.** Add `PushEventDTO` to `EventAccess`, create a test that sends a push webhook, and verify storage + notification. This validates that the generalised architecture actually generalises.

6. **Add deduplication for SQS messages.** Store processed SQS message IDs (or a content hash) in MongoDB with a short TTL. Before sending an email, check for prior delivery. This closes the duplicate-notification gap.

7. **Fix the `MongoTemplateService` thread-safety issue.** Either make `setDomainClass` and `bulkWrite` synchronized, or refactor to pass `domainClass` as a method parameter instead of storing it as instance state.

8. **Gate `security.debug=true` on the dev profile only.** A one-line property change in `application.properties` vs. `application-prod.yml` splits this correctly.

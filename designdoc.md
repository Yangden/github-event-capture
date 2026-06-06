# Task
* issue events(may includeo open and closed) stored in the database. I need to implement a periodic task: send alert notifications to users when the time of issue event remained open exceeds users configured ttl.

# Related Database
* MongoDB
* Collections: IssueEvents, RepositorySubscribers, Filters, ttlConfig

# Schedule Task
* Scheduled task — see recommendation below to use @Scheduled instead of Quartz.

---

# Evaluation

## Conflicts with Existing Patterns

### 1. RepositorySubscribers is never populated
`RepositoryMap` (collection `RepositorySubscribers`) exists as an entity but `createFilters()` in
`EventFiltersServiceImpl` only writes to `Filters` and `EventTypeSubscribers` — never to
`RepositorySubscribers`. The scan would always return empty results. Either extend `FiltersDTO`
to include repo names, or add a separate subscription endpoint.

### 2. IssueEventDTO cannot identify individual issues
To find "open issues not yet closed," events must be grouped by issue identity (GitHub's
`issue.number`). Currently `IssueInfo` only stores `state`, `body`, and `createdAt`. Without
`issue.number`, you cannot determine which open events have been matched by a close event —
the scan is not implementable as designed.

### 3. IssueEventDTO.repository getter is private
The inner `Repository` class has `getName()` declared `private`. Code outside the class cannot
read the repository name, which the scan needs for the `RepositorySubscribers` lookup.

### 4. UID type mismatch
`RepositoryMap.uids` is `List<String>`, but `ttlConfig.uid` and `Filters.uid` are `long`. When
looking up a uid from `RepositorySubscribers` and then querying `ttlConfig`, an implicit
string-to-long conversion is required with no type safety.

### 5. ttlConfig is not writable
`ttlConfig` has no `@Id`, no `setUid()` method, and no repository or controller. There is no way
for a user to set their TTL — the collection will always be empty.

---

## Ambiguities

### A. How does a user subscribe to a repository?
No API or write path populates `RepositorySubscribers`. Clearest option: add
`repositories: List<String>` to `FiltersDTO` and extend `createFilters()` to upsert into
`RepositorySubscribers` using the same `$addToSet` bulk pattern already used for
`EventTypeSubscribers`.

### B. What does "open issue" mean across multiple events?
GitHub sends `opened`, `reopened`, `edited`, `closed`, `labeled` as separate webhook calls, each
stored as its own `IssueEvents` document. "Currently open" means: for a given issue, the most
recent event's action is not `closed`. This requires a MongoDB aggregation (group by
`issueId`, sort by time, filter on action).

### C. Whose TTL applies?
`ttlConfig` is per-user. If an issue has three subscribers with TTLs of 1 day, 3 days, and 7
days — alert each user at their own threshold, or use a global default? This changes the query
structure. Recommendation: per-user TTL, alert each subscriber only when their own threshold
is crossed.

### D. Alert deduplication
If the scheduler runs every 6 hours and an issue has been open for 2 days, users get alerted
repeatedly until the issue closes. Need either an `AlertHistory` collection or an `alertedAt`
field on a per-issue tracking document to prevent re-alerting.

### E. Same SQS queue or a new one?
The existing `EventNotificationsQueue` + `QueueMessageDTO` + `EventNotificationImplConcurrency`
email path can be reused directly — enqueue a `QueueMessageDTO` with `eventType = "alert"`.
No new infrastructure needed. Reusing is strongly preferred.

### F. Quartz adds unjustified complexity
Quartz is useful for clustered, exactly-once scheduling across multiple app instances. This app
is single-instance with Spring Boot already wired. `@Scheduled` (Spring's built-in) is fully
sufficient and consistent with the existing framework.

---

## Implementation Plan

### Phase 1 — Data model fixes (prerequisites for everything else)
- `IssueEventDTO`: add `issueNumber` field (`issue.number` from GitHub JSON); make
  `Repository.getName()` and `IssueInfo` getters public; add public `getRepositoryName()`
- `ttlConfig`: add `@Id`, add `setUid()`, confirm `uid` type is `long`
- `RepositoryMap`: change `List<String> uids` to `List<Long> uids`
- New entity: `AlertRecord` (fields: `issueNumber`, `repository`, `uid`, `alertedAt`) stored in
  MongoDB collection `AlertHistory` — used for deduplication

### Phase 2 — Populate RepositorySubscribers
- Add `repositories: List<String>` to `FiltersDTO`
- Extend `createFilters()` to upsert uid into `RepositorySubscribers` using the same
  `$addToSet` bulk pattern already used for `EventTypeSubscribers`
- Add `RepositoryMapRepository` with a `findByRepository` query method

### Phase 3 — TTL config API
- New `TtlConfigRepository` (MongoDB)
- New `TtlConfigServiceImpl` with create/update logic
- New `POST /api/ttl` endpoint (JWT-protected) so users can set their `day`/`hour` thresholds

### Phase 4 — Alert scanner service
`IssueAlertService.scanAndAlert()` logic:
1. MongoDB aggregation on `IssueEvents`: group by `issueId`, take the latest event per group,
   filter where `action != "closed"`, filter where `createdAt < now - globalMaxTtl`
   (use max across all users as the outer bound)
2. For each candidate open issue: look up subscribers in `RepositorySubscribers`
3. Cross-reference `Filters` to confirm subscriber has `"issues"` event type enabled
4. Load each subscriber's `ttlConfig`; skip if issue age has not yet crossed their personal
   threshold
5. Check `AlertRecord` — skip if this `(uid, issueId)` tuple already exists
6. Enqueue `QueueMessageDTO` to existing `EventNotificationsQueue` (reuse existing email path)
7. Write `AlertRecord` to mark the alert as sent

### Phase 5 — Scheduler
- Add `@EnableScheduling` to the application config class
- New `IssueAlertScheduler` with `@Scheduled(cron = "${alert.cron:0 0 * * * *}")` (hourly by
  default, overridable via properties)
- No Quartz

### Phase 6 — Tests
- Unit test `IssueAlertService` with mocked repositories
- Integration test for the full scan-to-SQS path using the existing LocalStack + local MongoDB
  setup (consistent with `SQStest` and `MongoTemplateServiceTest` patterns)

---

## Summary of Required Improvements to the Original Design
1. Drop Quartz — use `@Scheduled` instead
2. Specify the repo subscription write path — `RepositorySubscribers` must be populated;
   extend `FiltersDTO` and `createFilters()`
3. Add deduplication via `AlertRecord` — without it users are spammed on every scheduler run
4. Clarify per-user vs. global TTL — per-user is recommended; changes the aggregation query
5. Reuse the existing SQS/email path explicitly — `QueueMessageDTO` into
   `EventNotificationsQueue` avoids duplicating infrastructure
6. Fix blocking data model gaps before writing any service code: `issue.number` in
   `IssueEventDTO`, public getters, `ttlConfig` writability, UID type consistency


# Solution
* Identify individual issue: use `issue.id` stored as `long` in `IssueInfo`. Globally unique
  across GitHub — simpler than composite `(repository, issue.number)`.
* Alert deduplication: `AlertRecord` entity (`issueId`, `uid`, `alertedAt`) in `AlertHistory`
  collection. `alertedAt` is for auditing and future re-alert logic; deduplication key is
  `(issueId, uid)`.
* Scheduler: use Spring `@Scheduled`, not Quartz.
* SQS: reuse existing `EventNotificationsQueue` + `QueueMessageDTO` + email path.
* TTL: per-user (each subscriber's own `ttlConfig` threshold is checked individually).
* Per-subscriber cache in scan loop: deferred. `filterRepository.findByUserId()` and
  `userRepository.findById()` are called once per subscriber per open issue — if the same
  subscriber appears across many open issues they are re-fetched each time. An in-scan cache
  (Map<Long, Filters> / Map<Long, User>) would eliminate the redundant reads but adds
  complexity. Not needed at current scale; revisit if scan latency becomes a concern.
* Open/closed state tracking: rejected. Maintaining a boolean `isOpen` flag per issue would
  simplify the scan query but adds branching write logic to `KafkaDatabaseConsumer` and is
  vulnerable to out-of-order Kafka events (a `closed` event processed before its `opened`
  counterpart would leave the state permanently wrong). The append-only `IssueEvents` log +
  aggregation approach is correct and sufficient at this scale.

# Progress

## Phase 1 — COMPLETE (commits 60fc74e, 05279d2)
- `IssueEventDTO`: added `issue.id` as `long`, made inner-class getters public, added
  `getIssueId()` / `getRepositoryName()` / `getCreatedAt()` on the outer class
- `ttlConfig`: replaced `@Field("uid")` with `@Id`, added `setUid()`
- `RepositoryMap`: fixed uid list type `List<String>` → `List<Long>`, `addUid(String)` → `addUid(long)`
- `AlertRecord`: new entity in `AlertHistory` collection — `issueId`, `uid`, `alertedAt`

## Phase 2 — next up
Populate `RepositorySubscribers`: extend `FiltersDTO` with `repositories: List<String>`,
extend `createFilters()` to upsert into `RepositorySubscribers` using the existing `$addToSet`
bulk pattern, add `RepositoryMapRepository`.

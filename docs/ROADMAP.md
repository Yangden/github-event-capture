# Roadmap: Pushing This Project Forward in the AI Era

Written: 2026-07-06. Companion to `docs/ARCHITECTURE.md` (current-state review) and
`docs/ANALYSIS.md` (defect inventory H1–H8). This document answers: *where should this project
go, given that we now build software with AI coding agents, and given the owner's goal of going
deep on harness engineering and AI agents?*

The AI factor cuts along two axes, and it is worth keeping them separate:

- **Axis 1 — what the product should become.** GitHub events are the sensory input of AI coding
  agents. A system that captures, stores, and routes them is no longer just a notification demo;
  it is the natural *trigger and observability layer* for agents working on repositories.
- **Axis 2 — how the product should be built.** When AI writes most of the code, the scarce
  resources shift from "typing speed" to *verification, context, and guardrails*. That changes
  which engineering investments pay off — and it re-ranks the existing H1–H8 backlog.

---

## 1. Axis 1: Product Direction — From Notification Demo to Agent Event Layer

### 1.1 Why this repositioning is natural, not forced

Look at what the system already does through an agent-era lens:

| Current framing | Agent-era framing |
|---|---|
| Webhook ingest → Kafka event log | Durable, replayable stream of *everything happening to a repo* — exactly the context an agent needs |
| Inverted-index subscriptions (eventType → uids) | Routing table: *which consumer cares about which event* — where a "consumer" can be an agent, not just an email address |
| TTL stale-issue alert → email a human | Trigger condition → *dispatch an agent* to triage, summarize, or attempt the fix |
| SQS `EventNotificationsQueue` → SES | Generic **action queue** — email is just one action type; "invoke agent," "post GitHub comment," "call MCP tool" are others |

The architecture does not need to change shape to support this. It needs the hardening already
identified in `ANALYSIS.md`, plus a small number of *extension points* (below). That is the
strongest possible position: the pivot is additive.

### 1.2 Concrete product increments (ordered by leverage)

**P1 — LLM event enrichment (third Kafka consumer group).**
Add an `enrichment-consumer` group beside `database-consumer` and `filter-consumer` — the
existing dual-consumer pattern extends to N groups for free. It calls Claude
(`claude-haiku-4-5` for cost; classification is a small-model task) to attach to each issue
event: category, severity estimate, one-line summary, duplicate-candidate flag. Store the
enrichment either on the event document or in a parallel `EventEnrichments` collection.
*This single feature retroactively delivers the descoped original requirements* (label
frequency analysis, high-priority detection) because labels no longer need to be parsed from
GitHub metadata — they are inferred.
Engineering notes: batch where possible, cache by content hash (issue edits re-fire webhooks
with near-identical bodies), make enrichment failures non-blocking (the raw event is already
persisted by the database consumer — enrichment is best-effort by design).

**P2 — Alert emails worth reading.**
`FormatEmail` currently wraps raw JSON in `<p>` tags. Feed the enrichment output (P1) into the
notification path: subject = issue title + severity, body = LLM summary + "open for N days" +
link. Cheap, visible, and exercises the enrichment data end-to-end.

**P3 — Agent dispatch as an action type.**
Generalize `QueueMessageDTO` with an `actionType` field (`email` today). Add an `agent-triage`
action: when the TTL scan finds a stale issue, instead of (or before) emailing, invoke a
headless agent (Claude Agent SDK, or `claude -p` in CI) whose job is: read the issue thread,
summarize state, identify what is blocking, post the summary as an issue comment, and *then*
notify the human with the agent's summary. The human receives "here's why this is stuck," not
"this is old."
This is the first true *harness engineering* artifact in the project: defining an agent's
inputs, tools, permissions, and success criteria, triggered by machine-detected conditions.
Guardrails from day one: agent actions must be idempotent (the `AlertRecord` dedup pattern
already exists — reuse it), rate-limited (the Guava `RateLimiter` pattern already exists), and
budget-capped (new; agents cost real money per invocation, unlike emails).

**P4 — MCP server over the event store.**
Expose the captured data as MCP tools: `query_open_issues(repo, older_than)`,
`get_issue_timeline(issueId)`, `get_event_stats(repo, since)`, `set_subscription(...)`.
Now any MCP-capable agent (Claude Code included) can use this system as its "what's happening
in my repos" sense organ. This also finally creates the missing *read path* over the data
(ARCHITECTURE §3.6): the same queries back a REST API or dashboard later.
This is the highest-signal portfolio piece of the four: "built an MCP server exposing a
Kafka/MongoDB event pipeline to AI agents" is a 2026 skill statement, not a 2023 one.

**P5 — PR events end-to-end (validates generality, feeds agents).**
Add `pull_request` to `EventAccess` + a DTO + collection. This was an original requirement,
it is the event type agents care about most (review requests, CI outcomes), and it is the test
of whether the "generic" pipeline actually generalizes (it has never run a second type
end-to-end). Note the ingest bug first: today an unregistered event type NPEs in the producer
(ARCHITECTURE §3.1) — fixing that is a precondition, not a nice-to-have.

**Deliberately out of scope for now:** multi-tenant SaaS packaging, GitHub App marketplace
distribution, horizontal scaling work. Single-operator, few-repos is the honest current scale;
the architecture holds a 100× margin already.

### 1.3 One sentence of product honesty

Off-the-shelf tools (GitHub native notifications, Slack integrations, Zapier) already do
"email me about events." They do *not* do "maintain a durable, queryable, agent-accessible
event log with LLM enrichment and conditional agent dispatch under my control." The roadmap
above moves the project from competing with the former to being an instance of the latter.

---

## 2. Axis 2: How AI Changes the Way This Project Is Built

The recent git history already shows agent-assisted development (phased design doc → guard
clause tests → `docs/ANALYSIS.md`). The lesson of that experience generalizes:

> **When code generation is cheap, the binding constraints are verification, context, and
> guardrails. Invest where the constraint is.**

### 2.1 Verification is now the bottleneck — re-rank the backlog accordingly

An agent can produce a plausible fix for H1–H8 in minutes each. What it *cannot* do today is
prove the fix works, because:

- **CI runs zero tests** (`maven.yml` runs only checkstyle). Nothing gates a regression, human-
  or agent-authored. *Single highest-leverage change in the repository:* make CI run
  `mvn test`, with unit tests (Mockito tier) always-on.
- **Integration tests are not machine-runnable.** `SQStest` hits real AWS with a personal STS
  role; `MongoTemplateServiceTest` needs a hand-started docker-compose. An agent (or CI) cannot
  execute either autonomously. Migrate to **Testcontainers** (MongoDB, Kafka, LocalStack
  modules) so `mvn verify` brings up everything it needs, runs, and tears down. This converts
  the integration suite from "manual scripts" to "agent-executable oracle."
- **No end-to-end smoke test exists.** Define one: POST a signed sample webhook → assert the
  event lands in Mongo → assert a message reaches (LocalStack) SQS → assert the poller consumes
  it. One command. This becomes the `verify` skill for the repo — the thing an agent runs after
  every change to prove the pipeline still flows.

Rule of thumb for every future PR, agent-authored or not: *the diff must be checkable by a
command, not by re-reading the code.* Where that command doesn't exist yet, building it comes
first.

### 2.2 Context is an engineered artifact — keep feeding the machine that fed you

This project's docs (`designdoc.md`, `ANALYSIS.md`, `CLAUDE.md`) are why agent sessions on it
are productive: the agent starts each session knowing the architecture, the known bugs, and the
conventions. Treat that as infrastructure with a maintenance contract:

- **Definition of done includes doc deltas.** Every feature phase updates `CLAUDE.md` (commands,
  new components) and the entity/collection tables in `RESEARCH.md`. The stale "ttlConfig is a
  placeholder" flag that survived three phases of implementation is the cautionary example —
  stale context actively misleads the next agent session.
- **Design docs before implementation, always.** The alert feature's designdoc → phases →
  tests-per-phase workflow measurably outperformed the earlier ad-hoc work (the quality
  gradient is visible in the code — ARCHITECTURE §2.4). Keep the pattern: for each roadmap item
  above, a short design doc with *rejected alternatives recorded*, then phased commits.
- **Conventions are context too.** Naming violations (`ttlConfig`, `getUidFromSeucrityContext`,
  `applicationl.yml`) are no longer just style debt — they are noise injected into every future
  agent's context window, and each one risks being faithfully imitated in new code. Fix them
  once; agents make the mechanical rename across all callers cheap.

### 2.3 Guardrails: agents raise the stakes on existing security debt

- **Secrets in the repo are now agent-readable.** Every plaintext credential
  (`application-prod.yml`, `application-prod.properties`, dev properties, docker-compose) is
  read into the context of every AI session on this repo and can surface in generated docs,
  PR descriptions, or logs. H6 was "high" for humans; with agents in the loop it is *urgent*,
  and it comes with a step often skipped: **rotate** the exposed Confluent, Atlas, and RDS
  credentials — moving them to env vars does not un-leak them from git history.
- **Webhook signature verification (ARCHITECTURE §3.2) becomes load-bearing** the moment P3
  ships: an unauthenticated `/webhook` that can trigger *agent actions* is an unauthenticated
  remote agent-invocation endpoint. Verify `X-Hub-Signature-256` before any other roadmap work
  touches the ingest path.
- **Agent actions need budgets and kill switches.** Emails mis-sent cost embarrassment; agent
  runs mis-triggered cost money and can write to GitHub. P3's design doc must specify: per-day
  invocation caps, an explicit allowlist of repos/actions, dry-run mode as the default, and an
  operator off-switch (a config flag, checked per dispatch).
- **Review gates stay human.** `/code-review` and `/security-review` before merges; the
  existing checkstyle gate stays. Agents draft; verification pipelines and a human with a
  ranked findings list decide.

### 2.4 The skill-building frame (why this project, for this owner)

The stated goal is to go deep on AI-era engineering: harness engineering and agents. This
project is a better vehicle for that than a greenfield "AI app" would be, precisely because it
is a real, flawed, distributed system:

- **Harness engineering is verification engineering.** Building the Testcontainers suite, the
  e2e smoke command, and the CI gates *is* harness work — the same skills that make agent loops
  reliable (define the oracle, make it fast, make it runnable headlessly).
- **Agent dispatch (P3) teaches the hard parts** — idempotency, budgeting, permissioning,
  observability of agent actions — on a system you fully control, against your own repos.
- **MCP (P4) teaches the interface discipline** — designing tools an LLM can use correctly is
  API design under adversarial ambiguity.
- The existing observability stack extends naturally to **agent telemetry**: count agent
  invocations, token spend, action outcomes in the same Prometheus/Grafana setup that already
  tracks DB throughput. "Grafana dashboard of my agents' behavior" closes the loop.

---

## 3. Same Problems, Escalated Stakes

`ARCHITECTURE.md` §4 distills the existing defects into five underlying engineering problems
(P-A through P-E). The AI-era roadmap does not introduce a new problem space — it *re-raises
the stakes on the same five problems*. This table is the bridge between the two documents, and
the reason the phase ordering in §4 is what it is:

| Problem (ARCHITECTURE §4) | Stakes today (email pipeline) | Stakes after Phases 3–4 (agent layer) | Where addressed |
|---|---|---|---|
| **P-A Delivery semantics undecided** | Lost events; duplicate emails (annoying) | Duplicate *agent runs*: real money per invocation, duplicate GitHub comments/actions in the world | Phase 2 (dedup, commit-after-write, DLQ) — deliberately gates Phase 4 |
| **P-B Open ingestion trust boundary** | Fake events pollute the DB and spam subscribers | Unauthenticated `/webhook` becomes an unauthenticated **remote agent-invocation endpoint** | Phase 0.2 (HMAC verification) — before any feature work touches ingest |
| **P-C Blocking I/O in stream consumers** | Sync SQS call throttled the pipeline (already paid for and fixed) | LLM calls (100s of ms–seconds) in-band in the enrichment consumer would recreate the identical failure | §5 decision seed: enrichment is out-of-band from the start |
| **P-D Shared mutable state / multi-write consistency** | Silent cross-collection corruption; inverted indexes drift from `Filters` | Agents making dispatch decisions on corrupted/drifted subscription data act on wrong targets | Phase 2.1, 2.4 |
| **P-E No machine-checkable verification** | Regressions land silently; only humans can test | Agents generate fixes in minutes but nothing can *prove* them; code generation accelerates while verification stays manual — the binding constraint of §2.1 | Phase 1 (CI tests, Testcontainers, e2e oracle) — the single highest-leverage phase |

One problem class is genuinely **new** rather than an escalation: **guardrails for autonomous
actions**. An email system never needed a spend budget, a dry-run mode, or a kill switch; an
agent-dispatch system needs all three before its first real invocation (Phase 4.1). Watch for
it becoming P-F: unlike P-A–P-E there is no existing instance in the codebase to learn from,
so it must be designed in up front rather than discovered by load test.

---

## 4. Phased Plan

Ordering principle: *safety → verifiability → reliability → product*. Each phase is sized to
the established workflow (design doc → phases → tests → doc deltas).

### Phase 0 — Stop the bleeding (security) — *days*
1. Rotate all exposed credentials (Confluent, Atlas, RDS, dev Postgres); move config to env
   vars / Secrets Manager (extends existing `SecretManager` wiring). [ANALYSIS H6, wider]
2. Verify `X-Hub-Signature-256` on `/webhook` (shared secret via Secrets Manager). [NEW]
3. Remove JWT secret logging; fail fast on secret-fetch failure. [H3, H4]
4. `debug=true` off; actuator exposure restricted to `health,info,prometheus`. [H5, medium]

### Phase 1 — Make correctness checkable — *1–2 weeks*
1. CI runs `mvn test` (unit tier) on every push/PR.
2. Testcontainers migration: Mongo, Kafka, LocalStack; `mvn verify` = full integration tier,
   headless.
3. E2E smoke test: signed webhook → Mongo → SQS → poller, one command; document it in
   `CLAUDE.md` as the repo's verify entry point.
4. Fix-with-tests the known crashers, now that tests gate them: `FilteredEventConsumer`
   `Optional.get()` [H2], producer NPE on unknown event type [NEW], `@RequestMapping` on
   `/api/filters` [H7].

### Phase 2 — Reliability of the pipeline — *1–2 weeks*
1. Refactor `MongoTemplateService` to stateless (parameterized `bulkWrite`). [H1]
2. Delete-after-send in the notification worker (currently delete-before-send — lost-email
   window); add SQS message-id dedup record. [NEW + flag #13]
3. DLQ topic for ingest failures; manual-ack / commit-after-write on the database consumer.
   [flags #2, #7]
4. MongoDB indexes on all queried fields [H8]; `Filters` upsert-by-uid to kill duplicate
   documents [NEW]; `clearAllFilters` cleans both inverted indexes [medium].
5. Externalize hardcoded queue URLs / region / sender email into profile config; delete the
   dead `my-dev-profile` code paths.

### Phase 3 — First AI increments — *2–3 weeks*
1. P1 enrichment consumer (Claude API, Haiku-class model, content-hash cache, best-effort
   semantics) + enrichment fields in the alert email (P2).
2. P5 `pull_request` end-to-end, proving pipeline generality.
3. Prometheus counters for LLM calls, tokens, cache hits — enrichment gets the same
   observability as the databases did.

### Phase 4 — Agent layer — *3–4 weeks, the differentiating work*
1. P3 agent dispatch: `actionType` on `QueueMessageDTO`, `agent-triage` worker with dry-run
   default, budget caps, dedup via `AlertRecord` pattern, kill switch. Design doc first;
   the permissioning/guardrail section is the deliverable as much as the code.
2. P4 MCP server over the event store (read tools first; subscription-write tools once auth
   story for MCP clients is settled).
3. Agent telemetry in Grafana.

### Phase 5 — Positioning & polish — *ongoing*
1. README that tells the story (currently one line): architecture diagram, the load-test
   bottleneck narrative, the agent layer. This repo is also a portfolio artifact; make the
   engineering visible.
2. GitHub App packaging (per-installation webhooks + tokens) if multi-repo/multi-user use
   materializes; not before.

---

## 5. Decision Log Seeds

Questions each later phase's design doc must answer (recording them now so they aren't
re-litigated from scratch):

- **P1:** enrich in-band (block the consumer per event) vs. out-of-band (enqueue enrichment
  jobs)? Recommendation: out-of-band from the start — LLM latency (hundreds of ms to seconds)
  inside a Kafka listener recreates the exact sync-SQS mistake this project already paid to
  learn.
- **P3:** where do agents run? (CI runner via `claude -p`, a local always-on worker, or cloud
  sessions.) Cost model and secret exposure differ sharply; dry-run mode makes the choice
  reversible.
- **P4:** MCP transport and auth — local stdio for the owner's own use first (zero auth
  surface), HTTP+auth only if it ever serves others.
- **Cross-cutting:** when does the `EventAccess` static map become a Spring-managed registry?
  Answer: the moment a third consumer of it appears (the enrichment consumer is the likely
  trigger) — not before.

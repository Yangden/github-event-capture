# GitHub Event Capture — Architecture Research

## 1. End-to-End Data Flow

```
GitHub Webhook POST /webhook (X-GitHub-EVENT header)
        ↓
[WebHookController] → filter "ping", return HTTP 202 immediately
        ↓
[EventProducerImpl] → deserialize JSON to DTO via EventAccess map → Kafka topic "github-event-topic" (key = eventType)
        ↓
Two independent Kafka consumer groups reading the same topic:
    ├─ groupId="database-consumer" [KafkaDatabaseConsumer]
    │       → persist raw event to MongoDB collection (PushEvents / IssueEvents)
    └─ groupId="filter-consumer"  [FilteredEventConsumer]
            → lookup EventTypeSubscribers (inverted index in MongoDB)
            → build QueueMessageDTO for each subscriber
            → batch send to AWS SQS "EventNotificationsQueue"
                    ↓
        [EventNotificationImplConcurrency] — 10-thread pool + Guava RateLimiter (10 req/s)
                → poll SQS (20-second long-poll, max 10 messages)
                → send email via AWS SES (SendEmailAsync)
                → delete message from SQS after dispatch
                    ↓
                [User's inbox]
```

Prometheus scrapes `/actuator/prometheus` every 5 s; Grafana (port 3000) visualises.

---

## 2. Package Map

### `controller`
| Class | Endpoint | Responsibility |
|---|---|---|
| `WebHookController` | `POST /webhook` | Accepts GitHub webhook payloads, delegates to `EventProducerImpl` |
| `EventFilterController` | `POST /api/filters/create`, `POST /api/filters/deleteAll` | CRUD for user subscription filters (JWT-protected) |
| `AuthController` | `POST /register`, `POST /login` | Registration + JWT-based login |

### `service` (interfaces) + `service/impl`
| Class | Role |
|---|---|
| `EventProducerImpl` | Deserializes payload using `EventAccess.getEventObj(eventType)`, sends to Kafka |
| `KafkaDatabaseConsumer` | Kafka listener — persists raw events to MongoDB |
| `FilteredEventConsumer` | Kafka listener — queries inverted index, batch-pushes to SQS |
| `AsyncQueueserviceImpl` | Async SQS: `batchSend`, `receiveMessage` (long-poll), `deleteMessage` |
| `EventNotificationImplConcurrency` | Thread-pool SQS poller + SES email dispatch; `@PostConstruct` starts loop; `DisposableBean` for graceful shutdown |
| `EmailSenderServiceImpl` | Wraps AWS SES sync + async clients |
| `EventFiltersServiceImpl` | Filter CRUD in MongoDB + maintains EventTypeSubscribers inverted index |
| `AuthServiceImpl` | Register (BCrypt + PostgreSQL), login (verify + generate JWT) |
| `JwtService` | HS512 JWT generation/validation; secret pulled from AWS Secrets Manager |
| `SecretManager` | Fetches secret `key-for-jwt` from AWS Secrets Manager |
| `MonitorServiceImpl` | Typed wrappers around Micrometer counters for DB and SQS throughput |
| `MongoTemplateService` | Bulk MongoDB operations; stateful `domainClass` field (see §6 flag #14) |
| `QueueServiceImpl` | Synchronous SQS client — used only in local/test context |

### `entity` / `entity/dto`
| Class | Store | Notes |
|---|---|---|
| `User` | PostgreSQL | id, email (unique), password (BCrypt) |
| `Event` | MongoDB (base) | Holds `@Id`; subclassed by DTOs |
| `PushEventDTO` | MongoDB `PushEvents` | repository, pusher, commits |
| `IssueEventDTO` | MongoDB `IssueEvents` | action, issueInfo |
| `Filters` | MongoDB `Filters` | uid + set of subscribed eventType strings |
| `EventTypeMap` | MongoDB `EventTypeSubscribers` | eventType → List<Long uids> (inverted index) |
| `ttlConfig` | MongoDB `ttlConfig` | uid, day, hour — entity exists, no consumption logic yet |
| `QueueMessageDTO` | (SQS envelope) | event JSON, email, eventType |
| `UserDTO` / `FiltersDTO` | (request DTOs) | Input objects with no validation annotations |

### `repository`
| Interface | Backend | Notable |
|---|---|---|
| `UserRepository` | PostgreSQL (JPA) | `findByEmail`, `findEmailsByUids` (@Query + @Counted) |
| `FilterRepository` | MongoDB | `findByUserId` (@Query JSON), `deleteByUid` |
| `EventTypeMapRepository` | MongoDB | `findByEventType`, `addUid` ($addToSet @Update) |
| `EventRepository` | MongoDB | `save` (@Counted) |

### `security`
- `JwtAuthenticationFilter` — `OncePerRequestFilter`; extracts Bearer token, calls `JwtService`, sets `SecurityContext`
- `CustomUserDetail` / `CustomUserDetailService` — wraps `uid` + email as `UserDetails`
- `SecurityContextService` — static helper; note typo: `getUidFromSeucrityContext()`

### `configuration`
- `AwsCredentialsConfig` — STS `AssumeRole` for all AWS clients (role ARN: `arn:aws:iam::038462794128:role/github_event_capture`, 3600 s)
- `SqsConfiguration` — four SQS beans: sync/async × local/cloud, HTTP pool: 200 max concurrency
- `SecurityConfiguration` — CSRF off, stateless sessions, `/api/**` requires auth; **`debug=true` left on**
- `CounterAspectConfig` — registers `CountedAspect` bean for `@Counted` AOP
- `OpenApiConfig` — Springdoc/Swagger with JWT bearer auth scheme

### `utils`
- `EventAccess` — static `HashMap<String, Class>`: `"issues"→IssueEventDTO`, `"push"→PushEventDTO`
- `Result<T>` — generic success/fail wrapper returned by all service methods
- `PasswordUtil` — BCrypt encode + verify
- `FormatEmail` — wraps event JSON in HTML body for SES emails
- `HttpResponseMsg` — string constants for response messages

---

## 3. External Dependencies

### Kafka
| Property | Dev | Prod |
|---|---|---|
| Bootstrap | `localhost:9092` | Confluent Cloud `pkc-p11xm.us-east-1.aws.confluent.cloud:9092` |
| Auth | none | SASL_SSL / PLAIN |
| Topic | `github-event-topic` | same |
| Producer acks | default | `all`, retries=3 |
| Consumer reset | `earliest` | `earliest` |

Credentials are hardcoded in `application-prod.yml` (see §6 flag #1).

### AWS SQS
- **Prod queue:** `https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue`
- **Local queue:** `http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue`
- Async batch manager used for sends; 20-second long-poll for receives
- HTTP client: `AwsCrtAsyncHttpClient`, max concurrency 200

### AWS SES
- Region: `US_EAST_1`
- Sender hardcoded to `yangdeng2001@gmail.com`
- Both sync (`SesClient`) and async (`SesAsyncClient`) clients instantiated
- Async path used in `EventNotificationImplConcurrency`

### AWS Secrets Manager
- Secret: `key-for-jwt`, field: `github-event-capture-jwt`
- Used exclusively by `JwtService` for the HS512 signing key

### AWS STS
- All AWS service credentials flow through `AwsCredentialsConfig.provideCredential()`
- Bootstrapped from environment variables → STS AssumeRole → temporary credentials
- Auto-refreshed before expiration; no fallback on STS failure

### MongoDB
| Environment | URI |
|---|---|
| Dev | `mongodb://localhost:27017/test` |
| Prod | MongoDB Atlas `eventstorage.xuij4wc.mongodb.net`, db `github-event-capture` |

Collections: `PushEvents`, `IssueEvents`, `Filters`, `EventTypeSubscribers`, `ttlConfig`

### PostgreSQL / RDS
| Environment | JDBC URL |
|---|---|
| Dev | `jdbc:postgresql://localhost:5432/github_event_capture` |
| Prod | AWS RDS `eventcap.cn2wu8iwc8yh.us-east-1.rds.amazonaws.com:5432` |

Hibernate DDL: `update` (auto-creates/alters tables). Credentials in plaintext in properties files.

### Prometheus & Grafana
- Prometheus scrapes:
  1. Self at `localhost:9090`
  2. App at `host.docker.internal:8080/actuator/prometheus` (5 s interval)
  3. Kafka exporter at `kafka-exporter:9308` (5 s interval)
  4. Router telemetry at `host.docker.internal:8000`
- Grafana: port 3000, admin/admin, dashboard provisioned from `src/monitoring/grafana-dashboard.json`
- Metrics: counter `database.throughput` (tags: `db`, `metrics`) and `event.count` (tag: `metrics`)
- `management.endpoints.web.exposure.include=*` — all actuator endpoints exposed

### LocalStack
- Port 4566, mocks SQS locally
- Init script `local-dev/aws/init-aws.sh` creates `local-demo-queue` on startup

---

## 4. Key Design Decisions

### Event Type Polymorphism via `EventAccess`
Static `HashMap<String, Class>` maps GitHub event type header to DTO class. Both producer and consumer share this map for dynamic deserialization. Adding a new event type = create DTO + add one entry.

### Dual Kafka Consumer Groups
Both `database-consumer` and `filter-consumer` independently read the same topic. Persistence and fan-out concerns are decoupled — each can be scaled or fail independently.

### Inverted Index for Subscriber Lookup (`EventTypeSubscribers`)
`EventFiltersServiceImpl.createFilters()` atomically writes to both `Filters` (user → eventTypes) and `EventTypeSubscribers` (eventType → uids) using MongoDB `$addToSet` bulk upserts. Lookup is O(1) by event type.

### Concurrent Email Dispatch
`EventNotificationImplConcurrency`: 10-thread `ExecutorService`, Guava `RateLimiter` (10/s), exponential backoff on consecutive errors (cap 3 errors → 30 s pause), `DisposableBean` for 30 s graceful shutdown.

### Async SQS via `CompletableFuture`
`AsyncQueueserviceImpl` wraps `SqsAsyncBatchManager` for non-blocking sends and receives. Email sending is also async via `SesAsyncClient`.

### STS AssumeRole for Credential Hygiene
No long-lived AWS credentials in code. `EnvironmentVariableCredentialsProvider` bootstraps → STS issues temporary 3600 s credentials → all downstream AWS clients use the same `AwsCredentialsProvider` bean.

### JWT + Stateless Sessions
HS512-signed JWTs; secret managed in Secrets Manager; no server-side sessions. `JwtAuthenticationFilter` validates on every request, populates `SecurityContext` with `CustomUserDetail` carrying `uid`.

### Polyglot Persistence
PostgreSQL for relational user identity (ACID, strong consistency); MongoDB for flexible event documents (schema-less, horizontal scale). Two separate connection pools managed by Spring.

### Micrometer AOP Metrics
`@Counted` annotations on repository methods auto-incremented by `CountedAspect`. `MonitorServiceImpl` provides typed wrappers for manual counter increments with consistent tag schemes.

---

## 5. Build, Run, and Test

### Maven
```bash
mvn clean package          # build JAR
mvn spring-boot:run        # run locally (default profile)
mvn test                   # run test suite
mvn validate               # checkstyle (google_checks.xml, fails on error)
```

### Docker Compose (local dev)
```bash
docker-compose -f local-dev/docker-compose.yml up -d
# Starts: zookeeper:2181, kafka:9092, mongodb:27017, postgres:5432,
#         prometheus:9090, grafana:3000, kafka-exporter:9308, localstack:4566
docker-compose -f local-dev/docker-compose.yml down
```

LocalStack auto-runs `local-dev/aws/init-aws.sh` on startup, creating `local-demo-queue`.

### Application Profiles
| Profile | Activation | Key differences |
|---|---|---|
| default | (none) | localhost services, security disabled, LocalStack SQS |
| `prod` | `spring.profiles.active=prod` | Confluent Kafka, Atlas MongoDB, RDS Postgres, real AWS SQS/SES, security enabled |

### Test Structure
```
src/test/.../
├── GithubEventCaptureApplicationTests  — context load
├── controller/AuthControllerTest       — JWT filter, login/register
├── service/AuthServiceTest             — service-layer auth logic
├── service/EmailSenderTest             — SES integration
├── service/MongoTemplateServiceTest    — bulk write operations
├── service/SQStest                     — SQS send/receive (LocalStack)
├── repository/UserRepositoryTest       — JPA queries
├── repository/EventTypeMapTest         — MongoDB filter repo
└── dbtemplate/MongoTemplateTest        — template bulk ops
```

Test config: `SQSTestConfig.java` wires LocalStack SQS bean for tests.

---

## 6. Flags & Non-Obvious Notes

1. **Hardcoded credentials** — MongoDB Atlas URI, RDS password, and Confluent Cloud SASL credentials are in plaintext in `application-prod.properties` / `application-prod.yml`. Anyone with repo access has full database access.

2. **Silent event loss in producer** — `EventProducerImpl` catches `JsonProcessingException`, logs it, and drops the event. No DLQ, no retry, no alert.

3. **`Optional.get()` without presence check** — `FilteredEventConsumer` calls `mapContent.get()` directly; throws `NoSuchElementException` if no subscriber has ever registered for that event type, crashing the Kafka consumer.

4. **Typo in method name** — `SecurityContextService.getUidFromSeucrityContext()` (should be `Security`).

5. **`debug=true` in `@EnableWebSecurity`** — verbose security logs leak request details in production.

6. **Kafka `auto-offset-reset=earliest` in prod** — first deploy (or new consumer group) replays entire event history, potentially flooding SQS and sending duplicate emails to all users.

7. **No transaction between Kafka offset commit and MongoDB write** — if MongoDB write fails after Kafka commits offset, event is silently lost with no replay path.

8. **`MongoTemplateService` is stateful and not thread-safe** — `setDomainClass()` mutates shared state; race condition if two threads call `bulkWrite()` concurrently for different collections.

9. **All actuator endpoints exposed** (`management.endpoints.web.exposure.include=*`) — `/actuator/env`, `/actuator/beans`, `/actuator/mappings` etc. expose internal state publicly.

10. **No input validation on DTOs** — `FiltersDTO`, `UserDTO` have no `@Valid`/`@NotEmpty`/`@Email` constraints; invalid data passes straight to service logic.

11. **`ttlConfig` entity is a placeholder** — collection schema defined (uid, day, hour), no read or enforcement logic yet; planned for future implementation by the owner.

12. **Sender email hardcoded** — `private static final String senderEmail = "yangdeng2001@gmail.com"` in `EventNotificationImplConcurrency`; not externalized to config.

13. **No idempotency/deduplication** — if a Kafka message is consumed twice or SQS visibility timeout fires before delete, users receive duplicate emails.

14. **Spring Security `debug=true`** — same as flag #5; should be profile-gated.

15. **`applicationl.yml` filename typo** — the Kafka-local config file has a stray `l` in the name; could be confusing and is not referenced by a standard Spring profile name.

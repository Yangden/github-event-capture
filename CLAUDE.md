# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./mvnw clean package
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=AuthServiceTest

# Run application (dev profile — local Kafka + LocalStack SQS)
./mvnw spring-boot:run

# Run with production profile (Confluent Cloud Kafka + AWS)
java -jar target/github-event-capture-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Start all local dependencies (Kafka, Zookeeper, MongoDB, PostgreSQL, Prometheus, Grafana, LocalStack)
cd local-dev && docker-compose up -d
```

Checkstyle (Google style) runs automatically on the `validate` phase. Fix violations before submitting.

## Architecture Overview

This is a GitHub webhook event capture and notification system. The core data flow:

```
GitHub → POST /webhook → Kafka topic "github-event-topic"
                              ↓                    ↓
                    KafkaDatabaseConsumer    FilteredEventConsumer
                    (group: database-consumer) (group: filter-consumer)
                              ↓                    ↓
                    MongoDB raw event      Query EventTypeMap (inverted index)
                    storage                Build QueueMessageDTO per subscriber
                                                   ↓
                                          AWS SQS "EventNotificationsQueue"
                                                   ↓
                                          EventNotificationImplConcurrency
                                          (10-thread pool + Guava RateLimiter)
                                                   ↓
                                          AWS SES → subscriber email
```

### Dual-Database Design

- **PostgreSQL** — user credentials (`User` entity, JPA/Hibernate)
- **MongoDB** — everything else: raw events (`PushEvents`/`IssueEvents` collections), filter subscriptions (`Filters`), and the inverted index (`EventTypeMap`: eventType → List\<uid\>)

### Key Architectural Patterns

**EventAccess registry** (`utils/EventAccess.java`): maps Kafka message keys (e.g., `"push"`, `"issues"`) to concrete DTO classes for polymorphic deserialization. Add new event types here first.

**Inverted index for subscriptions**: `EventTypeMap` stores eventType → List\<uid\> so `FilteredEventConsumer` can look up all subscribers for an event type in O(1) instead of scanning all `Filters` documents.

**Dual SQS clients**: `QueueServiceImpl` uses the synchronous SQS client; `AsyncQueueserviceImpl` uses `SqsAsyncBatchManager`. These are not interchangeable — the async batch manager is for high-throughput sends, the sync client is for polling/deleting.

**MongoTemplateService is stateful**: it stores `domainClass` and `BulkOperations` as instance fields. Do not call it concurrently from multiple threads without coordination.

**Metrics via Micrometer**: `@Counted` AOP on repository methods (see `CounterAspectConfig`). `MonitorServiceImpl` wraps typed counters. Exposed at `/actuator/prometheus`.

### Security / Auth

JWT tokens (HS512, 3600s TTL) are issued by `AuthController`. The signing secret is fetched from AWS Secrets Manager (key: `"key-for-jwt"`) by `JwtService`. In dev, this requires AWS credentials or a LocalStack override.

`SecurityContextService.getUidFromSeucrityContext()` — note the typo in the method name; it exists as-is throughout the codebase, do not rename without updating all callers.

## Environment Profiles

| Profile | Kafka | SQS | AWS Credentials |
|---------|-------|-----|-----------------|
| default (dev) | `localhost:9092` | LocalStack `http://localhost:4566` | Static/local |
| prod | Confluent Cloud (SASL_SSL) | AWS CloudSQS | STS AssumeRole via `AwsCredentialsConfig` |

Switch profiles with `--spring.profiles.active=prod`. Dev uses `application.properties`; prod overlays `application-prod.yml`.

## Testing

Two tiers:

- **Pure unit tests** (e.g. `IssueAlertServiceImplTest`) — `@ExtendWith(MockitoExtension.class)`, all collaborators mocked, no Spring context. Run anywhere, no external services required.
- **Integration tests** (e.g. `IssueAlertIntegrationTest`, `MongoTemplateServiceTest`) — real local infrastructure via `cd local-dev && docker-compose up -d`. `IssueAlertIntegrationTest` specifically needs MongoDB (`localhost:27017`) and LocalStack (`http://localhost:4566`) up; it overrides the `sqsAsyncClientCloud` bean to point at LocalStack and uses an H2 in-memory database in place of PostgreSQL.

## Orchestration

You (Fable) are the orchestrator: plan, decompose, and verify. Delegate execution
to subagents only when I explicitly ask ("delegate this", "use the implementer",
"fan this out"). Otherwise, do the work directly in this session.

When delegating: give the subagent a self-contained brief (objective, files,
interface, constraints) — it does not see this conversation.
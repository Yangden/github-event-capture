package com.example.github_event_capture.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.github_event_capture.TestConfig.LocalStackSqsTestConfig;
import com.example.github_event_capture.entity.Filters;
import com.example.github_event_capture.entity.RepositoryMap;
import com.example.github_event_capture.entity.User;
import com.example.github_event_capture.entity.dto.IssueEventDTO;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.entity.ttlConfig;
import com.example.github_event_capture.repository.AlertRecordRepository;
import com.example.github_event_capture.repository.FilterRepository;
import com.example.github_event_capture.repository.RepositoryMapRepository;
import com.example.github_event_capture.repository.TtlConfigRepository;
import com.example.github_event_capture.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

// Full scan-to-SQS integration test for IssueAlertServiceImpl.scanAndAlert(), per
// designdoc.md "Phase 6 Integration Test" and plan.md "Plan 1".
//
// Requires `cd local-dev && docker-compose up -d` (MongoDB on 27017, LocalStack on 4566) to be
// running before this test executes.
//
// Queue URL resolution (plan.md Plan 1 Step 0 was skipped — AsyncQueueserviceImpl's queue URL
// stays hardcoded to https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue):
// this test overrides the "sqsAsyncClientCloud" bean (see LocalStackSqsTestConfig) to point at
// LocalStack, and uses the LocalStack multi-account trick — access key id "038462794128" (the
// 12-digit account segment of the hardcoded URL) with secret "test" — for both that bean and
// this test's own SqsClient, so the AWS-shaped queue URL resolves against the LocalStack queue
// this test creates. Verified manually against running LocalStack before writing this class.
//
// CRITICAL history: a plain @Bean-based override in LocalStackSqsTestConfig did NOT reliably win
// against SqsConfiguration's production "sqsAsyncClientCloud" bean (registration order between
// this test's @Import and the app's component-scanned production config is not guaranteed), so
// scanAndAlert() was silently sending real alert messages to the real production AWS SQS queue
// during test runs. Fixed by having LocalStackSqsTestConfig replace the bean definition via a
// BeanDefinitionRegistryPostProcessor instead, which is order-independent. See that class's
// header comment for the full analysis.
//
// Note: IssueEventDTO seeding below explicitly registers JavaTimeModule on its ObjectMapper,
// matching KafkaDatabaseConsumer's production ObjectMapper (also registers JavaTimeModule),
// so IssueInfo.createdAt (java.time.Instant) deserializes the same way in both places.
//
// Field-path fix history: IssueAlertServiceImpl's aggregation groups on "issueInfo.eventId".
// IssueEventDTO's nested issue-id field was previously named "id", which Spring Data MongoDB's
// default convention silently remaps to the document field "_id" on any property literally
// named "id" (even nested/embedded), so the persisted field path did not match
// "issueInfo.id" and the aggregation crashed. The field was renamed to "eventId" (keeping
// @JsonProperty("id") for webhook JSON binding) and the aggregation now groups on
// "issueInfo.eventId", which lines up with what's actually persisted. Fixed in src/main
// (commit b465480); all scenarios below are enabled.
// @AutoConfigureObservability: Spring Boot Test disables metrics export by default for
// @SpringBootTest (via its built-in DisableObservabilityContextCustomizer), which leaves no
// PrometheusMeterRegistry bean in the context. MonitorServiceImpl has a hard constructor
// dependency on PrometheusMeterRegistry, so without this annotation ANY full @SpringBootTest in
// this repo fails to start (reproduced independently on the existing, unmodified
// GithubEventCaptureApplicationTests#contextLoads — a pre-existing repo-wide issue, not
// introduced here). Re-enabling observability autoconfiguration is the test-only fix.
@AutoConfigureObservability
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:issuealertintegration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.mongodb.uri=mongodb://localhost:27017/issueAlertIntegrationTest",
        "spring.kafka.listener.auto-startup=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(LocalStackSqsTestConfig.class)
public class IssueAlertIntegrationTest {

    private static final String QUEUE_NAME = "EventNotificationsQueue";
    private static final String QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";

    private static SqsClient testSqsClient;

    // Prevents EventNotificationImplConcurrency's @PostConstruct background poller from racing
    // with this test for messages on the (now LocalStack-backed) queue — it shares the same
    // AsyncQueueserviceImpl/queue as the code under test and would otherwise drain messages
    // before assertions run.
    @MockBean
    private EventNotificationImplConcurrency eventNotificationImplConcurrency;

    // Avoids a real AWS Secrets Manager call from JwtService's constructor during context
    // startup.
    @MockBean
    private SecretManager secretManager;

    @Autowired
    private IssueAlertServiceImpl issueAlertService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TtlConfigRepository ttlConfigRepository;

    @Autowired
    private RepositoryMapRepository repositoryMapRepository;

    @Autowired
    private FilterRepository filterRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper seedMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ObjectMapper readMapper = new ObjectMapper();

    @BeforeAll
    static void setUpQueue() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                LocalStackSqsTestConfig.ACCOUNT_ACCESS_KEY_ID,
                LocalStackSqsTestConfig.ACCOUNT_SECRET_ACCESS_KEY);
        testSqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(LocalStackSqsTestConfig.LOCALSTACK_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        try {
            testSqsClient.createQueue(
                    CreateQueueRequest.builder().queueName(QUEUE_NAME).build());
        } catch (QueueNameExistsException alreadyExists) {
            // queue already created by a previous run — fine.
        }
    }

    @AfterAll
    static void tearDownQueue() {
        if (testSqsClient != null) {
            testSqsClient.close();
        }
    }

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), "IssueEvents");
        ttlConfigRepository.deleteAll();
        filterRepository.deleteAll();
        repositoryMapRepository.deleteAll();
        alertRecordRepository.deleteAll();
        userRepository.deleteAll();
        drainQueue();
    }

    // purgeQueue has a ~60s cooldown on repeated calls and is flaky on LocalStack — drain via a
    // plain receive/delete loop instead.
    private void drainQueue() {
        List<Message> messages;
        do {
            messages = testSqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();
            for (Message message : messages) {
                testSqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        } while (!messages.isEmpty());
    }

    // batchManager.sendMessage buffers (flushes on batch size or ~200ms). Long-poll so buffered
    // sends have time to land.
    private List<Message> receiveMessages(int waitTimeSeconds) {
        return testSqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(waitTimeSeconds)
                .build()).messages();
    }

    private void deleteMessage(Message message) {
        testSqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    // --- Seeding helpers ---

    // Builds a minimal GitHub issue webhook payload and deserializes it exactly like
    // KafkaDatabaseConsumer's production write path (mongoTemplate.save() on the resulting
    // IssueEventDTO), so document shape/field-name mapping is exercised honestly.
    private void seedIssueEvent(long issueId, String action, Instant createdAt, String repoName)
            throws Exception {
        String json = String.format(
                "{\"action\":\"%s\",\"issue\":{\"id\":%d,\"state\":\"open\",\"body\":\"body\","
                        + "\"created_at\":\"%s\"},\"repository\":{\"name\":\"%s\"}}",
                action, issueId, createdAt.toString(), repoName);
        IssueEventDTO dto = seedMapper.readValue(json, IssueEventDTO.class);
        mongoTemplate.save(dto);
    }

    private void seedTtl(long uid, int day, int hour) {
        ttlConfig config = new ttlConfig();
        config.setUid(uid);
        config.setDay(day);
        config.setHour(hour);
        ttlConfigRepository.save(config);
    }

    private void seedRepoMap(String repository, long... uids) {
        RepositoryMap map = new RepositoryMap();
        map.setRepository(repository);
        for (long uid : uids) {
            map.addUid(uid);
        }
        repositoryMapRepository.save(map);
    }

    private void seedFilters(long uid, String... eventTypes) {
        Filters filters = new Filters();
        filters.setUid(uid);
        filters.setEventTypes(new HashSet<>(List.of(eventTypes)));
        filterRepository.save(filters);
    }

    // Persists a User row in H2 and returns the generated id, so callers can line up ttlConfig /
    // RepositoryMap / Filters uids with it.
    private long seedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("password");
        return userRepository.save(user).getId();
    }

    // Full subscriber setup: user + repo subscription + "issues" filter + TTL config.
    private long seedSubscriber(String repository, String email, int ttlDay, int ttlHour) {
        long uid = seedUser(email);
        seedRepoMap(repository, uid);
        seedFilters(uid, "issues");
        seedTtl(uid, ttlDay, ttlHour);
        return uid;
    }

    // --- Scenario 1: happy path ---

    @Test
    void happyPathEnqueuesAlertAndRecordsHistory() throws Exception {
        String repo = "octocat/happy-repo";
        long uid = seedSubscriber(repo, "happy@example.com", 1, 0);
        long issueId = 1001L;
        // TTL is 1 day (24h); issue opened 26h ago — past cutoff with margin.
        Instant createdAt = Instant.now().minus(26, ChronoUnit.HOURS);
        seedIssueEvent(issueId, "opened", createdAt, repo);

        issueAlertService.scanAndAlert();

        List<Message> messages = receiveMessages(8);
        assertEquals(1, messages.size(), "expected exactly one alert message");
        QueueMessageDTO dto = readMapper.readValue(messages.get(0).body(), QueueMessageDTO.class);
        assertEquals("alert", dto.getEventType());
        assertEquals("happy@example.com", dto.getEmail());

        JsonNode event = readMapper.readTree(dto.getEvent());
        assertEquals(issueId, event.get("issueId").asLong());
        assertEquals(repo, event.get("repository").asText());

        deleteMessage(messages.get(0));

        assertTrue(alertRecordRepository.existsByIssueIdAndUid(issueId, uid),
                "expected an AlertRecord for (issueId, uid)");
        assertEquals(1, alertRecordRepository.findAll().size());
    }

    // --- Scenario 2: within TTL ---

    @Test
    void issueWithinTtlDoesNotAlert() throws Exception {
        String repo = "octocat/within-ttl-repo";
        long uid = seedSubscriber(repo, "withinttl@example.com", 1, 0);
        long issueId = 1002L;
        // TTL is 1 day (24h); issue opened only 1h ago — well within TTL.
        Instant createdAt = Instant.now().minus(1, ChronoUnit.HOURS);
        seedIssueEvent(issueId, "opened", createdAt, repo);

        issueAlertService.scanAndAlert();

        Thread.sleep(2000);
        List<Message> messages = receiveMessages(1);
        assertTrue(messages.isEmpty(), "no alert expected while issue is within TTL");
        assertTrue(alertRecordRepository.findAll().isEmpty());
    }

    // --- Scenario 3: closed issue, latest event wins ---

    @Test
    void closedIssueLatestEventWinsSuppressesAlert() throws Exception {
        String repo = "octocat/closed-latest-repo";
        seedSubscriber(repo, "closedlatest@example.com", 1, 0);
        long issueId = 1003L;
        // opened (older) then closed (newer) — both past cutoff; aggregation must pick "closed".
        Instant openedAt = Instant.now().minus(48, ChronoUnit.HOURS);
        Instant closedAt = Instant.now().minus(26, ChronoUnit.HOURS);
        seedIssueEvent(issueId, "opened", openedAt, repo);
        seedIssueEvent(issueId, "closed", closedAt, repo);

        issueAlertService.scanAndAlert();

        Thread.sleep(2000);
        List<Message> messages = receiveMessages(1);
        assertTrue(messages.isEmpty(),
                "closed issue (latest event) must not alert even though an older 'opened' "
                        + "event exists");
        assertTrue(alertRecordRepository.findAll().isEmpty());
    }

    // --- Scenario 4: reopened issue, latest event wins in the alerting direction ---

    @Test
    void reopenedIssueLatestEventWinsAlerts() throws Exception {
        String repo = "octocat/reopened-repo";
        long uid = seedSubscriber(repo, "reopened@example.com", 1, 0);
        long issueId = 1004L;
        // closed (older) then reopened (newer, past cutoff) — aggregation must pick "reopened".
        Instant closedAt = Instant.now().minus(48, ChronoUnit.HOURS);
        Instant reopenedAt = Instant.now().minus(26, ChronoUnit.HOURS);
        seedIssueEvent(issueId, "closed", closedAt, repo);
        seedIssueEvent(issueId, "reopened", reopenedAt, repo);

        issueAlertService.scanAndAlert();

        List<Message> messages = receiveMessages(8);
        assertEquals(1, messages.size(), "reopened issue past TTL must alert");
        deleteMessage(messages.get(0));
        assertTrue(alertRecordRepository.existsByIssueIdAndUid(issueId, uid));
    }

    // --- Scenario 5: deduplication across two scans ---

    @Test
    void secondScanDoesNotReAlertAlreadyNotifiedSubscriber() throws Exception {
        String repo = "octocat/dedup-repo";
        long uid = seedSubscriber(repo, "dedup@example.com", 1, 0);
        long issueId = 1005L;
        Instant createdAt = Instant.now().minus(26, ChronoUnit.HOURS);
        seedIssueEvent(issueId, "opened", createdAt, repo);

        issueAlertService.scanAndAlert();
        List<Message> firstRun = receiveMessages(8);
        assertEquals(1, firstRun.size(), "first scan should enqueue exactly one alert");
        deleteMessage(firstRun.get(0));
        assertTrue(alertRecordRepository.existsByIssueIdAndUid(issueId, uid));

        issueAlertService.scanAndAlert();
        Thread.sleep(2000);
        List<Message> secondRun = receiveMessages(1);
        assertTrue(secondRun.isEmpty(), "second scan must not re-alert the same subscriber");
        assertEquals(1, alertRecordRepository.findAll().size(),
                "still exactly one AlertRecord after the second scan");
    }
}

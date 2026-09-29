package com.example.github_event_capture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.entity.RepositoryMap;
import com.example.github_event_capture.entity.dto.PushEventDTO;
import com.example.github_event_capture.repository.EventTypeMapRepository;
import com.example.github_event_capture.repository.RepositoryMapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// Integration tier for MongoTemplateService.bulkWrite / saveEvent against a real local MongoDB
// (the "test" database from application.properties), with the manually created unique indexes
// on EventTypeSubscribers / RepositorySubscribers already in place. See
// docs/plans/MongoTemplateService-IntegrationTest.md for the full design rationale.
//
// Requires: `cd local-dev && docker-compose up -d` (mongodb service) and
// `docker exec mongodb_container mongosh /scripts/create-index.js` applied once per fresh
// mongodb_data volume. The index guard below fails fast with that exact command if either
// unique index is missing.
//
// Isolation: every key this test writes carries a run-unique prefix ("it-<uuid>-..."), and
// cleanup only deletes documents whose key matches that prefix, so the manually created
// indexes and any developer's own data in "test" survive every run. This test never drops
// EventTypeSubscribers or RepositorySubscribers, and never creates an index on them.
@DataMongoTest
@Import(MongoTemplateService.class)
@TestInstance(PER_CLASS)
public class MongoTemplateServiceIntegrationTest {

    @Autowired
    private MongoTemplateService mongoTemplateService;

    @Autowired
    private EventTypeMapRepository eventTypeMapRepository;

    @Autowired
    private RepositoryMapRepository repositoryMapRepository;

    @SpyBean
    private MongoTemplate mongoTemplate;

    private String prefix;
    private String savedPushEventId;

    @BeforeAll
    void verifyRequiredIndexesExist() {
        assertUniqueIndexPresent(EventTypeMap.class, "event-type-index");
        assertUniqueIndexPresent(RepositoryMap.class, "repository-name-index");
    }

    private void assertUniqueIndexPresent(Class<?> domainClass, String indexName) {
        boolean present = mongoTemplate.indexOps(domainClass).getIndexInfo().stream()
                .anyMatch(info -> indexName.equals(info.getName()) && info.isUnique());
        if (!present) {
            Assertions.fail("Required unique index '" + indexName + "' is missing on "
                    + domainClass.getSimpleName() + ". run: docker exec mongodb_container "
                    + "mongosh /scripts/create-index.js");
        }
    }

    @BeforeEach
    void setUp() {
        prefix = "it-" + UUID.randomUUID() + "-";
        savedPushEventId = null;
        clearInvocations(mongoTemplate);
        mongoTemplate.indexOps(RetryProbe.class)
                .ensureIndex(new Index().on("uids", Sort.Direction.ASC).unique());
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.remove(query(where("eventType").regex("^" + prefix)), EventTypeMap.class);
        mongoTemplate.remove(query(where("repository").regex("^" + prefix)), RepositoryMap.class);
        if (savedPushEventId != null) {
            mongoTemplate.remove(query(where("_id").is(savedPushEventId)), PushEventDTO.class);
        }
        mongoTemplate.dropCollection(RetryProbe.class);
    }

    // --- Scenario 1: upsert creates then appends, read back through both paths ---

    @Test
    void upsertCreatesThenAppendsAndRoundTripsThroughRepository() {
        String k1 = prefix + "push";
        String k2 = prefix + "issues";
        Set<String> keys = Set.of(k1, k2);

        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 3L, "eventType", "uids");
        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 4L, "eventType", "uids");

        List<EventTypeMap> k1Docs = mongoTemplate.find(
                query(where("eventType").is(k1)), EventTypeMap.class);
        assertThat(k1Docs).hasSize(1);
        assertThat(k1Docs.get(0).getUids()).containsExactly(3L, 4L);

        List<EventTypeMap> k2Docs = mongoTemplate.find(
                query(where("eventType").is(k2)), EventTypeMap.class);
        assertThat(k2Docs).hasSize(1);
        assertThat(k2Docs.get(0).getUids()).containsExactly(3L, 4L);

        assertThat(eventTypeMapRepository.findByEventType(k1))
                .isPresent()
                .get()
                .extracting(EventTypeMap::getUids)
                .satisfies(uids -> assertThat(uids).contains(3L, 4L));
    }

    // --- Scenario 2: $addToSet upsert is idempotent on a repeated value ---

    @Test
    void addToSetUpsertIsIdempotentOnRepeatedValue() {
        String k1 = prefix + "push";
        String k2 = prefix + "issues";
        Set<String> keys = Set.of(k1, k2);

        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 3L, "eventType", "uids");
        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 3L, "eventType", "uids");

        List<EventTypeMap> k1Docs = mongoTemplate.find(
                query(where("eventType").is(k1)), EventTypeMap.class);
        assertThat(k1Docs).hasSize(1);
        assertThat(k1Docs.get(0).getUids()).containsExactly(3L);

        List<EventTypeMap> k2Docs = mongoTemplate.find(
                query(where("eventType").is(k2)), EventTypeMap.class);
        assertThat(k2Docs).hasSize(1);
        assertThat(k2Docs.get(0).getUids()).containsExactly(3L);
    }

    // --- Scenario 3: RepositoryMap routing, no cross-collection leakage ---

    @Test
    void bulkWriteRoutesToRepositoryMapCollectionOnly() {
        String r1 = prefix + "repoA";

        mongoTemplateService.bulkWrite(RepositoryMap.class, Set.of(r1), 3L, "repository", "uids");

        List<RepositoryMap> docs = mongoTemplate.find(
                query(where("repository").is(r1)), RepositoryMap.class);
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getUids()).containsExactly(3L);

        assertThat(repositoryMapRepository.findByRepository(r1))
                .isPresent()
                .get()
                .extracting(RepositoryMap::getUids)
                .isEqualTo(List.of(3L));

        assertThat(mongoTemplate.exists(
                query(where("repository").is(r1)), EventTypeMap.class)).isFalse();
    }

    // --- Scenario 4: concurrent same-key upserts, Layer 2 invariant ---

    @Test
    void concurrentSameKeyUpsertsProduceOneDocumentWithAllUids() throws Exception {
        String k1 = prefix + "push";
        String k2 = prefix + "issues";
        Set<String> keys = Set.of(k1, k2);

        runConcurrently(8, uid -> () -> mongoTemplateService.bulkWrite(
                EventTypeMap.class, keys, uid + 1L, "eventType", "uids"));

        List<EventTypeMap> k1Docs = mongoTemplate.find(
                query(where("eventType").is(k1)), EventTypeMap.class);
        assertThat(k1Docs).hasSize(1);
        assertThat(k1Docs.get(0).getUids())
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        List<EventTypeMap> k2Docs = mongoTemplate.find(
                query(where("eventType").is(k2)), EventTypeMap.class);
        assertThat(k2Docs).hasSize(1);
        assertThat(k2Docs.get(0).getUids())
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    // --- Scenario 5: concurrent cross-collection sequences, Layer 1 regression guard ---

    @Test
    void concurrentCrossCollectionSequencesNeverCrossPollute() throws Exception {
        String k1 = prefix + "push";
        String r1 = prefix + "repoA";

        runConcurrently(8, uid -> () -> {
            mongoTemplateService.bulkWrite(
                    EventTypeMap.class, Set.of(k1), uid + 1L, "eventType", "uids");
            mongoTemplateService.bulkWrite(
                    RepositoryMap.class, Set.of(r1), uid + 1L, "repository", "uids");
        });

        assertThat(mongoTemplate.count(
                query(where("repository").exists(true)), EventTypeMap.class)).isZero();
        assertThat(mongoTemplate.count(
                query(where("eventType").exists(true)), RepositoryMap.class)).isZero();

        List<EventTypeMap> k1Docs = mongoTemplate.find(
                query(where("eventType").is(k1)), EventTypeMap.class);
        assertThat(k1Docs).hasSize(1);
        assertThat(k1Docs.get(0).getUids())
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        List<RepositoryMap> r1Docs = mongoTemplate.find(
                query(where("repository").is(r1)), RepositoryMap.class);
        assertThat(r1Docs).hasSize(1);
        assertThat(r1Docs.get(0).getUids())
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    // --- Scenario 6: real duplicate-key translation and the bounded single retry ---

    @Test
    void bulkWriteTranslatesDuplicateKeyAndBoundsRetryToOneAttempt() {
        RetryProbe other = new RetryProbe();
        other.setKey("other");
        other.addUid(7L);
        mongoTemplate.insert(other);

        assertThatThrownBy(() -> mongoTemplateService.bulkWrite(
                RetryProbe.class, Set.of("k"), 7L, "key", "uids"))
                .isInstanceOf(BulkOperationException.class)
                .satisfies(e -> assertThat(((BulkOperationException) e).getErrors())
                        .isNotEmpty()
                        .allSatisfy(err -> assertThat(err.getCode()).isEqualTo(11000)));

        verify(mongoTemplate, times(2))
                .bulkOps(BulkOperations.BulkMode.UNORDERED, RetryProbe.class);

        List<RetryProbe> probes = mongoTemplate.findAll(RetryProbe.class);
        assertThat(probes).hasSize(1);
        assertThat(probes.get(0).getKey()).isEqualTo("other");
    }

    // --- Scenario 7: saveEvent lands in the entity's own collection ---

    @Test
    void saveEventPersistsToPushEventsCollection() throws Exception {
        String json = "{\"repository\":{\"full_name\":\"" + prefix + "repo\"},"
                + "\"pusher\":{\"name\":\"octocat\",\"email\":\"octocat@example.com\"},"
                + "\"commits\":[{\"message\":\"test commit\"}]}";
        PushEventDTO dto = new ObjectMapper().readValue(json, PushEventDTO.class);

        mongoTemplateService.saveEvent(dto);

        assertThat(dto.getId()).isNotNull();
        savedPushEventId = dto.getId();

        PushEventDTO found = mongoTemplate.findById(dto.getId(), PushEventDTO.class);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(dto.getId());
    }

    // --- Concurrency helper ---

    // Runs `workers` tasks on a fixed thread pool, released together by a CountDownLatch gate,
    // and propagates any worker exception via future.get() so a failure fails the test.
    private void runConcurrently(int workers, IntFunction<Runnable> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                int uid = i;
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    task.apply(uid).run();
                }));
            }
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
    }

    // --- Deterministic duplicate-key probe entity (test sources only) ---

    // Throwaway entity used only by scenario 6 to force a real 11000 BulkOperationException
    // on demand: a unique index on `uids` is created on this collection in
    // @BeforeEach and the whole collection is dropped in @AfterEach, so this never touches the
    // production EventTypeSubscribers / RepositorySubscribers collections or their indexes.
    @Document(collection = "MongoTemplateServiceRetryProbe")
    private static final class RetryProbe {
        @Field("key")
        private String key;
        @Field("uids")
        private List<Long> uids = new ArrayList<>();

        public void setKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public void addUid(long uid) {
            this.uids.add(uid);
        }

        public List<Long> getUids() {
            return uids;
        }
    }
}

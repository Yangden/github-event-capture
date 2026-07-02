package com.example.github_event_capture.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.github_event_capture.entity.Filters;
import com.example.github_event_capture.entity.RepositoryMap;
import com.example.github_event_capture.entity.User;
import com.example.github_event_capture.entity.ttlConfig;
import com.example.github_event_capture.repository.AlertRecordRepository;
import com.example.github_event_capture.repository.FilterRepository;
import com.example.github_event_capture.repository.RepositoryMapRepository;
import com.example.github_event_capture.repository.TtlConfigRepository;
import com.example.github_event_capture.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
public class IssueAlertServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RepositoryMapRepository repositoryMapRepository;

    @Mock
    private FilterRepository filterRepository;

    @Mock
    private TtlConfigRepository ttlConfigRepository;

    @Mock
    private AlertRecordRepository alertRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AsyncQueueserviceImpl asyncQueueService;

    @InjectMocks
    private IssueAlertServiceImpl service;

    // --- Helper factory methods ---

    private ttlConfig makeTtlConfig(long uid, int day, int hour) {
        ttlConfig config = new ttlConfig();
        config.setUid(uid);
        config.setDay(day);
        config.setHour(hour);
        return config;
    }

    private RepositoryMap makeRepoMap(String repo, long... uids) {
        RepositoryMap map = new RepositoryMap();
        map.setRepository(repo);
        for (long uid : uids) {
            map.addUid(uid);
        }
        return map;
    }

    private Filters makeFilters(long uid, String... types) {
        Filters filters = new Filters();
        filters.setUid(uid);
        HashSet<String> set = new HashSet<>();
        for (String type : types) {
            set.add(type);
        }
        filters.setEventTypes(set);
        return filters;
    }

    private User makeUser(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private IssueAlertServiceImpl.OpenIssueResult makeOpenIssue(
            long issueId, String repoName, Instant createdAt) {
        return new IssueAlertServiceImpl.OpenIssueResult(issueId, "opened", createdAt, repoName);
    }

    // Mocks mongoTemplate.aggregate() to return the given list.
    // @SuppressWarnings needed for the unchecked cast from raw AggregationResults to the
    // typed AggregationResults<OpenIssueResult>.
    @SuppressWarnings("unchecked")
    private void givenAggregationReturns(List<IssueAlertServiceImpl.OpenIssueResult> issues) {
        AggregationResults<IssueAlertServiceImpl.OpenIssueResult> results =
                mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(issues);
        when(mongoTemplate.aggregate(
                any(Aggregation.class),
                eq("IssueEvents"),
                eq(IssueAlertServiceImpl.OpenIssueResult.class)
        )).thenReturn(results);
    }

    // --- Scan-level early-exit tests ---


    // No ttlConfig documents exist — the service cannot compute a cutoff so it returns immediately.
    // Nothing downstream (MongoTemplate, AlertRecordRepository) should be touched.
    @Test
    void scanAndAlertNoTtlConfigsSkipsImmediately() {
        when(ttlConfigRepository.findAll()).thenReturn(Collections.emptyList());

        service.scanAndAlert();

        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(alertRecordRepository);
    }

    // ttlConfig exists so the aggregation runs, but it returns no open issues.
    // No per-issue processing should happen — repositoryMapRepository and alertRecordRepository
    // must never be called.
    @Test
    void scanAndAlertNoOpenIssuesDoesNothing() {
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(1L, 1, 0)));
        givenAggregationReturns(Collections.emptyList());

        service.scanAndAlert();

        verifyNoInteractions(repositoryMapRepository);
        verifyNoInteractions(alertRecordRepository);
    }

    // --- Per-subscriber guard clause tests ---

    // The issue's repository has no RepositorySubscribers document — no subscribers to notify.
    @Test
    void noRepoMapSkipsIssue() {
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(1L, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.empty());

        service.scanAndAlert();

        verifyNoInteractions(filterRepository);
        verifyNoInteractions(alertRecordRepository);
    }

    // RepositoryMap exists but its uid list is null — addUid was never called.
    @Test
    void emptyUidListSkipsIssue() {
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(1L, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        RepositoryMap emptyMap = new RepositoryMap();
        emptyMap.setRepository("my-repo");
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(emptyMap));

        service.scanAndAlert();

        verifyNoInteractions(filterRepository);
        verifyNoInteractions(alertRecordRepository);
    }

    // The subscriber uid is in RepositoryMap but has no Filters document at all.
    @Test
    void subscriberHasNoFiltersSkips() {
        long uid = 1L;
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(uid, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uid)));
        when(filterRepository.findByUserId(uid)).thenReturn(java.util.Optional.empty());

        service.scanAndAlert();

        verifyNoInteractions(alertRecordRepository);
    }

    // The subscriber has Filters but has not subscribed to the "issues" event type.
    @Test
    void filterMissingIssuesTypeSkips() {
        long uid = 1L;
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(uid, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uid)));
        when(filterRepository.findByUserId(uid)).thenReturn(
                java.util.Optional.of(makeFilters(uid, "push")));

        service.scanAndAlert();

        verifyNoInteractions(alertRecordRepository);
    }

    // The subscriber appears in RepositoryMap but has no ttlConfig — their uid is absent from
    // the configByUid map built at the start of the scan.
    @Test
    void noTtlConfigForSubscriberSkips() {
        long uidWithConfig = 1L;
        long uidWithoutConfig = 2L;
        when(ttlConfigRepository.findAll()).thenReturn(
                List.of(makeTtlConfig(uidWithConfig, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uidWithoutConfig)));
        when(filterRepository.findByUserId(uidWithoutConfig)).thenReturn(
                java.util.Optional.of(makeFilters(uidWithoutConfig, "issues")));

        service.scanAndAlert();

        verifyNoInteractions(alertRecordRepository);
    }

    // The issue is open but hasn't crossed the subscriber's personal TTL yet.
    // User TTL = 3 days; issue opened 2 days ago — should be skipped.
    @Test
    void issueNotYetPastPersonalTtlSkips() {
        long uid = 1L;
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(uid, 3, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(100L, "my-repo", Instant.now().minus(2, ChronoUnit.DAYS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uid)));
        when(filterRepository.findByUserId(uid)).thenReturn(
                java.util.Optional.of(makeFilters(uid, "issues")));

        service.scanAndAlert();

        verifyNoInteractions(alertRecordRepository);
    }

    // An AlertRecord already exists for this (issueId, uid) pair — alert was already sent.
    @Test
    void alertAlreadySentSkips() {
        long uid = 1L;
        long issueId = 100L;
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(uid, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(issueId, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uid)));
        when(filterRepository.findByUserId(uid)).thenReturn(
                java.util.Optional.of(makeFilters(uid, "issues")));
        when(alertRecordRepository.existsByIssueIdAndUid(issueId, uid)).thenReturn(true);

        service.scanAndAlert();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(asyncQueueService);
    }

    // AlertRecord does not exist yet but the User record cannot be found in PostgreSQL.
    @Test
    void userNotFoundSkips() {
        long uid = 1L;
        long issueId = 100L;
        when(ttlConfigRepository.findAll()).thenReturn(List.of(makeTtlConfig(uid, 1, 0)));
        givenAggregationReturns(List.of(
                makeOpenIssue(issueId, "my-repo", Instant.now().minus(48, ChronoUnit.HOURS))));
        when(repositoryMapRepository.findByRepository("my-repo")).thenReturn(
                java.util.Optional.of(makeRepoMap("my-repo", uid)));
        when(filterRepository.findByUserId(uid)).thenReturn(
                java.util.Optional.of(makeFilters(uid, "issues")));
        when(alertRecordRepository.existsByIssueIdAndUid(issueId, uid)).thenReturn(false);
        when(userRepository.findById(uid)).thenReturn(java.util.Optional.empty());

        service.scanAndAlert();

        verifyNoInteractions(asyncQueueService);
    }
}

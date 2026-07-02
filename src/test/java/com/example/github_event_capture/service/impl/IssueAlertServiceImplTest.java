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
}

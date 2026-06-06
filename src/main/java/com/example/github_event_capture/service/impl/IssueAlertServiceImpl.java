package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.entity.AlertRecord;
import com.example.github_event_capture.entity.Filters;
import com.example.github_event_capture.entity.RepositoryMap;
import com.example.github_event_capture.entity.User;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.entity.ttlConfig;
import com.example.github_event_capture.repository.AlertRecordRepository;
import com.example.github_event_capture.repository.FilterRepository;
import com.example.github_event_capture.repository.RepositoryMapRepository;
import com.example.github_event_capture.repository.TtlConfigRepository;
import com.example.github_event_capture.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IssueAlertServiceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssueAlertServiceImpl.class);

    private final MongoTemplate mongoTemplate;
    private final RepositoryMapRepository repositoryMapRepository;
    private final FilterRepository filterRepository;
    private final TtlConfigRepository ttlConfigRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final UserRepository userRepository;
    private final AsyncQueueserviceImpl asyncQueueService;
    private final ObjectMapper mapper = new ObjectMapper();

    public IssueAlertServiceImpl(MongoTemplate mongoTemplate,
            RepositoryMapRepository repositoryMapRepository,
            FilterRepository filterRepository,
            TtlConfigRepository ttlConfigRepository,
            AlertRecordRepository alertRecordRepository,
            UserRepository userRepository,
            AsyncQueueserviceImpl asyncQueueService) {
        this.mongoTemplate = mongoTemplate;
        this.repositoryMapRepository = repositoryMapRepository;
        this.filterRepository = filterRepository;
        this.ttlConfigRepository = ttlConfigRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.userRepository = userRepository;
        this.asyncQueueService = asyncQueueService;
    }

    public void scanAndAlert() {
        List<ttlConfig> allConfigs = ttlConfigRepository.findAll();
        if (allConfigs.isEmpty()) {
            LOGGER.info("No TTL configs found, skipping alert scan");
            return;
        }

        long minTtlHours = allConfigs.stream()
                .mapToLong(c -> c.getDay() * 24L + c.getHour())
                .min()
                .orElse(24);
        Instant cutoff = Instant.now().minus(minTtlHours, ChronoUnit.HOURS);

        List<OpenIssueResult> openIssues = queryOpenIssues(cutoff);
        LOGGER.info("Found {} open issue candidates past global cutoff", openIssues.size());

        Map<Long, ttlConfig> configByUid = allConfigs.stream()
                .collect(Collectors.toMap(ttlConfig::getUid, c -> c));

        for (OpenIssueResult issue : openIssues) {
            processOpenIssue(issue, configByUid);
        }
    }

    // IssueEventDTO is persisted via mongoTemplate.save(), so Spring Data uses Java field names —
    // issueInfo.id, issueInfo.createdAt, repository.name — not the @JsonProperty names.
    private List<OpenIssueResult> queryOpenIssues(Instant cutoff) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.sort(Sort.Direction.DESC, "issueInfo.createdAt"),
                Aggregation.group("issueInfo.id")
                        .first("action").as("action")
                        .first("issueInfo.createdAt").as("createdAt")
                        .first("repository.name").as("repositoryName"),
                Aggregation.match(
                        Criteria.where("action").ne("closed")
                                .and("createdAt").lt(cutoff)
                )
        );
        return mongoTemplate
                .aggregate(aggregation, "IssueEvents", OpenIssueResult.class)
                .getMappedResults();
    }

    private void processOpenIssue(OpenIssueResult issue, Map<Long, ttlConfig> configByUid) {
        Optional<RepositoryMap> repoMapOpt =
                repositoryMapRepository.findByRepository(issue.getRepositoryName());
        if (repoMapOpt.isEmpty()) {
            return;
        }

        List<Long> uids = repoMapOpt.get().getUids();
        if (uids == null || uids.isEmpty()) {
            return;
        }

        for (long uid : uids) {
            Optional<Filters> filtersOpt = filterRepository.findByUserId(uid);
            if (filtersOpt.isEmpty()) {
                continue;
            }
            Set<String> eventTypes = filtersOpt.get().getEventTypes();
            if (eventTypes == null || !eventTypes.contains("issues")) {
                continue;
            }

            ttlConfig config = configByUid.get(uid);
            if (config == null) {
                continue;
            }
            long userTtlHours = config.getDay() * 24L + config.getHour();
            if (issue.getCreatedAt().isAfter(Instant.now().minus(userTtlHours, ChronoUnit.HOURS))) {
                continue;
            }

            if (alertRecordRepository.existsByIssueIdAndUid(issue.getIssueId(), uid)) {
                continue;
            }

            Optional<User> userOpt = userRepository.findById(uid);
            if (userOpt.isEmpty()) {
                continue;
            }

            enqueueAlert(issue, userOpt.get().getEmail());

            AlertRecord record = new AlertRecord();
            record.setIssueId(issue.getIssueId());
            record.setUid(uid);
            record.setAlertedAt(Instant.now());
            alertRecordRepository.save(record);
        }
    }

    private void enqueueAlert(OpenIssueResult issue, String email) {
        QueueMessageDTO dto = new QueueMessageDTO();
        dto.setEmail(email);
        dto.setEventType("alert");
        dto.setEvent(String.format(
                "{\"issueId\":%d,\"repository\":\"%s\",\"openedAt\":\"%s\"}",
                issue.getIssueId(), issue.getRepositoryName(), issue.getCreatedAt()
        ));
        try {
            asyncQueueService.batchSend(mapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize alert for issueId={}: {}",
                    issue.getIssueId(), e.getMessage());
        }
    }

    // Projection target for the aggregation — not a MongoDB entity, only used for result mapping.
    // _id in the aggregation output is the group key (issueInfo.id).
    static class OpenIssueResult {
        @Field("_id")
        private long issueId;
        private String action;
        private Instant createdAt;
        private String repositoryName;

        public long getIssueId() {
            return issueId;
        }

        public String getAction() {
            return action;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public String getRepositoryName() {
            return repositoryName;
        }
    }
}

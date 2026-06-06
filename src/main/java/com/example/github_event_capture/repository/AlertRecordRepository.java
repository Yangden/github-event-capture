package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.AlertRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRecordRepository extends MongoRepository<AlertRecord, String> {
    boolean existsByIssueIdAndUid(long issueId, long uid);
}

package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.ttlConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TtlConfigRepository extends MongoRepository<ttlConfig, Long> {
}

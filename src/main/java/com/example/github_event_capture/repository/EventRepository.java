package com.example.github_event_capture.repository;

import io.micrometer.core.annotation.Counted;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.github_event_capture.entity.Event;

public interface EventRepository extends MongoRepository<Event, String> {
    @Counted(value = "database.throughput", extraTags = {"metrics", "write-count", "db", "mongodb"})
    Event save(Event event);

}


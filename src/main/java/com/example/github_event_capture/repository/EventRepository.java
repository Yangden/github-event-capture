package com.example.github_event_capture.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.github_event_capture.entity.Event;

public interface EventRepository extends MongoRepository<Event, String> {
    // custom queries
}

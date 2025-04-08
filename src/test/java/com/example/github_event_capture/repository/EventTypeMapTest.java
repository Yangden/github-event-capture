package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.repository.EventTypeMapRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.junit.jupiter.api.Test;

@DataMongoTest
public class EventTypeMapTest {
    @Autowired
    private EventTypeMapRepository eventTypeMapRepository;

    private MongoTemplate mongoTemplate;

    @Test
    public void addUserIdTest() {
        eventTypeMapRepository.addUid("issues", 1);

    }
}

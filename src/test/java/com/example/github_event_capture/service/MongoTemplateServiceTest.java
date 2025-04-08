package com.example.github_event_capture.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import com.example.github_event_capture.service.MongoTemplateService;
import java.util.HashSet;
import com.example.github_event_capture.entity.EventTypeMap;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import(MongoTemplateService.class)
public class MongoTemplateServiceTest {
    @Autowired
    private MongoTemplateService mongoTemplateService;

    @Test
    public void testBulkWrite() {
        HashSet<String> eventTypes = new HashSet<>() {
            {
            add("issues");
            add("push");
            }
        };
        int uid = 3;
        mongoTemplateService.setDomainClass(EventTypeMap.class);
        mongoTemplateService.setBulkOps();
        mongoTemplateService.bulkWrite(eventTypes, uid, "eventType", "uids");
    }

}

package com.example.github_event_capture.service;

import org.assertj.core.util.VisibleForTesting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import com.example.github_event_capture.service.MongoTemplateService;
import java.util.HashSet;
import com.example.github_event_capture.entity.EventTypeMap;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.example.github_event_capture.entity.dto.IssueEventDTO;

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

    @Test
    public void testSaveEvent() {
        IssueEventDTO issueEventDTO = new IssueEventDTO();
        mongoTemplateService.saveEvent(issueEventDTO);
    }

}

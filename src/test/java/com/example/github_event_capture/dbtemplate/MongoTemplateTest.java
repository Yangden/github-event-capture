package com.example.github_event_capture.dbtemplate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import com.example.github_event_capture.entity.EventTypeMap;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;


@DataMongoTest
public class MongoTemplateTest {
    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    public void TestBulkWrite() {
        int uid = 2;
        HashSet<String> eventTypes = new HashSet() {
            {
                add("issues");
                add("push");
            }
        };

        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class);
        for (String eventType : eventTypes) {
            Query query = Query.query(Criteria.where("eventType").is(eventType));
            Update update = new Update().addToSet("uids", uid);
            ops.upsert(query, update);
        }
        ops.execute();
    }

}

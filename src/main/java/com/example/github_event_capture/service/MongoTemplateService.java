package com.example.github_event_capture.service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.BulkOperations;
import java.util.Set;
import org.springframework.stereotype.Service;


@Service
public class MongoTemplateService {
    private final MongoTemplate mongoTemplate;
    private BulkOperations ops;
    private Class domainClass;

    public MongoTemplateService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /* settlers */
    public void setDomainClass(Class domainClass) {
        this.domainClass = domainClass;
    }
    public void setBulkOps() {
        this.ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, domainClass);
    }

    /****************
    bulk operations
     **************/
    /* bulk write a value */
    public void bulkWrite(Set<String> keys, long value, String keyName, String valName) {
        for (String key : keys) {
            Query query = Query.query(Criteria.where(keyName).is(key));
            Update update = new Update().addToSet(valName, value);
            ops.upsert(query, update);
        }
        ops.execute();
    }

}

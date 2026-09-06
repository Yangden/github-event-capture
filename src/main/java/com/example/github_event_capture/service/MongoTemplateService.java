package com.example.github_event_capture.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.BulkOperations;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.github_event_capture.entity.Event;


@Service
public class MongoTemplateService {
    private final MongoTemplate mongoTemplate;

    public MongoTemplateService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

     /**********************************************
     * write events to the corresponding collections
     **********************************************/
     public void saveEvent(Event event) {
         mongoTemplate.save(event);
     }
    /****************
    bulk operations
     **************/
    /* bulk write a value */
    public void bulkWrite(Class<?> domainClass, Set<String> keys, long value,
                          String keyName, String valName) {
        try {
            executeBatch(domainClass, keys, value, keyName, valName);
        } catch (DuplicateKeyException e) {
            // upsert lost the insert race under the unique index; the document now
            // exists, so one rebuilt retry takes the atomic $addToSet update path
            executeBatch(domainClass, keys, value, keyName, valName);
        }
    }

    private void executeBatch(Class<?> domainClass, Set<String> keys, long value,
                              String keyName, String valName) {
        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, domainClass);
        for (String key : keys) {
            Query query = Query.query(Criteria.where(keyName).is(key));
            Update update = new Update().addToSet(valName, value);
            ops.upsert(query, update);
        }
        ops.execute();
    }

}

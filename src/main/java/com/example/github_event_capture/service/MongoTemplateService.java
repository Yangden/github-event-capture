package com.example.github_event_capture.service;

import org.springframework.data.mongodb.BulkOperationException;
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
    private static final int DUPLICATE_KEY = 11000;

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
        } catch (BulkOperationException e) {
            // DefaultBulkOperations wraps the driver's MongoBulkWriteException before the
            // exception translator sees it, so a duplicate key arrives here, not as
            // DuplicateKeyException. Only the upsert insert race (11000) is retried: the
            // winning document now exists, so one rebuilt retry takes the atomic $addToSet
            // update path. Any other write error propagates unchanged.
            if (!onlyDuplicateKeyErrors(e)) {
                throw e;
            }
            executeBatch(domainClass, keys, value, keyName, valName);
        }
    }

    private static boolean onlyDuplicateKeyErrors(BulkOperationException e) {
        return !e.getErrors().isEmpty()
                && e.getErrors().stream().allMatch(err -> err.getCode() == DUPLICATE_KEY);
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

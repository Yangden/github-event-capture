package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.EventTypeMap;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import java.util.Optional;

public interface EventTypeMapRepository extends MongoRepository<EventTypeMap, String> {
    /* fetch document based on event type */
    Optional<EventTypeMap> findByEventType(String eventType);
    /* add an element to the field uids */
    @Query("{'eventType':  ?0}")
    @Update("{'$addToSet' :  {'uids' :  ?1}}")
    void addUid(String eventType, long uid);
}

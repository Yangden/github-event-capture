package com.example.github_event_capture.repository;

import io.micrometer.core.annotation.Counted;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.github_event_capture.entity.Filters;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface FilterRepository extends MongoRepository<Filters, String> {
    /* fetch a single document using user id)*/
    @Counted(value = "database.throughput", extraTags = {"metrics", "read-count", "db", "mongodb"})
    @Query("{ 'uid' : ?0 }")
    Optional<Filters> findByUserId(long uid);
    /* delete filters by user id */
    @Counted(value = "database.throughput", extraTags = {"metrics", "write-count", "db", "mongodb"})
    void deleteByUid(long id);

}

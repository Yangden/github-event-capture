package com.example.github_event_capture.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.github_event_capture.entity.Filters;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface FilterRepository extends MongoRepository<Filters, String> {
    /* fetch a single document using user id)*/
    @Query("{ 'uid' : ?0 }")
    Optional<Filters> findByUserId(int uid);

}

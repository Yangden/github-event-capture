package com.example.github_event_capture.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.github_event_capture.entity.Testrepo;
import org.springframework.stereotype.Repository;

@Repository
public interface TestRepository extends MongoRepository<Testrepo, String> {
    //custom query
}

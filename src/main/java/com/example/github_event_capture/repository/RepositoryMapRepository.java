package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.RepositoryMap;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import java.util.Optional;

public interface RepositoryMapRepository extends MongoRepository<RepositoryMap, String> {
    Optional<RepositoryMap> findByRepository(String repository);

    @Query("{'repository': ?0}")
    @Update("{'$addToSet': {'uids': ?1}}")
    void addUid(String repository, long uid);
}

package com.example.github_event_capture.repository;

import io.micrometer.core.annotation.Counted;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.github_event_capture.entity.User;
import java.util.List;


public interface UserRepository extends JpaRepository<User, Long> {
    // fetch a record by email
    @Counted(value = "database.throughput", extraTags = {"metrics", "read-count", "db", "postgres"})
    User findByEmail(String email);
    // fetch a list of emails using a list of uids
    @Counted(value = "database.throughput", extraTags = {"metrics", "read-count", "db", "postgres"})
    @Query("SELECT u.email FROM User u where u.id IN ?1")
    List<String> findEmailsByUids(List<Long> uids);
}

package com.example.github_event_capture.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.github_event_capture.entity.User;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    // fetch a record by email
    User findByEmail(String email);
    User findById(long id);
    // fetch a list of emails using a list of uids
    @Query("SELECT u.email FROM User u where u.id IN ?1")
    List<String> findEmailsByUids(List<Long> uids);
}

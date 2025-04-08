package com.example.github_event_capture.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import com.example.github_event_capture.entity.User;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends CrudRepository<User, Long> {
    // fetch a record by email
    User findByEmail(String email);
    User findById(long id);
    // fetch a list of emails using a list of uids
    @Query("SELECT u.email FROM Users u where u.uid IN :uidList")
    List<String> fetchEmailsbyUids(@Param("uidList") List<Long> uids);
}

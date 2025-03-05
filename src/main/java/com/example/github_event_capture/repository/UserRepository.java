package com.example.github_event_capture.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.github_event_capture.entity.User;
import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    // fetch a record by email
    User findByEmail(String email);
}

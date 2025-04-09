package com.example.github_event_capture.repository;

import com.example.github_event_capture.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;


@DataJpaTest
public class UserRepositoryTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRepositoryTest.class);

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestEntityManager entityManager;

    @BeforeEach
    public void populateData() {
        User user = new User();
        user.setEmail("exampl@123.com");
        user.setPassword("password");
        entityManager.persist(user);
    }

    @Test
    public void testFindEmails() {
        List<Long> uids = new ArrayList<>();
        uids.add(1L);
        List<String> emails = userRepository.findEmailsByUids(uids);
        LOGGER.info("emails: {}", emails);
    }
}

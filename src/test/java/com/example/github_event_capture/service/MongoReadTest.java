package com.example.github_event_capture.service;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.github_event_capture.repository.FilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import com.example.github_event_capture.entity.Filters;
import java.util.List;

@SpringBootTest
public class MongoReadTest {
    @Autowired
    private FilterRepository filterRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoReadTest.class);

    @Test
    public void FindById() {
        /* test whether can fetch all Ids */
        Optional<Filters> FilterVal = filterRepository.findByUserId(1);
        if (!FilterVal.isPresent()) {
            LOGGER.error("Filter not found");
        }
    }
}

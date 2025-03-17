package com.example.github_event_capture.TestConfig;

import com.example.github_event_capture.service.QueueService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.example.github_event_capture.service.impl.QueueServiceImpl;

@TestConfiguration
public class SQSTestConfig {
    @Bean
    public QueueServiceImpl queueService() {
        return new QueueServiceImpl();
    }
}

package com.example.github_event_capture.service;

import com.example.github_event_capture.service.impl.IssueAlertServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IssueAlertScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssueAlertScheduler.class);

    private final IssueAlertServiceImpl issueAlertService;

    public IssueAlertScheduler(IssueAlertServiceImpl issueAlertService) {
        this.issueAlertService = issueAlertService;
    }

    @Scheduled(cron = "${alert.cron:0 0 * * * *}")
    public void run() {
        LOGGER.info("Issue alert scan started");
        issueAlertService.scanAndAlert();
        LOGGER.info("Issue alert scan completed");
    }
}

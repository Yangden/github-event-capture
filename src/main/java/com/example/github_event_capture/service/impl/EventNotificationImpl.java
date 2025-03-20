package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.EventNotification;
import com.example.github_event_capture.service.impl.QueueServiceImpl;
import com.example.github_event_capture.service.impl.EmailSenderServiceImpl;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.utils.FormatEmail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EventNotificationImpl implements EventNotification {
    private final QueueServiceImpl queueService;
    private final EmailSenderServiceImpl emailSenderService;
    private final ObjectMapper mapToObject = new ObjectMapper();
    private final Logger LOGGER = LoggerFactory.getLogger(EventNotificationImpl.class);
    private final String senderEmail = "yangdeng2001@gmail.com";

    public EventNotificationImpl(QueueServiceImpl queueService, EmailSenderServiceImpl emailSenderService) {
        this.queueService = queueService;
        this.emailSenderService = emailSenderService;
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    @Async
    public void sendNotifications() {
        /* get messages from the queue */
        List<String> messages = queueService.receiveMessage();
        LOGGER.info("messages from sqs received");

        /* for each message: map to objects to extract certain fields */
        try {
            for (String message : messages) {
                QueueMessageDTO messageDTO = mapToObject.readValue(message, QueueMessageDTO.class);
                LOGGER.info("mapped succesfully to QueueMessageDTO");
                String emailBody = FormatEmail.getFormatEmail(messageDTO.getEvent());
                emailSenderService.SendEmail(senderEmail, messageDTO.getEmail(),
                                            "Event Notification:" + " " + messageDTO.getEventType(),
                        emailBody);
                LOGGER.info("email succesfully sent");
            }
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage());
        }
    }
}

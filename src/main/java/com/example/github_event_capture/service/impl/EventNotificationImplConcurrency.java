package com.example.github_event_capture.service.impl;

import jakarta.annotation.PostConstruct;

import java.text.Normalizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import com.example.github_event_capture.service.impl.QueueServiceImpl;
import com.example.github_event_capture.service.impl.EmailSenderServiceImpl;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.utils.FormatEmail;
import org.springframework.stereotype.Service;

@Service
public class EventNotificationImplConcurrency implements DisposableBean {
    int N_THREADS = 10; // set the size of the thread poll
    ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
    private static final String senderEmail = "yangdeng2001@gmail.com";
    private final QueueServiceImpl queueService;
    private final EmailSenderServiceImpl emailSenderService;
    private final Logger LOGGER = LoggerFactory.getLogger(EventNotificationImplConcurrency.class);
    private boolean RUNNING = true;
    private final ObjectMapper mapToObject = new ObjectMapper();

    public EventNotificationImplConcurrency(QueueServiceImpl queueService,
                                            EmailSenderServiceImpl emailSenderService) {
        this.queueService = queueService;
        this.emailSenderService = emailSenderService;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("message polling starts");
        executor.submit(this::pollMessages);
    }

    public void pollMessages() {
        while (RUNNING) {
             List<String> messages = queueService.receiveMessage(); // long poll to receive messages from sqs
             for (String message : messages) {
                 try {
                     QueueMessageDTO messageDTO = mapToObject.readValue(message, QueueMessageDTO.class);
                     LOGGER.info("map succesfully to QueueMessageDTO");

                     String emailBody = FormatEmail.getFormatEmail(messageDTO.getEvent());
                     if (messageDTO.getEmail() != "dengyang2001@outlook,com" && messageDTO.getEmail() != "yangdeng2001@gmail.com"){
                         emailSenderService.SendEmail(senderEmail, messageDTO.getEmail(),
                                 "Event Notification:" + " " + messageDTO.getEventType(),
                                 emailBody);
                         LOGGER.info("Email sent successfully");
                     }

                 } catch (JsonProcessingException e) {
                     LOGGER.error(e.getMessage());
                 }

             }

        }
    }

    public void destroy() {
        RUNNING = false;
    }


}

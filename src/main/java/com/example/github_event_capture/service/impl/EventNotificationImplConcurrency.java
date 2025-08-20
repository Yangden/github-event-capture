package com.example.github_event_capture.service.impl;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.DisposableBean;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.utils.FormatEmail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import com.example.github_event_capture.service.impl.MonitorServiceImpl;

@Service
public class EventNotificationImplConcurrency implements DisposableBean {
    int N_THREADS = 10; // set the size of the thread poll
    ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);

    private static final String senderEmail = "yangdeng2001@gmail.com";
    private final EmailSenderServiceImpl emailSenderService;

    private final Logger LOGGER = LoggerFactory.getLogger(EventNotificationImplConcurrency.class);
    private boolean RUNNING = true;
    private final ObjectMapper mapToObject = new ObjectMapper();

    private final AsyncQueueserviceImpl asyncQueueService;
    private int consecutiveErrors = 0;

    @Value("30")
    private int errorRetrySeconds;

    @Value("3")
    private int maxConsecutiveErrors;

    private final MonitorServiceImpl monitorService;

    public EventNotificationImplConcurrency(MonitorServiceImpl monitorService, @Qualifier("sqsAsyncClient") SqsAsyncClient sqsAsyncClient,
                                            EmailSenderServiceImpl emailSenderService, AsyncQueueserviceImpl asyncQueueService) {
        this.emailSenderService = emailSenderService;
        this.asyncQueueService = asyncQueueService;
        this.monitorService = monitorService;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("message polling starts");
        executor.submit(this::pollMessages);
    }

    public void pollMessages() {
        while (RUNNING && !Thread.currentThread().isInterrupted()) {
            try {
                CompletableFuture<ReceiveMessageResponse> received = asyncQueueService.receiveMessage();
                received.thenAccept(responses -> {
                    if (!responses.hasMessages()) {
                        LOGGER.debug("no messages received from the queue");
                        return;
                    }
                    monitorService.recordEventCountBeforDelete(responses.messages().size());
                    responses.messages().forEach(
                            message -> {
                                asyncQueueService.deleteMessage(message.receiptHandle());
                                /* send email notification */
                                try {
                                    QueueMessageDTO messageDTO = mapToObject.readValue(message.body(), QueueMessageDTO.class);
                                    LOGGER.info("mapped to QueueMessageDTO succesfully");
                                    String emailBody = FormatEmail.getFormatEmail(messageDTO.getEvent());
                                    emailSenderService.SendEmailAsync(senderEmail, messageDTO.getEmail(),
                                            "Event Notification:" + " " + messageDTO.getEventType(),
                                            emailBody);
                                } catch (JsonProcessingException e) {
                                    LOGGER.error("error parsing message {}: {}", message.messageId(), e.getMessage());
                                }
                            }

                    );
                }).exceptionally(ex-> {
                    consecutiveErrors++;
                    LOGGER.error("Error receiving the message: {}", ex.getMessage());

                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        LOGGER.info("Too many consecutive errors, stop consumer for {} seconds", errorRetrySeconds);
                        try {
                            Thread.sleep(errorRetrySeconds * 1000L);
                            consecutiveErrors = 0; // reset the consecutiveErrors
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            RUNNING = false;
                        }
                    } else { // add exponential backoff for consecutive errors
                            try {
                                long backoffs = Math.min(1000L * (long) Math.pow(2, consecutiveErrors), 3000L);
                                LOGGER.debug("Backing off for {} seconds", backoffs);
                                Thread.sleep(backoffs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                RUNNING = false;
                            }
                    }
                    return null;
                });
            } catch (Exception e) {
                consecutiveErrors++;
                LOGGER.error("Error in the consumer loop: {}", e.getMessage());

                if (consecutiveErrors >= maxConsecutiveErrors) {
                    LOGGER.error("Too many consecutive errors, stop the consumer for {} seconds", errorRetrySeconds);
                    try {
                        Thread.sleep(1000L * errorRetrySeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        RUNNING = false;
                    }
                } else {
                    try {
                        Long backoffs = Math.min(1000L * (long) Math.pow(2, consecutiveErrors), 3000L);
                        LOGGER.debug("Backing off for {} seconds", backoffs);
                        Thread.sleep(backoffs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        RUNNING = false;
                    }
                }
            }

        }
    }

    public void destroy() {
        stop();
    }

    public void stop() {
        RUNNING = false;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("the service did not terminate within the specified time");
                List<Runnable> droppedTasks =executor.shutdownNow();
                LOGGER.warn("Executor was abruptly shut down. {} tasks will not be executed",
                        droppedTasks.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(e.getMessage());
        }
    }


}

package com.example.github_event_capture.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AsyncQueueserviceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueServiceImpl.class);
    private final SqsAsyncClient sqsAsyncClient;
    private final SqsAsyncBatchManager batchManager;
    private static final String queueUrl = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";
    private final MonitorServiceImpl monitorServiceImpl;

    public AsyncQueueserviceImpl(@Qualifier("sqsAsyncClient") SqsAsyncClient sqsAsyncClient, MonitorServiceImpl monitorServiceImpl) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.batchManager = sqsAsyncClient.batchManager();
        this.monitorServiceImpl = monitorServiceImpl;
    }

    public CompletableFuture<SendMessageResponse> batchSend(String message) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message).build();

        /* use batch manager to send messages in batches */
        return batchManager.sendMessage(sendMessageRequest)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("encountering error when sending message : {}", throwable.getMessage());
                        LOGGER.error(throwable.getMessage(), throwable);
                    } else {
                        LOGGER.info("Sent message successfully");
                        monitorServiceImpl.recordEventCountSqsConsumer(1);
                    }
                }).exceptionally(ex -> {
                    LOGGER.error("error sending message : {}", ex.getMessage());
                    return null;
                });
    }

    /*
     * send a list of messages in batches
     * @param messages the list of messages
     * @return list of CompletableFuture objects for each of the message sending result
     */
    public List<CompletableFuture<SendMessageResponse>> sendMessage(List<String> messages) {
        return messages.stream()
                .map(this::batchSend)
                .collect(Collectors.toList());
    }

    public CompletableFuture<ReceiveMessageResponse> receiveMessage() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build();

        return sqsAsyncClient.receiveMessage(request);

    }

    public CompletableFuture<DeleteMessageResponse> deleteMessage(String receiptHandle) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).build();

        return sqsAsyncClient.deleteMessage(deleteMessageRequest)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error(throwable.getMessage(), throwable);
                    } else {
                        monitorServiceImpl.recordEventCountafterDelete(1);
                        LOGGER.info("Delete message successfully");
                    }
                });
    }
}

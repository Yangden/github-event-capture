package com.example.github_event_capture.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.service.QueueService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AsyncQueueserviceImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueServiceImpl.class);
    private final SqsAsyncClient sqsAsyncClient;
    private final SqsAsyncBatchManager batchManager;
    private static final String queueUrl = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";


    public AsyncQueueserviceImpl(@Qualifier("sqsAsyncClient") SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.batchManager = sqsAsyncClient.batchManager();
    }

    public CompletableFuture<SendMessageResponse> batchSend(String message) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message).build();

        /* use batch manager to send messages in batches */
        return batchManager.sendMessage(sendMessageRequest)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error(throwable.getMessage(), throwable);
                    } else {
                        LOGGER.info("Sent message successfully");
                    }
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

}

package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.QueueService;
import com.example.github_event_capture.service.impl.MonitorServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;



@Service
public class QueueServiceImpl implements QueueService {
    //private static final String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
    private static final String queueUrl = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueServiceImpl.class);
    private final SqsClient sqsClient;

    private final MonitorServiceImpl monitorService;

    public QueueServiceImpl(@Qualifier("sqsClientLocal") SqsClient sqsClient, MonitorServiceImpl monitorService) {
        this.sqsClient = sqsClient;
        this.monitorService = monitorService;
    }

    public void sendMessage(String message) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build();
        try {
            sqsClient.sendMessage(sendMessageRequest);
            LOGGER.info("Message sent");
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    public List<String> receiveMessage( ) {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20) // set to maximum waiting time
                .build();
        try {
            List<Message> messageList = sqsClient.receiveMessage(receiveMessageRequest).messages();
            monitorService.recordEventCountBeforDelete(messageList.size());
            List<String> messageContents = new ArrayList<>();
            for (Message m: messageList) {
                messageContents.add(m.body());
                /* delete the consumed message */
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(m.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
            }
            return messageContents;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        return null;

    }



}

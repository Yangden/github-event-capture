package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.QueueService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.List;

@Service
public class QueueServiceImpl implements QueueService {
    private static final String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
    private static final ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private static final SqsClient sqsClient = SqsClient.builder().region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialsProvider).build();
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueServiceImpl.class);


    public void  sendMessage(String message) {
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
                .waitTimeSeconds(20) // set to maximum waiting time
                .build();
        try {
            List<Message> messageList = sqsClient.receiveMessage(receiveMessageRequest).messages();
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

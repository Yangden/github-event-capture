package com.example.github_event_capture.service;

import com.example.github_event_capture.service.impl.QueueServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import java.util.List;


public class SQStest {
    private static final String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
    private static final ProfileCredentialsProvider profileCredentialProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private static final SqsClient sqsClient = SqsClient.builder().region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialProvider).build();

    private static final Logger LOGGER = LoggerFactory.getLogger(SQStest.class);

    @DisplayName("send message")
    @Test
    public void sendMessage() {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("hello")
                .build();
        try {
            sqsClient.sendMessage(sendMessageRequest);
            LOGGER.info("Message sent");
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }


    }

    @DisplayName("receive message")
    @Test
    public void recieveMessage() {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .build();

        try {
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            LOGGER.info("the number of messages consumed:{}", messages.size());
            for (Message m : messages) {
                System.out.println(m.body());
                // delete the message
                String receiptHandle = m.receiptHandle();
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle).build();
                sqsClient.deleteMessage(deleteMessageRequest);
                LOGGER.info("consumed message deleted");
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

    }

}

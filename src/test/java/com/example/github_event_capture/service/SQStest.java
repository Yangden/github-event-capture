package com.example.github_event_capture.service;

import com.example.github_event_capture.service.impl.QueueServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;


import java.net.URI;
import java.util.List;


public class SQStest {
    /* sts client configuration */
    private final StsClient stsClient = StsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .build();

    /* the role being requested */
    private static final String roleArn = "arn:aws:iam::038462794128:role/github_event_capture";
    private static final String roleSessionName = "test";

    private static final String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
    private static final ProfileCredentialsProvider profileCredentialProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private static final SqsClient sqsClient = SqsClient.builder().region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialProvider).build();

    private static final AwsBasicCredentials credentials = AwsBasicCredentials.create("test", "test");
    private static final StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
    private static final String queueUrlLocal = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";
    private static final SqsClient sqsClientLocal = SqsClient.builder().region(Region.US_EAST_1)
            .endpointOverride(URI.create("http://localhost:4566"))
            .credentialsProvider(credentialsProvider)
            .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(SQStest.class);


    private StsAssumeRoleCredentialsProvider generateCredentials() {
        AssumeRoleRequest request = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(roleSessionName)
                .durationSeconds(3600)
                .build();

        return StsAssumeRoleCredentialsProvider.builder()
                .refreshRequest(request)
                .stsClient(stsClient)
                .build();
    }

    @DisplayName("using credentials from sts")
    @Test
    public void SqsFromSts() {
        try {
            LOGGER.info("build sts client");
            final SqsClient client = SqsClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(generateCredentials())
                    .build();
            LOGGER.info("sts client created");

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody("hello")
                    .build();

            client.sendMessage(sendMessageRequest);
            LOGGER.info("message sent succesfully");

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

    }

    @DisplayName("send message")
    @Test
    public void sendMessage() {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrlLocal)
                .messageBody("hello")
                .build();
        try {
            sqsClientLocal.sendMessage(sendMessageRequest);
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

package com.example.github_event_capture.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class SqsConfiguration {

    @Bean
    public SqsClient sqsClient() {
        // String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
        ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
        return SqsClient.builder().region(Region.US_EAST_1)
                .credentialsProvider(profileCredentialsProvider).build();
    }

    @Bean
    public SqsClient sqsClientLocal() {
        String queueURL = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";
        AwsBasicCredentials credentials = AwsBasicCredentials.create("akid", "skid");
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        return SqsClient.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(credentialsProvider).build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        String queueURL = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/local-demo-queue";
        AwsBasicCredentials credentials = AwsBasicCredentials.create("akid", "skid");
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        return SqsAsyncClient.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}

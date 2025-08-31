package com.example.github_event_capture.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import java.net.URI;
import java.time.Duration;

@Configuration
public class SqsConfiguration {

    @Bean
    public SqsClient sqsClient(@Qualifier("provideCredential")AwsCredentialsProvider credentialProvider) {
        // String queueUrl = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
        ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
        return SqsClient.builder().region(Region.US_EAST_1)
                .credentialsProvider(credentialProvider).build();
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
                .httpClientBuilder(AwsCrtAsyncHttpClient.builder()
                        .maxConcurrency(200)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                        .connectionMaxIdleTime(Duration.ofMinutes(5))
                        .connectionTimeout(Duration.ofSeconds(5)))
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClientCloud(@Qualifier("provideCredential")AwsCredentialsProvider credentialProvider) {
        String queueURL = "https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue";
        ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");

        return SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .httpClientBuilder(AwsCrtAsyncHttpClient.builder()
                        .maxConcurrency(200)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                        .connectionMaxIdleTime(Duration.ofMinutes(5)))
                .credentialsProvider(credentialProvider)
                .build();
    }
}

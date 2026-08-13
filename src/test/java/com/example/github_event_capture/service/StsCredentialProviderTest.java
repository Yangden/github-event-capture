package com.example.github_event_capture.service;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;


public class StsCredentialProviderTest {
    private static final String roleArn = "arn:aws:iam::038462794128:role/github_event_capture";
    private static final String roleSessionName = "test";
    private final Logger LOGGER = LoggerFactory.getLogger(StsCredentialProviderTest.class);

    @Test
    public void testGetCredentials() {
        LOGGER.info("start the test");
        try {
            AssumeRoleRequest request = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName(roleSessionName)
                    .durationSeconds(3600)
                    .build();

            System.out.println("AWS_ACCESS_KEY_ID: " + System.getenv("AWS_ACCESS_KEY_ID"));
            System.out.println("AWS_SECRET_ACCESS_KEY: " + (System.getenv("AWS_SECRET_ACCESS_KEY") != null ? "SET" : "NOT SET"));

            StsClient stsClient = StsClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .build();

            StsAssumeRoleCredentialsProvider credentialsProvider = StsAssumeRoleCredentialsProvider
                    .builder()
                    .stsClient(stsClient)
                    .refreshRequest(request)
                    .build();

            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            LOGGER.info("key pair: {}, {}", credentials.accessKeyId(), credentials.secretAccessKey());
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }
}

package com.example.github_event_capture.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;


@Configuration
public class AwsCredentialsConfig {
    private static final String roleArn = "arn:aws:iam::038462794128:role/github_event_capture";
    private static final String roleSessionName = "test";
    private final StsClient stsClient = StsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .build();

    @Bean
    public StsAssumeRoleCredentialsProvider provideCredential() {
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

}

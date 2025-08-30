package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.AwsCredentialService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;

public class AwsCredentialServiceImpl implements AwsCredentialService {
    private static final String roleArn = "arn:aws:iam::038462794128:role/github_event_capture";
    private static final String roleSessionName = "github-event-capture";

    public StsAssumeRoleCredentialsProvider getCredential() {
        AssumeRoleRequest request = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(roleSessionName)
                .durationSeconds(3600)
                .build();

        StsClient stsClient = StsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();

        return StsAssumeRoleCredentialsProvider.builder()
                .refreshRequest(request)
                .stsClient(stsClient)
                .build();

    }
}

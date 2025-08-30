package com.example.github_event_capture.service;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public interface AwsCredentialService {
    AwsCredentialsProvider getCredential();
}

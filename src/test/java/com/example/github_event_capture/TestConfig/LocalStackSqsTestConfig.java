package com.example.github_event_capture.TestConfig;

import java.net.URI;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

// Overrides the production "sqsAsyncClientCloud" bean (see SqsConfiguration) so that
// AsyncQueueserviceImpl.batchSend()/receiveMessage()/deleteMessage() — which hardcode the
// production queue URL https://sqs.us-east-1.amazonaws.com/038462794128/EventNotificationsQueue —
// resolve against local LocalStack instead of real AWS.
//
// LocalStack account-namespace trick: a 12-digit access key id ("038462794128", matching the
// account segment of the hardcoded queue URL) puts LocalStack's in-memory state under that
// account namespace, so the AWS-shaped queue URL resolves to the same queue LocalStack created
// under that account. Verified manually via `aws --endpoint-url=http://localhost:4566` with
// these exact credentials before relying on it here. Both this bean and the test's own SqsClient
// (see IssueAlertIntegrationTest) must use the same access key id for the queue to line up.
@TestConfiguration
public class LocalStackSqsTestConfig {

    public static final String LOCALSTACK_ENDPOINT = "http://localhost:4566";
    public static final String ACCOUNT_ACCESS_KEY_ID = "038462794128";
    public static final String ACCOUNT_SECRET_ACCESS_KEY = "test";

    // Bean name matches the method name ("sqsAsyncClientCloud"), which is how
    // AsyncQueueserviceImpl's @Qualifier("sqsAsyncClientCloud") constructor parameter resolves
    // it — same convention as the production SqsConfiguration bean it overrides.
    @Bean
    public SqsAsyncClient sqsAsyncClientCloud() {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(ACCOUNT_ACCESS_KEY_ID, ACCOUNT_SECRET_ACCESS_KEY);
        return SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}

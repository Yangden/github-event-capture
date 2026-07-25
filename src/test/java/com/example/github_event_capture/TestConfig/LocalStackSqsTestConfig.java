package com.example.github_event_capture.TestConfig;

import java.net.URI;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
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
//
// IMPORTANT — why this is a BeanDefinitionRegistryPostProcessor and not a plain @Bean method:
// A plain `@Bean sqsAsyncClientCloud()` here, combined with `spring.main.allow-bean-definition-
// overriding=true`, was found NOT to reliably win against SqsConfiguration's production
// "sqsAsyncClientCloud" bean (which does a real STS AssumeRole using ambient AWS credentials and
// sends to the REAL production queue). Verified directly: with the plain-@Bean approach, the
// SqsAsyncClient actually injected into AsyncQueueserviceImpl was `DefaultSqsAsyncClient` with
// `endpointOverride=Optional.empty` (i.e. the production bean), not the LocalStack one — the
// registration order between this test's @Import'ed configuration and the app's component-
// scanned SqsConfiguration is not guaranteed, and here it favored production. Since scanAndAlert
// calls batchSend() unconditionally when it finds an alertable issue, this silently sent real
// alert messages to the real AWS SQS queue during test runs (confirmed no error was logged —
// only that the message landed somewhere other than LocalStack).
//
// Fix: register a BeanDefinitionRegistryPostProcessor that runs after ALL @Configuration classes
// (both production and test) have had their @Bean method definitions loaded into the registry,
// unconditionally removes whatever "sqsAsyncClientCloud" definition is currently registered, and
// replaces it with a definition built from a supplier that is guaranteed to construct the
// LocalStack-pointed client. This does not depend on configuration-class processing order.
@TestConfiguration
public class LocalStackSqsTestConfig {

    public static final String LOCALSTACK_ENDPOINT = "http://localhost:4566";
    public static final String ACCOUNT_ACCESS_KEY_ID = "038462794128";
    public static final String ACCOUNT_SECRET_ACCESS_KEY = "test";

    // Must be a static @Bean method returning a BeanDefinitionRegistryPostProcessor so Spring
    // instantiates and invokes it early enough (before regular singleton beans, including
    // AsyncQueueserviceImpl, are created) to affect which "sqsAsyncClientCloud" definition wins.
    @Bean
    static BeanDefinitionRegistryPostProcessor sqsAsyncClientCloudOverrideProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                String beanName = "sqsAsyncClientCloud";
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                }
                registry.registerBeanDefinition(beanName, BeanDefinitionBuilder
                        .genericBeanDefinition(SqsAsyncClient.class,
                                LocalStackSqsTestConfig::buildLocalStackAsyncClient)
                        .getBeanDefinition());
            }

            @Override
            public void postProcessBeanFactory(
                    org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {
                // no-op — all work happens in postProcessBeanDefinitionRegistry
            }
        };
    }

    private static SqsAsyncClient buildLocalStackAsyncClient() {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(ACCOUNT_ACCESS_KEY_ID, ACCOUNT_SECRET_ACCESS_KEY);
        return SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}

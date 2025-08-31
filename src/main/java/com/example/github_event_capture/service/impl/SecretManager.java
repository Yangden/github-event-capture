package com.example.github_event_capture.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

@Service
public class SecretManager {
    private static final Region region = Region.US_EAST_1;
    private static final String secretName = "key-for-jwt";
    private static final String secretKey = "github-event-capture-jwt";
    private static Logger LOGGER = LoggerFactory.getLogger(SecretManager.class);
    private static final ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private final AwsCredentialsProvider awsCredentialsProvider;

    public SecretManager(@Qualifier("provideCredential") AwsCredentialsProvider credentialProvider) {
        this.awsCredentialsProvider = credentialProvider;
    }

    public String GetSecretValue() {

        try {
            /* secret manager client */
            SecretsManagerClient client = SecretsManagerClient.builder()
                    .credentialsProvider(awsCredentialsProvider)
                    .region(region)
                    .build();

            /* Get request for the secret */
            GetSecretValueRequest requestForKey = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            /* send the request and obtain the key */
            GetSecretValueResponse responseFromManager = client.getSecretValue(requestForKey);
            JSONObject secretJson = new JSONObject(responseFromManager.secretString());
            String secret = (String) secretJson.get(secretKey);
            LOGGER.info("Secret Value: {}", secret);

            return secret;
        } catch (SecretsManagerException e) {
            LOGGER.error(e.getMessage());
            /* how to handle this exception flow */
            return null;
        }


    }
}
